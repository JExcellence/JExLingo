package de.jexcellence.lingo.stats;

import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.provider.CircuitBreaker;
import de.jexcellence.lingo.text.LingoPanel;
import de.jexcellence.lingo.text.SafeText;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The statistics as a chat panel ({@code /lingo stats} from the console) and as plain lines for the Bedrock form,
 * plus the shared wording of sources and breaker states used by {@code /lingo status} and the menus.
 *
 * @author JExcellence
 * @since 0.3.0
 */
public final class StatsLines {

    private static final String KEY = "lingo_stats.";
    private static final String LABEL = KEY + "label.";
    private static final String NONE = "-";
    private static final int TOP_PAIRS = 5;

    private StatsLines() {
    }

    /**
     * Builds the statistics panel.
     *
     * @param viewer  the viewer, or {@code null} for the console
     * @param period  the period
     * @param summary the totals
     * @param sources live provider state
     * @return the panel, ready to send or to flatten
     */
    public static @NotNull LingoPanel panel(@Nullable Player viewer, @NotNull StatsPeriod period,
                                            @NotNull StatsSummary summary, @NotNull StatsSources sources) {
        NumberFormat numbers = numbers(viewer);
        LingoPanel panel = LingoPanel.forViewer(viewer)
                .header(KEY + "chat.header")
                .context(SafeText.msg(KEY + "chat.context")
                        .with("period", SafeText.msg(KEY + "period." + period.id()).plain(viewer))
                        .component(viewer))
                .section(KEY + "overview.section")
                .row(LABEL + "translations", numbers.format(summary.translations()))
                .row(LABEL + "characters", numbers.format(summary.characters()))
                .row(LABEL + "saved", summary.savedPercent() + "%", LingoPanel.Tone.OK)
                .row(LABEL + "fallback", summary.fallbackPercent() + "%")
                .section(KEY + "provider.section")
                .row(LABEL + "calls", numbers.format(summary.count(TranslationOrigin.PROVIDER)))
                .row(LABEL + "average", summary.averageProviderLatency() + " ms")
                .row(LABEL + "latency", sources.gateway().latency().p50() + " / "
                        + sources.gateway().latency().p95() + " ms")
                .section(KEY + "origins.section")
                .row(LABEL + "sources", origins(viewer, summary.byOrigin(), numbers))
                .row(LABEL + "pairs", pairs(summary, numbers));
        if (sources.toggle().isPaused()) {
            panel.row(LABEL + "paused", SafeText.msg(KEY + "value.paused").plain(viewer), LingoPanel.Tone.WARN);
        }
        return panel;
    }

    /**
     * The statistics as plain lines without padding, for the Bedrock form.
     *
     * @param viewer  the viewer
     * @param period  the period
     * @param summary the totals
     * @param sources live provider state
     * @return header and rows
     */
    public static @NotNull List<Component> of(@Nullable Player viewer, @NotNull StatsPeriod period,
                                              @NotNull StatsSummary summary, @NotNull StatsSources sources) {
        return panel(viewer, period, summary, sources).plainLines();
    }

    /**
     * The non-zero result counts by source, with translated source names.
     *
     * @param viewer  the viewer
     * @param counts  results per source
     * @param numbers the number format
     * @return for example {@code Cache 12, Translator 4}, or {@code -}
     */
    public static @NotNull String origins(@Nullable Player viewer, @NotNull Map<TranslationOrigin, Long> counts,
                                          @NotNull NumberFormat numbers) {
        String joined = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0L)
                .map(entry -> originName(viewer, entry.getKey()) + " " + numbers.format(entry.getValue()))
                .collect(Collectors.joining(", "));
        return joined.isEmpty() ? NONE : joined;
    }

    /**
     * The translated name of a result source.
     *
     * @param viewer the viewer
     * @param origin the source
     * @return the name
     */
    public static @NotNull String originName(@Nullable Player viewer, @NotNull TranslationOrigin origin) {
        return SafeText.msg("lingo.origin." + origin.name().toLowerCase(Locale.ROOT)).plain(viewer);
    }

    /**
     * The translated state of the circuit breaker.
     *
     * @param viewer the viewer
     * @param state  the state
     * @return the state name
     */
    public static @NotNull String breakerName(@Nullable Player viewer, @NotNull CircuitBreaker.State state) {
        return SafeText.msg("lingo.breaker." + state.name().toLowerCase(Locale.ROOT)).plain(viewer);
    }

    /**
     * The colour of a breaker state: closed is healthy, open blocks calls, half open is probing.
     *
     * @param state the state
     * @return the tone
     */
    public static @NotNull LingoPanel.Tone breakerTone(@NotNull CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED -> LingoPanel.Tone.OK;
            case OPEN -> LingoPanel.Tone.BAD;
            case HALF_OPEN -> LingoPanel.Tone.WARN;
            default -> throw new IllegalStateException("Unexpected breaker state: " + state);
        };
    }

    /**
     * The number format of a viewer.
     *
     * @param viewer the viewer, or {@code null} for the console
     * @return the integer format
     */
    public static @NotNull NumberFormat numbers(@Nullable Player viewer) {
        return NumberFormat.getIntegerInstance(viewer == null ? Locale.ROOT : viewer.locale());
    }

    private static @NotNull String pairs(@NotNull StatsSummary summary, @NotNull NumberFormat numbers) {
        List<Map.Entry<String, Long>> top = summary.topPairs(TOP_PAIRS);
        if (top.isEmpty()) {
            return NONE;
        }
        return top.stream()
                .map(entry -> entry.getKey().toUpperCase(Locale.ROOT).replace(">", " » ") + " "
                        + numbers.format(entry.getValue()))
                .collect(Collectors.joining(", "));
    }
}
