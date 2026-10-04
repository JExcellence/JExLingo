package de.jexcellence.lingo.provider;

import de.jexcellence.lingo.api.LanguageCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Checks the provider's language list on start and at a fixed interval. Reports when the backend is unreachable or
 * misses an enabled language (for example because LibreTranslate was started without that model), and sends one
 * warm-up translation after the first successful check so the first chat line does not pay the model load time.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class ProviderHealthMonitor {

    private static final String WARM_UP_TEXT = "Hello";
    private static final int WARM_UP_ROUNDS = 4;

    private final ProviderGateway gateway;
    private final Logger logger;
    private final AtomicReference<List<LanguageCode>> enabled;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.UNKNOWN);
    private final AtomicBoolean warmedUp = new AtomicBoolean();

    /**
     * The last check.
     *
     * @param reachable whether the backend answered
     * @param available languages the backend offers
     * @param missing   enabled languages the backend does not offer
     * @param checkedAt time of the check, or {@code null} before the first one
     */
    public record Snapshot(boolean reachable, @NotNull Set<LanguageCode> available, @NotNull Set<LanguageCode> missing,
                           @Nullable Instant checkedAt) {

        /** State before the first check. */
        public static final Snapshot UNKNOWN = new Snapshot(false, Set.of(), Set.of(), null);
    }

    /**
     * Creates the monitor.
     *
     * @param gateway the provider gateway
     * @param enabled the enabled languages
     * @param logger  the plugin logger
     */
    public ProviderHealthMonitor(@NotNull ProviderGateway gateway, @NotNull List<LanguageCode> enabled,
                                 @NotNull Logger logger) {
        this.gateway = gateway;
        this.enabled = new AtomicReference<>(List.copyOf(enabled));
        this.logger = logger;
    }

    /**
     * Updates the enabled languages after a reload.
     *
     * @param languages the enabled languages
     */
    public void setEnabled(@NotNull List<LanguageCode> languages) {
        enabled.set(List.copyOf(languages));
    }

    /** Runs one check; safe to call from any thread, never blocks. */
    public void check() {
        gateway.provider().languages().whenComplete((available, error) -> {
            if (error != null) {
                onUnreachable(error);
            } else {
                onAvailable(available);
            }
        });
    }

    /**
     * Returns the last check.
     *
     * @return the last check
     */
    public @NotNull Snapshot snapshot() {
        return snapshot.get();
    }

    private void onUnreachable(@NotNull Throwable error) {
        Snapshot previous = snapshot.getAndSet(new Snapshot(false, Set.of(), Set.of(), Instant.now()));
        if (previous.reachable() || previous.checkedAt() == null) {
            String reason = rootMessage(error);
            logger.log(Level.WARNING, () -> "Translation provider '" + gateway.provider().id()
                    + "' is not reachable (" + reason + "). Chat shows original messages until it is back.");
        }
    }

    private void onAvailable(@NotNull Set<LanguageCode> available) {
        Set<LanguageCode> missing = enabled.get().stream()
                .filter(language -> !available.contains(language))
                .collect(Collectors.toUnmodifiableSet());
        Snapshot previous = snapshot.getAndSet(new Snapshot(true, Set.copyOf(available), missing, Instant.now()));
        if (!previous.reachable()) {
            logger.log(Level.INFO, () -> "Translation provider '" + gateway.provider().id() + "' is reachable ("
                    + available.size() + " languages).");
        }
        if (!missing.isEmpty() && !missing.equals(previous.missing())) {
            logger.log(Level.WARNING, () -> "The translation provider has no model for " + missing
                    + ". Load it (LibreTranslate: --load-only) or remove it from languages.enabled.");
        }
        if (warmedUp.compareAndSet(false, true)) {
            warmUp(available);
        }
    }

    private void warmUp(@NotNull Set<LanguageCode> available) {
        List<LanguageCode> languages = enabled.get().stream().filter(available::contains).toList();
        for (LanguageCode source : languages) {
            for (LanguageCode target : languages) {
                warmUpPair(source, target);
            }
        }
    }

    private void warmUpPair(@NotNull LanguageCode source, @NotNull LanguageCode target) {
        if (source.equals(target)) {
            return;
        }
        for (int round = 0; round < WARM_UP_ROUNDS; round++) {
            gateway.provider().translate(WARM_UP_TEXT, source, target).exceptionally(error -> WARM_UP_TEXT);
        }
    }

    private static @NotNull String rootMessage(@NotNull Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null ? root.getClass().getSimpleName() : message;
    }
}
