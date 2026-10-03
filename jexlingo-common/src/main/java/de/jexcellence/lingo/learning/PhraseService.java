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
import java.util.HexFormat;
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
    private final Map<String, String> pinned = new ConcurrentHashMap<>();

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
                        .forEach(phrase -> pinned.put(phraseKey(phrase.pair(), phrase.sourceKey()),
                                phrase.targetText())))
                .exceptionally(error -> {
                    logger.log(Level.WARNING, error, () -> "Could not load pinned phrases");
                    return null;
                });
    }

    @Override
    public @NotNull Optional<String> find(@NotNull LanguagePair pair, @NotNull String key) {
        return Optional.ofNullable(pinned.get(phraseKey(pair, key)));
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

    private void pin(@NotNull LanguagePair pair, @NotNull String key, @NotNull String translation,
                     @NotNull String phraseKey) {
        if (pinned.putIfAbsent(phraseKey, translation) != null) {
            return;
        }
        repository.createAsync(new PinnedPhraseEntity(new PinnedPhrase(0L, pair, key, translation)))
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
