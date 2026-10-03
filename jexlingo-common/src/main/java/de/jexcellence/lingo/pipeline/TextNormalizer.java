package de.jexcellence.lingo.pipeline;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Builds lookup keys: the same line typed with other spacing or case finds the same cache, memory and phrase entry.
 * The text shown to players keeps its original form.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class TextNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");

    private TextNormalizer() {
    }

    /**
     * The lookup key of a text.
     *
     * @param text the text
     * @return trimmed, whitespace collapsed, lower case
     */
    public static @NotNull String key(@NotNull String text) {
        return WHITESPACE.matcher(text.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    /**
     * The text with whitespace collapsed, for display and storage.
     *
     * @param text the text
     * @return trimmed with single spaces
     */
    public static @NotNull String clean(@NotNull String text) {
        return WHITESPACE.matcher(text.trim()).replaceAll(" ");
    }

    /**
     * Whether the text contains at least one letter in any script.
     *
     * @param text the text
     * @return {@code false} for numbers, punctuation and symbols only
     */
    public static boolean hasLetters(@NotNull String text) {
        return LETTER.matcher(text).find();
    }
}
