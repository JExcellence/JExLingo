package de.jexcellence.lingo.language;

import de.jexcellence.lingo.api.LanguageCode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Detected languages of one player's recent messages. Keeps at most {@code capacity} votes; a language wins once
 * it has {@code threshold} of them.
 *
 * @author JExcellence
 * @since 0.2.0
 */
public final class LanguageVotes {

    private final int capacity;
    private final List<LanguageCode> votes = new ArrayList<>();
    private int observed;

    /**
     * Creates an empty tally.
     *
     * @param capacity how many messages are sampled at most
     */
    public LanguageVotes(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /**
     * Whether another message may be sampled. Counts the sample.
     *
     * @return {@code false} once {@code capacity} messages were sampled
     */
    public synchronized boolean trySample() {
        if (observed >= capacity) {
            return false;
        }
        observed++;
        return true;
    }

    /**
     * Adds one detected language.
     *
     * @param language the detected language
     */
    public synchronized void add(@NotNull LanguageCode language) {
        if (votes.size() < capacity) {
            votes.add(language);
        }
    }

    /**
     * The language with at least {@code threshold} votes.
     *
     * @param threshold votes needed
     * @return the winner, or empty while no language has enough votes
     */
    public synchronized @NotNull Optional<LanguageCode> winner(int threshold) {
        Map<LanguageCode, Integer> counts = new HashMap<>();
        for (LanguageCode vote : votes) {
            int count = counts.merge(vote, 1, Integer::sum);
            if (count >= threshold) {
                return Optional.of(vote);
            }
        }
        return Optional.empty();
    }
}
