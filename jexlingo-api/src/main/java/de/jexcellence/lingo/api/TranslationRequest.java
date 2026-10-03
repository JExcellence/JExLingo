package de.jexcellence.lingo.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One text to translate into one language.
 *
 * @param text    the text as written
 * @param source  the language the text is written in, or {@code null} to let the provider detect it
 * @param target  the language to translate into
 * @param context where the text comes from
 * @author JExcellence
 * @since 0.1.0
 */
public record TranslationRequest(
        @NotNull String text,
        @Nullable LanguageCode source,
        @NotNull LanguageCode target,
        @NotNull TranslationContext context
) {

    /**
     * A request from another plugin with a known source language.
     *
     * @param text   the text
     * @param source the source language
     * @param target the target language
     * @return the request with context {@link TranslationContext#API}
     */
    public static @NotNull TranslationRequest of(@NotNull String text, @NotNull LanguageCode source,
                                                 @NotNull LanguageCode target) {
        return new TranslationRequest(text, source, target, TranslationContext.API);
    }
}
