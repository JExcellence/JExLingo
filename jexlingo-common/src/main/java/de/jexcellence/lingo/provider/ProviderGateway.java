package de.jexcellence.lingo.provider;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.provider.DetectedLanguage;
import de.jexcellence.lingo.api.provider.TranslationProvider;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * The only way JExLingo talks to a provider. Adds what every backend needs: a cap on requests in flight, the
 * {@link CircuitBreaker}, a hard timeout and latency statistics. A refused call fails at once instead of queueing,
 * so chat never waits behind a slow backend.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class ProviderGateway {

    private final AtomicReference<TranslationProvider> provider;
    private final CircuitBreaker breaker;
    private final Semaphore permits;
    private final long timeoutMillis;
    private final LatencyStats latency = new LatencyStats();
    private final LongAdder requests = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder refused = new LongAdder();

    /**
     * Creates the gateway.
     *
     * @param provider       the active provider
     * @param breaker        the circuit breaker
     * @param maxConcurrent  requests in flight at the same time
     * @param requestTimeout hard timeout of one call
     */
    public ProviderGateway(@NotNull TranslationProvider provider, @NotNull CircuitBreaker breaker, int maxConcurrent,
                           @NotNull Duration requestTimeout) {
        this.provider = new AtomicReference<>(provider);
        this.breaker = breaker;
        this.permits = new Semaphore(Math.max(1, maxConcurrent));
        this.timeoutMillis = requestTimeout.toMillis();
    }

    /**
     * Translates through the active provider.
     *
     * @param text   the masked text
     * @param source the source language
     * @param target the target language
     * @return the translation; fails with {@link ProviderException} when refused, failed or timed out
     */
    public @NotNull CompletableFuture<String> translate(@NotNull String text, @NotNull LanguageCode source,
                                                        @NotNull LanguageCode target) {
        if (!permits.tryAcquire()) {
            refused.increment();
            return CompletableFuture.failedFuture(new ProviderException(ProviderException.NOT_SENT,
                    "Too many translations in flight"));
        }
        if (!breaker.tryAcquire()) {
            permits.release();
            refused.increment();
            return CompletableFuture.failedFuture(new ProviderException(ProviderException.NOT_SENT,
                    "Provider is paused after repeated failures"));
        }
        requests.increment();
        long started = System.nanoTime();
        CompletableFuture<String> call;
        try {
            call = provider.get().translate(text, source, target);
        } catch (RuntimeException ex) {
            call = CompletableFuture.failedFuture(ex);
        }
        return call.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .whenComplete((result, error) -> complete(started, error == null));
    }

    /**
     * Detects a language through the active provider. Not counted against the breaker; detection is optional.
     *
     * @param text the text
     * @return the best guess, or empty on any failure
     */
    public @NotNull CompletableFuture<Optional<DetectedLanguage>> detect(@NotNull String text) {
        if (breaker.state() != CircuitBreaker.State.CLOSED) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return provider.get().detect(text)
                .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .exceptionally(error -> Optional.empty());
    }

    /**
     * Replaces the active provider and closes the previous one.
     *
     * @param next the new provider
     */
    public void switchTo(@NotNull TranslationProvider next) {
        TranslationProvider previous = provider.getAndSet(next);
        if (previous != next) {
            previous.close();
        }
        breaker.recordSuccess();
    }

    /** Closes the active provider. */
    public void close() {
        provider.get().close();
    }

    /**
     * Returns the active provider.
     *
     * @return the active provider
     */
    public @NotNull TranslationProvider provider() {
        return provider.get();
    }

    /**
     * Returns the circuit breaker.
     *
     * @return the circuit breaker
     */
    public @NotNull CircuitBreaker breaker() {
        return breaker;
    }

    /**
     * Returns latency of the last provider calls.
     *
     * @return latency of the last provider calls
     */
    public @NotNull LatencyStats latency() {
        return latency;
    }

    /**
     * Returns calls sent to the provider.
     *
     * @return calls sent to the provider
     */
    public long requestCount() {
        return requests.sum();
    }

    /**
     * Returns calls that failed or timed out.
     *
     * @return calls that failed or timed out
     */
    public long failureCount() {
        return failures.sum();
    }

    /**
     * Returns calls refused by the breaker or the in-flight cap.
     *
     * @return calls refused by the breaker or the in-flight cap
     */
    public long refusedCount() {
        return refused.sum();
    }

    private void complete(long startedNanos, boolean success) {
        permits.release();
        if (success) {
            breaker.recordSuccess();
            latency.record(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
        } else {
            failures.increment();
            breaker.recordFailure();
        }
    }
}
