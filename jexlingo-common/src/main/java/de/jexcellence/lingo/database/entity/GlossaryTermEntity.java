package de.jexcellence.lingo.database.entity;

import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.glossary.GlossaryMode;
import de.jexcellence.lingo.glossary.GlossaryTerm;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One glossary term. A {@code null} language means the term applies to every source or target language.
 *
 * @author JExcellence
 * @since 0.1.0
 */
@Entity
@Table(name = "jexlingo_glossary")
public class GlossaryTermEntity extends LongIdEntity {

    @Column(name = "term", nullable = false, length = 128)
    private String term;

    @Column(name = "replacement", length = 256)
    private String replacement;

    @Column(name = "mode", length = 8)
    private String mode;

    @Column(name = "source_language", length = 8)
    private String sourceLanguage;

    @Column(name = "target_language", length = 8)
    private String targetLanguage;

    protected GlossaryTermEntity() {
    }

    /**
     * Creates a row from a term.
     *
     * @param term the term (its id is ignored)
     */
    public GlossaryTermEntity(@NotNull GlossaryTerm term) {
        this.term = term.term();
        this.replacement = term.replacement();
        this.mode = term.mode().name();
        this.sourceLanguage = code(term.source());
        this.targetLanguage = code(term.target());
    }

    /**
     * Returns the row as a domain term.
     *
     * @return the row as a domain term
     */
    public @NotNull GlossaryTerm toTerm() {
        return new GlossaryTerm(
                getId() == null ? 0L : getId(),
                term,
                replacement,
                GlossaryMode.parse(mode),
                LanguageCode.parse(sourceLanguage).orElse(null),
                LanguageCode.parse(targetLanguage).orElse(null));
    }

    private static @Nullable String code(@Nullable LanguageCode language) {
        return language == null ? null : language.code();
    }
}
