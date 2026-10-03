package de.jexcellence.lingo.database.entity;

import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.learning.MemoryEntry;
import de.jexcellence.lingo.learning.MemoryStatus;
import de.jexcellence.lingo.pipeline.LanguagePair;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A suggested or approved translation. The submitter is only kept while the entry waits for review; on approval or
 * rejection it is removed.
 *
 * @author JExcellence
 * @since 0.1.0
 */
@Entity
@Table(name = "jexlingo_memory", indexes = {
        @Index(name = "idx_lingo_memory_lookup", columnList = "source_language,target_language,source_key"),
        @Index(name = "idx_lingo_memory_status", columnList = "status")
})
public class TranslationMemoryEntity extends LongIdEntity {

    @Column(name = "source_language", nullable = false, length = 8)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false, length = 8)
    private String targetLanguage;

    @Column(name = "source_key", nullable = false, length = 512)
    private String sourceKey;

    @Column(name = "source_text", nullable = false, length = 1024)
    private String sourceText;

    @Column(name = "target_text", nullable = false, length = 1024)
    private String targetText;

    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "submitted_by", length = 36)
    private UUID submittedBy;

    @Column(name = "hits")
    private Long hits;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected TranslationMemoryEntity() {
    }

    /**
     * Creates a pending suggestion.
     *
     * @param pair        the language pair
     * @param sourceKey   the normalized original
     * @param sourceText  the original as written
     * @param targetText  the suggested translation
     * @param submittedBy the player who suggested it
     */
    public TranslationMemoryEntity(@NotNull LanguagePair pair, @NotNull String sourceKey, @NotNull String sourceText,
                                   @NotNull String targetText, @Nullable UUID submittedBy) {
        this.sourceLanguage = pair.source().code();
        this.targetLanguage = pair.target().code();
        this.sourceKey = sourceKey;
        this.sourceText = sourceText;
        this.targetText = targetText;
        this.submittedBy = submittedBy;
        this.status = MemoryStatus.PENDING.name();
        this.hits = 0L;
    }

    /**
     * Returns the row as a domain entry.
     *
     * @return the row as a domain entry
     */
    public @NotNull MemoryEntry toEntry() {
        return new MemoryEntry(
                getId() == null ? 0L : getId(),
                new LanguagePair(LanguageCode.of(sourceLanguage), LanguageCode.of(targetLanguage)),
                sourceText,
                targetText,
                MemoryStatus.parse(status),
                submittedBy,
                getCreatedAt());
    }

    /**
     * Marks the entry as reviewed and drops the submitter.
     *
     * @param decision   approved or rejected
     * @param finalText  the translation to keep, possibly edited by staff
     */
    public void review(@NotNull MemoryStatus decision, @NotNull String finalText) {
        this.status = decision.name();
        this.targetText = finalText;
        this.submittedBy = null;
        this.reviewedAt = Instant.now();
    }

    /**
     * Adds lookups to the hit counter.
     *
     * @param delta lookups since the last flush
     */
    public void addHits(long delta) {
        this.hits = (hits == null ? 0L : hits) + delta;
    }

    /**
     * Returns when the entry was reviewed, or {@code null} while pending.
     *
     * @return when the entry was reviewed, or {@code null} while pending
     */
    public @Nullable Instant getReviewedAt() {
        return reviewedAt;
    }
}
