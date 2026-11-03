package de.jexcellence.lingo.provider;

import de.jexcellence.lingo.api.LanguageCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionException;
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
    private static final int HTTP_BAD_REQUEST = 400;
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final String GUIDE = "https://github.com/JExcellence/JExLingo/blob/main/docs/SELF_HOSTING.md";

    private final ProviderGateway gateway;
    private final Logger logger;
    private final AtomicReference<List<LanguageCode>> enabled;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.UNKNOWN);
    private final AtomicReference<String> location = new AtomicReference<>("the configured address");
    private final AtomicBoolean warmedUp = new AtomicBoolean();
    private final AtomicBoolean keyReported = new AtomicBoolean();

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
            String reason = reason(error);
            logger.log(Level.WARNING, () -> "Translation provider '" + gateway.provider().id() + "' at "
                    + location.get() + " is not reachable (" + reason + "). Chat shows original messages until it"
                    + " is back. Check that LibreTranslate runs and provider.url points to it; setup: " + GUIDE);
        }
    }

    private void onAvailable(@NotNull Set<LanguageCode> available) {
        List<LanguageCode> wanted = enabled.get();
        Set<LanguageCode> missing = wanted.stream()
                .filter(language -> !available.contains(language))
                .collect(Collectors.toUnmodifiableSet());
        Snapshot previous = snapshot.getAndSet(new Snapshot(true, Set.copyOf(available), missing, Instant.now()));
        if (!previous.reachable()) {
            logger.log(Level.INFO, () -> "Translation provider '" + gateway.provider().id() + "' at "
                    + location.get() + " is reachable (" + available.size() + " languages).");
        }
        if (!missing.isEmpty() && !missing.equals(previous.missing())) {
            String loadOnly = wanted.stream().map(LanguageCode::code).sorted().collect(Collectors.joining(","));
            String names = missing.stream().map(LanguageCode::code).sorted().collect(Collectors.joining(", "));
            logger.log(Level.WARNING, () -> "The translation provider has no model for " + names
                    + ". Start LibreTranslate with --load-only " + loadOnly + " (LT_LANGUAGES=" + loadOnly
                    + " in deploy/libretranslate/.env), or remove " + names + " from languages.enabled.");
        }
        if (warmedUp.compareAndSet(false, true)) {
            warmUp(available);
        }
    }

    /**
     * Updates the address shown in log lines after a reload.
     *
     * @param address the provider address, for example {@code http://127.0.0.1:5000}
     */
    public void setLocation(@NotNull String address) {
        location.set(address);
    }

    /** A rejected key shows up as 401 or 403, or as 400 with a message about the key when one is required. */
    private void checkKey(@Nullable Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (!(cause instanceof ProviderException provider)) {
            return;
        }
        String message = String.valueOf(provider.getMessage()).toLowerCase(Locale.ROOT);
        boolean rejected = provider.status() == HTTP_UNAUTHORIZED || provider.status() == HTTP_FORBIDDEN
                || provider.status() == HTTP_BAD_REQUEST && message.contains("api key");
        if (rejected && keyReported.compareAndSet(false, true)) {
            logger.log(Level.WARNING, () -> "The translation provider rejected the API key (" + provider.status()
                    + "). Set provider.api-key in config.yml or the JEXLINGO_API_KEY environment variable to a key"
                    + " from 'ltmanage keys add', then /lingo reload. Until then every line stays untranslated.");
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
            gateway.provider().translate(WARM_UP_TEXT, source, target)
                    .whenComplete((result, error) -> checkKey(error))
                    .exceptionally(error -> WARM_UP_TEXT);
        }
    }

    /** The failure in words a server owner can act on; the JDK often reports a refused port as a closed channel. */
    static @NotNull String reason(@NotNull Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return switch (root) {
            case ConnectException _, ClosedChannelException _ -> "connection refused, nothing listens on that port";
            case HttpTimeoutException _ -> "no answer in time";
            case UnresolvedAddressException _, UnknownHostException _ -> "unknown host name";
            default -> root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        };
    }
}
