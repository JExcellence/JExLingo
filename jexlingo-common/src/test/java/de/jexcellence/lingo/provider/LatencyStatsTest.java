package de.jexcellence.lingo.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LatencyStatsTest {

    @Test
    void reportsNearestRankPercentiles() {
        LatencyStats stats = new LatencyStats();
        for (int millis = 1; millis <= 100; millis++) {
            stats.record(millis);
        }

        assertEquals(50L, stats.p50());
        assertEquals(95L, stats.p95());
        assertEquals(100, stats.count());
    }

    @Test
    void emptyStatsReportMinusOne() {
        assertEquals(-1L, new LatencyStats().p95());
    }

    @Test
    void keepsOnlyTheLatestSamples() {
        LatencyStats stats = new LatencyStats();
        for (int i = 0; i < 600; i++) {
            stats.record(i < 100 ? 10_000 : 5);
        }

        assertEquals(512, stats.count());
        assertEquals(5L, stats.p95());
    }
}
