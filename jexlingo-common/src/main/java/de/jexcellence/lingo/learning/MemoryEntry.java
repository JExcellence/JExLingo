package de.jexcellence.lingo.learning;

import de.jexcellence.lingo.pipeline.LanguagePair;
import de.jexcellence.lingo.pipeline.TextNormalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A suggested or approved translation.
 *
 * @param id          the database id
 * @param pair        the language pair
 * @param sourceText  the original as written
 * @param targetText  the suggested or approved translation
 * @param status      review status
 * @param submittedBy the submitter while pending, {@code null} after review
 * @param createdAt   when the suggestion was made, may be {@code null} for rows from old versions
 * @author JExcellence
 * @since 0.1.0
 */
public record MemoryEntry(
        long id,
        @NotNull LanguagePair pair,
        @NotNull String sourceText,
        @NotNull String targetText,
        @NotNull MemoryStatus status,
        @Nullable UUID submittedBy,
        @Nullable Instant createdAt
) {

    /**
     * Returns the normalized original used for lookups.
     *
     * @return the normalized original used for lookups
     */
    public @NotNull String sourceKey() {
        return TextNormalizer.key(sourceText);
    }
}
