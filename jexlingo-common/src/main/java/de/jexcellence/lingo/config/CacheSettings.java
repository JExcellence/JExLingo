package de.jexcellence.lingo.config;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * In-memory cache of provider results.
 *
 * @param maxEntries most cached translations
 * @param ttl        how long an entry stays after it was written
 * @author JExcellence
 * @since 0.1.0
 */
public record CacheSettings(int maxEntries, @NotNull Duration ttl) {
}
