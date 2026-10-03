package de.jexcellence.lingo.chat;

import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationContext;
import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.api.TranslationRequest;
import de.jexcellence.lingo.api.TranslationResult;
import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.learning.RecentMessage;
import de.jexcellence.lingo.learning.RecentMessageBuffer;
import de.jexcellence.lingo.pipeline.TranslateOptions;
import de.jexcellence.lingo.pipeline.TranslationPipeline;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /lingo show <id>}: translates one recent line for one player, used by the translate button of the click
 * mode. Lines already translated for someone else in the same language come straight from memory.
 *
 * @author JExcellence
 * @since 0.2.0
 */
public final class OnDemandTranslator {

    /** Outcome of a request. */
    public enum Result {
        /** The translated line was sent. */
        SHOWN,
        /** The id is unknown or older than five minutes. */
        UNKNOWN_MESSAGE,
        /** The line is already in the player's language. */
        SAME_LANGUAGE,
        /** The provider could not translate it right now. */
        FAILED
    }

    private final RecentMessageBuffer recent;
    private final TranslationPipeline pipeline;
    private final LanguageResolver resolver;
    private final TranslatedLineDecorator decorator;
    private final PlatformScheduler scheduler;

    /**
     * Creates the translator.
     *
     * @param recent    recent messages
     * @param pipeline  the pipeline
     * @param resolver  language resolver
     * @param decorator line decorator
     * @param scheduler platform scheduler
     */
    public OnDemandTranslator(@NotNull RecentMessageBuffer recent, @NotNull TranslationPipeline pipeline,
                              @NotNull LanguageResolver resolver, @NotNull TranslatedLineDecorator decorator,
                              @NotNull PlatformScheduler scheduler) {
        this.recent = recent;
        this.pipeline = pipeline;
        this.resolver = resolver;
        this.decorator = decorator;
        this.scheduler = scheduler;
    }

    /**
     * Translates a recent line for a player and sends it. Call on the player's thread.
     *
     * @param viewer    the player
     * @param messageId the recent message id
     * @return the outcome
     */
    public @NotNull CompletableFuture<Result> show(@NotNull Player viewer, @NotNull String messageId) {
        Optional<RecentMessage> found = recent.find(messageId);
        if (found.isEmpty()) {
            return CompletableFuture.completedFuture(Result.UNKNOWN_MESSAGE);
        }
        RecentMessage message = found.get();
        LanguageCode target = resolver.resolve(viewer);
        LanguageCode source = message.source();
        if (source.equals(target)) {
            return CompletableFuture.completedFuture(Result.SAME_LANGUAGE);
        }
        Optional<String> known = message.translation(target);
        CompletableFuture<TranslationResult> result = known.isPresent()
                ? CompletableFuture.completedFuture(new TranslationResult(known.get(), message.original(), source,
                        target, TranslationOrigin.CACHE, 0L))
                : pipeline.translate(new TranslationRequest(message.original(), source, target, TranslationContext.CHAT),
                        source, new TranslateOptions(true, Bukkit.getOnlinePlayers().stream().map(Player::getName)
                                .toList()));
        return result.thenApply(translation -> {
            if (!translation.translated()) {
                return Result.FAILED;
            }
            message.putTranslation(target, translation.text());
            scheduler.runAtEntity(viewer, () -> {
                recent.markSeen(viewer.getUniqueId(), message.id());
                viewer.sendMessage(decorator.followUp(message.senderName(), translation, viewer, message.id()));
            });
            return Result.SHOWN;
        });
    }
}
