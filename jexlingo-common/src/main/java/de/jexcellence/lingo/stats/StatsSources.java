package de.jexcellence.lingo.stats;

import de.jexcellence.lingo.chat.TranslationSwitch;
import de.jexcellence.lingo.learning.PhraseService;
import de.jexcellence.lingo.learning.TranslationMemoryService;
import de.jexcellence.lingo.pipeline.TranslationPipeline;
import de.jexcellence.lingo.provider.ProviderGateway;
import de.jexcellence.lingo.provider.ProviderHealthMonitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the statistics menu, form and chat report read from.
 *
 * @param stats    stored and live daily totals
 * @param gateway  provider calls since start
 * @param health   provider health
 * @param pipeline cache and running translations
 * @param toggle   pause switch
 * @param memory   approved corrections, or {@code null} in the free edition
 * @param phrases  pinned phrases, or {@code null} in the free edition
 * @author JExcellence
 * @since 0.3.0
 */
public record StatsSources(
        @NotNull StatsService stats,
        @NotNull ProviderGateway gateway,
        @NotNull ProviderHealthMonitor health,
        @NotNull TranslationPipeline pipeline,
        @NotNull TranslationSwitch toggle,
        @Nullable TranslationMemoryService memory,
        @Nullable PhraseService phrases
) {
}
