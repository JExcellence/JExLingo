package de.jexcellence.lingo.provider;

import de.jexcellence.lingo.LingoEdition;
import de.jexcellence.lingo.api.provider.TranslationProvider;
import de.jexcellence.lingo.config.ProviderSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Picks the active provider from {@code provider.type}. LibreTranslate is built in; other providers are registered
 * by plugins through the API and may only be selected in the Premium edition.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class ProviderRegistry {

    private final LingoEdition edition;
    private final Logger logger;
    private final Map<String, TranslationProvider> registered = new ConcurrentHashMap<>();
    private final AtomicReference<String> wanted = new AtomicReference<>(LibreTranslateProvider.ID);
    private final AtomicReference<ProviderGateway> gateway = new AtomicReference<>();

    /**
     * Creates the registry.
     *
     * @param edition the edition
     * @param logger  the plugin logger
     */
    public ProviderRegistry(@NotNull LingoEdition edition, @NotNull Logger logger) {
        this.edition = edition;
        this.logger = logger;
    }

    /**
     * Creates the provider the settings ask for.
     *
     * @param settings the provider settings
     * @return the provider to activate
     */
    public @NotNull TranslationProvider select(@NotNull ProviderSettings settings) {
        String type = settings.type().toLowerCase(Locale.ROOT);
        wanted.set(type);
        if (LibreTranslateProvider.ID.equals(type)) {
            return new LibreTranslateProvider(settings);
        }
        TranslationProvider custom = registered.get(type);
        if (custom != null && edition.customProvidersEnabled()) {
            return custom;
        }
        if (custom != null) {
            logger.log(Level.WARNING, () -> "Provider '" + type + "' needs JExLingo Premium; using LibreTranslate.");
        } else {
            logger.log(Level.WARNING, () -> "Provider '" + type + "' is not registered yet; using LibreTranslate"
                    + " until it registers.");
        }
        return new LibreTranslateProvider(settings);
    }

    /**
     * Connects the registry to the gateway, so a late registration can take over.
     *
     * @param value the gateway
     */
    public void attach(@NotNull ProviderGateway value) {
        gateway.set(value);
    }

    /**
     * Registers a provider from another plugin.
     *
     * @param provider the provider
     */
    public void register(@NotNull TranslationProvider provider) {
        String id = provider.id().toLowerCase(Locale.ROOT);
        registered.put(id, provider);
        ProviderGateway active = gateway.get();
        if (active != null && id.equals(wanted.get()) && edition.customProvidersEnabled()) {
            active.switchTo(provider);
            logger.log(Level.INFO, () -> "Switched to translation provider '" + id + "'.");
        }
    }
}
