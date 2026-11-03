package de.jexcellence.lingo.stats;

import de.jexcellence.lingo.api.event.TranslationSuggestedEvent;
import de.jexcellence.lingo.pipeline.LanguagePair;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Counts submitted corrections per day and language pair for the correction rate in the statistics (Premium). Only
 * the pair is counted; the texts and the player stay out of the statistics.
 *
 * @author JExcellence
 * @since 0.5.0
 */
public final class CorrectionStatsListener implements Listener {

    private final StatsRecorder recorder;

    /**
     * Creates the listener.
     *
     * @param recorder the daily statistics
     */
    public CorrectionStatsListener(@NotNull StatsRecorder recorder) {
        this.recorder = recorder;
    }

    /**
     * Counts one correction.
     *
     * @param event the submitted suggestion
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onSuggested(@NotNull TranslationSuggestedEvent event) {
        recorder.correction(new LanguagePair(event.getSource(), event.getTarget()));
    }
}
