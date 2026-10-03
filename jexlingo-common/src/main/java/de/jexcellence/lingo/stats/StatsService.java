package de.jexcellence.lingo.stats;

import de.jexcellence.lingo.database.entity.DailyStatEntity;
import de.jexcellence.lingo.database.repository.DailyStatRepository;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Answers "how much was translated" for a period: stored rows plus the totals still in memory.
 *
 * @author JExcellence
 * @since 0.3.0
 */
public final class StatsService {

    private final DailyStatRepository repository;
    private final StatsRecorder recorder;
    private final Clock clock;
    private final Executor worker;

    /**
     * Creates the service.
     *
     * @param repository the statistics repository
     * @param recorder   the live recorder
     * @param clock      clock for the current day
     * @param worker     executor for database reads
     */
    public StatsService(@NotNull DailyStatRepository repository, @NotNull StatsRecorder recorder,
                        @NotNull Clock clock, @NotNull Executor worker) {
        this.repository = repository;
        this.recorder = recorder;
        this.clock = clock;
        this.worker = worker;
    }

    /**
     * The totals of a period.
     *
     * @param period the period
     * @return the summary
     */
    public @NotNull CompletableFuture<StatsSummary> summary(@NotNull StatsPeriod period) {
        LocalDate since = period.since(LocalDate.now(clock));
        return CompletableFuture.supplyAsync(() -> {
            List<StatRow> rows = new ArrayList<>(repository.findSince(since).stream()
                    .map(DailyStatEntity::toRow).toList());
            recorder.pending().stream().filter(row -> !row.day().isBefore(since)).forEach(rows::add);
            return StatsSummary.of(rows);
        }, worker);
    }

    /**
     * Returns the live recorder.
     *
     * @return the recorder
     */
    public @NotNull StatsRecorder recorder() {
        return recorder;
    }
}
