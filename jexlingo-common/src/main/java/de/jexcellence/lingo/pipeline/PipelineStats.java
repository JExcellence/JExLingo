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
}
