package de.jexcellence.lingo.config;

/**
 * Language detection of chat messages: optional per message, and a cheap sample of each player's first messages
 * to learn which language they really write in.
 *
 * @param enabled        whether every message is checked with the provider's detection
 * @param minLength      shortest message that is checked
 * @param minConfidence  lowest confidence (0-100) that counts
 * @param learnWriting   whether the first messages of a session are sampled to suggest a writing language
 * @param learnSamples   how many messages per session are sampled
 * @param learnThreshold how many samples must agree before the player gets a suggestion
 * @author JExcellence
 * @since 0.1.0
 */
public record DetectionSettings(boolean enabled, int minLength, int minConfidence, boolean learnWriting,
                                int learnSamples, int learnThreshold) {
}
