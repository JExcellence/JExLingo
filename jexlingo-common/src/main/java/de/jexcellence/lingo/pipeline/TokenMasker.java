package de.jexcellence.lingo.pipeline;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Protects parts of a message from the translator: colour codes, URLs, {@code {placeholders}}, {@code [item]}-style chat tokens,
 * {@code @mentions}, online player names and glossary terms. Names that are ordinary words ({@code Max},
 * {@code Help}) do not count when typed in lower case. Each protected part becomes a numbered token
 * {@code Q0Q}, {@code Q1Q}, ... that the translator keeps like a name; {@link #unmask(String, MaskedText)} puts the
 * original back, or the fixed translation for a force term. If the translator lost or invented a token, unmasking
 * fails and the caller shows the original instead of a broken line.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class TokenMasker {

    private static final String FIXED_PARTS = FormatCodes.CODE
            + "|https?://\\S+|www\\.\\S+"
            + "|\\{[^{}\\s]{1,32}\\}"
            + "|\\[[A-Za-z0-9_]{1,32}\\]"
            + "|@\\w{2,16}"
            + "|(?<![\\p{L}\\p{N}])Q\\d{1,3}Q(?![\\p{L}\\p{N}])";
    private static final Pattern FIXED_ONLY = Pattern.compile(FIXED_PARTS);
    /**
     * A token as the translator may return it, with changed case or an inserted space ({@code q0q}, {@code Q 0 Q}).
     * Measured on LibreTranslate de to en: {@code {0}} was lost in 3 of 4 lines, {@code Q0Q} kept in all of them
     * with natural word order, because the model reads it as a name.
     */
    private static final Pattern TOKEN = Pattern.compile("(?<![\\p{L}\\p{N}])[Qq] ?(\\d{1,3}) ?[Qq](?![\\p{L}\\p{N}])");
    private static final Pattern PLAIN_WORD = Pattern.compile("\\p{L}+");
    private static final int PATTERN_CACHE_SIZE = 128;
    /** Compiled patterns by glossary rules and online names; both change rarely compared to chat lines. */
    private static final Cache<PatternKey, Pattern> PATTERNS = Caffeine.newBuilder()
            .maximumSize(PATTERN_CACHE_SIZE)
            .build();

    private TokenMasker() {
    }

    /**
     * A masked text and what each token stands for.
     *
     * @param text     the text with tokens
     * @param restores the replacement of token {@code i} at index {@code i}
     * @param forced   whether at least one force term was replaced
     * @param glue     per token: {@link #GLUE_BEFORE} and {@link #GLUE_AFTER} when a space was added because the
     *                 protected part touched a letter or digit ({@code &awirklich}); the space is removed again
     */
    public record MaskedText(@NotNull String text, @NotNull List<String> restores, boolean forced,
                             @NotNull List<Integer> glue) {

        /** The token was glued to the character before it. */
        public static final int GLUE_BEFORE = 1;
        /** The token was glued to the character after it. */
        public static final int GLUE_AFTER = 2;

        /**
         * Copies the lists.
         *
         * @param text     the text with tokens
         * @param restores token replacements
         * @param forced   whether a force term was replaced
         * @param glue     glue flags per token
         */
        public MaskedText {
            restores = List.copyOf(restores);
            glue = List.copyOf(glue);
        }

        /**
         * Creates a masked text whose tokens were not glued to anything.
         *
         * @param text     the text with tokens
         * @param restores token replacements
         * @param forced   whether a force term was replaced
         */
        public MaskedText(@NotNull String text, @NotNull List<String> restores, boolean forced) {
            this(text, restores, forced, Collections.nCopies(restores.size(), 0));
        }

        private int glueOf(int index) {
            return index < glue.size() ? glue.get(index) : 0;
        }

        /**
         * Returns whether nothing was masked.
         *
         * @return whether nothing was masked
         */
        public boolean isPlain() {
            return restores.isEmpty();
        }

        /**
         * Returns whether the text has words left for the translator once the tokens are removed.
         *
         * @return whether the text has words left for the translator once the tokens are removed
         */
        public boolean hasTranslatableText() {
            return TextNormalizer.hasLetters(withoutTokens(text));
        }
    }

    /**
     * Masks a text with the {@link NameProtection#SMART} name rule.
     *
     * @param text  the original text
     * @param rules glossary terms of the language pair
     * @param names online player names
     * @return the masked text
     */
    public static @NotNull MaskedText mask(@NotNull String text, @NotNull MaskRules rules,
                                           @NotNull Collection<String> names) {
        return mask(text, rules, names, NameProtection.SMART);
    }

    /**
     * Masks a text.
     *
     * @param text       the original text
     * @param rules      glossary terms of the language pair
     * @param names      online player names
     * @param protection which of the names are protected
     * @return the masked text
     */
    public static @NotNull MaskedText mask(@NotNull String text, @NotNull MaskRules rules,
                                           @NotNull Collection<String> names, @NotNull NameProtection protection) {
        Collection<String> protectedNames = protection == NameProtection.MENTIONS ? List.of() : names;
        Pattern pattern = patternFor(rules, protectedNames, protection);
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 8);
        List<String> restores = new ArrayList<>();
        List<Integer> glue = new ArrayList<>();
        boolean forced = false;
        while (matcher.find()) {
            String matched = matcher.group();
            Optional<String> replacement = rules.forced(matched);
            forced |= replacement.isPresent();
            boolean before = matcher.start() > 0 && Character.isLetterOrDigit(text.codePointBefore(matcher.start()));
            boolean after = matcher.end() < text.length() && Character.isLetterOrDigit(text.codePointAt(matcher.end()));
            String padded = (before ? " " : "") + token(restores.size()) + (after ? " " : "");
            matcher.appendReplacement(out, Matcher.quoteReplacement(padded));
            restores.add(replacement.orElse(matched));
            glue.add((before ? MaskedText.GLUE_BEFORE : 0) | (after ? MaskedText.GLUE_AFTER : 0));
        }
        matcher.appendTail(out);
        return new MaskedText(out.toString(), restores, forced, glue);
    }

    /**
     * Restores the tokens in a translated text.
     *
     * @param translated the translator's output
     * @param masked     the masked input it was made from
     * @return the final text, or empty when a token is missing, repeated or unknown
     */
    public static @NotNull Optional<String> unmask(@NotNull String translated, @NotNull MaskedText masked) {
        if (masked.isPlain()) {
            return Optional.of(translated);
        }
        Matcher matcher = TOKEN.matcher(translated);
        StringBuilder out = new StringBuilder(translated.length() + 16);
        Set<Integer> seen = new LinkedHashSet<>();
        boolean valid = true;
        int last = 0;
        while (matcher.find() && valid) {
            int index = Integer.parseInt(matcher.group(1));
            valid = index < masked.restores().size() && seen.add(index) && matcher.start() >= last;
            if (valid) {
                int glue = masked.glueOf(index);
                String between = translated.substring(last, matcher.start());
                if ((glue & MaskedText.GLUE_BEFORE) != 0 && between.endsWith(" ")) {
                    between = between.substring(0, between.length() - 1);
                }
                out.append(between).append(masked.restores().get(index));
                last = matcher.end();
                if ((glue & MaskedText.GLUE_AFTER) != 0 && last < translated.length()
                        && translated.charAt(last) == ' ') {
                    last++;
                }
            }
        }
        if (!valid || seen.size() != masked.restores().size()) {
            return Optional.empty();
        }
        out.append(translated, last, translated.length());
        return Optional.of(out.toString());
    }

    /**
     * Returns the token that stands for protected part {@code index}.
     *
     * @param index the index of the protected part
     * @return the token
     */
    public static @NotNull String token(int index) {
        return "Q" + index + "Q";
    }

    /**
     * Removes every token, for checks that must only look at the words around them.
     *
     * @param masked the masked text
     * @return the text without tokens
     */
    public static @NotNull String withoutTokens(@NotNull String masked) {
        return TOKEN.matcher(masked).replaceAll(" ");
    }

    /**
     * Splits a masked text into tokens and the text between them.
     *
     * @param masked the masked text
     * @return the pieces in order; joined they give the input again
     */
    public static @NotNull List<Segment> segments(@NotNull String masked) {
        List<Segment> parts = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(masked);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                parts.add(Segment.text(masked.substring(last, matcher.start())));
            }
            parts.add(new Segment(matcher.group(), false));
            last = matcher.end();
        }
        if (last < masked.length()) {
            parts.add(Segment.text(masked.substring(last)));
        }
        return parts;
    }

    /**
     * Returns whether a player name looks like an ordinary word ({@code Max}, {@code Insel}) rather than a typical
     * user name ({@code Steve_42}, {@code DarkKnight}). Ordinary-word names are only protected when written as the
     * player is called, capitalised or in capitals, so chat words that happen to be someone's name stay translatable
     * when typed in lower case.
     *
     * @param name the player name
     * @return whether the name is a plain word
     */
    public static boolean isPlainWordName(@NotNull String name) {
        if (!PLAIN_WORD.matcher(name).matches()) {
            return false;
        }
        return name.substring(1).chars().noneMatch(Character::isUpperCase);
    }

    private static @NotNull Pattern patternFor(@NotNull MaskRules rules, @NotNull Collection<String> names,
                                               @NotNull NameProtection protection) {
        if (rules.isEmpty() && names.isEmpty()) {
            return FIXED_ONLY;
        }
        return PATTERNS.get(new PatternKey(rules, List.copyOf(names), protection), TokenMasker::compile);
    }

    private static @NotNull Pattern compile(@NotNull PatternKey key) {
        List<String> anyCase = new ArrayList<>(key.rules().keepTerms());
        anyCase.addAll(key.rules().forceTerms().keySet());
        List<String> exactCase = new ArrayList<>();
        for (String name : key.names()) {
            String trimmed = name.trim();
            if (key.protection() == NameProtection.SMART && isPlainWordName(trimmed)) {
                exactCase.addAll(nameSpellings(trimmed));
            } else {
                anyCase.add(trimmed);
            }
        }
        List<String> groups = new ArrayList<>(2);
        alternatives(anyCase).ifPresent(groups::add);
        alternatives(exactCase).map(words -> "(?-i:" + words + ")").ifPresent(groups::add);
        if (groups.isEmpty()) {
            return FIXED_ONLY;
        }
        return Pattern.compile(FIXED_PARTS + "|(?<![\\p{L}\\p{N}_])(?:" + String.join("|", groups)
                + ")(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** A plain-word name as typed, capitalised and in capitals; an all-lower-case word is read as a chat word. */
    private static @NotNull List<String> nameSpellings(@NotNull String name) {
        if (name.isEmpty()) {
            return List.of();
        }
        String capitalised = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
        return List.of(name, capitalised, name.toUpperCase(Locale.ROOT));
    }

    private static @NotNull Optional<String> alternatives(@NotNull List<String> words) {
        List<String> distinct = words.stream()
                .filter(word -> !word.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        if (distinct.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(distinct.stream().map(Pattern::quote).collect(Collectors.joining("|")));
    }

    /**
     * A piece of a masked text.
     *
     * @param text         the piece
     * @param translatable whether it is text with letters between tokens
     */
    public record Segment(@NotNull String text, boolean translatable) {

        private static @NotNull Segment text(@NotNull String text) {
            return new Segment(text, TextNormalizer.hasLetters(text));
        }
    }

    private record PatternKey(@NotNull MaskRules rules, @NotNull List<String> names,
                              @NotNull NameProtection protection) {
    }
}
