package de.jexcellence.lingo;

import de.jexcellence.jehibernate.core.JEHibernate;
import de.jexcellence.lingo.config.LearningSettings;
import de.jexcellence.lingo.database.repository.PinnedPhraseRepository;
import de.jexcellence.lingo.database.repository.TranslationMemoryRepository;
import de.jexcellence.lingo.glossary.GlossaryService;
import de.jexcellence.lingo.learning.PhraseService;
import de.jexcellence.lingo.learning.TranslationMemoryService;
import de.jexcellence.lingo.pipeline.ChatTextPreparer;
import de.jexcellence.lingo.pipeline.PipelineLayers;
import de.jexcellence.lingo.pipeline.ProviderResultListener;
import de.jexcellence.lingo.pipeline.TranslationLookup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * The learning layer of the Premium edition: approved corrections and pinned phrases. Both are {@code null} in
 * the free edition, where the pipeline only uses the glossary.
 *
 * @param memory  approved corrections, or {@code null}
 * @param phrases pinned phrases, or {@code null}
 * @author JExcellence
 * @since 0.3.0
 */
public record LingoLearning(@Nullable TranslationMemoryService memory, @Nullable PhraseService phrases) {

    /**
     * Builds the layer for an edition.
     *
     * @param edition   the edition
     * @param hibernate the database
     * @param settings  learning limits
     * @param worker    executor for database work
     * @param logger    the plugin logger
     * @return the layer, empty in the free edition
     */
    public static @NotNull LingoLearning create(@NotNull LingoEdition edition, @NotNull JEHibernate hibernate,
                                                @NotNull LearningSettings settings, @NotNull Executor worker,
                                                @NotNull Logger logger) {
        if (!edition.learningEnabled()) {
            return new LingoLearning(null, null);
        }
        var repositories = hibernate.repositories();
        return new LingoLearning(
                new TranslationMemoryService(repositories.get(TranslationMemoryRepository.class), worker, logger),
                new PhraseService(repositories.get(PinnedPhraseRepository.class), settings, logger));
    }

    /**
     * The pipeline layers with this learning layer in front of the provider.
     *
     * @param glossary the glossary
     * @param preparer the text preparer
     * @return the layers
     */
    public @NotNull PipelineLayers layers(@NotNull GlossaryService glossary, @NotNull ChatTextPreparer preparer) {
        if (memory == null || phrases == null) {
            return new PipelineLayers(TranslationLookup.NONE, TranslationLookup.NONE, glossary::rulesFor,
                    ProviderResultListener.NONE, preparer);
        }
        return new PipelineLayers(memory, phrases, glossary::rulesFor, phrases, preparer);
    }

    /** Loads approved corrections and pinned phrases into memory. */
    public void load() {
        if (memory != null) {
            memory.load();
        }
        if (phrases != null) {
            phrases.load();
        }
    }
}
