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
 * @param playerCooldown minimum time between two provider calls for one sender
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
        boolean onboarding,
        @NotNull NameProtection protectNames
) {
}
