package de.jexcellence.lingo.config;

import org.jetbrains.annotations.NotNull;

/**
 * What JExLingo keeps: the in-memory result cache and the daily statistics.
 *
 * @param cache      in-memory cache of provider results
 * @param statistics daily statistics
 * @author JExcellence
 * @since 0.3.0
 */
public record StorageSettings(@NotNull CacheSettings cache, @NotNull StatisticsSettings statistics) {
}
