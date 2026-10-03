package de.jexcellence.lingo.pipeline;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InFlightRegistryTest {

    private final InFlightRegistry<String> registry = new InFlightRegistry<>();

    @Test
    void identicalRunningCallsShareOneFuture() {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<String> backend = new CompletableFuture<>();

        CompletableFuture<String> first = registry.join("k", () -> {
            calls.incrementAndGet();
            return backend;
        });
        CompletableFuture<String> second = registry.join("k", () -> {
            calls.incrementAndGet();
            return backend;
        });

        assertSame(first, second);
        assertEquals(1, calls.get());
        backend.complete("done");
        assertEquals("done", second.join());
        assertEquals(0, registry.size());
    }

    @Test
    void aFinishedCallIsStartedAgain() {
        AtomicInteger calls = new AtomicInteger();
        registry.join("k", () -> CompletableFuture.completedFuture("a" + calls.incrementAndGet())).join();
        String second = registry.join("k", () -> CompletableFuture.completedFuture("a" + calls.incrementAndGet()))
                .join();

        assertEquals("a2", second);
    }

    @Test
    void failuresReachEveryWaiterAndClearTheEntry() {
        CompletableFuture<String> backend = new CompletableFuture<>();
        CompletableFuture<String> joined = registry.join("k", () -> backend);

        backend.completeExceptionally(new IllegalStateException("down"));

        assertTrue(joined.isCompletedExceptionally());
        assertEquals(0, registry.size());
    }

    @Test
    void aThrowingStarterFailsTheFuture() {
        CompletableFuture<String> joined = registry.join("k", () -> {
            throw new IllegalStateException("boom");
        });

        assertTrue(joined.isCompletedExceptionally());
        assertEquals(0, registry.size());
    }
}
