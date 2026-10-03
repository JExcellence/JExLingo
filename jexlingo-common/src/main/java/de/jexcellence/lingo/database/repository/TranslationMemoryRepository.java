package de.jexcellence.lingo.database.repository;

import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import de.jexcellence.lingo.database.entity.TranslationMemoryEntity;
import de.jexcellence.lingo.learning.MemoryStatus;
import jakarta.persistence.EntityManagerFactory;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Reads and writes the translation memory.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public class TranslationMemoryRepository extends AbstractCrudRepository<TranslationMemoryEntity, Long> {

    private static final String STATUS = "status";

    public TranslationMemoryRepository(@NotNull ExecutorService executor, @NotNull EntityManagerFactory emf,
                                       @NotNull Class<TranslationMemoryEntity> entityClass) {
        super(executor, emf, entityClass);
    }

    /**
     * Every entry with a status.
     *
     * @param status the status
     * @return the entries, oldest first
     */
    public @NotNull CompletableFuture<List<TranslationMemoryEntity>> findByStatusAsync(@NotNull MemoryStatus status) {
        return query().and(STATUS, status.name()).orderBy("id").listAsync();
    }

    /**
     * Rejected entries reviewed before a time, for cleanup.
     *
     * @param before the cut-off
     * @return the entries
     */
    public @NotNull List<TranslationMemoryEntity> findRejectedBefore(@NotNull Instant before) {
        return query().and(STATUS, MemoryStatus.REJECTED.name()).lessThan("reviewedAt", before).list();
    }
}
