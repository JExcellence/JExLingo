package de.jexcellence.lingo.command;

import de.jexcellence.lingo.LingoEdition;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.learning.PhraseService;
import de.jexcellence.lingo.learning.TranslationMemoryService;
import de.jexcellence.lingo.pipeline.TranslationPipeline;
import de.jexcellence.lingo.provider.ProviderGateway;
import de.jexcellence.lingo.provider.ProviderHealthMonitor;
import de.jexcellence.lingo.text.SafeText;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code /lingo status}: provider health, breaker, latency, cache and where translations came from. A high share
 * of provider results together with many corrections points to missing glossary terms.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class StatusReport {

    private static final String KEY = "lingo.status.";
    private static final String VALUE = "value";

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
        ProviderHealthMonitor.Snapshot snapshot = health.snapshot();
        SafeText.msg(KEY + "header").with("edition", edition.name()).send(sender);
        row(sender, "provider", gateway.provider().id());
        row(sender, "reachable", snapshot.reachable() ? text("reachable") : text("unreachable"));
        row(sender, "breaker", gateway.breaker().state().name().toLowerCase(Locale.ROOT));
        if (!snapshot.missing().isEmpty()) {
            row(sender, "missing", snapshot.missing().stream().map(LanguageCode::upper)
                    .collect(Collectors.joining(", ")));
        }
        row(sender, "latency", gateway.latency().p50() + " / " + gateway.latency().p95() + " ms");
        row(sender, "requests", gateway.requestCount() + " / " + gateway.failureCount() + " / "
                + gateway.refusedCount());
        row(sender, "cache", pipeline.cache().size() + " (" + Math.round(pipeline.cache().hitRate() * 100.0) + "%)");
        row(sender, "origins", origins(pipeline.stats().snapshot()));
        if (memory != null && phrases != null) {
            row(sender, "learning", memory.approvedCount() + " / " + phrases.pinnedCount());
        }
    }

    private static void row(@NotNull CommandSender sender, @NotNull String label, @NotNull String value) {
        SafeText.msg(KEY + "row")
                .with("label", SafeText.msg(KEY + "label." + label).text(null))
                .with(VALUE, value)
                .send(sender);
    }

    private static @NotNull String text(@NotNull String key) {
        return SafeText.msg(KEY + "value." + key).text(null);
    }

    private static @NotNull String origins(@NotNull Map<TranslationOrigin, Long> counts) {
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 0L)
                .map(entry -> entry.getKey().name().toLowerCase(Locale.ROOT) + " " + entry.getValue())
                .collect(Collectors.joining(", "));
    }
}
