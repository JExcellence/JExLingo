package de.jexcellence.lingo;

import de.jexcellence.jehibernate.config.PropertyLoader;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jexplatform.JExPlatform;
import de.jexcellence.jexplatform.logging.LogLevel;
import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.jexplatform.scheduler.TaskHandle;
import de.jexcellence.jextranslate.R18nManager;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.command.CommandTreeMerger;
import de.jexcellence.lingo.config.ConfigFileMerger;
import de.jexcellence.lingo.config.LanguageSettings;
import de.jexcellence.lingo.config.LingoConfig;
import de.jexcellence.lingo.config.LingoConfigLoader;
import de.jexcellence.lingo.config.TranslationFileMerger;
import de.jexcellence.lingo.glossary.GlossarySeed;
import de.jexcellence.lingo.glossary.GlossaryTerm;
import de.jexcellence.lingo.pipeline.SlangDictionary;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lifecycle of JExLingo, shared by both editions: files, translations, database, services, front end, scheduled
 * tasks and reload. The edition decides the limits.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class JExLingo {

    private static final String DEFAULT_LOCALE = "en_US";
    private static final String[] EXTRA_LOCALES = {"de_DE"};
    private static final String CONFIG_FILE = "config.yml";
    private static final String GLOSSARY_FILE = "glossary.yml";
    private static final String SLANG_FILE = "slang.yml";
    private static final String DATABASE_FILE = "database/hibernate.properties";
    private static final long TICKS_PER_SECOND = 20L;
    private static final long FLUSH_PERIOD_TICKS = 5L * 60L * TICKS_PER_SECOND;
    private static final long PURGE_PERIOD_TICKS = 60L * 60L * TICKS_PER_SECOND;
    private static final int WORKER_THREADS = 2;

    private final JavaPlugin plugin;
    private final LingoEdition edition;
    private final Logger logger;
    private final AtomicReference<LingoConfig> config = new AtomicReference<>();
    private final List<TaskHandle> tasks = new ArrayList<>();

    private JExPlatform platform;
    private JEHibernate hibernate;
    private ExecutorService worker;
    private LingoCore core;
    private LingoFrontend frontend;

    /**
     * Creates the lifecycle.
     *
     * @param plugin  the plugin
     * @param edition the edition
     */
    public JExLingo(@NotNull JavaPlugin plugin, @NotNull LingoEdition edition) {
        this.plugin = plugin;
        this.edition = edition;
        this.logger = plugin.getLogger();
    }

    /** Copies the default files and reads the config. */
    public void onLoad() {
        logger.log(Level.INFO, () -> "Loading JExLingo " + edition.name() + " Edition v"
                + plugin.getPluginMeta().getVersion());
        saveDefault(CONFIG_FILE);
        ConfigFileMerger.addMissingKeys(plugin, CONFIG_FILE);
        config.set(readConfig());
    }

    /** Starts every service; disables the plugin when the start fails. */
    public void onEnable() {
        try {
            prepareFiles();
            platform = JExPlatform.builder(plugin)
                    .withLogLevel(LogLevel.INFO)
                    .enableTranslations(DEFAULT_LOCALE, EXTRA_LOCALES)
                    .build();
            platform.initialize();
            hibernate = JEHibernate.builder()
                    .configuration(builder -> builder.fromProperties(
                            PropertyLoader.load(plugin.getDataFolder(), "database", "hibernate.properties")))
                    .scanPackages("de.jexcellence.lingo.database")
                    .build();
            worker = Executors.newFixedThreadPool(WORKER_THREADS,
                    Thread.ofPlatform().name("JExLingo-worker-", 0).daemon(true).factory());
            core = new LingoCore(config.get(), edition, hibernate, readSlang(), worker, logger);
            loadData();
            PlatformScheduler scheduler = PlatformScheduler.of(plugin);
            frontend = new LingoFrontend(plugin, core, config, scheduler);
            frontend.start(worker, this::reload);
            scheduleTasks(scheduler);
            logger.log(Level.INFO, () -> "JExLingo " + edition.name() + " enabled - provider "
                    + config.get().provider() + ", languages " + config.get().languages().enabled());
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Failed to enable JExLingo", ex);
            Bukkit.getPluginManager().disablePlugin(plugin);
        }
    }

    /** Stops tasks, unregisters the front end and closes every resource. */
    public void onDisable() {
        tasks.forEach(TaskHandle::cancel);
        tasks.clear();
        if (frontend != null) {
            frontend.stop();
        }
        if (core != null) {
            core.close();
        }
        shutdownWorker();
        if (hibernate != null) {
            hibernate.close();
        }
        if (platform != null) {
            platform.shutdown();
        }
        logger.info("JExLingo disabled");
    }

    /**
     * Reloads {@code config.yml} and the translation files and applies them to the running services. The
     * database, the cache size and the phrase window need a restart.
     */
    public void reload() {
        LingoConfig next = readConfig();
        config.set(next);
        core.apply(next, readSlang());
        frontend.apply(next);
        R18nManager.getInstance().reload();
    }

    private void prepareFiles() {
        saveDefault(DATABASE_FILE);
        saveDefault(GLOSSARY_FILE);
        saveDefault(SLANG_FILE);
        saveDefault(LingoCommands.COMMAND_FILE);
        CommandTreeMerger.addMissingSubcommands(plugin, LingoCommands.COMMAND_FILE);
        List<String> locales = new ArrayList<>(List.of(EXTRA_LOCALES));
        locales.addFirst(DEFAULT_LOCALE);
        TranslationFileMerger.addMissingKeys(plugin, locales);
    }

    private void loadData() {
        core.glossary().load(readGlossarySeed());
        if (core.memory() != null) {
            core.memory().load();
        }
        if (core.phrases() != null) {
            core.phrases().load();
        }
    }

    private void scheduleTasks(@NotNull PlatformScheduler scheduler) {
        long healthPeriod = config.get().provider().breaker().healthCheckInterval().toSeconds() * TICKS_PER_SECOND;
        tasks.add(scheduler.runRepeatingAsync(core.health()::check, TICKS_PER_SECOND, healthPeriod));
        if (core.memory() != null) {
            tasks.add(scheduler.runRepeatingAsync(core.memory()::flushHits, FLUSH_PERIOD_TICKS, FLUSH_PERIOD_TICKS));
            tasks.add(scheduler.runRepeatingAsync(core.memory()::purgeRejected, PURGE_PERIOD_TICKS,
                    PURGE_PERIOD_TICKS));
        }
    }

    private @NotNull LingoConfig readConfig() {
        File file = new File(plugin.getDataFolder(), CONFIG_FILE);
        LingoConfigLoader.LoadResult result = LingoConfigLoader.load(YamlConfiguration.loadConfiguration(file),
                System.getenv());
        result.warnings().forEach(warning -> logger.log(Level.WARNING, () -> "config.yml: " + warning));
        LingoConfig loaded = result.config();
        List<LanguageCode> allowed = edition.limitLanguages(loaded.languages().enabled());
        if (allowed.size() == loaded.languages().enabled().size()) {
            return loaded;
        }
        final int max = edition.maxLanguages();
        logger.log(Level.WARNING, () -> "The Free edition translates up to " + max + " languages; only "
                + allowed + " are used. JExLingo Premium has no language limit.");
        LanguageCode fallback = loaded.languages().fallback();
        LanguageSettings limited = new LanguageSettings(allowed,
                allowed.contains(fallback) ? fallback : allowed.getFirst());
        return new LingoConfig(loaded.provider(), limited, loaded.detection(), loaded.chat(), loaded.cache(),
                loaded.learning(), loaded.bedrock());
    }

    private @NotNull List<GlossaryTerm> readGlossarySeed() {
        File file = new File(plugin.getDataFolder(), GLOSSARY_FILE);
        return GlossarySeed.parse(YamlConfiguration.loadConfiguration(file),
                warning -> logger.log(Level.WARNING, warning));
    }

    private @NotNull SlangDictionary readSlang() {
        File file = new File(plugin.getDataFolder(), SLANG_FILE);
        if (!file.isFile()) {
            return SlangDictionary.EMPTY;
        }
        return SlangDictionary.parse(YamlConfiguration.loadConfiguration(file),
                warning -> logger.log(Level.WARNING, warning));
    }

    private void saveDefault(@NotNull String resourcePath) {
        File target = new File(plugin.getDataFolder(), resourcePath.replace('/', File.separatorChar));
        if (!target.exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    private void shutdownWorker() {
        if (worker == null) {
            return;
        }
        worker.shutdown();
        try {
            if (!worker.awaitTermination(2, TimeUnit.SECONDS)) {
                worker.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
    }
}
