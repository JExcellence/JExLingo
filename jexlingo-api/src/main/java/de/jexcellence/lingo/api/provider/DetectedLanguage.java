package de.jexcellence.lingo.api.provider;

import de.jexcellence.lingo.api.LanguageCode;
import org.jetbrains.annotations.NotNull;

/**
 * A language detection guess.
 *
 * @param language   the detected language
 * @param confidence the confidence from 0 to 100
 * @author JExcellence
 * @since 0.1.0
 */
public record DetectedLanguage(@NotNull LanguageCode language, double confidence) {
}
