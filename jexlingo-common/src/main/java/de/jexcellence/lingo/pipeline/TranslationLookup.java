package de.jexcellence.lingo.pipeline;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * An exact-match store in front of the provider, such as the translation memory or the pinned phrases.
 *
 * @author JExcellence
 * @since 0.1.0
 */
@FunctionalInterface
public interface TranslationLookup {

    /** A store without entries. */
    TranslationLookup NONE = (pair, key) -> Optional.empty();

    /**
     * Finds a stored translation.
     *
     * @param pair the language pair
     * @param key  the normalized text
     * @return the stored translation
     */
    @NotNull Optional<String> find(@NotNull LanguagePair pair, @NotNull String key);
}
