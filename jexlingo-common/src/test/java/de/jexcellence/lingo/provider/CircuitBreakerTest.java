package de.jexcellence.lingo.provider;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerTest {

    private final AtomicLong now = new AtomicLong();
    private final CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(10), now::get);

    @Test
    void opensAfterTheThreshold() {
        breaker.recordFailure();
        breaker.recordFailure();
        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());

        breaker.recordFailure();
        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
        assertFalse(breaker.tryAcquire());
    }

    @Test
    void letsExactlyOneProbeThroughAfterThePause() {
        openBreaker();
        now.addAndGet(Duration.ofSeconds(10).toNanos());

        assertTrue(breaker.tryAcquire());
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.state());
        assertFalse(breaker.tryAcquire());
    }

    @Test
    void closesWhenTheProbeSucceeds() {
        openBreaker();
        now.addAndGet(Duration.ofSeconds(11).toNanos());
        breaker.tryAcquire();

        breaker.recordSuccess();

        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
        assertEquals(0, breaker.consecutiveFailures());
        assertTrue(breaker.tryAcquire());
    }

    @Test
    void reopensWhenTheProbeFails() {
        openBreaker();
        now.addAndGet(Duration.ofSeconds(11).toNanos());
        breaker.tryAcquire();

        breaker.recordFailure();

        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
        assertFalse(breaker.tryAcquire());
    }

    @Test
    void successResetsTheFailureCount() {
        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordSuccess();
        breaker.recordFailure();
        breaker.recordFailure();

        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
    }

    private void openBreaker() {
        for (int i = 0; i < 3; i++) {
            breaker.recordFailure();
        }
    }
}
