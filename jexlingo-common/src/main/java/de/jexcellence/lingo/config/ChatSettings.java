package de.jexcellence.lingo.config;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Chat behaviour and limits.
 *
 * @param mode           inline or follow-up translation
 * @param inlineWait     how long the inline mode waits for a translation
 * @param minLength      shortest message that is translated
 * @param maxLength      longest message that is translated
 * @param skipPrefix     messages starting with this text are never translated; empty disables it
 * @param playerCooldown minimum time between two provider calls for one sender
 * @param onboarding     whether new players get the one-time hint
 * @author JExcellence
 * @since 0.1.0
 */
public record ChatSettings(
        @NotNull ChatMode mode,
        @NotNull Duration inlineWait,
        int minLength,
        int maxLength,
        @NotNull String skipPrefix,
        @NotNull Duration playerCooldown,
        boolean onboarding
) {
}
