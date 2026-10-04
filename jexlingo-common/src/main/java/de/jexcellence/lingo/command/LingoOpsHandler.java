package de.jexcellence.lingo.command;

import com.raindropcentral.commands.v2.CommandContext;
import com.raindropcentral.commands.v2.CommandHandler;
import de.jexcellence.lingo.LingoEdition;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.bedrock.StatsForm;
import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.settings.IncomingMode;
import de.jexcellence.lingo.settings.PlayerLanguageSettings;
import de.jexcellence.lingo.settings.PlayerSettingsService;
import de.jexcellence.lingo.stats.StatsLines;
import de.jexcellence.lingo.stats.StatsPeriod;
import de.jexcellence.lingo.stats.StatsSources;
import de.jexcellence.lingo.text.SafeText;
import de.jexcellence.lingo.view.StatsView;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

/**
 * Staff tools of {@code /lingo}: statistics, inspecting and changing a player's settings, pausing chat
 * translation, pinging the provider and clearing the cache.
 *
 * @author JExcellence
 * @since 0.3.0
 */
public final class LingoOpsHandler {

    private static final String ROOT = "lingo.";
    private static final String KEY = "lingo.ops.";
    private static final String PLAYER = "player";
    private static final String NAME = "name";
    private static final String VALUE = "value";
    private static final String PING_TEXT = "Hello";

    private final StatsSources sources;
    private final LingoEdition edition;
    private final PlayerSettingsService settings;
    private final LanguageResolver resolver;
    private final StatsView statsView;
    private final Replies replies;
    private @Nullable StatsForm statsForm;

    /**
     * Creates the handler.
     *
     * @param sources   statistics and provider state
     * @param edition   the edition
     * @param settings  player settings
     * @param resolver  language resolver
     * @param statsView statistics menu
     * @param replies   reply helper
     */
    public LingoOpsHandler(@NotNull StatsSources sources, @NotNull LingoEdition edition,
                           @NotNull PlayerSettingsService settings, @NotNull LanguageResolver resolver,
                           @NotNull StatsView statsView, @NotNull Replies replies) {
        this.sources = sources;
        this.edition = edition;
        this.settings = settings;
        this.resolver = resolver;
        this.statsView = statsView;
        this.replies = replies;
    }

    /**
     * Wires the Bedrock statistics form when Floodgate is present.
     *
     * @param value the form
     */
    public void setStatsForm(@Nullable StatsForm value) {
        this.statsForm = value;
    }

    /**
     * Returns the handlers by tree path.
     *
     * @return the handlers
     */
    public @NotNull Map<String, CommandHandler> handlerMap() {
        Map<String, CommandHandler> handlers = new LinkedHashMap<>();
        handlers.put(ROOT + "stats", this::onStats);
        handlers.put(ROOT + "inspect", this::onInspect);
        handlers.put(ROOT + "set.lang", ctx -> onSetLanguage(ctx, false));
        handlers.put(ROOT + "set.write", ctx -> onSetLanguage(ctx, true));
        handlers.put(ROOT + "set.incoming", ctx -> change(ctx, current -> current.withIncoming(
                ctx.require("mode", IncomingMode.class))));
        handlers.put(ROOT + "set.outgoing", ctx -> change(ctx, current -> current.withOutgoing(enabled(ctx))));
        handlers.put(ROOT + "set.original", ctx -> change(ctx, current -> current.withShowOriginal(enabled(ctx))));
        handlers.put(ROOT + "pause", this::onPause);
        handlers.put(ROOT + "resume", this::onResume);
        handlers.put(ROOT + "ping", this::onPing);
        handlers.put(ROOT + "cache.clear", this::onCacheClear);
        return handlers;
    }

    private void onStats(@NotNull CommandContext ctx) {
        StatsPeriod period = ctx.get("period", String.class).flatMap(StatsPeriod::parse).orElse(StatsPeriod.TODAY);
        if (period != StatsPeriod.TODAY && !edition.statisticsHistory()) {
            replies.send(ctx.sender(), KEY + "history_premium");
            period = StatsPeriod.TODAY;
        }
        Player player = ctx.asPlayer().orElse(null);
        if (player != null && statsForm != null && statsForm.isBedrock(player)) {
            statsForm.open(player, period);
        } else if (player != null) {
            statsView.open(player);
        } else {
            CommandSender sender = ctx.sender();
            StatsPeriod chosen = period;
            sources.stats().summary(chosen).thenAccept(summary -> replies.run(sender,
                    () -> StatsLines.panel(null, chosen, summary, sources).send(sender)));
        }
    }

