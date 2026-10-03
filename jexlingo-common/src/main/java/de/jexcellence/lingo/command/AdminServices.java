package de.jexcellence.lingo.command;

import de.jexcellence.lingo.glossary.GlossaryService;
import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.learning.TrainingExportService;
import de.jexcellence.lingo.learning.TranslationMemoryService;
import de.jexcellence.lingo.pipeline.TranslationPipeline;
import de.jexcellence.lingo.settings.PlayerSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The services behind the staff part of {@code /lingo}.
 *
 * @param pipeline the translation pipeline, for {@code /lingo test}
 * @param glossary the glossary
 * @param settings player settings, for blocking suggesters
 * @param resolver language resolver
 * @param memory   translation memory, or {@code null} in the free edition
 * @param export   training export, or {@code null} in the free edition
 * @param reload   reloads config, translations and glossary
 * @author JExcellence
 * @since 0.1.0
 */
public record AdminServices(
        @NotNull TranslationPipeline pipeline,
        @NotNull GlossaryService glossary,
        @NotNull PlayerSettingsService settings,
        @NotNull LanguageResolver resolver,
        @Nullable TranslationMemoryService memory,
        @Nullable TrainingExportService export,
        @NotNull Runnable reload
) {
}
