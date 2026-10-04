package de.jexcellence.lingo;

import de.jexcellence.lingo.api.JExLingoApi;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationContext;
import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.api.TranslationRequest;
import de.jexcellence.lingo.api.TranslationResult;
import de.jexcellence.lingo.api.provider.DetectedLanguage;
import de.jexcellence.lingo.api.provider.TranslationProvider;
import de.jexcellence.lingo.chat.TranslationSwitch;
import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.language.LocalLanguageGuess;
import de.jexcellence.lingo.pipeline.TranslateOptions;
import de.jexcellence.lingo.pipeline.TranslationPipeline;
import de.jexcellence.lingo.provider.ProviderGateway;
import de.jexcellence.lingo.provider.ProviderRegistry;
import de.jexcellence.lingo.settings.IncomingMode;
import de.jexcellence.lingo.settings.PlayerSettingsService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
    private final TranslationSwitch toggle;

    /**
     * Creates the API.
     *
     * @param pipeline  the translation pipeline
     * @param gateway   the provider gateway
     * @param providers the provider registry
     * @param resolver  the language resolver
     * @param settings  player settings
     * @param toggle    staff pause switch
     */
    public JExLingoApiImpl(@NotNull TranslationPipeline pipeline, @NotNull ProviderGateway gateway,
                           @NotNull ProviderRegistry providers, @NotNull LanguageResolver resolver,
                           @NotNull PlayerSettingsService settings, @NotNull TranslationSwitch toggle) {
        this.pipeline = pipeline;
        this.gateway = gateway;
        this.providers = providers;
        this.resolver = resolver;
        this.settings = settings;
        this.toggle = toggle;
    }

    @Override
    public @NotNull CompletableFuture<TranslationResult> translateFor(@NotNull UUID writer, @NotNull UUID reader,
                                                                      @NotNull String text,
                                                                      @NotNull TranslationContext context) {
        LanguageCode source = sourceOf(writer, text);
        LanguageCode target = languageOf(reader);
        boolean blocked = toggle.isPaused()
                || !settings.get(writer).translateOutgoing()
                || settings.get(reader).incoming() == IncomingMode.OFF;
        if (blocked) {
            return CompletableFuture.completedFuture(
                    TranslationResult.unchanged(text, source, target, TranslationOrigin.SKIPPED));
        }
        List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return pipeline.translate(new TranslationRequest(text, source, target, context), source,
                        new TranslateOptions(true, names))
                .exceptionally(error -> TranslationResult.unchanged(text, source, target, TranslationOrigin.FALLBACK));
    }

    @Override
    public @NotNull CompletableFuture<TranslationResult> translateFrom(@NotNull UUID writer, @NotNull String text,
                                                                       @NotNull LanguageCode target,
                                                                       @NotNull TranslationContext context) {
        LanguageCode source = sourceOf(writer, text);
        if (toggle.isPaused() || !settings.get(writer).translateOutgoing()) {
            return CompletableFuture.completedFuture(
                    TranslationResult.unchanged(text, source, target, TranslationOrigin.SKIPPED));
        }
        return translate(new TranslationRequest(text, source, target, context));
    }

    @Override
    public @NotNull CompletableFuture<TranslationResult> translateTo(@NotNull UUID reader, @NotNull String text,
                                                                     @Nullable LanguageCode source,
                                                                     @NotNull TranslationContext context) {
        LanguageCode target = languageOf(reader);
        if (toggle.isPaused() || settings.get(reader).incoming() == IncomingMode.OFF) {
            LanguageCode known = source == null ? target : source;
            return CompletableFuture.completedFuture(
                    TranslationResult.unchanged(text, known, target, TranslationOrigin.SKIPPED));
        }
        return translate(new TranslationRequest(text, source, target, context));
    }

    private @NotNull LanguageCode sourceOf(@NotNull UUID writer, @NotNull String text) {
        return LocalLanguageGuess.guess(text, resolver.languages()).orElseGet(() -> writingLanguageOf(writer));
    }

    @Override
    public @NotNull LanguageCode writingLanguageOf(@NotNull UUID player) {
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            return resolver.resolveWriting(online);
        }
        return LanguageResolver.resolveWriting(settings.get(player).writeLanguage(), resolver.resolve(player),
                resolver.languages());
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
