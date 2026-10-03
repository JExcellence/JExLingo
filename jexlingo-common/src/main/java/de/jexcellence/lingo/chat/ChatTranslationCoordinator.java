package de.jexcellence.lingo.chat;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationContext;
import de.jexcellence.lingo.api.TranslationRequest;
import de.jexcellence.lingo.api.TranslationResult;
import de.jexcellence.lingo.api.event.ChatTranslatedEvent;
import de.jexcellence.lingo.config.ChatMode;
import de.jexcellence.lingo.config.ChatSettings;
import de.jexcellence.lingo.learning.RecentMessage;
import de.jexcellence.lingo.pipeline.SkipRules;
import de.jexcellence.lingo.pipeline.TranslateOptions;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Starts the translations of one public chat message. Groups the viewers by language, starts one pipeline call per
 * target language without waiting, remembers the message for suggestions and fires {@link ChatTranslatedEvent}
 * once all translations are done. In follow-up mode it also sends the translated lines when they arrive.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class ChatTranslationCoordinator {

    private final ChatContext context;
    private final TranslatedLineDecorator decorator;
    private final AtomicReference<ChatSettings> chat;
    private final Map<UUID, Long> lastProviderUse = new ConcurrentHashMap<>();

    /**
     * Creates the coordinator.
     *
     * @param context   chat services
     * @param decorator line decorator
     * @param chat      chat settings
     */
    public ChatTranslationCoordinator(@NotNull ChatContext context, @NotNull TranslatedLineDecorator decorator,
                                      @NotNull ChatSettings chat) {
        this.context = context;
        this.decorator = decorator;
        this.chat = new AtomicReference<>(chat);
    }

    /**
     * Starts translating a message.
     *
     * @param sender         the sender
     * @param text           the plain message
     * @param viewers        the event's viewers
     * @param mayWaitInline  whether the renderer may wait (only on the async chat thread)
     * @return the session, or empty when nobody needs a translation
     */
    public @NotNull Optional<ChatSession> begin(@NotNull Player sender, @NotNull String text,
                                                @NotNull Collection<? extends Audience> viewers,
                                                boolean mayWaitInline) {
        ChatSettings settings = chat.get();
        if (!context.settings().get(sender.getUniqueId()).translateOutgoing()
                || SkipRules.from(settings).skips(text, TranslationContext.CHAT)) {
            return Optional.empty();
        }
        LanguageCode assumed = context.resolver().resolve(sender);
        Map<UUID, LanguageCode> viewerLanguages = viewerLanguages(sender, viewers);
        if (viewerLanguages.isEmpty()) {
            return Optional.empty();
        }
        RecentMessage message = context.recent().register(sender.getUniqueId(), sender.getName(), text, assumed);
        CompletableFuture<LanguageCode> source = context.detector()
                .detect(text, assumed, context.resolver().languages())
                .thenApply(detected -> {
                    message.setSource(detected);
                    return detected;
                });
        TranslateOptions options = new TranslateOptions(providerAllowed(sender.getUniqueId(), settings),
                onlineNames());
        Map<LanguageCode, CompletableFuture<TranslationResult>> results = new HashMap<>();
        for (LanguageCode target : new LinkedHashSet<>(viewerLanguages.values())) {
            results.put(target, translate(text, target, source, options, message));
        }
        boolean inline = mayWaitInline && settings.mode() == ChatMode.INLINE;
        ChatSession session = new ChatSession(message, results, viewerLanguages, inline,
                settings.inlineWait().toNanos());
        whenAllDone(sender, session, source);
        if (!inline) {
            sendFollowUps(session);
        }
        return Optional.of(session);
    }

    /**
     * Replaces the chat settings after a reload.
     *
     * @param value the new settings
     */
    public void setChatSettings(@NotNull ChatSettings value) {
        chat.set(value);
    }

    /**
     * Forgets a player who left.
     *
     * @param player the player's UUID
     */
    public void forget(@NotNull UUID player) {
        lastProviderUse.remove(player);
    }

    private @NotNull Map<UUID, LanguageCode> viewerLanguages(@NotNull Player sender,
                                                             @NotNull Collection<? extends Audience> viewers) {
        LanguageCode senderLanguage = context.resolver().resolve(sender);
        boolean detection = context.detector().isEnabled();
        Map<UUID, LanguageCode> languages = new HashMap<>();
        for (Audience audience : viewers) {
            if (audience instanceof Player viewer && !viewer.equals(sender)
                    && context.settings().get(viewer.getUniqueId()).translateIncoming()) {
                LanguageCode language = context.resolver().resolve(viewer);
                if (detection || !language.equals(senderLanguage)) {
                    languages.put(viewer.getUniqueId(), language);
                }
            }
        }
        return languages;
    }

    private @NotNull CompletableFuture<TranslationResult> translate(@NotNull String text,
                                                                    @NotNull LanguageCode target,
                                                                    @NotNull CompletableFuture<LanguageCode> source,
                                                                    @NotNull TranslateOptions options,
                                                                    @NotNull RecentMessage message) {
        TranslationRequest request = new TranslationRequest(text, null, target, TranslationContext.CHAT);
        return source.thenCompose(language -> context.pipeline().translate(request, language, options))
                .thenApply(result -> {
                    if (result.translated()) {
                        message.putTranslation(target, result.text());
                    }
                    return result;
                });
    }

    private boolean providerAllowed(@NotNull UUID sender, @NotNull ChatSettings settings) {
        long now = System.currentTimeMillis();
        long cooldown = settings.playerCooldown().toMillis();
        Long last = lastProviderUse.get(sender);
        if (last != null && now - last < cooldown) {
            return false;
        }
        lastProviderUse.put(sender, now);
        return true;
    }

    private static @NotNull List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    private void whenAllDone(@NotNull Player sender, @NotNull ChatSession session,
                             @NotNull CompletableFuture<LanguageCode> source) {
        CompletableFuture<?>[] all = session.results().values().toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(all).thenRunAsync(() -> {
            Map<LanguageCode, TranslationResult> done = new HashMap<>();
            session.results().forEach((language, future) -> done.put(language, future.join()));
            Bukkit.getPluginManager().callEvent(new ChatTranslatedEvent(sender, session.message().original(),
                    source.join(), done));
        }, context.worker());
    }

    private void sendFollowUps(@NotNull ChatSession session) {
        session.results().forEach((language, future) -> future.thenAccept(result -> {
            if (!result.translated()) {
                return;
            }
            session.viewerLanguages().forEach((viewerId, viewerLanguage) -> {
                Player viewer = viewerLanguage.equals(language) ? Bukkit.getPlayer(viewerId) : null;
                if (viewer != null) {
                    context.scheduler().runAtEntity(viewer, () -> deliver(viewer, session, result));
                }
            });
        }));
    }

    private void deliver(@NotNull Player viewer, @NotNull ChatSession session, @NotNull TranslationResult result) {
        context.recent().markSeen(viewer.getUniqueId(), session.message().id());
        viewer.sendMessage(decorator.followUp(session.message().senderName(), result, viewer,
                session.message().id()));
    }
}
