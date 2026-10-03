package de.jexcellence.lingo;

import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.lingo.chat.TranslationSwitch;
import de.jexcellence.lingo.config.StatisticsSettings;
import de.jexcellence.lingo.database.repository.DailyStatRepository;
import de.jexcellence.lingo.stats.StatsRecorder;
import de.jexcellence.lingo.stats.StatsService;
import de.jexcellence.lingo.stats.StatsSources;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Running the plugin: daily statistics and the pause switch. Feeds every pipeline result into the statistics and
 * writes them in batches.
 *
 * @author JExcellence
 * @since 0.3.0
 */
public final class LingoOperations {

    private final LingoCore core;
    private final StatsRecorder recorder;
    private final StatsService stats;
    private final TranslationSwitch toggle;
    private final Executor worker;
    private final Logger logger;
    private final AtomicReference<StatisticsSettings> settings;

    /**
     * Creates the operations and connects the statistics to the pipeline.
     *
     * @param core      the services
     * @param hibernate the database
     * @param settings  statistics settings
     * @param worker    executor for database work
     * @param logger    the plugin logger
     */
    public LingoOperations(@NotNull LingoCore core, @NotNull JEHibernate hibernate,
                           @NotNull StatisticsSettings settings, @NotNull Executor worker, @NotNull Logger logger) {
        Clock clock = Clock.systemDefaultZone();
        DailyStatRepository repository = hibernate.repositories().get(DailyStatRepository.class);
        this.core = core;
        this.recorder = new StatsRecorder(repository, clock, settings.enabled(), logger);
        this.stats = new StatsService(repository, recorder, clock, worker);
        this.toggle = new TranslationSwitch(clock);
        this.worker = worker;
        this.logger = logger;
        this.settings = new AtomicReference<>(settings);
        core.pipeline().setResultSink(recorder);
    }

    /** Loads today's stored total for the today counter. */
    public void start() {
        run(recorder::loadToday, "load today's statistics");
    }

    /** Writes the counted statistics. */
    public void flush() {
        run(recorder::flush, "store statistics");
    }

    /** Deletes statistics older than the retention. */
    public void purge() {
        run(() -> recorder.purge(settings.get().retentionDays()), "delete old statistics");
    }

    /**
     * Writes the counted statistics and waits for it, for shutdown.
     *
     * @param timeout how long to wait
     */
    public void flushAndWait(@NotNull Duration timeout) {
        try {
            CompletableFuture.runAsync(recorder::flush, worker).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ex) {
            logger.log(Level.WARNING, ex, () -> "Could not store the last statistics on shutdown");
        }
    }

    /**
     * Applies reloaded statistics settings.
     *
     * @param value the new settings
     */
    public void apply(@NotNull StatisticsSettings value) {
        settings.set(value);
        recorder.setEnabled(value.enabled());
    }

    /**
     * Returns everything the statistics views read from.
     *
     * @return the sources
     */
    public @NotNull StatsSources sources() {
        return new StatsSources(stats, core.gateway(), core.health(), core.pipeline(), toggle, core.memory(),
                core.phrases());
    }

    /**
     * Returns the pause switch.
     *
     * @return the switch
     */
    public @NotNull TranslationSwitch toggle() {
        return toggle;
    }

    /**
     * Returns the live statistics recorder.
     *
     * @return the recorder
     */
    public @NotNull StatsRecorder recorder() {
        return recorder;
    }

    private void run(@NotNull Runnable task, @NotNull String what) {
        CompletableFuture.runAsync(task, worker).exceptionally(error -> {
            logger.log(Level.WARNING, error, () -> "Could not " + what);
            return null;
        });
    }
}
