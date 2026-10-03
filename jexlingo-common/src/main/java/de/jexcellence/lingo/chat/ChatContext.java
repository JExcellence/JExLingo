package de.jexcellence.lingo.chat;

import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.lingo.language.LanguageDetector;
import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.learning.RecentMessageBuffer;
import de.jexcellence.lingo.pipeline.TranslationPipeline;
import de.jexcellence.lingo.settings.PlayerSettingsService;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

/**
 * The services the chat translation needs, grouped so the coordinator stays small.
 *
 * @param pipeline  the translation pipeline
 * @param settings  player settings
 * @param resolver  language resolver
 * @param detector  optional language detection
 * @param recent    recent message buffer
 * @param scheduler platform scheduler for player-thread work
 * @param worker    executor for async events
 * @author JExcellence
 * @since 0.1.0
 */
public record ChatContext(
        @NotNull TranslationPipeline pipeline,
        @NotNull PlayerSettingsService settings,
        @NotNull LanguageResolver resolver,
        @NotNull LanguageDetector detector,
        @NotNull RecentMessageBuffer recent,
        @NotNull PlatformScheduler scheduler,
        @NotNull Executor worker
) {
}
