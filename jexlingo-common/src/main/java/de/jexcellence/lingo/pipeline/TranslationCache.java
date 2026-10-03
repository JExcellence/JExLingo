package de.jexcellence.lingo.pipeline;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.jexcellence.lingo.config.CacheSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * In-memory cache of provider results, keyed by language pair and normalized text. Lives only in memory and is
 * never written to disk, so chat contents are not stored.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class TranslationCache {

    private final Cache<String, String> entries;

    /**
     * Creates the cache.
     *
     * @param settings size and lifetime
     */
    public TranslationCache(@NotNull CacheSettings settings) {
        this.entries = Caffeine.newBuilder()
                .maximumSize(settings.maxEntries())
                .expireAfterWrite(settings.ttl())
                .recordStats()
                .build();
    }

    /**
     * Looks up a translation.
     *
     * @param pair the language pair
     * @param key  the normalized text
     * @return the cached translation
     */
    public @NotNull Optional<String> get(@NotNull LanguagePair pair, @NotNull String key) {
        return Optional.ofNullable(entries.getIfPresent(cacheKey(pair, key)));
    }

    /**
     * Stores a translation.
     *
     * @param pair        the language pair
     * @param key         the normalized text
     * @param translation the translation
     */
    public void put(@NotNull LanguagePair pair, @NotNull String key, @NotNull String translation) {
        entries.put(cacheKey(pair, key), translation);
    }

    /** Removes every entry, for example after the glossary changed. */
    public void clear() {
        entries.invalidateAll();
    }

    /**
     * Returns the number of entries (approximate).
     *
     * @return the number of entries (approximate)
     */
    public long size() {
        return entries.estimatedSize();
    }

    /**
     * Returns the share of lookups that hit, from 0 to 1.
     *
     * @return the share of lookups that hit, from 0 to 1
     */
    public double hitRate() {
        return entries.stats().hitRate();
    }

    private static @NotNull String cacheKey(@NotNull LanguagePair pair, @NotNull String key) {
        return pair.key() + '\u0000' + key;
    }
}
