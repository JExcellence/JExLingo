package de.jexcellence.lingo.bedrock;

import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.lingo.LingoEdition;
import de.jexcellence.lingo.stats.StatsLines;
import de.jexcellence.lingo.stats.StatsPeriod;
import de.jexcellence.lingo.stats.StatsSources;
import de.jexcellence.lingo.text.SafeText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Bedrock mirror of {@code /lingo stats}: the statistics as text, with one button per period (Premium).
 *
 * @author JExcellence
 * @since 0.3.0
 */
public final class StatsForm {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final BedrockFormBridge bridge;
    private final StatsSources sources;
    private final LingoEdition edition;
    private final PlatformScheduler scheduler;

    /**
     * Creates the form.
     *
     * @param bridge    the Floodgate bridge
     * @param sources   statistics sources
     * @param edition   the edition, for the history buttons
     * @param scheduler platform scheduler
     */
    public StatsForm(@NotNull BedrockFormBridge bridge, @NotNull StatsSources sources, @NotNull LingoEdition edition,
                     @NotNull PlatformScheduler scheduler) {
        this.bridge = bridge;
        this.sources = sources;
        this.edition = edition;
        this.scheduler = scheduler;
    }

    /**
     * Whether a player joined through Geyser.
     *
     * @param player the player
     * @return {@code true} for Bedrock players
     */
    public boolean isBedrock(@NotNull Player player) {
        return bridge.isBedrockPlayer(player);
    }

    /**
     * Sends the statistics of a period.
     *
     * @param player the Bedrock player
     * @param period the period
     */
    public void open(@NotNull Player player, @NotNull StatsPeriod period) {
        sources.stats().summary(period).thenAccept(summary -> scheduler.runAtEntity(player, () -> {
            List<Component> lines = StatsLines.of(player, period, summary, sources);
            String content = lines.stream().map(PLAIN::serialize).collect(Collectors.joining("\n"));
            SimpleForm.Builder form = SimpleForm.builder()
                    .title(SafeText.msg("lingo_stats.title").toPlainString(player))
                    .content(content);
            List<StatsPeriod> periods = edition.statisticsHistory() ? List.of(StatsPeriod.values()) : List.of();
            periods.forEach(value -> form.button(SafeText.msg("lingo_stats.period." + value.id())
                    .toPlainString(player)));
            form.validResultHandler(response -> scheduler.runAtEntity(player,
                    () -> open(player, periods.get(response.clickedButtonId()))));
            bridge.sendForm(player, form.build());
        }));
    }
}
