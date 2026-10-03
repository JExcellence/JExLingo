package de.jexcellence.lingo.stats;

import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.api.TranslationResult;
import de.jexcellence.lingo.database.entity.DailyStatEntity;
import de.jexcellence.lingo.database.repository.DailyStatRepository;
import de.jexcellence.lingo.pipeline.LanguagePair;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Counts every translation result in memory and writes the totals per day, language pair and source to the
 * database in batches. Results that never needed a translation (same language, skipped) are not counted. Nothing
 * about the text or the player is kept.
 *
 * @author JExcellence
 * @since 0.3.0
 */
public final class StatsRecorder implements Consumer<TranslationResult> {

    private final DailyStatRepository repository;
    private final Clock clock;
    private final Logger logger;
    private final AtomicBoolean enabled;
    private final Map<Key, Counter> counters = new ConcurrentHashMap<>();
    private final AtomicReference<LocalDate> today;
    private final AtomicLong todayStored = new AtomicLong();
    private final LongAdder todayLive = new LongAdder();

    private record Key(@NotNull LocalDate day, @NotNull LanguagePair pair, @NotNull TranslationOrigin origin) {
    }

    private static final class Counter {
        private final LongAdder translations = new LongAdder();
        private final LongAdder characters = new LongAdder();
        private final LongAdder latency = new LongAdder();
    }

    /**
     * Creates the recorder.
     *
     * @param repository the statistics repository
     * @param clock      clock for the current day
     * @param enabled    whether results are counted
     * @param logger     the plugin logger
     */
    public StatsRecorder(@NotNull DailyStatRepository repository, @NotNull Clock clock, boolean enabled,
                         @NotNull Logger logger) {
        this.repository = repository;
        this.clock = clock;
        this.logger = logger;
        this.enabled = new AtomicBoolean(enabled);
        this.today = new AtomicReference<>(LocalDate.now(clock));
    }

    @Override
    public void accept(@NotNull TranslationResult result) {
        if (!enabled.get() || !counts(result.origin())) {
            return;
        }
        LocalDate day = rollDay();
        Counter counter = counters.computeIfAbsent(
                new Key(day, new LanguagePair(result.source(), result.target()), result.origin()),
                ignored -> new Counter());
        counter.translations.increment();
        counter.characters.add(result.original().length());
        counter.latency.add(result.latencyMillis());
        todayLive.increment();
    }

    /**
     * Writes the counted totals to the database. Call on a worker thread; runs one batch at a time.
     */
    public synchronized void flush() {
        for (Map.Entry<Key, Counter> entry : counters.entrySet()) {
            long translations = entry.getValue().translations.sumThenReset();
            long characters = entry.getValue().characters.sumThenReset();
            long latency = entry.getValue().latency.sumThenReset();
            if (translations > 0L) {
                store(entry.getKey(), translations, characters, latency);
            }
        }
        counters.entrySet().removeIf(entry -> !entry.getKey().day().equals(today.get())
                && entry.getValue().translations.sum() == 0L);
    }

    /**
     * Returns the totals not yet written, as rows.
     *
     * @return the pending rows
     */
    public @NotNull List<StatRow> pending() {
        List<StatRow> rows = new ArrayList<>();
        counters.forEach((key, counter) -> {
            long translations = counter.translations.sum();
            if (translations > 0L) {
                rows.add(new StatRow(key.day(), key.pair(), key.origin(), translations, counter.characters.sum(),
                        counter.latency.sum()));
            }
        });
        return rows;
    }

    /**
     * Loads today's stored total once, so the today counter survives restarts. Call on a worker thread.
     */
    public void loadToday() {
        LocalDate day = rollDay();
        long stored = repository.findSince(day).stream()
                .mapToLong(row -> row.toRow().translations())
                .sum();
        todayStored.set(stored);
    }

    /**
     * Returns today's translations, stored plus counted since.
     *
     * @return the count
     */
    public long todayTotal() {
        rollDay();
        return todayStored.get() + todayLive.sum();
    }

    /**
     * Deletes rows older than the retention. Call on a worker thread.
     *
     * @param retentionDays days to keep
     */
    public void purge(int retentionDays) {
        LocalDate keepFrom = LocalDate.now(clock).minusDays(retentionDays - 1L);
        List<DailyStatEntity> old = repository.findBefore(keepFrom);
        old.forEach(repository::deleteEntity);
        if (!old.isEmpty()) {
            final int count = old.size();
            logger.log(Level.FINE, () -> "Deleted " + count + " old statistics rows");
        }
    }

    /**
     * Switches counting on or off after a reload.
     *
     * @param value whether results are counted
     */
    public void setEnabled(boolean value) {
        enabled.set(value);
    }

    /**
     * Whether a result source is counted.
     *
     * @param origin the source
     * @return {@code false} for results that needed no translation
     */
    static boolean counts(@NotNull TranslationOrigin origin) {
        return origin != TranslationOrigin.SAME_LANGUAGE && origin != TranslationOrigin.SKIPPED;
    }

    private @NotNull LocalDate rollDay() {
        LocalDate now = LocalDate.now(clock);
        LocalDate previous = today.getAndSet(now);
        if (!now.equals(previous)) {
            todayStored.set(0L);
            todayLive.reset();
        }
        return now;
    }

    private void store(@NotNull Key key, long translations, long characters, long latency) {
        try {
            DailyStatEntity row = repository.find(key.day(), key.pair(), key.origin())
                    .orElseGet(() -> new DailyStatEntity(key.day(), key.pair(), key.origin()));
            row.add(translations, characters, latency);
            repository.save(row);
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, ex, () -> "Could not store statistics for " + key.day());
        }
    }
}
