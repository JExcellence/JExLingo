package de.jexcellence.lingo.pipeline;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Lets identical requests that run at the same time share one provider call. When ten players spam the same line,
 * the provider sees it once.
 *
 * @param <T> the result type
 * @author JExcellence
 * @since 0.1.0
 */
public final class InFlightRegistry<T> {

    private final Map<String, CompletableFuture<T>> running = new ConcurrentHashMap<>();

    /**
     * Joins the running call for {@code key} or starts a new one.
     *
     * @param key  identifies equal requests
     * @param call starts the call; invoked at most once per key while it runs
     * @return the shared future
     */
    public @NotNull CompletableFuture<T> join(@NotNull String key, @NotNull Supplier<CompletableFuture<T>> call) {
        CompletableFuture<T> created = new CompletableFuture<>();
        CompletableFuture<T> existing = running.putIfAbsent(key, created);
        if (existing != null) {
            return existing;
        }
        CompletableFuture<T> started;
        try {
            started = call.get();
        } catch (RuntimeException ex) {
            started = CompletableFuture.failedFuture(ex);
        }
        started.whenComplete((result, error) -> {
            running.remove(key, created);
            if (error != null) {
                created.completeExceptionally(error);
            } else {
                created.complete(result);
            }
        });
        return created;
    }

    /**
     * Returns calls currently running.
     *
     * @return calls currently running
     */
    public int size() {
        return running.size();
    }
}