    private void onInspect(@NotNull CommandContext ctx) {
        OfflinePlayer target = ctx.require(PLAYER, OfflinePlayer.class);
        UUID uuid = target.getUniqueId();
        CommandSender sender = ctx.sender();
        settings.load(uuid).thenAccept(loaded -> replies.run(sender, () -> {
            Player online = target.getPlayer();
            if (online == null) {
                settings.forget(uuid);
            }
            InspectReport.send(sender, displayName(target), loaded, online, resolver);
        }));
    }

    private void onSetLanguage(@NotNull CommandContext ctx, boolean writing) {
        String choice = ctx.require("language", String.class);
        LanguageCode language = LingoArgumentTypes.AUTO.equals(choice) ? null : LanguageCode.of(choice);
        change(ctx, current -> writing ? current.withWriteLanguage(language) : current.withLanguage(language));
    }

    private void change(@NotNull CommandContext ctx, @NotNull UnaryOperator<PlayerLanguageSettings> change) {
        OfflinePlayer target = ctx.require(PLAYER, OfflinePlayer.class);
        UUID uuid = target.getUniqueId();
        CompletableFuture<PlayerLanguageSettings> ready = target.isOnline()
                ? CompletableFuture.completedFuture(settings.get(uuid))
                : settings.load(uuid);
        ready.thenCompose(loaded -> settings.update(uuid, change)).thenRun(() -> {
            if (!target.isOnline()) {
                settings.forget(uuid);
            }
            replies.sendUser(ctx.sender(), SafeText.msg(KEY + "set.done"), Map.of(NAME, displayName(target)));
        });
    }

    private void onPause(@NotNull CommandContext ctx) {
        Duration duration = ctx.get("minutes", Long.class).map(Duration::ofMinutes).orElse(null);
        sources.toggle().pause(duration);
        if (duration == null) {
            replies.send(ctx.sender(), KEY + "paused");
        } else {
            replies.send(ctx.sender(), SafeText.msg(KEY + "paused_for").with("minutes", duration.toMinutes()));
        }
    }

    private void onResume(@NotNull CommandContext ctx) {
        sources.toggle().resume();
        replies.send(ctx.sender(), KEY + "resumed");
    }

    private void onPing(@NotNull CommandContext ctx) {
        List<LanguageCode> enabled = resolver.languages().enabled();
        if (enabled.size() < 2) {
            replies.send(ctx.sender(), KEY + "ping.one_language");
            return;
        }
        sources.health().check();
        long started = System.nanoTime();
        sources.gateway().translate(PING_TEXT, enabled.get(0), enabled.get(1)).whenComplete((result, error) -> {
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            if (error != null) {
                replies.sendUser(ctx.sender(), SafeText.msg(KEY + "ping.failed"),
                        Map.of(VALUE, rootMessage(error)));
            } else {
                replies.send(ctx.sender(), SafeText.msg(KEY + "ping.done").with("millis", millis)
                        .with("provider", sources.gateway().provider().id()));
            }
        });
    }

    private void onCacheClear(@NotNull CommandContext ctx) {
        long size = sources.pipeline().cache().size();
        sources.pipeline().cache().clear();
        replies.send(ctx.sender(), SafeText.msg(KEY + "cache_cleared").with("count", size));
    }

    private static boolean enabled(@NotNull CommandContext ctx) {
        return Boolean.TRUE.equals(ctx.require("state", Boolean.class));
    }

    private static @NotNull String displayName(@NotNull OfflinePlayer target) {
        String name = target.getName();
        return name == null ? target.getUniqueId().toString() : name;
    }

    private static @NotNull String rootMessage(@NotNull Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null ? root.getClass().getSimpleName() : message;
    }
}
