package de.jexcellence.lingo.pipeline;

import org.jetbrains.annotations.NotNull;

/**
 * Receives quality signals of the pipeline per language pair, for example the daily statistics.
 *
 * @author JExcellence
 * @since 0.5.0
 */
public interface PipelineQualitySink {

    /**
     * The provider answer for this pair came back with a broken protected token.
     *
     * @param pair the language pair
     */
    void tokenLoss(@NotNull LanguagePair pair);

    /**
     * A broken answer for this pair was saved by translating the line piece by piece.
     *
     * @param pair the language pair
     */
    void recovery(@NotNull LanguagePair pair);
}
