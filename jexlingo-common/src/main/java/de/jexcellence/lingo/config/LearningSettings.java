package de.jexcellence.lingo.config;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Limits of the learning layer (Premium).
 *
 * @param suggestionsPerHour most suggestions one player may send per hour
 * @param minPlaytime        playtime a player needs before suggesting
 * @param promoteAfter       uses within {@link #promoteWindow()} that pin a phrase
 * @param promoteWindow      counting window for phrase promotion
 * @param promoteMaxLength   longest phrase that can be pinned
 * @param autoApproveVotes   distinct players suggesting the same text that approve it without staff, 0 = off
 * @author JExcellence
 * @since 0.1.0
 */
public record LearningSettings(
        int suggestionsPerHour,
        @NotNull Duration minPlaytime,
        int promoteAfter,
        @NotNull Duration promoteWindow,
        int promoteMaxLength,
        int autoApproveVotes
) {
}
