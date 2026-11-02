package de.jexcellence.lingo.pipeline;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
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
 * {@code {0}}, {@code {1}}, ... that the translator leaves alone; {@link #unmask(String, MaskedText)} puts the
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
            + "|@\\w{2,16}";
    private static final Pattern FIXED_ONLY = Pattern.compile(FIXED_PARTS);
    private static final Pattern TOKEN = Pattern.compile("\\{\\s*(\\d{1,3})\\s*\\}");
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
     */
    public record MaskedText(@NotNull String text, @NotNull List<String> restores, boolean forced) {

        /**
         * Copies the list.
         *
         * @param text     the text with tokens
         * @param restores token replacements
         * @param forced   whether a force term was replaced
         */
        public MaskedText {
            restores = List.copyOf(restores);
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
            return TextNormalizer.hasLetters(TOKEN.matcher(text).replaceAll(" "));
        }
    }

    /**
     * Masks a text.
     *
     * @param text  the original text
     * @param rules glossary terms of the language pair
     * @param names online player names
     * @return the masked text
     */
    public static @NotNull MaskedText mask(@NotNull String text, @NotNull MaskRules rules,
                                           @NotNull Collection<String> names) {
        Pattern pattern = patternFor(rules, names);
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 8);
        List<String> restores = new ArrayList<>();
        boolean forced = false;
        while (matcher.find()) {
            String matched = matcher.group();
            Optional<String> replacement = rules.forced(matched);
            forced |= replacement.isPresent();
            matcher.appendReplacement(out, Matcher.quoteReplacement("{" + restores.size() + "}"));
            restores.add(replacement.orElse(matched));
        }
        matcher.appendTail(out);
        return new MaskedText(out.toString(), restores, forced);
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
        while (matcher.find() && valid) {
            int index = Integer.parseInt(matcher.group(1));
            valid = index < masked.restores().size() && seen.add(index);
            if (valid) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(masked.restores().get(index)));
            }
        }
        if (!valid || seen.size() != masked.restores().size()) {
            return Optional.empty();
        }
        matcher.appendTail(out);
        return Optional.of(out.toString());
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

    private static @NotNull Pattern patternFor(@NotNull MaskRules rules, @NotNull Collection<String> names) {
        if (rules.isEmpty() && names.isEmpty()) {
            return FIXED_ONLY;
        }
        return PATTERNS.get(new PatternKey(rules, List.copyOf(names)), TokenMasker::compile);
    }

    private static @NotNull Pattern compile(@NotNull PatternKey key) {
        List<String> anyCase = new ArrayList<>(key.rules().keepTerms());
        anyCase.addAll(key.rules().forceTerms().keySet());
        List<String> exactCase = new ArrayList<>();
        for (String name : key.names()) {
            String trimmed = name.trim();
            if (isPlainWordName(trimmed)) {
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

    private record PatternKey(@NotNull MaskRules rules, @NotNull List<String> names) {
    }
}
