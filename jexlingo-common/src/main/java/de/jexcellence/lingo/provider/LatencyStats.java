package de.jexcellence.lingo.provider;

import java.util.Arrays;

/**
 * Keeps the last provider latencies in a ring buffer for p50/p95 in {@code /lingo status}.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LatencyStats {

    private static final int CAPACITY = 512;
    private static final double MEDIAN = 0.50;
    private static final double TAIL = 0.95;

    private final long[] samples = new long[CAPACITY];
    private int size;
    private int next;

    /**
     * Records one latency.
     *
     * @param millis the latency in milliseconds
     */
    public synchronized void record(long millis) {
        samples[next] = Math.max(0L, millis);
        next = (next + 1) % CAPACITY;
        size = Math.min(size + 1, CAPACITY);
    }

    /**
     * Returns the median of the recorded latencies, or -1 without samples.
     *
     * @return the median of the recorded latencies, or -1 without samples
     */
    public long p50() {
        return percentile(MEDIAN);
    }

    /**
     * Returns the 95th percentile of the recorded latencies, or -1 without samples.
     *
     * @return the 95th percentile of the recorded latencies, or -1 without samples
     */
    public long p95() {
        return percentile(TAIL);
    }

    /**
     * Returns the number of recorded samples (at most 512).
     *
     * @return the number of recorded samples (at most 512)
     */
    public synchronized int count() {
        return size;
    }

    /**
     * The given percentile of the recorded latencies (nearest rank).
     *
     * @param fraction the percentile as a fraction, for example 0.95
     * @return the latency, or -1 without samples
     */
    public synchronized long percentile(double fraction) {
        if (size == 0) {
            return -1L;
        }
        long[] sorted = Arrays.copyOf(samples, size);
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(Math.clamp(fraction, 0.0, 1.0) * size);
        return sorted[Math.clamp(rank - 1L, 0, size - 1)];
    }
}
