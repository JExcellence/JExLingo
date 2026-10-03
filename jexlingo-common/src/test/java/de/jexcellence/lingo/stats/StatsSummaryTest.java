package de.jexcellence.lingo.stats;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.pipeline.LanguagePair;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsSummaryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 3);
    private static final LanguagePair DE_EN = new LanguagePair(LanguageCode.of("de"), LanguageCode.of("en"));
    private static final LanguagePair EN_DE = new LanguagePair(LanguageCode.of("en"), LanguageCode.of("de"));

    @Test
    void addsUpRowsAndSharesOfSavedAndFallback() {
        StatsSummary summary = StatsSummary.of(List.of(
                new StatRow(DAY, DE_EN, TranslationOrigin.PROVIDER, 6, 120, 1200),
                new StatRow(DAY, DE_EN, TranslationOrigin.CACHE, 2, 40, 0),
                new StatRow(DAY.minusDays(1), EN_DE, TranslationOrigin.MEMORY, 1, 10, 0),
                new StatRow(DAY, EN_DE, TranslationOrigin.FALLBACK, 1, 30, 3000)));

        assertEquals(10L, summary.translations());
        assertEquals(200L, summary.characters());
        assertEquals(30, summary.savedPercent());
        assertEquals(10, summary.fallbackPercent());
        assertEquals(200L, summary.averageProviderLatency());
        assertEquals(List.of(Map.entry("de>en", 8L), Map.entry("en>de", 2L)), summary.topPairs(5));
    }

    @Test
    void emptyPeriodHasNoDivisionByZero() {
        assertEquals(0, StatsSummary.EMPTY.savedPercent());
        assertEquals(0L, StatsSummary.EMPTY.averageProviderLatency());
        assertTrue(StatsSummary.EMPTY.topPairs(3).isEmpty());
    }

    @Test
    void periodsCoverTodayAndCycle() {
        assertEquals(DAY, StatsPeriod.TODAY.since(DAY));
        assertEquals(DAY.minusDays(6), StatsPeriod.WEEK.since(DAY));
        assertEquals(StatsPeriod.TODAY, StatsPeriod.MONTH.cycle(true));
        assertEquals(StatsPeriod.MONTH, StatsPeriod.TODAY.cycle(false));
    }

    @Test
    void untranslatedResultsAreNotCounted() {
        assertFalse(StatsRecorder.counts(TranslationOrigin.SAME_LANGUAGE));
        assertFalse(StatsRecorder.counts(TranslationOrigin.SKIPPED));
        assertTrue(StatsRecorder.counts(TranslationOrigin.FALLBACK));
        assertTrue(StatsRecorder.counts(TranslationOrigin.CACHE));
    }
}
