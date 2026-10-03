package de.jexcellence.lingo.learning;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.event.TranslationSuggestedEvent;
import de.jexcellence.lingo.config.LearningSettings;
import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.pipeline.LanguagePair;
import de.jexcellence.lingo.pipeline.TextNormalizer;
import de.jexcellence.lingo.settings.PlayerSettingsService;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Takes a player's better translation for a recent chat line and stores it for staff review (Premium). Guards
 * against abuse: blocked players, too little playtime and too many suggestions per hour are refused.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class SuggestionService {

    /** Longest accepted suggestion. */
    public static final int MAX_LENGTH = 256;

    private static final Duration WINDOW = Duration.ofHours(1);
    private static final long TICKS_PER_MINUTE = 20L * 60L;

    /** Outcome of a suggestion. */
    public enum Result {
        /** Stored for review. */
        SAVED,
        /** The message id is unknown or older than five minutes. */
        UNKNOWN_MESSAGE,
        /** The message was not translated into the player's language. */
        NOT_TRANSLATED,
        /** The suggestion equals the shown translation. */
        UNCHANGED,
        /** The suggestion is empty or too long. */
        INVALID,
        /** The player reached the hourly limit. */
        LIMIT,
        /** The player has not played long enough. */
        PLAYTIME,
        /** Staff blocked the player from suggesting. */
        BLOCKED
    }

    private final RecentMessageBuffer recent;
    private final TranslationMemoryService memory;
    private final PlayerSettingsService settings;
    private final LanguageResolver resolver;
    private final AtomicReference<LearningSettings> limits;
    private final Cache<UUID, List<Long>> history = Caffeine.newBuilder().expireAfterWrite(WINDOW).build();

    /**
     * Creates the service.
     *
     * @param recent   recent chat messages
     * @param memory   the translation memory
     * @param settings player settings
     * @param resolver language resolver
     * @param limits   suggestion limits
     */
    public SuggestionService(@NotNull RecentMessageBuffer recent, @NotNull TranslationMemoryService memory,
                             @NotNull PlayerSettingsService settings, @NotNull LanguageResolver resolver,
                             @NotNull LearningSettings limits) {
        this.recent = recent;
        this.memory = memory;
        this.settings = settings;
        this.resolver = resolver;
        this.limits = new AtomicReference<>(limits);
    }

    /**
     * Submits a suggestion. Call on the player's thread; the statistic read needs it.
     *
     * @param player     the player
     * @param messageId  the recent message id
     * @param suggestion the suggested translation
     * @return the outcome
     */
    public @NotNull CompletableFuture<Result> suggest(@NotNull Player player, @NotNull String messageId,
                                                      @NotNull String suggestion) {
        Result blocked = precheck(player);
        if (blocked != null) {
            return CompletableFuture.completedFuture(blocked);
        }
        RecentMessage message = recent.find(messageId).orElse(null);
        if (message == null) {
            return CompletableFuture.completedFuture(Result.UNKNOWN_MESSAGE);
        }
        LanguageCode target = resolver.resolve(player);
        String shown = message.translation(target).orElse(null);
        if (shown == null || message.source().equals(target)) {
            return CompletableFuture.completedFuture(Result.NOT_TRANSLATED);
        }
        String clean = TextNormalizer.clean(suggestion);
        if (clean.isEmpty() || clean.length() > MAX_LENGTH) {
            return CompletableFuture.completedFuture(Result.INVALID);
        }
        if (clean.equalsIgnoreCase(TextNormalizer.clean(shown))) {
            return CompletableFuture.completedFuture(Result.UNCHANGED);
        }
        record(player.getUniqueId());
        LanguagePair pair = new LanguagePair(message.source(), target);
        UUID submitter = player.getUniqueId();
        return memory.submit(pair, message.original(), clean, submitter).thenApply(entry -> {
            Bukkit.getPluginManager().callEvent(new TranslationSuggestedEvent(!Bukkit.isPrimaryThread(), submitter,
                    entry.sourceText(), entry.targetText(), pair.source(), pair.target()));
            return Result.SAVED;
        });
    }

    /**
     * Replaces the limits after a reload.
     *
     * @param value the new limits
     */
    public void setLimits(@NotNull LearningSettings value) {
        limits.set(value);
    }

    private @Nullable Result precheck(@NotNull Player player) {
        if (settings.get(player.getUniqueId()).suggestionsBlocked()) {
            return Result.BLOCKED;
        }
        LearningSettings current = limits.get();
        long playedMinutes = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / TICKS_PER_MINUTE;
        if (playedMinutes < current.minPlaytime().toMinutes()) {
            return Result.PLAYTIME;
        }
        if (recentCount(player.getUniqueId()) >= current.suggestionsPerHour()) {
            return Result.LIMIT;
        }
        return null;
    }

    private int recentCount(@NotNull UUID player) {
        long cutoff = System.currentTimeMillis() - WINDOW.toMillis();
        List<Long> times = history.getIfPresent(player);
        return times == null ? 0 : (int) times.stream().filter(time -> time > cutoff).count();
    }

    private void record(@NotNull UUID player) {
        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW.toMillis();
        history.asMap().compute(player, (key, previous) -> {
            List<Long> next = new ArrayList<>();
            if (previous != null) {
                previous.stream().filter(time -> time > cutoff).forEach(next::add);
            }
            next.add(now);
            return List.copyOf(next);
        });
    }
}
