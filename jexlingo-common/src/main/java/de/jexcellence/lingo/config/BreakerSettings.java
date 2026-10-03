package de.jexcellence.lingo.config;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Circuit breaker and health check of the provider.
 *
 * @param failureThreshold    failures in a row that pause the provider
 * @param openDuration        how long the provider stays paused before one probe request
 * @param healthCheckInterval how often the provider's language list is checked
 * @author JExcellence
 * @since 0.1.0
 */
public record BreakerSettings(int failureThreshold, @NotNull Duration openDuration,
                              @NotNull Duration healthCheckInterval) {
}
