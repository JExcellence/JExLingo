package de.jexcellence.lingo.chat;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationContext;
import de.jexcellence.lingo.api.TranslationRequest;
import de.jexcellence.lingo.api.TranslationResult;
import de.jexcellence.lingo.api.event.ChatTranslatedEvent;
import de.jexcellence.lingo.config.ChatMode;
import de.jexcellence.lingo.config.ChatSettings;
import de.jexcellence.lingo.language.LocalLanguageGuess;
import de.jexcellence.lingo.language.WritingLanguageLearner;
import de.jexcellence.lingo.learning.RecentMessage;
import de.jexcellence.lingo.pipeline.SkipRules;
import de.jexcellence.lingo.pipeline.TranslateOptions;
import de.jexcellence.lingo.settings.IncomingMode;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final WritingLanguageLearner learner;
    private final TranslationSwitch toggle;
    private final Map<UUID, Long> lastProviderUse = new ConcurrentHashMap<>();

    /**
     * Creates the coordinator.
     *
     * @param context   chat services
     * @param decorator line decorator
     * @param chat      chat settings
     * @param learner   writing-language learner
     * @param toggle    staff pause switch
     */
    public ChatTranslationCoordinator(@NotNull ChatContext context, @NotNull TranslatedLineDecorator decorator,
                                      @NotNull ChatSettings chat, @NotNull WritingLanguageLearner learner,
                                      @NotNull TranslationSwitch toggle) {
        this.context = context;
        this.decorator = decorator;
        this.chat = new AtomicReference<>(chat);
        this.learner = learner;
        this.toggle = toggle;
    }

    /**
     * The viewers of one message, split by how they receive other languages.
     *
     * @param auto  viewer UUID to target language, translated automatically
     * @param click viewers who get a translate button instead
     */
    private record Audiences(@NotNull Map<UUID, LanguageCode> auto, @NotNull Set<UUID> click) {

        boolean isEmpty() {
            return auto.isEmpty() && click.isEmpty();
        }
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
        if (toggle.isPaused() || !context.settings().get(sender.getUniqueId()).translateOutgoing()
                || SkipRules.from(settings).skips(text, TranslationContext.CHAT)) {
            return Optional.empty();
        }
        LanguageCode assumed = context.resolver().resolveWriting(sender);
        if (context.settings().get(sender.getUniqueId()).writeLanguage() == null) {
            learner.observe(sender, text, assumed, context.resolver().languages());
        }
        Optional<LanguageCode> guessed = LocalLanguageGuess.guess(text, context.resolver().languages());
        LanguageCode written = guessed.orElse(assumed);
        Audiences audiences = classify(sender, written, guessed.isEmpty(), viewers);
        if (audiences.isEmpty()) {
            return Optional.empty();
        }
        Map<UUID, LanguageCode> viewerLanguages = audiences.auto();
        RecentMessage message = context.recent().register(sender.getUniqueId(), sender.getName(), text, written);
        CompletableFuture<LanguageCode> source = sourceOf(text, written, guessed.isPresent())
                .thenApply(language -> {
                    message.setSource(language);
                    return language;
                });
        TranslateOptions options = new TranslateOptions(providerAllowed(sender.getUniqueId(), settings),
                onlineNames());
        Map<LanguageCode, CompletableFuture<TranslationResult>> results = new HashMap<>();
        for (LanguageCode target : new LinkedHashSet<>(viewerLanguages.values())) {
            results.put(target, translate(text, target, source, options, message));
        }
        boolean inline = mayWaitInline && settings.mode() == ChatMode.INLINE;
        ChatSession session = new ChatSession(message, results, viewerLanguages, audiences.click(), inline,
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
        learner.forget(player);
    }

    private @NotNull CompletableFuture<LanguageCode> sourceOf(@NotNull String text, @NotNull LanguageCode written,
                                                              boolean guessedLocally) {
        if (guessedLocally) {
            return CompletableFuture.completedFuture(written);
        }
        return context.detector().detect(text, written, context.resolver().languages());
    }

    private @NotNull Audiences classify(@NotNull Player sender, @NotNull LanguageCode senderLanguage,
                                        boolean uncertain, @NotNull Collection<? extends Audience> viewers) {
        boolean detection = uncertain && context.detector().isEnabled();
        Map<UUID, LanguageCode> auto = new HashMap<>();
        Set<UUID> click = new HashSet<>();
        for (Audience audience : viewers) {
            if (audience instanceof Player viewer && !viewer.equals(sender)) {
                IncomingMode mode = context.settings().get(viewer.getUniqueId()).incoming();
                LanguageCode language = context.resolver().resolve(viewer);
                boolean foreign = !language.equals(senderLanguage);
                if (mode == IncomingMode.AUTO && (detection || foreign)) {
                    auto.put(viewer.getUniqueId(), language);
                } else if (mode == IncomingMode.CLICK && foreign) {
                    click.add(viewer.getUniqueId());
                }
            }
        }
        return new Audiences(auto, click);
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
        if (session.results().isEmpty()) {
            return;
        }
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
