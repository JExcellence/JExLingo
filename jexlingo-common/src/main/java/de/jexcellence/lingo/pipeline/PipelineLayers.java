package de.jexcellence.lingo.pipeline;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * The learning layers in front of the provider. The free edition uses {@link #glossaryOnly(Function)}.
 *
 * @param memory   approved corrections
 * @param phrases  pinned frequent phrases
 * @param glossary glossary terms per language pair
 * @param listener notified about provider and cache results
 * @author JExcellence
 * @since 0.1.0
 */
public record PipelineLayers(
        @NotNull TranslationLookup memory,
        @NotNull TranslationLookup phrases,
        @NotNull Function<LanguagePair, MaskRules> glossary,
        @NotNull ProviderResultListener listener
) {

    /**
     * Layers with a glossary but without memory, phrases and listener.
     *
     * @param glossary glossary terms per language pair
     * @return the layers
     */
    public static @NotNull PipelineLayers glossaryOnly(@NotNull Function<LanguagePair, MaskRules> glossary) {
        return new PipelineLayers(TranslationLookup.NONE, TranslationLookup.NONE, glossary,
                ProviderResultListener.NONE);
    }
}
