package de.jexcellence.lingo.language;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.provider.DetectedLanguage;
import de.jexcellence.lingo.config.DetectionSettings;
import de.jexcellence.lingo.config.LanguageSettings;
import de.jexcellence.lingo.provider.ProviderGateway;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Optional check of the language a message is actually written in. A German player with an English client who
 * writes German is then translated correctly. Off by default: it costs a second provider call per message.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LanguageDetector {

    private final ProviderGateway gateway;
    private final AtomicReference<DetectionSettings> settings;

    /**
     * Creates the detector.
     *
     * @param gateway  the provider gateway
     * @param settings detection settings
     */
    public LanguageDetector(@NotNull ProviderGateway gateway, @NotNull DetectionSettings settings) {
        this.gateway = gateway;
        this.settings = new AtomicReference<>(settings);
    }

    /**
     * The language of a message.
     *
     * @param text      the message
     * @param assumed   the sender's resolved language
     * @param languages enabled languages
     * @return the detected language when detection is on, confident and enabled; else {@code assumed}
     */
    public @NotNull CompletableFuture<LanguageCode> detect(@NotNull String text, @NotNull LanguageCode assumed,
                                                           @NotNull LanguageSettings languages) {
        DetectionSettings current = settings.get();
        if (!current.enabled() || text.trim().length() < current.minLength()) {
            return CompletableFuture.completedFuture(assumed);
        }
        return gateway.detect(text).thenApply(guess -> guess
                .filter(found -> found.confidence() >= current.minConfidence())
                .map(DetectedLanguage::language)
                .filter(languages::isEnabled)
                .orElse(assumed));
    }

    /**
     * Returns whether detection is switched on.
     *
     * @return whether detection is switched on
     */
    public boolean isEnabled() {
        return settings.get().enabled();
    }

    /**
     * Replaces the settings after a reload.
     *
     * @param value the new settings
     */
    public void setSettings(@NotNull DetectionSettings value) {
        settings.set(value);
    }
}
