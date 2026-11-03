package de.jexcellence.lingo.chat;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Spam protection for provider calls: a small token bucket per sender. A player can send {@code burst} lines in a row
 * that all reach the provider; after that one more line per {@code refill} interval. Lines answered from memory,
 * pinned phrases or the cache never use a token, because the pipeline only asks the gate once it really needs the
 * provider, and a message translated into several languages uses one token in total.
 *
 * @author JExcellence
 * @since 0.5.0
 */
public final class ProviderBudget {

    private static final int UNDECIDED = 0;
    private static final int ALLOWED = 1;
    private static final int REFUSED = 2;

    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    private static final class Bucket {
        private double tokens;
        private long updatedAt;

        private Bucket(double tokens, long updatedAt) {
            this.tokens = tokens;
            this.updatedAt = updatedAt;
        }
    }

    /** Creates a budget on the system clock. */
    public ProviderBudget() {
        this(System::nanoTime);
    }

    /**
     * Creates a budget on a given clock (for tests).
     *
     * @param clock nanosecond clock
     */
    public ProviderBudget(@NotNull LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Returns a gate for one message. It takes a token the first time it is asked and answers the same for every
     * further target language of that message.
     *
     * @param sender the sender
     * @param burst  lines in a row that reach the provider
     * @param refill time until one more line is allowed; zero turns the limit off
     * @return the gate
     */
    public @NotNull BooleanSupplier gateFor(@NotNull UUID sender, int burst, @NotNull Duration refill) {
        AtomicInteger decision = new AtomicInteger(UNDECIDED);
        return () -> {
            int current = decision.get();
            if (current == UNDECIDED) {
                int decided = tryTake(sender, burst, refill) ? ALLOWED : REFUSED;
                current = decision.compareAndExchange(UNDECIDED, decided);
                if (current == UNDECIDED) {
                    current = decided;
                }
            }
            return current == ALLOWED;
        };
    }

    /**
     * Takes one token from the sender's bucket.
     *
     * @param sender the sender
     * @param burst  bucket size
     * @param refill time per token; zero turns the limit off
     * @return whether a token was available
     */
    public boolean tryTake(@NotNull UUID sender, int burst, @NotNull Duration refill) {
        long refillNanos = refill.toNanos();
        if (refillNanos <= 0L) {
            return true;
        }
        int capacity = Math.max(1, burst);
        long now = clock.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(sender, ignored -> new Bucket(capacity, now));
        synchronized (bucket) {
            double refilled = (now - bucket.updatedAt) / (double) refillNanos;
            bucket.tokens = Math.min(capacity, bucket.tokens + refilled);
            bucket.updatedAt = now;
            if (bucket.tokens < 1.0) {
                return false;
            }
            bucket.tokens -= 1.0;
            return true;
        }
    }

    /**
     * Drops the sender's bucket when they leave.
     *
     * @param sender the sender
     */
    public void forget(@NotNull UUID sender) {
        buckets.remove(sender);
    }
}
