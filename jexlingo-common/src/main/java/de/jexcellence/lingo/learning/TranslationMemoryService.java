package de.jexcellence.lingo.learning;

import de.jexcellence.lingo.database.entity.TranslationMemoryEntity;
import de.jexcellence.lingo.database.repository.TranslationMemoryRepository;
import de.jexcellence.lingo.pipeline.LanguagePair;
import de.jexcellence.lingo.pipeline.TextNormalizer;
import de.jexcellence.lingo.pipeline.TranslationLookup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Approved corrections, used as exact-match overrides before the provider (Premium). Approved entries are held in
 * memory; suggestions wait in the database for staff review. Lookup hits are counted in memory and written in
 * batches by {@link #flushHits()}.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class TranslationMemoryService implements TranslationLookup {

    private static final Duration REJECTED_RETENTION = Duration.ofDays(7);

    private final TranslationMemoryRepository repository;
    private final Executor worker;
    private final Logger logger;
    private final Map<String, Approved> approved = new ConcurrentHashMap<>();
    private final Map<Long, LongAdder> pendingHits = new ConcurrentHashMap<>();

    private record Approved(long id, @NotNull String targetText) {
    }

    /**
     * Creates the service.
     *
     * @param repository the memory repository
     * @param worker     executor for database work
     * @param logger     the plugin logger
     */
    public TranslationMemoryService(@NotNull TranslationMemoryRepository repository, @NotNull Executor worker,
                                    @NotNull Logger logger) {
        this.repository = repository;
        this.worker = worker;
        this.logger = logger;
    }

    /**
     * Returns completes when every approved entry is in memory.
     *
     * @return completes when every approved entry is in memory
     */
    public @NotNull CompletableFuture<Void> load() {
        return repository.findByStatusAsync(MemoryStatus.APPROVED)
                .thenAccept(rows -> rows.stream().map(TranslationMemoryEntity::toEntry).forEach(this::remember))
                .exceptionally(error -> {
                    logger.log(Level.WARNING, error, () -> "Could not load the translation memory");
                    return null;
                });
    }

    @Override
    public @NotNull Optional<String> find(@NotNull LanguagePair pair, @NotNull String key) {
        Approved entry = approved.get(memoryKey(pair, key));
        if (entry == null) {
            return Optional.empty();
        }
        pendingHits.computeIfAbsent(entry.id(), id -> new LongAdder()).increment();
        return Optional.of(entry.targetText());
    }

    /**
     * Approves a suggestion without staff when enough different players suggested the same text for the same
     * original. The other copies of the winning text are rejected as merged.
     *
     * @param entry  the suggestion just stored
     * @param needed distinct players needed, 0 = crowd approval off
     * @return whether a suggestion was approved
     */
    public @NotNull CompletableFuture<Boolean> approveByVotes(@NotNull MemoryEntry entry, int needed) {
        if (needed <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> repository.findPending(entry.pair(), entry.sourceKey()).stream()
                .map(TranslationMemoryEntity::toEntry).toList(), worker)
                .thenCompose(pending -> CrowdVotes.count(pending, needed)
                        .map(outcome -> approve(outcome.winner().id(), null).thenCompose(approved -> {
                            outcome.duplicates().forEach(duplicate -> reject(duplicate.id()));
                            return CompletableFuture.completedFuture(approved.isPresent());
                        }))
                        .orElseGet(() -> CompletableFuture.completedFuture(false)));
    }

    /**
     * Deletes the pending suggestions of a player (right to erasure).
     *
     * @param submitter the player's UUID
     * @return the number of deleted suggestions
     */
    public @NotNull CompletableFuture<Integer> erasePending(@NotNull UUID submitter) {
        return CompletableFuture.supplyAsync(() -> {
            List<TranslationMemoryEntity> rows = repository.findPendingBySubmitter(submitter);
            rows.forEach(repository::deleteEntity);
            return rows.size();
        }, worker);
    }

    /**
     * Stores a suggestion for review.
     *
     * @param pair       the language pair
     * @param sourceText the original
     * @param suggestion the suggested translation
     * @param submitter  the player who suggested it
     * @return the stored entry
     */
    public @NotNull CompletableFuture<MemoryEntry> submit(@NotNull LanguagePair pair, @NotNull String sourceText,
                                                          @NotNull String suggestion, @NotNull UUID submitter) {
        String clean = TextNormalizer.clean(sourceText);
        return repository.createAsync(new TranslationMemoryEntity(pair, TextNormalizer.key(clean), clean,
                        TextNormalizer.clean(suggestion), submitter))
                .thenApply(TranslationMemoryEntity::toEntry);
    }

    /**
     * Returns suggestions waiting for review, oldest first.
     *
     * @return suggestions waiting for review, oldest first
     */
    public @NotNull CompletableFuture<List<MemoryEntry>> pending() {
        return repository.findByStatusAsync(MemoryStatus.PENDING)
                .thenApply(rows -> rows.stream().map(TranslationMemoryEntity::toEntry).toList());
    }

    /**
     * Returns every approved entry, for the training export.
     *
     * @return every approved entry, for the training export
     */
    public @NotNull CompletableFuture<List<MemoryEntry>> approvedEntries() {
        return repository.findByStatusAsync(MemoryStatus.APPROVED)
                .thenApply(rows -> rows.stream().map(TranslationMemoryEntity::toEntry).toList());
    }

    /**
     * Approves a suggestion; from now on it answers every identical line.
     *
     * @param id         the entry id
     * @param editedText a staff correction of the suggestion, or {@code null} to keep it
     * @return the approved entry, or empty when no pending entry has this id
     */
    public @NotNull CompletableFuture<Optional<MemoryEntry>> approve(long id, @Nullable String editedText) {
        return review(id, MemoryStatus.APPROVED, editedText).thenApply(entry -> {
            entry.ifPresent(this::remember);
            return entry;
        });
    }

    /**
     * Rejects a suggestion.
     *
     * @param id the entry id
     * @return whether a pending entry was rejected
     */
    public @NotNull CompletableFuture<Boolean> reject(long id) {
        return review(id, MemoryStatus.REJECTED, null).thenApply(Optional::isPresent);
    }

    /** Writes the counted lookups to the database. Runs on the worker. */
    public void flushHits() {
        if (pendingHits.isEmpty()) {
            return;
        }
        CompletableFuture.runAsync(() -> pendingHits.forEach((id, adder) -> {
            long delta = adder.sumThenReset();
            if (delta > 0L) {
                repository.findById(id).ifPresent(row -> {
                    row.addHits(delta);
                    repository.update(row);
                });
            }
        }), worker).exceptionally(error -> {
            logger.log(Level.WARNING, error, () -> "Could not store translation memory hits");
            return null;
        });
    }

    /** Deletes rejected suggestions older than seven days. Runs on the worker. */
    public void purgeRejected() {
        CompletableFuture.runAsync(() -> {
            List<TranslationMemoryEntity> old = repository.findRejectedBefore(Instant.now().minus(REJECTED_RETENTION));
            old.forEach(repository::deleteEntity);
        }, worker).exceptionally(error -> {
            logger.log(Level.WARNING, error, () -> "Could not purge rejected suggestions");
            return null;
        });
    }

    /**
     * Returns approved entries in memory.
     *
     * @return approved entries in memory
     */
    public int approvedCount() {
        return approved.size();
    }

    private @NotNull CompletableFuture<Optional<MemoryEntry>> review(long id, @NotNull MemoryStatus decision,
                                                                     @Nullable String editedText) {
        return CompletableFuture.supplyAsync(() -> repository.findById(id)
                .filter(row -> row.toEntry().status() == MemoryStatus.PENDING)
                .map(row -> {
                    String text = editedText == null || editedText.isBlank()
                            ? row.toEntry().targetText()
                            : TextNormalizer.clean(editedText);
                    row.review(decision, text);
                    return repository.update(row).toEntry();
                }), worker);
    }

    private void remember(@NotNull MemoryEntry entry) {
        approved.put(memoryKey(entry.pair(), entry.sourceKey()), new Approved(entry.id(), entry.targetText()));
    }

    private static @NotNull String memoryKey(@NotNull LanguagePair pair, @NotNull String key) {
        return pair.key() + '\u0000' + key;
    }
}
