package de.jexcellence.lingo.chat;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Staff switch for chat translation, for example while LibreTranslate is updated: {@code /lingo pause [minutes]}
 * shows every line as written until {@code /lingo resume} or until the time runs out. Not stored; a restart
 * resumes translation.
 *
 * @author JExcellence
 * @since 0.3.0
 */
public final class TranslationSwitch {

    private static final Instant FOREVER = Instant.MAX;

    private final Clock clock;
    private final AtomicReference<Instant> pausedUntil = new AtomicReference<>();

    /**
     * Creates a running switch.
     *
     * @param clock the clock
     */
    public TranslationSwitch(@NotNull Clock clock) {
        this.clock = clock;
    }

    /**
     * Pauses chat translation.
     *
     * @param duration how long, or {@code null} until resumed
     */
    public void pause(@Nullable Duration duration) {
        pausedUntil.set(duration == null ? FOREVER : clock.instant().plus(duration));
    }

    /** Resumes chat translation. */
    public void resume() {
        pausedUntil.set(null);
    }

    /**
     * Returns whether chat translation is paused right now.
     *
     * @return {@code true} while paused
     */
    public boolean isPaused() {
        Instant until = pausedUntil.get();
        if (until == null) {
            return false;
        }
        if (clock.instant().isBefore(until)) {
            return true;
        }
        pausedUntil.compareAndSet(until, null);
        return false;
    }

    /**
     * Returns the time left while paused.
     *
     * @return the remaining time, empty when running or paused until resumed
     */
    public @NotNull Optional<Duration> remaining() {
        Instant until = pausedUntil.get();
        if (until == null || until.equals(FOREVER) || !isPaused()) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(clock.instant(), until));
    }
}
