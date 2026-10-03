package de.jexcellence.lingo.provider;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Pauses the provider after repeated failures. {@link State#CLOSED}: calls pass. After {@code failureThreshold}
 * failures in a row it opens: calls are refused for {@code openDuration}. Then it lets exactly one probe call
 * through ({@link State#HALF_OPEN}); a success closes it, a failure opens it again.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class CircuitBreaker {

    /** The breaker state. */
    public enum State {
        /** Calls pass. */
        CLOSED,
        /** Calls are refused until the pause ends. */
        OPEN,
        /** One probe call is in flight. */
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long openNanos;
    private final LongSupplier clock;

    private State state = State.CLOSED;
    private int failures;
    private long openedAt;

    /**
     * Creates a breaker on the system clock.
     *
     * @param failureThreshold failures in a row that open the breaker
     * @param openDuration     pause before the probe call
     */
    public CircuitBreaker(int failureThreshold, @NotNull Duration openDuration) {
        this(failureThreshold, openDuration, System::nanoTime);
    }

    /**
     * Creates a breaker on a given clock (for tests).
     *
     * @param failureThreshold failures in a row that open the breaker
     * @param openDuration     pause before the probe call
     * @param clock            nanosecond clock
     */
    public CircuitBreaker(int failureThreshold, @NotNull Duration openDuration, @NotNull LongSupplier clock) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openNanos = openDuration.toNanos();
        this.clock = clock;
    }

    /**
     * Asks for permission to call the provider. In {@link State#OPEN} after the pause this switches to
     * {@link State#HALF_OPEN} and permits exactly this one call.
     *
     * @return whether the call may go out
     */
    public synchronized boolean tryAcquire() {
        return switch (state) {
            case CLOSED -> true;
            case HALF_OPEN -> false;
            case OPEN -> {
                if (clock.getAsLong() - openedAt >= openNanos) {
                    state = State.HALF_OPEN;
                    yield true;
                }
                yield false;
            }
            default -> throw new IllegalStateException("Unexpected breaker state: " + state);
        };
    }

    /** Records a successful call and closes the breaker. */
    public synchronized void recordSuccess() {
        failures = 0;
        state = State.CLOSED;
    }

    /** Records a failed call; opens the breaker at the threshold or when the probe failed. */
    public synchronized void recordFailure() {
        failures++;
        if (state == State.HALF_OPEN || failures >= failureThreshold) {
            state = State.OPEN;
            openedAt = clock.getAsLong();
        }
    }

    /**
     * Returns the current state.
     *
     * @return the current state
     */
    public synchronized @NotNull State state() {
        return state;
    }

    /**
     * Returns failures in a row since the last success.
     *
     * @return failures in a row since the last success
     */
    public synchronized int consecutiveFailures() {
        return failures;
    }
}
