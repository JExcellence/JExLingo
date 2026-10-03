package de.jexcellence.lingo.stats;

import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.text.SafeText;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The statistics as text lines, for the console and chat ({@code /lingo stats}) and the Bedrock form.
 *
 * @author JExcellence
 * @since 0.3.0
 */
public final class StatsLines {

    private static final String KEY = "lingo_stats.";
    private static final int TOP_PAIRS = 5;

    private StatsLines() {
    }

    /**
     * Builds the lines.
     *
     * @param viewer  the viewer, or {@code null} for the console
     * @param period  the period
     * @param summary the totals
     * @param sources live provider state
     * @return header and rows
     */
    public static @NotNull List<Component> of(@Nullable Player viewer, @NotNull StatsPeriod period,
                                              @NotNull StatsSummary summary, @NotNull StatsSources sources) {
        NumberFormat numbers = NumberFormat.getIntegerInstance(viewer == null ? Locale.ROOT : viewer.locale());
        List<Component> lines = new ArrayList<>();
        lines.add(SafeText.msg(KEY + "chat.header")
                .with("period", SafeText.msg(KEY + "period." + period.id()).text(viewer))
                .component(viewer));
        lines.add(row(viewer, "translations", numbers.format(summary.translations())));
        lines.add(row(viewer, "characters", numbers.format(summary.characters())));
        lines.add(row(viewer, "saved", summary.savedPercent() + "%"));
        lines.add(row(viewer, "fallback", summary.fallbackPercent() + "%"));
        lines.add(row(viewer, "calls", numbers.format(summary.count(TranslationOrigin.PROVIDER))));
        lines.add(row(viewer, "average", summary.averageProviderLatency() + " ms"));
        lines.add(row(viewer, "latency", sources.gateway().latency().p50() + " / "
                + sources.gateway().latency().p95() + " ms"));
        lines.add(row(viewer, "sources", origins(summary, numbers)));
        lines.add(row(viewer, "pairs", pairs(summary, numbers)));
        if (sources.toggle().isPaused()) {
            lines.add(row(viewer, "paused", SafeText.msg(KEY + "value.paused").text(viewer)));
        }
        return lines;
    }

    private static @NotNull Component row(@Nullable Player viewer, @NotNull String label, @NotNull String value) {
        return SafeText.msg("lingo.status.row")
                .with("label", SafeText.msg(KEY + "label." + label).text(viewer))
                .with("value", value)
                .component(viewer);
    }

    private static @NotNull String origins(@NotNull StatsSummary summary, @NotNull NumberFormat numbers) {
        String joined = summary.byOrigin().entrySet().stream()
                .filter(entry -> entry.getValue() > 0L)
                .map(entry -> entry.getKey().name().toLowerCase(Locale.ROOT) + " " + numbers.format(entry.getValue()))
                .collect(Collectors.joining(", "));
        return joined.isEmpty() ? "-" : joined;
    }

    private static @NotNull String pairs(@NotNull StatsSummary summary, @NotNull NumberFormat numbers) {
        List<Map.Entry<String, Long>> top = summary.topPairs(TOP_PAIRS);
        if (top.isEmpty()) {
            return "-";
        }
        return top.stream()
                .map(entry -> entry.getKey().toUpperCase(Locale.ROOT).replace(">", " » ") + " "
                        + numbers.format(entry.getValue()))
                .collect(Collectors.joining(", "));
    }
}
