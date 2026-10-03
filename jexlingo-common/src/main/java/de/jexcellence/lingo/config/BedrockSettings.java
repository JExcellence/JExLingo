package de.jexcellence.lingo.config;

/**
 * Options for Bedrock viewers, who have no hover text.
 *
 * @param showOriginalLine whether Bedrock viewers get the original as a second line under a translation
 * @author JExcellence
 * @since 0.1.0
 */
public record BedrockSettings(boolean showOriginalLine) {
}
