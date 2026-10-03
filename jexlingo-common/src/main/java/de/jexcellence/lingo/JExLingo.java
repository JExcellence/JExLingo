package de.jexcellence.lingo;

import de.jexcellence.jehibernate.config.PropertyLoader;
import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.jexplatform.JExPlatform;
import de.jexcellence.jexplatform.logging.LogLevel;
import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.jexplatform.scheduler.TaskHandle;
import de.jexcellence.jextranslate.R18nManager;
import de.jexcellence.lingo.config.LingoConfig;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
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

    private static final long TICKS_PER_SECOND = 20L;
    private static final long FLUSH_PERIOD_TICKS = 5L * 60L * TICKS_PER_SECOND;
    private static final long PURGE_PERIOD_TICKS = 60L * 60L * TICKS_PER_SECOND;
    private static final Duration SHUTDOWN_FLUSH = Duration.ofSeconds(3);
    private static final int WORKER_THREADS = 2;

    private final JavaPlugin plugin;
    private final LingoEdition edition;
    private final Logger logger;
    private final LingoFiles files;
    private final AtomicReference<LingoConfig> config = new AtomicReference<>();
    private final List<TaskHandle> tasks = new ArrayList<>();

    private JExPlatform platform;
    private JEHibernate hibernate;
    private ExecutorService worker;
    private LingoCore core;
    private LingoOperations operations;
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
        this.files = new LingoFiles(plugin, edition, logger);
    }

    /** Copies the default config, merges new options into it and reads it. */
    public void onLoad() {
        logger.log(Level.INFO, () -> "Loading JExLingo " + edition.name() + " Edition v"
                + plugin.getPluginMeta().getVersion());
        files.prepareConfig();
        config.set(files.readConfig());
    }

    /** Starts every service; disables the plugin when the start fails. */
    public void onEnable() {
        try {
            files.prepareRest();
            platform = JExPlatform.builder(plugin)
                    .withLogLevel(LogLevel.INFO)
                    .enableTranslations(LingoFiles.DEFAULT_LOCALE, LingoFiles.EXTRA_LOCALES)
                    .build();
            platform.initialize();
            hibernate = JEHibernate.builder()
                    .configuration(builder -> builder.fromProperties(
                            PropertyLoader.load(plugin.getDataFolder(), "database", "hibernate.properties")))
                    .scanPackages("de.jexcellence.lingo.database")
                    .build();
            worker = Executors.newFixedThreadPool(WORKER_THREADS,
                    Thread.ofPlatform().name("JExLingo-worker-", 0).daemon(true).factory());
            core = new LingoCore(config.get(), edition, hibernate, files.readSlang(), worker, logger);
            operations = new LingoOperations(core, hibernate, config.get().storage().statistics(), worker, logger);
            core.glossary().load(files.readGlossarySeed());
            core.learning().load();
            operations.start();
            PlatformScheduler scheduler = PlatformScheduler.of(plugin);
            frontend = new LingoFrontend(plugin, core, operations, config, scheduler);
            frontend.start(worker, this::reload);
            scheduleTasks(scheduler);
            logger.log(Level.INFO, () -> "JExLingo " + edition.name() + " enabled - provider "
                    + config.get().provider() + ", languages " + config.get().languages().enabled());
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Failed to enable JExLingo", ex);
            Bukkit.getPluginManager().disablePlugin(plugin);
        }
    }

    /** Stops tasks, stores the last statistics, unregisters the front end and closes every resource. */
    public void onDisable() {
        tasks.forEach(TaskHandle::cancel);
        tasks.clear();
        if (frontend != null) {
            frontend.stop();
        }
        if (operations != null) {
            operations.flushAndWait(SHUTDOWN_FLUSH);
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
     * Reloads {@code config.yml}, {@code slang.yml} and the translation files and applies them to the running
     * services. The database, the cache size and the phrase window need a restart.
     */
    public void reload() {
        LingoConfig next = files.readConfig();
        config.set(next);
        core.apply(next, files.readSlang());
        operations.apply(next.storage().statistics());
        frontend.apply(next);
        R18nManager.getInstance().reload();
    }

    private void scheduleTasks(@NotNull PlatformScheduler scheduler) {
        long healthPeriod = config.get().provider().breaker().healthCheckInterval().toSeconds() * TICKS_PER_SECOND;
        tasks.add(scheduler.runRepeatingAsync(core.health()::check, TICKS_PER_SECOND, healthPeriod));
        tasks.add(scheduler.runRepeatingAsync(operations::flush, FLUSH_PERIOD_TICKS, FLUSH_PERIOD_TICKS));
        tasks.add(scheduler.runRepeatingAsync(operations::purge, PURGE_PERIOD_TICKS, PURGE_PERIOD_TICKS));
        if (core.memory() != null) {
            tasks.add(scheduler.runRepeatingAsync(core.memory()::flushHits, FLUSH_PERIOD_TICKS, FLUSH_PERIOD_TICKS));
            tasks.add(scheduler.runRepeatingAsync(core.memory()::purgeRejected, PURGE_PERIOD_TICKS,
                    PURGE_PERIOD_TICKS));
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
