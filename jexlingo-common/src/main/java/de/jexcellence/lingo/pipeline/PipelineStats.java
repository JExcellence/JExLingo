package de.jexcellence.lingo.pipeline;

import de.jexcellence.lingo.api.TranslationOrigin;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counts results by {@link TranslationOrigin} since the last start, for {@code /lingo status}. A high share of
 * provider results with many corrections points to missing glossary terms.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class PipelineStats {

    private final Map<TranslationOrigin, LongAdder> counts = new EnumMap<>(TranslationOrigin.class);
    private final LongAdder tokenLosses = new LongAdder();
    private final LongAdder recoveries = new LongAdder();

    /** Creates empty counters. */
    public PipelineStats() {
        for (TranslationOrigin origin : TranslationOrigin.values()) {
            counts.put(origin, new LongAdder());
        }
    }

    /**
     * Counts one result.
     *
     * @param origin the result's origin
     */
    public void record(@NotNull TranslationOrigin origin) {
        counts.get(origin).increment();
    }

    /**
     * Returns a copy of the counters.
     *
     * @return a copy of the counters
     */
    public @NotNull Map<TranslationOrigin, Long> snapshot() {
        Map<TranslationOrigin, Long> copy = new EnumMap<>(TranslationOrigin.class);
        counts.forEach((origin, adder) -> copy.put(origin, adder.sum()));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Returns all counted results.
     *
     * @return all counted results
     */
    public long total() {
        return counts.values().stream().mapToLong(LongAdder::sum).sum();
    }

    /** Counts a provider answer whose protected tokens came back broken. */
    public void recordTokenLoss() {
        tokenLosses.increment();
    }

    /** Counts a broken answer that was saved by translating the line piece by piece. */
    public void recordRecovery() {
        recoveries.increment();
    }

    /**
     * Returns provider answers with broken tokens.
     *
     * @return provider answers with broken tokens
     */
    public long tokenLosses() {
        return tokenLosses.sum();
    }

    /**
     * Returns broken answers that were saved piece by piece.
     *
     * @return broken answers that were saved piece by piece
     */
    public long recoveries() {
        return recoveries.sum();
    }
}
