package de.jexcellence.lingo;

import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.lingo.api.JExLingoApi;
import de.jexcellence.lingo.bedrock.BedrockFormBridge;
import de.jexcellence.lingo.bedrock.LingoBedrockForms;
import de.jexcellence.lingo.chat.ChatContext;
import de.jexcellence.lingo.chat.ChatTranslationCoordinator;
import de.jexcellence.lingo.chat.ChatTranslationListener;
import de.jexcellence.lingo.chat.OnDemandTranslator;
import de.jexcellence.lingo.chat.TranslatedLineDecorator;
import de.jexcellence.lingo.config.LingoConfig;
import de.jexcellence.lingo.language.WritingLanguageLearner;
import de.jexcellence.lingo.learning.RecentMessageBuffer;
import de.jexcellence.lingo.learning.SuggestionService;
import de.jexcellence.lingo.placeholder.LingoPlaceholderExpansion;
import de.jexcellence.lingo.settings.PlayerSessionListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Everything players and staff touch: chat hook, session listener, Bedrock forms, commands and menus (through
 * {@link LingoCommands}), placeholders and the API registration. Built on top of a ready {@link LingoCore}.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LingoFrontend {

    private final JavaPlugin plugin;
    private final LingoCore core;
    private final LingoOperations operations;
    private final AtomicReference<LingoConfig> config;
    private final PlatformScheduler scheduler;
    private final RecentMessageBuffer recent = new RecentMessageBuffer();
    private @Nullable ChatTranslationCoordinator coordinator;
    private @Nullable WritingLanguageLearner learner;
    private @Nullable SuggestionService suggestions;
    private @Nullable LingoPlaceholderExpansion placeholders;
    private @Nullable JExLingoApi api;

    /**
     * Creates the front end.
     *
     * @param plugin    the plugin
     * @param core       the services
     * @param operations statistics and pause switch
     * @param config     the live config
     * @param scheduler the platform scheduler
     */
    public LingoFrontend(@NotNull JavaPlugin plugin, @NotNull LingoCore core, @NotNull LingoOperations operations,
                         @NotNull AtomicReference<LingoConfig> config, @NotNull PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.core = core;
        this.operations = operations;
        this.config = config;
        this.scheduler = scheduler;
    }

    /**
     * Registers listeners, menus, commands, placeholders and the API.
     *
     * @param worker executor for async work
     * @param reload reloads the plugin
     */
    public void start(@NotNull Executor worker, @NotNull Runnable reload) {
        BedrockFormBridge bedrock = new BedrockFormBridge();
        TranslatedLineDecorator decorator = new TranslatedLineDecorator(core.settings(), bedrock,
                () -> core.edition().learningEnabled(), () -> config.get().bedrock().showOriginalLine());
        learner = new WritingLanguageLearner(core.gateway(), scheduler, config.get().detection());
        ChatTranslationCoordinator chat = new ChatTranslationCoordinator(new ChatContext(core.pipeline(),
                core.settings(), core.resolver(), core.detector(), recent, scheduler, worker), decorator,
                config.get().chat(), learner, operations.toggle());
        OnDemandTranslator onDemand = new OnDemandTranslator(recent, core.pipeline(), core.resolver(), decorator,
                scheduler);
        coordinator = chat;
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(new ChatTranslationListener(chat, decorator, recent), plugin);
        pluginManager.registerEvents(new PlayerSessionListener(core.settings(), core.resolver(), chat,
                scheduler, () -> config.get().chat().onboarding()), plugin);
        Bukkit.getOnlinePlayers().forEach(player -> core.settings().load(player.getUniqueId()));

        if (core.memory() != null) {
            suggestions = new SuggestionService(recent, core.memory(), core.settings(), core.resolver(),
                    config.get().learning());
        }
        LingoBedrockForms forms = null;
        if (bedrock.isAvailable()) {
            forms = new LingoBedrockForms(bedrock, new LingoBedrockForms.FormServices(core.settings(),
                    core.resolver(), recent, suggestions, core.memory(), core.glossary(), scheduler));
        }
        new LingoCommands(plugin, core, operations, scheduler)
                .register(worker, reload, suggestions, forms, onDemand, bedrock);
        registerPlaceholders();
        registerApi();
    }

    /**
     * Applies a reloaded config to the front end.
     *
     * @param next the new config
     */
    public void apply(@NotNull LingoConfig next) {
        if (coordinator != null) {
            coordinator.setChatSettings(next.chat());
        }
        if (learner != null) {
            learner.setSettings(next.detection());
        }
        if (suggestions != null) {
            suggestions.setLimits(next.learning());
        }
    }

    /** Unregisters placeholders and the API. */
    public void stop() {
        if (placeholders != null) {
            placeholders.unregister();
        }
        if (api != null) {
            Bukkit.getServicesManager().unregister(JExLingoApi.class, api);
        }
    }

    private void registerPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        placeholders = new LingoPlaceholderExpansion(core.settings(), core.resolver(), operations.sources(),
                plugin.getPluginMeta().getVersion());
        placeholders.register();
    }

    private void registerApi() {
        JExLingoApi registered = new JExLingoApiImpl(core.pipeline(), core.gateway(), core.providers(),
                core.resolver(), core.settings());
        api = registered;
        Bukkit.getServicesManager().register(JExLingoApi.class, registered, plugin, ServicePriority.Normal);
    }
}
