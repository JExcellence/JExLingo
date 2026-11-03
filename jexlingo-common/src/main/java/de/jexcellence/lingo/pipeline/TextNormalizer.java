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
    private static final Pattern REPEATS = Pattern.compile("(\\p{L})\\1{2,}");
    private static final Pattern TRAILING_MARKS = Pattern.compile("[.!\\s]+$");
    /** Apostrophes and commas never change what a chat line means: {@code geht's} and {@code gehts}. */
    private static final Pattern NEUTRAL_MARKS = Pattern.compile("['’`´,]");
    private static final Pattern QUESTION_RUN = Pattern.compile("\\?{2,}");

    private TextNormalizer() {
    }

    /**
     * The lookup key of a text.
     *
     * @param text the text
     * @return trimmed, apostrophes and commas removed, whitespace collapsed, letter spam shortened, repeated
     *         {@code ?} collapsed, trailing {@code .} and {@code !} removed, lower case; a question mark stays
     *         because it changes the meaning, and emoji and quotes stay because a cached answer must contain them
     */
    public static @NotNull String key(@NotNull String text) {
        String neutral = QUESTION_RUN.matcher(NEUTRAL_MARKS.matcher(text).replaceAll("")).replaceAll("?");
        String collapsed = WHITESPACE.matcher(neutral.trim()).replaceAll(" ");
        String shortened = REPEATS.matcher(collapsed).replaceAll("$1$1");
        String trimmed = TRAILING_MARKS.matcher(shortened).replaceAll("");
        return (trimmed.isEmpty() ? shortened : trimmed).toLowerCase(Locale.ROOT);
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
