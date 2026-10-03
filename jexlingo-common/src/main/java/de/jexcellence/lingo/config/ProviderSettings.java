package de.jexcellence.lingo.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.time.Duration;

/**
 * Connection to the translation backend.
 *
 * @param type                  provider id, {@code libretranslate} by default
 * @param url                   base URL of the backend
 * @param apiKey                API key, or {@code null}; {@code JEXLINGO_API_KEY} wins over the config
 * @param connectTimeout        TCP connect timeout
 * @param requestTimeout        timeout for one request
 * @param maxConcurrentRequests requests in flight at the same time
 * @param breaker               circuit breaker and health check
 * @author JExcellence
 * @since 0.1.0
 */
public record ProviderSettings(
        @NotNull String type,
        @NotNull URI url,
        @Nullable String apiKey,
        @NotNull Duration connectTimeout,
        @NotNull Duration requestTimeout,
        int maxConcurrentRequests,
        @NotNull BreakerSettings breaker
) {

    /**
     * Returns whether an API key is set.
     *
     * @return whether an API key is set
     */
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Returns the settings as text for logs, with the API key hidden.
     *
     * @return the settings as text for logs, with the API key hidden
     */
    @Override
    public @NotNull String toString() {
        return "ProviderSettings[type=" + type + ", url=" + url + ", apiKey=" + (hasApiKey() ? "set" : "none")
                + ", connectTimeout=" + connectTimeout + ", requestTimeout=" + requestTimeout
                + ", maxConcurrentRequests=" + maxConcurrentRequests + ", breaker=" + breaker + "]";
    }
}
