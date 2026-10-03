package de.jexcellence.lingo.config;

import org.jetbrains.annotations.NotNull;

/**
 * The whole {@code config.yml} as immutable values. Built by {@link LingoConfigLoader}; services receive the
 * section they need, never raw YAML.
 *
 * @param provider  translation backend connection
 * @param languages enabled languages and fallback
 * @param detection optional language detection
 * @param chat      chat behaviour and limits
 * @param cache     in-memory result cache
 * @param learning  suggestion and phrase promotion limits
 * @param bedrock   Bedrock viewer options
 * @author JExcellence
 * @since 0.1.0
 */
public record LingoConfig(
        @NotNull ProviderSettings provider,
        @NotNull LanguageSettings languages,
        @NotNull DetectionSettings detection,
        @NotNull ChatSettings chat,
        @NotNull CacheSettings cache,
        @NotNull LearningSettings learning,
        @NotNull BedrockSettings bedrock
) {
}
