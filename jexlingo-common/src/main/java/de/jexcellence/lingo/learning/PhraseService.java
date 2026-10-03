package de.jexcellence.lingo.learning;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.jexcellence.lingo.config.LearningSettings;
import de.jexcellence.lingo.database.entity.PinnedPhraseEntity;
import de.jexcellence.lingo.database.repository.PinnedPhraseRepository;
import de.jexcellence.lingo.pipeline.LanguagePair;
import de.jexcellence.lingo.pipeline.ProviderResultListener;
import de.jexcellence.lingo.pipeline.TranslationLookup;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Pins frequent short phrases (Premium). Every provider or cache result of a short line is counted in memory under
 * a hash, never the text. When a phrase reaches the configured count within the window, its translation is stored
 * without any player reference and answers that phrase from then on, across restarts.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class PhraseService implements TranslationLookup, ProviderResultListener {

    private final PinnedPhraseRepository repository;
    private final LearningSettings settings;
    private final Logger logger;
    private final Cache<String, AtomicInteger> counts;
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
                .maximumSize(50_000)
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
                        .forEach(phrase -> pinned.put(phraseKey(phrase.pair(), phrase.sourceKey()), phrase)))
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
        Optional<Map.Entry<String, PinnedPhrase>> match = pinned.entrySet().stream()
                .filter(entry -> entry.getValue().id() == id)
                .findFirst();
        if (match.isEmpty() || id <= 0L) {
            return CompletableFuture.completedFuture(false);
        }
        pinned.remove(match.get().getKey());
        counts.invalidate(hash(match.get().getKey()));
        return repository.deleteAsync(id).thenApply(ignored -> true);
    }

    private void pin(@NotNull LanguagePair pair, @NotNull String key, @NotNull String translation,
                     @NotNull String phraseKey) {
        PinnedPhrase phrase = new PinnedPhrase(0L, pair, key, translation);
        if (pinned.putIfAbsent(phraseKey, phrase) != null) {
            return;
        }
        repository.createAsync(new PinnedPhraseEntity(phrase))
                .thenAccept(stored -> pinned.replace(phraseKey, phrase, stored.toPhrase()))
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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
