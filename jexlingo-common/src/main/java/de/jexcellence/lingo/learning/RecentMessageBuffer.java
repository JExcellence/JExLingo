package de.jexcellence.lingo.learning;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.jexcellence.lingo.api.LanguageCode;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The chat messages of the last five minutes and which translated messages each viewer saw, in memory only. Backs
 * {@code /lingo suggest}: Java players click a translated line, Bedrock players pick from their last translated
 * lines.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class RecentMessageBuffer {

    private static final Duration LIFETIME = Duration.ofMinutes(5);
    private static final int MAX_MESSAGES = 2000;
    private static final int PER_VIEWER = 5;

    private final AtomicLong sequence = new AtomicLong(System.currentTimeMillis() % 1_000_000L);
    private final Cache<String, RecentMessage> messages = Caffeine.newBuilder()
            .expireAfterWrite(LIFETIME)
            .maximumSize(MAX_MESSAGES)
            .build();
    private final Cache<UUID, List<String>> seenByViewer = Caffeine.newBuilder()
            .expireAfterWrite(LIFETIME)
            .maximumSize(MAX_MESSAGES)
            .build();

    /**
     * Registers a new message.
     *
     * @param sender     the sender's UUID
     * @param senderName the sender's name
     * @param original   the message
     * @param source     the assumed source language
     * @return the entry with its new id
     */
    public @NotNull RecentMessage register(@NotNull UUID sender, @NotNull String senderName,
                                           @NotNull String original, @NotNull LanguageCode source) {
        String id = Long.toString(sequence.incrementAndGet(), Character.MAX_RADIX).toLowerCase(Locale.ROOT);
        RecentMessage message = new RecentMessage(id, sender, senderName, original, source);
        messages.put(id, message);
        return message;
    }

    /**
     * Finds a message.
     *
     * @param id the id
     * @return the message while it is recent
     */
    public @NotNull Optional<RecentMessage> find(@NotNull String id) {
        return Optional.ofNullable(messages.getIfPresent(id.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * Notes that a viewer saw a translated message.
     *
     * @param viewer    the viewer's UUID
     * @param messageId the message id
     */
    public void markSeen(@NotNull UUID viewer, @NotNull String messageId) {
        seenByViewer.asMap().compute(viewer, (key, previous) -> {
            List<String> next = new ArrayList<>(PER_VIEWER + 1);
            next.add(messageId);
            if (previous != null) {
                previous.stream().filter(id -> !id.equals(messageId)).limit(PER_VIEWER - 1L).forEach(next::add);
            }
            return List.copyOf(next);
        });
    }

    /**
     * The translated messages a viewer saw last, newest first.
     *
     * @param viewer the viewer's UUID
     * @return the messages still in the buffer
     */
    public @NotNull List<RecentMessage> seenBy(@NotNull UUID viewer) {
        List<String> seen = seenByViewer.getIfPresent(viewer);
        if (seen == null) {
            return List.of();
        }
        return seen.stream().map(this::find).flatMap(Optional::stream).toList();
    }
}
