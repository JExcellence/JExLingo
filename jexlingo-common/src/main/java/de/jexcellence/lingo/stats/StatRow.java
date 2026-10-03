package de.jexcellence.lingo.stats;

import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.pipeline.LanguagePair;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;

/**
 * Totals of one day, language pair and result source.
 *
 * @param day          the day
 * @param pair         the language pair
 * @param origin       where the results came from
 * @param translations number of results
 * @param characters   characters of the originals
 * @param latencyMs    summed latency of the results
 * @author JExcellence
 * @since 0.3.0
 */
public record StatRow(
        @NotNull LocalDate day,
        @NotNull LanguagePair pair,
        @NotNull TranslationOrigin origin,
        long translations,
        long characters,
        long latencyMs
) {
}
