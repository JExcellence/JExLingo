package de.jexcellence.lingo.settings;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.event.PlayerLanguageChangeEvent;
import de.jexcellence.lingo.database.entity.LingoPlayerSettingsEntity;
import de.jexcellence.lingo.database.repository.LingoPlayerSettingsRepository;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Keeps the settings of online players in memory and writes changes to the database off the server threads.
 * Writes for one player run one after another, so two quick clicks never overwrite each other out of order.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class PlayerSettingsService {

    private final LingoPlayerSettingsRepository repository;
    private final Executor worker;
    private final Logger logger;
    private final Map<UUID, PlayerLanguageSettings> cache = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<?>> writes = new ConcurrentHashMap<>();

    /**
     * Creates the service.
     *
     * @param repository the settings repository
     * @param worker     executor for database writes, never a server thread
     * @param logger     the plugin logger
     */
    public PlayerSettingsService(@NotNull LingoPlayerSettingsRepository repository, @NotNull Executor worker,
                                 @NotNull Logger logger) {
        this.repository = repository;
        this.worker = worker;
        this.logger = logger;
    }

    /**
     * Loads a player's settings into memory.
     *
     * @param uuid the player's UUID
     * @return the settings
     */
    public @NotNull CompletableFuture<PlayerLanguageSettings> load(@NotNull UUID uuid) {
        return repository.findByUuidAsync(uuid)
                .thenApply(row -> {
                    PlayerLanguageSettings settings = row.map(LingoPlayerSettingsEntity::toSettings)
                            .orElse(PlayerLanguageSettings.DEFAULTS);
                    cache.put(uuid, settings);
                    return settings;
                })
                .exceptionally(error -> {
                    logger.log(Level.WARNING, error, () -> "Could not load translation settings of " + uuid);
                    return get(uuid);
                });
    }

    /**
     * The settings in memory.
     *
     * @param uuid the player's UUID
     * @return the settings, or the defaults when they are not loaded
     */
    public @NotNull PlayerLanguageSettings get(@NotNull UUID uuid) {
        return cache.getOrDefault(uuid, PlayerLanguageSettings.DEFAULTS);
    }

    /**
     * Changes a player's settings and stores them.
     *
     * @param uuid   the player's UUID
     * @param change the change
     * @return the new settings once stored
     */
    public @NotNull CompletableFuture<PlayerLanguageSettings> update(@NotNull UUID uuid,
                                                                     @NotNull UnaryOperator<PlayerLanguageSettings> change) {
        PlayerLanguageSettings before = get(uuid);
        PlayerLanguageSettings after = change.apply(before);
        cache.put(uuid, after);
        CompletableFuture<PlayerLanguageSettings> stored = new CompletableFuture<>();
        writes.compute(uuid, (key, previous) -> {
            CompletableFuture<?> start = previous == null ? CompletableFuture.completedFuture(null) : previous;
            CompletableFuture<?> next = start.handleAsync((ignored, error) -> persist(uuid, after), worker)
                    .whenComplete((ignored, error) -> stored.complete(after));
            next.whenComplete((ignored, error) -> writes.remove(uuid, next));
            return next;
        });
        return stored.thenApply(settings -> {
            fireLanguageChange(uuid, before.language(), settings.language());
            return settings;
        });
    }

    /**
     * Deletes a player's stored settings (right to erasure). Waits for pending writes of that player first.
     *
     * @param uuid the player's UUID
     * @return whether a row existed
     */
    public @NotNull CompletableFuture<Boolean> erase(@NotNull UUID uuid) {
        cache.remove(uuid);
        CompletableFuture<?> pending = writes.getOrDefault(uuid, CompletableFuture.completedFuture(null));
        return pending.handleAsync((ignored, error) -> repository.findByUuid(uuid)
                .map(row -> {
                    repository.deleteEntity(row);
                    return true;
                })
                .orElse(false), worker);
    }

    /**
     * Drops a player from memory, for example on quit.
     *
     * @param uuid the player's UUID
     */
    public void forget(@NotNull UUID uuid) {
        cache.remove(uuid);
    }

    private @NotNull PlayerLanguageSettings persist(@NotNull UUID uuid, @NotNull PlayerLanguageSettings settings) {
        try {
            LingoPlayerSettingsEntity row = repository.findByUuid(uuid)
                    .orElseGet(() -> new LingoPlayerSettingsEntity(uuid));
            row.apply(settings);
            repository.save(row);
        } catch (RuntimeException ex) {
            logger.log(Level.WARNING, ex, () -> "Could not store translation settings of " + uuid);
        }
        return settings;
    }

    private static void fireLanguageChange(@NotNull UUID uuid, @Nullable LanguageCode before,
                                           @Nullable LanguageCode after) {
        if (!Objects.equals(before, after)) {
            Bukkit.getPluginManager().callEvent(
                    new PlayerLanguageChangeEvent(!Bukkit.isPrimaryThread(), uuid, before, after));
        }
    }
}
