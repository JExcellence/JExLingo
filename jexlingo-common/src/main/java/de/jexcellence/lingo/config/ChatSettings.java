package de.jexcellence.lingo.config;

import de.jexcellence.lingo.pipeline.NameProtection;
import de.jexcellence.lingo.pipeline.SkipRules;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Chat behaviour and limits.
 *
 * @param mode           inline or follow-up translation
 * @param inlineWait     how long the inline mode waits for a translation
 * @param skip           length limits and the skip prefix
 * @param playerCooldown time until a sender may send one more line to the provider (token refill)
 * @param burst          lines in a row a sender may send to the provider before the refill applies
 * @param onboarding     whether new players get the one-time hint
 * @param protectNames   which online player names are kept out of the translator
 * @author JExcellence
 * @since 0.1.0
 */
public record ChatSettings(
        @NotNull ChatMode mode,
        @NotNull Duration inlineWait,
        @NotNull SkipRules skip,
        @NotNull Duration playerCooldown,
        int burst,
        boolean onboarding,
        @NotNull NameProtection protectNames
) {
}
