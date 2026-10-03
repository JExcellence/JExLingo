package de.jexcellence.lingo.chat;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationResult;
import de.jexcellence.lingo.learning.RecentMessage;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The translations of one chat message: one future per target language and which viewer reads which language.
 * The inline wait is a single deadline for the whole message, so the second viewer does not wait again.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class ChatSession {

    private final RecentMessage message;
    private final Map<LanguageCode, CompletableFuture<TranslationResult>> results;
    private final Map<UUID, LanguageCode> viewerLanguages;
    private final Set<UUID> clickViewers;
    private final boolean inline;
    private final long deadlineNanos;

    /**
     * Creates the session.
     *
     * @param message         the recent message entry
     * @param results         one future per target language
     * @param viewerLanguages viewer UUID to target language, only viewers who get a translation
     * @param clickViewers    viewers who get a translate button instead of an automatic translation
     * @param inline          whether the renderer waits for translations
     * @param inlineWaitNanos the wait budget from now
     */
    public ChatSession(@NotNull RecentMessage message,
                       @NotNull Map<LanguageCode, CompletableFuture<TranslationResult>> results,
                       @NotNull Map<UUID, LanguageCode> viewerLanguages, @NotNull Set<UUID> clickViewers,
                       boolean inline, long inlineWaitNanos) {
        this.message = message;
        this.results = Map.copyOf(results);
        this.viewerLanguages = Map.copyOf(viewerLanguages);
        this.clickViewers = Set.copyOf(clickViewers);
        this.inline = inline;
        this.deadlineNanos = System.nanoTime() + inlineWaitNanos;
    }

    /**
     * Returns the recent message entry.
     *
     * @return the recent message entry
     */
    public @NotNull RecentMessage message() {
        return message;
    }

    /**
     * Returns one future per target language.
     *
     * @return one future per target language
     */
    public @NotNull Map<LanguageCode, CompletableFuture<TranslationResult>> results() {
        return results;
    }

    /**
     * Returns viewer UUID to target language.
     *
     * @return viewer UUID to target language
     */
    public @NotNull Map<UUID, LanguageCode> viewerLanguages() {
        return viewerLanguages;
    }

    /**
     * Returns whether the renderer waits for translations.
     *
     * @return whether the renderer waits for translations
     */
    public boolean inline() {
        return inline;
    }

    /**
     * Whether a viewer gets a translate button on this line.
     *
     * @param viewer the viewer's UUID
     * @return {@code true} for viewers in click mode who read another language
     */
    public boolean offersButton(@NotNull UUID viewer) {
        return clickViewers.contains(viewer);
    }

    /**
     * The translation a viewer should see in the chat line itself, waiting at most until the deadline.
     *
     * @param viewer the viewer's UUID
     * @return the translated result, or empty for the original line
     */
    public @NotNull Optional<TranslationResult> inlineResult(@NotNull UUID viewer) {
        LanguageCode language = viewerLanguages.get(viewer);
        if (!inline || language == null) {
            return Optional.empty();
        }
        CompletableFuture<TranslationResult> future = results.get(language);
        if (future == null) {
            return Optional.empty();
        }
        return await(future).filter(TranslationResult::translated);
    }

    private @NotNull Optional<TranslationResult> await(@NotNull CompletableFuture<TranslationResult> future) {
        if (future.isDone()) {
            return Optional.ofNullable(future.getNow(null));
        }
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0L) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(future.get(remaining, TimeUnit.NANOSECONDS));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (ExecutionException | TimeoutException ex) {
            return Optional.empty();
        }
    }
}
