package de.jexcellence.lingo.config;

import de.jexcellence.lingo.api.LanguageCode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The languages this server translates between.
 *
 * @param enabled  enabled languages in config order
 * @param fallback language for players whose client language is not enabled
 * @author JExcellence
 * @since 0.1.0
 */
public record LanguageSettings(@NotNull List<LanguageCode> enabled, @NotNull LanguageCode fallback) {

    /**
     * Copies the list.
     *
     * @param enabled  enabled languages
     * @param fallback fallback language
     */
    public LanguageSettings {
        enabled = List.copyOf(enabled);
    }

    /**
     * Whether a language is enabled.
     *
     * @param language a language
     * @return whether the language is enabled
     */
    public boolean isEnabled(@NotNull LanguageCode language) {
        return enabled.contains(language);
    }
}
