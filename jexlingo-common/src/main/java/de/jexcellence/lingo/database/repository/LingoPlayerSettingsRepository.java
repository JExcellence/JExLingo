package de.jexcellence.lingo.database.repository;

import de.jexcellence.jehibernate.repository.base.AbstractCrudRepository;
import de.jexcellence.lingo.database.entity.LingoPlayerSettingsEntity;
import jakarta.persistence.EntityManagerFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Reads and writes the per-player translation settings rows.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public class LingoPlayerSettingsRepository extends AbstractCrudRepository<LingoPlayerSettingsEntity, Long> {

    private static final String PLAYER_UUID = "playerUuid";

    public LingoPlayerSettingsRepository(@NotNull ExecutorService executor, @NotNull EntityManagerFactory emf,
                                         @NotNull Class<LingoPlayerSettingsEntity> entityClass) {
        super(executor, emf, entityClass);
    }

    /**
     * Finds a player's row.
     *
     * @param uuid the player's UUID
     * @return the row
     */
    public @NotNull Optional<LingoPlayerSettingsEntity> findByUuid(@NotNull UUID uuid) {
        return query().and(PLAYER_UUID, uuid).first();
    }

    /**
     * Finds a player's row off the calling thread.
     *
     * @param uuid the player's UUID
     * @return the row
     */
    public @NotNull CompletableFuture<Optional<LingoPlayerSettingsEntity>> findByUuidAsync(@NotNull UUID uuid) {
        return query().and(PLAYER_UUID, uuid).firstAsync();
    }
}
