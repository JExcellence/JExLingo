package de.jexcellence.lingo.database.repository;

import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import de.jexcellence.lingo.database.entity.PinnedPhraseEntity;
import jakarta.persistence.EntityManagerFactory;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;

/**
 * Reads and writes pinned phrases. Loaded into memory as a whole on start.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public class PinnedPhraseRepository extends AbstractCrudRepository<PinnedPhraseEntity, Long> {

    public PinnedPhraseRepository(@NotNull ExecutorService executor, @NotNull EntityManagerFactory emf,
                                  @NotNull Class<PinnedPhraseEntity> entityClass) {
        super(executor, emf, entityClass);
    }
}
