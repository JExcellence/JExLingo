package de.jexcellence.lingo;

import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.lingo.config.LingoConfig;
import de.jexcellence.lingo.database.repository.GlossaryTermRepository;
import de.jexcellence.lingo.database.repository.LingoPlayerSettingsRepository;
import de.jexcellence.lingo.database.repository.PinnedPhraseRepository;
import de.jexcellence.lingo.database.repository.TranslationMemoryRepository;
import de.jexcellence.lingo.glossary.GlossaryService;
import de.jexcellence.lingo.language.LanguageDetector;
import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.learning.PhraseService;
import de.jexcellence.lingo.learning.TranslationMemoryService;
import de.jexcellence.lingo.pipeline.PipelineLayers;
import de.jexcellence.lingo.pipeline.SkipRules;
import de.jexcellence.lingo.pipeline.TranslationCache;
import de.jexcellence.lingo.pipeline.TranslationPipeline;
import de.jexcellence.lingo.provider.CircuitBreaker;
import de.jexcellence.lingo.provider.ProviderGateway;
import de.jexcellence.lingo.provider.ProviderHealthMonitor;
import de.jexcellence.lingo.provider.ProviderRegistry;
import de.jexcellence.lingo.settings.PlayerSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * The translation services without any Bukkit front end: provider, pipeline, settings, languages, glossary and the
 * learning layer. Built once per enable; {@link #apply(LingoConfig)} pushes a reloaded config into every service.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LingoCore {

    private final LingoEdition edition;
    private final ProviderRegistry providers;
    private final ProviderGateway gateway;
    private final ProviderHealthMonitor health;
    private final TranslationPipeline pipeline;
    private final PlayerSettingsService settings;
    private final LanguageResolver resolver;
    private final LanguageDetector detector;
    private final GlossaryService glossary;
    private final @Nullable TranslationMemoryService memory;
    private final @Nullable PhraseService phrases;

    /**
     * Builds every service.
     *
     * @param config    the loaded config
     * @param edition   the edition
     * @param hibernate the database
     * @param worker    executor for database and file work
     * @param logger    the plugin logger
     */
    public LingoCore(@NotNull LingoConfig config, @NotNull LingoEdition edition, @NotNull JEHibernate hibernate,
                     @NotNull Executor worker, @NotNull Logger logger) {
        this.edition = edition;
        var repositories = hibernate.repositories();
        this.providers = new ProviderRegistry(edition, logger);
        this.gateway = new ProviderGateway(providers.select(config.provider()),
                new CircuitBreaker(config.provider().breaker().failureThreshold(),
                        config.provider().breaker().openDuration()),
                config.provider().maxConcurrentRequests(), config.provider().requestTimeout());
        providers.attach(gateway);
        this.health = new ProviderHealthMonitor(gateway, config.languages().enabled(), logger);
        this.settings = new PlayerSettingsService(repositories.get(LingoPlayerSettingsRepository.class), worker,
                logger);
        this.resolver = new LanguageResolver(settings, config.languages());
        this.detector = new LanguageDetector(gateway, config.detection());
        this.glossary = new GlossaryService(repositories.get(GlossaryTermRepository.class), edition, worker, logger);
        if (edition.learningEnabled()) {
            this.memory = new TranslationMemoryService(repositories.get(TranslationMemoryRepository.class), worker,
                    logger);
            this.phrases = new PhraseService(repositories.get(PinnedPhraseRepository.class), config.learning(), logger);
        } else {
            this.memory = null;
            this.phrases = null;
        }
        this.pipeline = new TranslationPipeline(gateway, new TranslationCache(config.cache()), layers(),
                SkipRules.from(config.chat()));
        glossary.onChange(pipeline.cache()::clear);
    }

    /**
     * Pushes a reloaded config into the running services. Provider URL, key and timeouts switch the provider;
     * cache size and the phrase window need a restart.
     *
     * @param config the new config
     */
    public void apply(@NotNull LingoConfig config) {
        gateway.switchTo(providers.select(config.provider()));
        resolver.setLanguages(config.languages());
        detector.setSettings(config.detection());
        health.setEnabled(config.languages().enabled());
        pipeline.setSkipRules(SkipRules.from(config.chat()));
        pipeline.cache().clear();
        health.check();
    }

    /** Closes the provider connection. */
    public void close() {
        gateway.close();
    }

    private @NotNull PipelineLayers layers() {
        if (memory == null || phrases == null) {
            return PipelineLayers.glossaryOnly(glossary::rulesFor);
        }
        return new PipelineLayers(memory, phrases, glossary::rulesFor, phrases);
    }

    /**
     * Returns the edition.
     *
     * @return the edition
     */
    public @NotNull LingoEdition edition() { return edition; }

    /**
     * Returns the provider registry.
     *
     * @return the provider registry
     */
    public @NotNull ProviderRegistry providers() { return providers; }

    /**
     * Returns the provider gateway.
     *
     * @return the provider gateway
     */
    public @NotNull ProviderGateway gateway() { return gateway; }

    /**
     * Returns the provider health monitor.
     *
     * @return the provider health monitor
     */
    public @NotNull ProviderHealthMonitor health() { return health; }

    /**
     * Returns the translation pipeline.
     *
     * @return the translation pipeline
     */
    public @NotNull TranslationPipeline pipeline() { return pipeline; }

    /**
     * Returns player settings.
     *
     * @return player settings
     */
    public @NotNull PlayerSettingsService settings() { return settings; }

    /**
     * Returns the language resolver.
     *
     * @return the language resolver
     */
    public @NotNull LanguageResolver resolver() { return resolver; }

    /**
     * Returns the language detector.
     *
     * @return the language detector
     */
    public @NotNull LanguageDetector detector() { return detector; }

    /**
     * Returns the glossary.
     *
     * @return the glossary
     */
    public @NotNull GlossaryService glossary() { return glossary; }

    /**
     * Returns the translation memory, or {@code null} in the free edition.
     *
     * @return the translation memory, or {@code null} in the free edition
     */
    public @Nullable TranslationMemoryService memory() { return memory; }

    /**
     * Returns the pinned phrases, or {@code null} in the free edition.
     *
     * @return the pinned phrases, or {@code null} in the free edition
     */
    public @Nullable PhraseService phrases() { return phrases; }
}
