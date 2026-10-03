package de.jexcellence.lingo.api;

import org.jetbrains.annotations.NotNull;

/**
 * The outcome of one translation. Never fails: when the provider cannot help, {@link #text()} is the original and
 * {@link #origin()} says why.
 *
 * @param text          the text to show
 * @param original      the text as written
 * @param source        the language of the original
 * @param target        the language of {@link #text()}
 * @param origin        which layer produced the text
 * @param latencyMillis how long the translation took
 * @author JExcellence
 * @since 0.1.0
 */
public record TranslationResult(
        @NotNull String text,
        @NotNull String original,
        @NotNull LanguageCode source,
        @NotNull LanguageCode target,
        @NotNull TranslationOrigin origin,
        long latencyMillis
) {

    /**
     * A result that keeps the original text.
     *
     * @param original the original text
     * @param source   the source language
     * @param target   the target language
     * @param origin   why nothing was translated
     * @return the unchanged result
     */
    public static @NotNull TranslationResult unchanged(@NotNull String original, @NotNull LanguageCode source,
                                                       @NotNull LanguageCode target, @NotNull TranslationOrigin origin) {
        return new TranslationResult(original, original, source, target, origin, 0L);
    }

    /**
     * Returns whether the shown text differs from the original.
     *
     * @return whether the shown text differs from the original
     */
    public boolean translated() {
        return origin.translates() && !text.equals(original);
    }
}
