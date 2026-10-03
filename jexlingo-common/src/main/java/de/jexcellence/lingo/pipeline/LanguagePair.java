package de.jexcellence.lingo.pipeline;

import de.jexcellence.lingo.api.LanguageCode;
import org.jetbrains.annotations.NotNull;

/**
 * A translation direction.
 *
 * @param source the language of the original
 * @param target the language of the translation
 * @author JExcellence
 * @since 0.1.0
 */
public record LanguagePair(@NotNull LanguageCode source, @NotNull LanguageCode target) {

    /**
     * Returns a compact key such as {@code de>en} for maps and caches.
     *
     * @return a compact key such as {@code de>en} for maps and caches
     */
    public @NotNull String key() {
        return source.code() + ">" + target.code();
    }
}
