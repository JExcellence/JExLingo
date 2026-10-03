package de.jexcellence.lingo.database.entity;

import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.pipeline.LanguagePair;
import de.jexcellence.lingo.stats.StatRow;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;

/**
 * Totals of one day, language pair and result source. Holds no chat text and no player reference.
 *
 * @author JExcellence
 * @since 0.3.0
 */
@Entity
@Table(name = "jexlingo_daily_stats", indexes = {
        @Index(name = "idx_lingo_stats_key", columnList = "stat_day,source_language,target_language,origin",
                unique = true),
        @Index(name = "idx_lingo_stats_day", columnList = "stat_day")
})
public class DailyStatEntity extends LongIdEntity {

    @Column(name = "stat_day", nullable = false)
    private LocalDate day;

    @Column(name = "source_language", nullable = false, length = 8)
    private String sourceLanguage;

    @Column(name = "target_language", nullable = false, length = 8)
    private String targetLanguage;

    @Column(name = "origin", nullable = false, length = 16)
    private String origin;

    @Column(name = "translations")
    private Long translations;

    @Column(name = "characters")
    private Long characters;

    @Column(name = "latency_ms")
    private Long latencyMs;

    protected DailyStatEntity() {
    }

    /**
     * Creates an empty row for a key.
     *
     * @param day    the day
     * @param pair   the language pair
     * @param origin the result source
     */
    public DailyStatEntity(@NotNull LocalDate day, @NotNull LanguagePair pair, @NotNull TranslationOrigin origin) {
        this.day = day;
        this.sourceLanguage = pair.source().code();
        this.targetLanguage = pair.target().code();
        this.origin = origin.name();
        this.translations = 0L;
        this.characters = 0L;
        this.latencyMs = 0L;
    }

    /**
     * Adds counted results.
     *
     * @param addedTranslations results
     * @param addedCharacters   characters of the originals
     * @param addedLatencyMs    summed latency
     */
    public void add(long addedTranslations, long addedCharacters, long addedLatencyMs) {
        this.translations = value(translations) + addedTranslations;
        this.characters = value(characters) + addedCharacters;
        this.latencyMs = value(latencyMs) + addedLatencyMs;
    }

    /**
     * Returns the row as a value.
     *
     * @return the row
     */
    public @NotNull StatRow toRow() {
        return new StatRow(day,
                new LanguagePair(LanguageCode.of(sourceLanguage), LanguageCode.of(targetLanguage)),
                TranslationOrigin.valueOf(origin),
                value(translations), value(characters), value(latencyMs));
    }

    private static long value(Long stored) {
        return stored == null ? 0L : stored;
    }
}
