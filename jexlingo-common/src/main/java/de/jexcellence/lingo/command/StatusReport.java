package de.jexcellence.lingo.command;

import de.jexcellence.lingo.LingoEdition;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.learning.PhraseService;
import de.jexcellence.lingo.learning.TranslationMemoryService;
import de.jexcellence.lingo.pipeline.TranslationPipeline;
import de.jexcellence.lingo.provider.CircuitBreaker;
import de.jexcellence.lingo.provider.ProviderGateway;
import de.jexcellence.lingo.provider.ProviderHealthMonitor;
import de.jexcellence.lingo.stats.StatsLines;
import de.jexcellence.lingo.text.LingoPanel;
import de.jexcellence.lingo.text.SafeText;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.stream.Collectors;

/**
 * {@code /lingo status}: provider health, breaker, latency, cache and where translations came from, as one chat
 * panel. A high share of provider results together with many corrections points to missing glossary terms.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class StatusReport {

    private static final String KEY = "lingo.status.";
    private static final String LABEL = KEY + "label.";
    private static final String SEPARATOR = " / ";

    private final LingoEdition edition;
    private final ProviderGateway gateway;
    private final ProviderHealthMonitor health;
    private final TranslationPipeline pipeline;
    private @Nullable TranslationMemoryService memory;
    private @Nullable PhraseService phrases;

    /**
     * Creates the report.
     *
     * @param edition  the edition
     * @param gateway  the provider gateway
     * @param health   the health monitor
     * @param pipeline the pipeline
     */
    public StatusReport(@NotNull LingoEdition edition, @NotNull ProviderGateway gateway,
                        @NotNull ProviderHealthMonitor health, @NotNull TranslationPipeline pipeline) {
        this.edition = edition;
        this.gateway = gateway;
        this.health = health;
        this.pipeline = pipeline;
    }

    /**
     * Wires the learning layer (Premium).
     *
     * @param memoryService  the translation memory
     * @param phraseService  the pinned phrases
     */
    public void setLearning(@Nullable TranslationMemoryService memoryService, @Nullable PhraseService phraseService) {
        this.memory = memoryService;
        this.phrases = phraseService;
    }

    /**
     * Sends the report.
     *
     * @param sender the receiver
     */
    public void send(@NotNull CommandSender sender) {
        LingoPanel panel = LingoPanel.of(sender);
        Player viewer = panel.viewer();
        panel.header(KEY + "header")
                .context(SafeText.msg(KEY + "context").with("edition", edition.name()).component(viewer));
        provider(panel, viewer);
        pipeline(panel, viewer);
        panel.footer(KEY + "footer").send(sender);
    }

    private void provider(@NotNull LingoPanel panel, @Nullable Player viewer) {
        ProviderHealthMonitor.Snapshot snapshot = health.snapshot();
        CircuitBreaker.State breaker = gateway.breaker().state();
        boolean reachable = snapshot.reachable();
        String reachability = SafeText.msg(KEY + "value." + (reachable ? "reachable" : "unreachable")).plain(viewer);
        panel.section(KEY + "section.provider")
                .row(LABEL + "provider", gateway.provider().id())
                .row(LABEL + "reachable", reachability, reachable ? LingoPanel.Tone.OK : LingoPanel.Tone.BAD)
                .row(LABEL + "breaker", StatsLines.breakerName(viewer, breaker), StatsLines.breakerTone(breaker));
        if (!snapshot.missing().isEmpty()) {
            panel.row(LABEL + "missing", snapshot.missing().stream().map(LanguageCode::upper)
                    .collect(Collectors.joining(", ")), LingoPanel.Tone.WARN);
        }
        panel.row(LABEL + "latency", gateway.latency().p50() + SEPARATOR + gateway.latency().p95() + " ms")
                .row(LABEL + "requests", gateway.requestCount() + SEPARATOR + gateway.failureCount() + SEPARATOR
                        + gateway.refusedCount());
    }

    private void pipeline(@NotNull LingoPanel panel, @Nullable Player viewer) {
        NumberFormat numbers = StatsLines.numbers(viewer);
        panel.section(KEY + "section.pipeline")
                .row(LABEL + "cache", numbers.format(pipeline.cache().size()) + " ("
                        + Math.round(pipeline.cache().hitRate() * 100.0) + "%)")
                .row(LABEL + "inflight", Integer.toString(pipeline.inFlight()))
                .row(LABEL + "slang", Integer.toString(pipeline.preparer().slang().size()))
                .row(LABEL + "origins", StatsLines.origins(viewer, pipeline.stats().snapshot(), numbers));
        if (memory != null && phrases != null) {
            panel.row(LABEL + "learning", memory.approvedCount() + SEPARATOR + phrases.pinnedCount());
        }
    }
}
