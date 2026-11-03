package de.jexcellence.lingo.stats;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.config.CacheSettings;
import de.jexcellence.lingo.pipeline.LanguagePair;
import de.jexcellence.lingo.pipeline.TranslationCache;
import de.jexcellence.lingo.provider.LatencyStats;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsLinesTest {

    private static final LanguagePair DE_EN = new LanguagePair(LanguageCode.of("de"), LanguageCode.of("en"));

    @Test
    void latencyShowsADashBeforeTheFirstCall() {
        LatencyStats latency = new LatencyStats();

        assertEquals("-", StatsLines.latency(latency));
        latency.record(80L);
        latency.record(120L);
        assertEquals("80 / 120 ms", StatsLines.latency(latency));
    }

    @Test
    void hitRateShowsADashBeforeTheFirstLookup() {
        TranslationCache cache = new TranslationCache(new CacheSettings(10, Duration.ofMinutes(1)));

        assertEquals("-", StatsLines.hitRate(cache));
        cache.put(DE_EN, "hallo", "hello");
        cache.get(DE_EN, "hallo");
        cache.get(DE_EN, "tschuess");
        assertEquals("50%", StatsLines.hitRate(cache));
    }
}
