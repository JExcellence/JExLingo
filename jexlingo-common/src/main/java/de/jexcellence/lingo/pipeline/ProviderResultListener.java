package de.jexcellence.lingo.pipeline;

import org.jetbrains.annotations.NotNull;

/**
 * Notified about every line that was translated by the provider or answered from the cache, so frequent phrases
 * can be counted.
 *
 * @author JExcellence
 * @since 0.1.0
 */
@FunctionalInterface
public interface ProviderResultListener {

    /** Ignores every result. */
    ProviderResultListener NONE = (pair, key, translation) -> {
        // Nothing listens.
    };

    /**
     * Called after a provider or cache result.
     *
     * @param pair        the language pair
     * @param key         the normalized original
     * @param translation the translation
     */
    void onResult(@NotNull LanguagePair pair, @NotNull String key, @NotNull String translation);
}
