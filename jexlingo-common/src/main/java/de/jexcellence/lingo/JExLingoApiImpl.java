package de.jexcellence.lingo;

import de.jexcellence.lingo.api.JExLingoApi;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.api.TranslationRequest;
import de.jexcellence.lingo.api.TranslationResult;
import de.jexcellence.lingo.api.provider.DetectedLanguage;
import de.jexcellence.lingo.api.provider.TranslationProvider;
import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.pipeline.TranslateOptions;
import de.jexcellence.lingo.pipeline.TranslationPipeline;
import de.jexcellence.lingo.provider.ProviderGateway;
import de.jexcellence.lingo.provider.ProviderRegistry;
import de.jexcellence.lingo.settings.PlayerSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The {@link JExLingoApi} registered in the services manager.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class JExLingoApiImpl implements JExLingoApi {

    private final TranslationPipeline pipeline;
    private final ProviderGateway gateway;
    private final ProviderRegistry providers;
    private final LanguageResolver resolver;
    private final PlayerSettingsService settings;

    /**
     * Creates the API.
     *
     * @param pipeline  the translation pipeline
     * @param gateway   the provider gateway
     * @param providers the provider registry
     * @param resolver  the language resolver
     * @param settings  player settings
     */
    public JExLingoApiImpl(@NotNull TranslationPipeline pipeline, @NotNull ProviderGateway gateway,
                           @NotNull ProviderRegistry providers, @NotNull LanguageResolver resolver,
                           @NotNull PlayerSettingsService settings) {
        this.pipeline = pipeline;
        this.gateway = gateway;
        this.providers = providers;
        this.resolver = resolver;
        this.settings = settings;
    }

    @Override
    public @NotNull CompletableFuture<TranslationResult> translate(@NotNull TranslationRequest request) {
        LanguageCode fallback = resolver.languages().fallback();
        CompletableFuture<LanguageCode> source = request.source() != null
                ? CompletableFuture.completedFuture(request.source())
                : gateway.detect(request.text()).thenApply(guess -> guess.map(DetectedLanguage::language)
                        .orElse(fallback));
        return source.thenCompose(language -> pipeline.translate(request, language, TranslateOptions.DEFAULT))
                .exceptionally(error -> TranslationResult.unchanged(request.text(), fallback, request.target(),
                        TranslationOrigin.FALLBACK));
    }

    @Override
    public @NotNull LanguageCode languageOf(@NotNull UUID player) {
        return resolver.resolve(player);
    }

    @Override
    public @NotNull CompletableFuture<Void> setLanguage(@NotNull UUID player, @Nullable LanguageCode language) {
        return settings.update(player, current -> current.withLanguage(language)).thenApply(ignored -> null);
    }

    @Override
    public @NotNull Set<LanguageCode> enabledLanguages() {
        return Set.copyOf(resolver.languages().enabled());
    }

    @Override
    public void registerProvider(@NotNull TranslationProvider provider) {
        providers.register(provider);
    }
}
