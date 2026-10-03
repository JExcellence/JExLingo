package de.jexcellence.lingo.command;

import com.raindropcentral.commands.v2.CommandContext;
import com.raindropcentral.commands.v2.CommandHandler;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationContext;
import de.jexcellence.lingo.api.TranslationRequest;
import de.jexcellence.lingo.bedrock.LingoBedrockForms;
import de.jexcellence.lingo.glossary.GlossaryMode;
import de.jexcellence.lingo.glossary.GlossaryTerm;
import de.jexcellence.lingo.learning.TrainingExportService;
import de.jexcellence.lingo.learning.TranslationMemoryService;
import de.jexcellence.lingo.pipeline.TranslateOptions;
import de.jexcellence.lingo.text.SafeText;
import de.jexcellence.lingo.view.GlossaryView;
import de.jexcellence.lingo.view.SuggestionReviewView;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The staff part of {@code /lingo}: review queue, glossary, test translation, status, export and reload.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LingoAdminHandler {

    private static final String ROOT = "lingo.";
    private static final String KEY = "lingo.";
    private static final String ID = "id";
    private static final String TERM = "term";
    private static final String TEXT = "text";
    private static final String COUNT = "count";
    private static final String NAME = "name";
    private static final String UNAVAILABLE = "lingo.learning.unavailable";

    private final AdminServices services;
    private final StatusReport status;
    private final GlossaryView glossaryView;
    private final Replies replies;
    private @Nullable SuggestionReviewView reviewView;
    private @Nullable LingoBedrockForms forms;

    /**
     * Creates the handler.
     *
     * @param services     staff services
     * @param status       status report
     * @param glossaryView glossary menu
     * @param replies      reply helper
     */
    public LingoAdminHandler(@NotNull AdminServices services, @NotNull StatusReport status,
                             @NotNull GlossaryView glossaryView, @NotNull Replies replies) {
        this.services = services;
        this.status = status;
        this.glossaryView = glossaryView;
        this.replies = replies;
    }

    /**
     * Wires the review menu (Premium).
     *
     * @param value the view
     */
    public void setReviewView(@Nullable SuggestionReviewView value) {
        this.reviewView = value;
    }

    /**
     * Wires the Bedrock forms when Floodgate is present.
     *
     * @param value the forms
     */
    public void setForms(@Nullable LingoBedrockForms value) {
        this.forms = value;
    }

    /**
     * Returns the handlers by tree path.
     *
     * @return the handlers by tree path
     */
    public @NotNull Map<String, CommandHandler> handlerMap() {
        Map<String, CommandHandler> handlers = new LinkedHashMap<>();
        handlers.put(ROOT + "review", this::onReview);
        handlers.put(ROOT + "review.approve", this::onApprove);
        handlers.put(ROOT + "review.reject", this::onReject);
        handlers.put(ROOT + "review.block", ctx -> onBlock(ctx, true));
        handlers.put(ROOT + "review.unblock", ctx -> onBlock(ctx, false));
        handlers.put(ROOT + "glossary", this::onGlossary);
        handlers.put(ROOT + "glossary.add", this::onGlossaryAdd);
        handlers.put(ROOT + "glossary.remove", this::onGlossaryRemove);
        handlers.put(ROOT + "glossary.list", this::onGlossaryList);
        handlers.put(ROOT + "test", this::onTest);
        handlers.put(ROOT + "status", ctx -> status.send(ctx.sender()));
        handlers.put(ROOT + "export", this::onExport);
        handlers.put(ROOT + "reload", this::onReload);
        handlers.put(ROOT + "erase", this::onErase);
        return handlers;
    }

    private void onReview(@NotNull CommandContext ctx) {
        Player player = ctx.asPlayer().orElse(null);
        if (services.memory() == null || reviewView == null) {
            replies.send(ctx.sender(), UNAVAILABLE);
        } else if (player == null) {
            replies.send(ctx.sender(), "lingo.error.players_only");
        } else if (forms != null && forms.isBedrock(player)) {
            forms.openReview(player);
        } else {
            reviewView.open(player);
        }
    }

    private void onApprove(@NotNull CommandContext ctx) {
        TranslationMemoryService memory = services.memory();
        if (memory == null) {
            replies.send(ctx.sender(), UNAVAILABLE);
            return;
        }
        long id = ctx.require(ID, Long.class);
        memory.approve(id, ctx.get(TEXT, String.class).orElse(null)).thenAccept(entry -> replies.send(ctx.sender(),
                SafeText.msg(entry.isPresent() ? KEY + "review.approved" : KEY + "review.not_pending").with(ID, id)));
    }

    private void onReject(@NotNull CommandContext ctx) {
        TranslationMemoryService memory = services.memory();
        if (memory == null) {
            replies.send(ctx.sender(), UNAVAILABLE);
            return;
        }
        long id = ctx.require(ID, Long.class);
        memory.reject(id).thenAccept(done -> replies.send(ctx.sender(),
                SafeText.msg(Boolean.TRUE.equals(done) ? KEY + "review.rejected" : KEY + "review.not_pending")
                        .with(ID, id)));
    }

    private void onBlock(@NotNull CommandContext ctx, boolean blocked) {
        OfflinePlayer target = ctx.require("player", OfflinePlayer.class);
        String name = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        services.settings().load(target.getUniqueId())
                .thenCompose(loaded -> services.settings().update(target.getUniqueId(),
                        current -> current.withSuggestionsBlocked(blocked)))
                .thenRun(() -> {
                    if (!target.isOnline()) {
                        services.settings().forget(target.getUniqueId());
                    }
                    replies.send(ctx.sender(), SafeText.msg(KEY + (blocked ? "review.blocked" : "review.unblocked"))
                            .with(NAME, name));
                });
    }

    private void onGlossary(@NotNull CommandContext ctx) {
        Player player = ctx.asPlayer().orElse(null);
        if (player == null) {
            onGlossaryList(ctx);
        } else if (forms != null && forms.isBedrock(player)) {
            forms.openGlossary(player);
        } else {
            glossaryView.open(player);
        }
    }

    private void onGlossaryAdd(@NotNull CommandContext ctx) {
        GlossaryMode mode = ctx.require("mode", GlossaryMode.class);
        String term = ctx.require(TERM, String.class).trim();
        String replacement = ctx.get("replacement", String.class).map(String::trim).orElse(null);
        GlossaryTerm candidate = new GlossaryTerm(0L, term, replacement, mode, null, null);
        services.glossary().add(candidate).thenAccept(result -> replies.sendUser(ctx.sender(),
                SafeText.msg(KEY + "glossary.add." + result.name().toLowerCase(Locale.ROOT)),
                Map.of(TERM, term)));
    }

    private void onGlossaryRemove(@NotNull CommandContext ctx) {
        String term = ctx.require(TERM, String.class);
        services.glossary().remove(term).thenAccept(count -> replies.sendUser(ctx.sender(),
                SafeText.msg(count > 0 ? KEY + "glossary.removed" : KEY + "glossary.not_found").with(COUNT, count),
                Map.of(TERM, term)));
    }

    private void onGlossaryList(@NotNull CommandContext ctx) {
        CommandSender sender = ctx.sender();
        List<GlossaryTerm> terms = services.glossary().terms();
        SafeText.msg(KEY + "glossary.list.header").with(COUNT, terms.size()).prefix().send(sender);
        for (GlossaryTerm term : terms) {
            String replacement = term.forces() ? term.replacement() : "";
            sender.sendMessage(SafeText.component(SafeText.msg(KEY + "glossary.list.entry")
                            .with("mode", SafeText.msg("lingo_glossary.mode." + modeKey(term.mode())).text(null)),
                    sender instanceof Player player ? player : null,
                    Map.of(TERM, term.term(), "replacement", replacement)));
        }
    }

    private void onTest(@NotNull CommandContext ctx) {
        LanguageCode from = ctx.require("from", LanguageCode.class);
        LanguageCode to = ctx.require("to", LanguageCode.class);
        String text = ctx.require(TEXT, String.class);
        TranslationRequest request = new TranslationRequest(text, from, to, TranslationContext.API);
        services.pipeline().translate(request, from, TranslateOptions.DEFAULT).thenAccept(result -> replies.sendUser(
                ctx.sender(),
                SafeText.msg(KEY + "test.result")
                        .with("from", from.upper())
                        .with("to", to.upper())
                        .with("origin", result.origin().name().toLowerCase(Locale.ROOT))
                        .with("millis", result.latencyMillis()),
                Map.of(TEXT, result.text())));
    }

    private void onExport(@NotNull CommandContext ctx) {
        TrainingExportService export = services.export();
        if (export == null) {
            replies.send(ctx.sender(), UNAVAILABLE);
            return;
        }
        export.export().whenComplete((written, error) -> {
            if (error != null) {
                replies.send(ctx.sender(), KEY + "export.failed");
            } else {
                replies.send(ctx.sender(), SafeText.msg(KEY + "export.done")
                        .with(COUNT, written.entries())
                        .with("file", written.file().getFileName().toString()));
            }
        });
    }

    private void onErase(@NotNull CommandContext ctx) {
        OfflinePlayer target = ctx.require("player", OfflinePlayer.class);
        String name = target.getName() == null ? target.getUniqueId().toString() : target.getName();
        TranslationMemoryService memory = services.memory();
        CompletableFuture<Integer> suggestions = memory == null
                ? CompletableFuture.completedFuture(0)
                : memory.erasePending(target.getUniqueId());
        services.settings().erase(target.getUniqueId())
                .thenCombine(suggestions, (settingsRow, removed) -> removed)
                .whenComplete((removed, error) -> {
                    if (error != null) {
                        replies.send(ctx.sender(), KEY + "erase.failed");
                    } else {
                        replies.send(ctx.sender(), SafeText.msg(KEY + "erase.done").with(NAME, name)
                                .with(COUNT, removed));
                    }
                });
    }

    private void onReload(@NotNull CommandContext ctx) {
        services.reload().run();
        replies.send(ctx.sender(), KEY + "reload.done");
    }

    private static @NotNull String modeKey(@NotNull GlossaryMode mode) {
        return mode == GlossaryMode.FORCE ? "force" : "keep";
    }
}
