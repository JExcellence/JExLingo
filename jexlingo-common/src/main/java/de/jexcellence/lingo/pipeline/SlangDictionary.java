package de.jexcellence.lingo.pipeline;

import de.jexcellence.lingo.api.LanguageCode;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Chat abbreviations per source language ({@code u} -> {@code you}, {@code vllt} -> {@code vielleicht}). Machine
 * translation models are trained on written text and translate chat slang badly; expanding it first gives clearly
 * better results. Only the text sent to the provider is expanded, players never see the expansion.
 *
 * @author JExcellence
 * @since 0.2.0
 */
public final class SlangDictionary {

    /** A dictionary without entries. */
    public static final SlangDictionary EMPTY = new SlangDictionary(Map.of());

    private final Map<LanguageCode, Map<String, String>> entries;
    private final Map<LanguageCode, Pattern> patterns;

    /**
     * Creates a dictionary.
     *
     * @param entries abbreviation (any case) to expansion, per source language
     */
    public SlangDictionary(@NotNull Map<LanguageCode, Map<String, String>> entries) {
        Map<LanguageCode, Map<String, String>> lower = new HashMap<>();
        Map<LanguageCode, Pattern> compiled = new HashMap<>();
        entries.forEach((language, words) -> {
            Map<String, String> byLower = words.entrySet().stream()
                    .filter(entry -> !entry.getKey().isBlank() && !entry.getValue().isBlank())
                    .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey().trim().toLowerCase(Locale.ROOT),
                            entry -> entry.getValue().trim(), (first, second) -> first));
            if (!byLower.isEmpty()) {
                lower.put(language, byLower);
                compiled.put(language, compile(byLower));
            }
        });
        this.entries = Map.copyOf(lower);
        this.patterns = Map.copyOf(compiled);
    }

    /**
     * Reads {@code slang.yml}: one section per language code with {@code abbreviation: expansion} pairs.
     *
     * @param root    the file root
     * @param warning receives a message for every skipped section
     * @return the dictionary
     */
    public static @NotNull SlangDictionary parse(@NotNull ConfigurationSection root, @NotNull Consumer<String> warning) {
        Map<LanguageCode, Map<String, String>> entries = new HashMap<>();
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            LanguageCode language = LanguageCode.parse(key).orElse(null);
            if (section == null || language == null) {
                warning.accept("slang.yml: '" + key + "' is no language section; skipped");
                continue;
            }
            Map<String, String> words = new HashMap<>();
            section.getKeys(false).forEach(word -> words.put(word, section.getString(word, "")));
            entries.put(language, words);
        }
        return new SlangDictionary(entries);
    }

    /**
     * Expands every abbreviation of the source language. Whole words only; a capitalized abbreviation gets a
     * capitalized expansion.
     *
     * @param text     the text
     * @param language the language the text is written in
     * @return the expanded text
     */
    public @NotNull String expand(@NotNull String text, @NotNull LanguageCode language) {
        Pattern pattern = patterns.get(language);
        if (pattern == null) {
            return text;
        }
        Map<String, String> words = entries.get(language);
        Matcher matcher = pattern.matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 16);
        while (matcher.find()) {
            String found = matcher.group();
            String expansion = words.get(found.toLowerCase(Locale.ROOT));
            matcher.appendReplacement(out, Matcher.quoteReplacement(matchCase(found, expansion)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Returns the number of abbreviations for all languages.
     *
     * @return the number of abbreviations for all languages
     */
    public int size() {
        return entries.values().stream().mapToInt(Map::size).sum();
    }

    private static @NotNull String matchCase(@NotNull String found, @NotNull String expansion) {
        boolean capitalized = Character.isUpperCase(found.codePointAt(0)) && found.length() > 1
                && !found.toUpperCase(Locale.ROOT).equals(found);
        if (!capitalized || expansion.isEmpty()) {
            return expansion;
        }
        return expansion.substring(0, 1).toUpperCase(Locale.ROOT) + expansion.substring(1);
    }

    private static @NotNull Pattern compile(@NotNull Map<String, String> words) {
        String alternatives = words.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return Pattern.compile("(?<![\\p{L}\\p{N}_{}])(?:" + alternatives + ")(?![\\p{L}\\p{N}_{}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
