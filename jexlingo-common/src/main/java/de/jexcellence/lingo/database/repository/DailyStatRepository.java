package de.jexcellence.lingo.database.repository;

import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.database.entity.DailyStatEntity;
import de.jexcellence.lingo.pipeline.LanguagePair;
import jakarta.persistence.EntityManagerFactory;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * Reads and writes the daily statistics.
 *
 * @author JExcellence
 * @since 0.3.0
 */
public class DailyStatRepository extends AbstractCrudRepository<DailyStatEntity, Long> {

    private static final String DAY = "day";

    public DailyStatRepository(@NotNull ExecutorService executor, @NotNull EntityManagerFactory emf,
                               @NotNull Class<DailyStatEntity> entityClass) {
        super(executor, emf, entityClass);
    }

    /**
     * The row of one key.
     *
     * @param day    the day
     * @param pair   the language pair
     * @param origin the result source
     * @return the row
     */
    public @NotNull Optional<DailyStatEntity> find(@NotNull LocalDate day, @NotNull LanguagePair pair,
                                                   @NotNull TranslationOrigin origin) {
        return query().and(DAY, day)
                .and("sourceLanguage", pair.source().code())
                .and("targetLanguage", pair.target().code())
                .and("origin", origin.name())
                .first();
    }

    /**
     * Every row from a day on.
     *
     * @param from the first day
     * @return the rows
     */
    public @NotNull List<DailyStatEntity> findSince(@NotNull LocalDate from) {
        return query().greaterThanOrEqual(DAY, from).list();
    }

    /**
     * Every row before a day, for cleanup.
     *
     * @param before the first day to keep
     * @return the rows
     */
    public @NotNull List<DailyStatEntity> findBefore(@NotNull LocalDate before) {
        return query().lessThan(DAY, before).list();
    }
}
