package de.jexcellence.lingo.database.entity;

import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.learning.PinnedPhrase;
import de.jexcellence.lingo.pipeline.LanguagePair;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;

/**
 * A frequent short phrase whose translation is kept across restarts. Holds no reference to any player.
 *
 * @author JExcellence
 * @since 0.1.0
 */
@Entity
@Table(name = "jexlingo_phrase", indexes = {
        @Index(name = "idx_lingo_phrase_lookup", columnList = "source_language,target_language,source_key")
})
public class PinnedPhraseEntity extends LongIdEntity {

    @Column(name = "source_language", nullable = false, length = 8)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false, length = 8)
    private String targetLanguage;

    @Column(name = "source_key", nullable = false, length = 256)
    private String sourceKey;

    @Column(name = "target_text", nullable = false, length = 512)
    private String targetText;

    protected PinnedPhraseEntity() {
    }

    /**
     * Creates a row.
     *
     * @param phrase the phrase (its id is ignored)
     */
    public PinnedPhraseEntity(@NotNull PinnedPhrase phrase) {
        this.sourceLanguage = phrase.pair().source().code();
        this.targetLanguage = phrase.pair().target().code();
        this.sourceKey = phrase.sourceKey();
        this.targetText = phrase.targetText();
    }

    /**
     * Returns the row as a domain phrase.
     *
     * @return the row as a domain phrase
     */
    public @NotNull PinnedPhrase toPhrase() {
        return new PinnedPhrase(
                getId() == null ? 0L : getId(),
                new LanguagePair(LanguageCode.of(sourceLanguage), LanguageCode.of(targetLanguage)),
                sourceKey,
                targetText);
    }
}
