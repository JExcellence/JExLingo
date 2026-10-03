package de.jexcellence.lingo.config;

/**
 * Optional language detection of chat messages.
 *
 * @param enabled       whether messages are checked with the provider's detection
 * @param minLength     shortest message that is checked
 * @param minConfidence lowest confidence (0-100) that overrides the sender's language
 * @author JExcellence
 * @since 0.1.0
 */
public record DetectionSettings(boolean enabled, int minLength, int minConfidence) {
}
