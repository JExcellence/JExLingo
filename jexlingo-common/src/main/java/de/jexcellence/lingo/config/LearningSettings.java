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
 * @param unpinAfter         distinct players suggesting a change to a pinned phrase that unpin it, 0 = off
 * @param pinLifetime        age after which a pinned phrase is dropped and checked again, zero = never
 * @param termProposalVotes  approved corrections keeping the same word that propose it as a glossary term
 * @author JExcellence
 * @since 0.1.0
 */
public record LearningSettings(
        int suggestionsPerHour,
        @NotNull Duration minPlaytime,
        int promoteAfter,
        @NotNull Duration promoteWindow,
        int promoteMaxLength,
        int autoApproveVotes,
        int unpinAfter,
        @NotNull Duration pinLifetime,
        int termProposalVotes
) {
}
