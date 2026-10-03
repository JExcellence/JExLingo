package de.jexcellence.lingo.database.repository;

import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import de.jexcellence.lingo.database.entity.GlossaryTermEntity;
import jakarta.persistence.EntityManagerFactory;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;

/**
 * Reads and writes glossary terms. The glossary is small and loaded into memory as a whole.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public class GlossaryTermRepository extends AbstractCrudRepository<GlossaryTermEntity, Long> {

    public GlossaryTermRepository(@NotNull ExecutorService executor, @NotNull EntityManagerFactory emf,
                                  @NotNull Class<GlossaryTermEntity> entityClass) {
        super(executor, emf, entityClass);
    }
}
