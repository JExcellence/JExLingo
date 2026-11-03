package de.jexcellence.lingo.learning;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.jexcellence.lingo.config.LearningSettings;
import de.jexcellence.lingo.database.entity.PinnedPhraseEntity;
import de.jexcellence.lingo.database.repository.PinnedPhraseRepository;
import de.jexcellence.lingo.pipeline.LanguagePair;
import de.jexcellence.lingo.pipeline.ProviderResultListener;
import de.jexcellence.lingo.pipeline.TextNormalizer;
import de.jexcellence.lingo.pipeline.TranslationLookup;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pins frequent short phrases (Premium). Every provider or cache result of a short line in normal case is counted in
 * memory under a SHA-256 hash, never the text (see {@code docs/PRIVACY.md}). When a phrase reaches the configured
 * count within the window, its translation is stored without any player reference and answers that phrase from then
 * on, across restarts.
 *
 * <p>A pin is not forever: it is dropped when the glossary changes, when enough players suggest a change to it,
 * when a correction for it is approved, and when it is older than {@code learning.pin-lifetime-days}. The phrase then
 * goes back to the provider and can be pinned again.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class PhraseService implements TranslationLookup, ProviderResultListener {

    private static final int MAX_COUNTED = 50_000;
    private static final Duration FLAG_WINDOW = Duration.ofDays(7);
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(PhraseService::newDigest);

    private final PinnedPhraseRepository repository;
    private final LearningSettings settings;
    private final Logger logger;
    private final Cache<String, AtomicInteger> counts;
    private final Cache<String, Set<UUID>> flags = Caffeine.newBuilder()
            .expireAfterWrite(FLAG_WINDOW)
            .maximumSize(MAX_COUNTED)
            .build();
    private final Map<String, PinnedPhrase> pinned = new ConcurrentHashMap<>();

    /**
     * Creates the service.
     *
     * @param repository the phrase repository
     * @param settings   promotion thresholds; a reload needs a restart for the window to change
     * @param logger     the plugin logger
     */
    public PhraseService(@NotNull PinnedPhraseRepository repository, @NotNull LearningSettings settings,
                         @NotNull Logger logger) {
        this.repository = repository;
        this.settings = settings;
        this.logger = logger;
        this.counts = Caffeine.newBuilder()
                .expireAfterWrite(settings.promoteWindow())
                .maximumSize(MAX_COUNTED)
                .build();
    }

    /**
     * Returns completes when every pinned phrase is in memory.
     *
     * @return completes when every pinned phrase is in memory
     */
    public @NotNull CompletableFuture<Void> load() {
        return repository.findAllAsync()
                .thenAccept(rows -> rows.stream().map(PinnedPhraseEntity::toPhrase)
                        .forEach(phrase -> pinned.put(
                                phraseKey(phrase.pair(), TextNormalizer.key(phrase.sourceKey())), phrase)))
                .thenRun(this::purgeExpired)
                .exceptionally(error -> {
                    logger.log(Level.WARNING, error, () -> "Could not load pinned phrases");
                    return null;
                });
    }

    @Override
    public @NotNull Optional<String> find(@NotNull LanguagePair pair, @NotNull String key) {
        return Optional.ofNullable(pinned.get(phraseKey(pair, key))).map(PinnedPhrase::targetText);
    }

    @Override
    public void onResult(@NotNull LanguagePair pair, @NotNull String key, @NotNull String translation) {
        if (key.length() > settings.promoteMaxLength()) {
            return;
        }
        String phraseKey = phraseKey(pair, key);
        if (pinned.containsKey(phraseKey)) {
            return;
        }
        int uses = counts.get(hash(phraseKey), ignored -> new AtomicInteger()).incrementAndGet();
        if (uses == settings.promoteAfter()) {
            pin(pair, key, translation, phraseKey);
        }
    }

    /**
     * Records that a player suggested a different translation for a line. When the line is pinned and enough
     * different players did so ({@code learning.unpin-after-suggestions}), the pin is dropped.
     *
     * @param pair   the language pair
     * @param key    the normalized line
     * @param player the player who suggested
     * @return whether the phrase was unpinned
     */
    public @NotNull CompletableFuture<Boolean> flag(@NotNull LanguagePair pair, @NotNull String key,
                                                    @NotNull UUID player) {
        String phraseKey = phraseKey(pair, key);
        PinnedPhrase phrase = pinned.get(phraseKey);
        int needed = settings.unpinAfter();
        if (phrase == null || needed <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        Set<UUID> voters = flags.asMap().compute(hash(phraseKey), (ignored, previous) -> {
            Set<UUID> next = previous == null ? new HashSet<>() : new HashSet<>(previous);
            next.add(player);
            return Set.copyOf(next);
        });
        if (voters == null || voters.size() < needed) {
            return CompletableFuture.completedFuture(false);
        }
        return unpin(phraseKey);
    }

    /**
     * Drops the pin of a line, for example because a correction for it was approved.
     *
     * @param pair the language pair
     * @param key  the normalized line
     * @return whether a pin was dropped
     */
    public @NotNull CompletableFuture<Boolean> forget(@NotNull LanguagePair pair, @NotNull String key) {
        return unpin(phraseKey(pair, key));
    }

    /**
     * Drops every pin. Pins hold the finished translation, so after a glossary change they would keep the old
     * wording; frequent phrases are pinned again with the new glossary.
     *
     * @return the number of dropped pins
     */
    public @NotNull CompletableFuture<Integer> clear() {
        List<String> keys = List.copyOf(pinned.keySet());
        List<CompletableFuture<Boolean>> drops = keys.stream().map(this::unpin).toList();
        return CompletableFuture.allOf(drops.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> (int) drops.stream().filter(drop -> Boolean.TRUE.equals(drop.join())).count());
    }

    /** Drops pins older than {@code learning.pin-lifetime-days}, so they are checked against the provider again. */
    public void purgeExpired() {
        Duration lifetime = settings.pinLifetime();
        if (lifetime.isZero() || lifetime.isNegative()) {
            return;
        }
        Instant cutoff = Instant.now().minus(lifetime);
        pinned.forEach((phraseKey, phrase) -> {
            if (phrase.pinnedAt() != null && phrase.pinnedAt().isBefore(cutoff)) {
                unpin(phraseKey);
            }
        });
    }

    /**
     * Returns pinned phrases in memory.
     *
     * @return pinned phrases in memory
     */
    public int pinnedCount() {
        return pinned.size();
    }

    /**
     * Returns every pinned phrase, newest first.
     *
     * @return the phrases
     */
    public @NotNull List<PinnedPhrase> list() {
        return pinned.values().stream().sorted(Comparator.comparingLong(PinnedPhrase::id).reversed()).toList();
    }

    /**
     * Unpins a phrase, for example when its translation is wrong. The phrase goes back to the provider and can be
     * pinned again later.
     *
     * @param id the phrase id
     * @return whether a phrase was removed
     */
    public @NotNull CompletableFuture<Boolean> remove(long id) {
        Optional<String> match = pinned.entrySet().stream()
                .filter(entry -> entry.getValue().id() == id)
                .map(Map.Entry::getKey)
                .findFirst();
        if (match.isEmpty() || id <= 0L) {
            return CompletableFuture.completedFuture(false);
        }
        return unpin(match.get());
    }

    private @NotNull CompletableFuture<Boolean> unpin(@NotNull String phraseKey) {
        PinnedPhrase removed = pinned.remove(phraseKey);
        if (removed == null) {
            return CompletableFuture.completedFuture(false);
        }
        String hashed = hash(phraseKey);
        counts.invalidate(hashed);
        flags.invalidate(hashed);
        if (removed.id() <= 0L) {
            return CompletableFuture.completedFuture(true);
        }
        return repository.deleteAsync(removed.id())
                .thenApply(ignored -> true)
                .exceptionally(error -> {
                    logger.log(Level.WARNING, error, () -> "Could not unpin phrase " + removed.id());
                    return true;
                });
    }

    private void pin(@NotNull LanguagePair pair, @NotNull String key, @NotNull String translation,
                     @NotNull String phraseKey) {
        PinnedPhrase phrase = new PinnedPhrase(0L, pair, key, translation, Instant.now());
        if (pinned.putIfAbsent(phraseKey, phrase) != null) {
            return;
        }
        repository.createAsync(new PinnedPhraseEntity(phrase))
                .thenAccept(stored -> {
                    PinnedPhrase saved = stored.toPhrase();
                    if (!pinned.replace(phraseKey, phrase, saved)) {
                        // Unpinned while the row was being written: remove the row as well.
                        repository.deleteAsync(saved.id());
                    }
                })
                .exceptionally(error -> {
                    pinned.remove(phraseKey);
                    logger.log(Level.WARNING, error, () -> "Could not pin a phrase");
                    return null;
                });
    }

    private static @NotNull String phraseKey(@NotNull LanguagePair pair, @NotNull String key) {
        return pair.key() + '\u0000' + key;
    }

    private static @NotNull String hash(@NotNull String value) {
        MessageDigest digest = SHA_256.get();
        digest.reset();
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static @NotNull MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
