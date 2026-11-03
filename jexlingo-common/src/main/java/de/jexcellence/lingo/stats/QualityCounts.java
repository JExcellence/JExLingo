package de.jexcellence.lingo.stats;

import org.jetbrains.annotations.NotNull;

/**
 * Quality signals of a day and language pair: provider answers with broken protected tokens, how many of them the
 * piece-by-piece retry saved, and how many corrections players suggested.
 *
 * @param tokenLosses provider answers with a broken token
 * @param recoveries  broken answers saved piece by piece
 * @param corrections suggestions submitted by players
 * @author JExcellence
 * @since 0.5.0
 */
public record QualityCounts(long tokenLosses, long recoveries, long corrections) {

    /** No signals. */
    public static final QualityCounts EMPTY = new QualityCounts(0L, 0L, 0L);

    /**
     * Returns both counts added up.
     *
     * @param other the other counts
     * @return the sum
     */
    public @NotNull QualityCounts plus(@NotNull QualityCounts other) {
        return new QualityCounts(tokenLosses + other.tokenLosses, recoveries + other.recoveries,
                corrections + other.corrections);
    }

    /**
     * Returns whether every count is zero.
     *
     * @return whether every count is zero
     */
    public boolean isEmpty() {
        return tokenLosses == 0L && recoveries == 0L && corrections == 0L;
    }
}
