package de.jexcellence.lingo.stats;

import de.jexcellence.lingo.api.TranslationOrigin;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The totals of a period, built from {@link StatRow}s.
 *
 * @param translations results counted
 * @param characters   characters of the originals
 * @param byOrigin     results per source
 * @param byPair       results per language pair key ({@code de>en})
 * @param latencyMs    summed latency of provider results
 * @author JExcellence
 * @since 0.3.0
 */
public record StatsSummary(
        long translations,
        long characters,
        @NotNull Map<TranslationOrigin, Long> byOrigin,
        @NotNull Map<String, Long> byPair,
        long latencyMs
) {

    /** Sources that answered without calling the provider. */
    private static final Set<TranslationOrigin> SAVED = EnumSet.of(TranslationOrigin.MEMORY, TranslationOrigin.PHRASE,
            TranslationOrigin.CACHE, TranslationOrigin.GLOSSARY);

    /** A period without results. */
    public static final StatsSummary EMPTY = of(List.of());

    /**
     * Copies the maps.
     *
     * @param translations results counted
     * @param characters   characters of the originals
     * @param byOrigin     results per source
     * @param byPair       results per language pair
     * @param latencyMs    summed provider latency
     */
    public StatsSummary {
        byOrigin = Collections.unmodifiableMap(new EnumMap<>(byOrigin.isEmpty()
                ? new EnumMap<>(TranslationOrigin.class) : byOrigin));
        byPair = Map.copyOf(byPair);
    }

    /**
     * Adds up rows.
     *
     * @param rows the rows of the period
     * @return the summary
     */
    public static @NotNull StatsSummary of(@NotNull Collection<StatRow> rows) {
        long translations = 0L;
        long characters = 0L;
        long latency = 0L;
        Map<TranslationOrigin, Long> byOrigin = new EnumMap<>(TranslationOrigin.class);
        Map<String, Long> byPair = new HashMap<>();
        for (StatRow row : rows) {
            translations += row.translations();
            characters += row.characters();
            byOrigin.merge(row.origin(), row.translations(), Long::sum);
            byPair.merge(row.pair().key(), row.translations(), Long::sum);
            if (row.origin() == TranslationOrigin.PROVIDER) {
                latency += row.latencyMs();
            }
        }
        return new StatsSummary(translations, characters, byOrigin, byPair, latency);
    }

    /**
     * Returns the results of one source.
     *
     * @param origin the source
     * @return the count
     */
    public long count(@NotNull TranslationOrigin origin) {
        return byOrigin.getOrDefault(origin, 0L);
    }

    /**
     * Returns results answered without a provider call (memory, phrases, cache, glossary).
     *
     * @return the count
     */
    public long savedCalls() {
        return SAVED.stream().mapToLong(this::count).sum();
    }

    /**
     * Returns the share of results answered without a provider call.
     *
     * @return 0 to 100
     */
    public int savedPercent() {
        return percent(savedCalls());
    }

    /**
     * Returns the share of results that fell back to the original.
     *
     * @return 0 to 100
     */
    public int fallbackPercent() {
        return percent(count(TranslationOrigin.FALLBACK));
    }

    /**
     * Returns the average latency of a provider result.
     *
     * @return milliseconds, or 0 without provider results
     */
    public long averageProviderLatency() {
        long calls = count(TranslationOrigin.PROVIDER);
        return calls == 0L ? 0L : latencyMs / calls;
    }

    /**
     * Returns the busiest language pairs.
     *
     * @param limit how many pairs
     * @return pair key and count, busiest first
     */
    public @NotNull List<Map.Entry<String, Long>> topPairs(int limit) {
        return byPair.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .toList();
    }

    private int percent(long part) {
        return translations == 0L ? 0 : (int) Math.round(part * 100.0 / translations);
    }
}
