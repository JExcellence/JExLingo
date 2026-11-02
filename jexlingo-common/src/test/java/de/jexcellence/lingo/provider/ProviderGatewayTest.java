package de.jexcellence.lingo.provider;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.provider.DetectedLanguage;
import de.jexcellence.lingo.api.provider.TranslationProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderGatewayTest {

    private static final LanguageCode DE = LanguageCode.of("de");
    private static final LanguageCode EN = LanguageCode.of("en");
    private static final LanguageCode EO = LanguageCode.of("eo");

    @Test
    void clientErrorsDoNotOpenTheBreaker() {
        StatusProvider provider = new StatusProvider(400);
        CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(30));
        ProviderGateway gateway = new ProviderGateway(provider, breaker, 4, Duration.ofSeconds(2));

        for (int i = 0; i < 5; i++) {
            gateway.translate("hallo " + i, DE, EN).exceptionally(error -> null).join();
        }

        assertEquals(CircuitBreaker.State.CLOSED, breaker.state());
    }

    @Test
    void serverErrorsStillOpenTheBreaker() {
        StatusProvider provider = new StatusProvider(503);
        CircuitBreaker breaker = new CircuitBreaker(3, Duration.ofSeconds(30));
        ProviderGateway gateway = new ProviderGateway(provider, breaker, 4, Duration.ofSeconds(2));

        for (int i = 0; i < 3; i++) {
            gateway.translate("hallo " + i, DE, EN).exceptionally(error -> null).join();
        }

        assertEquals(CircuitBreaker.State.OPEN, breaker.state());
    }

    @Test
    void aRejectedPairIsSkippedWithoutHittingTheProvider() {
        StatusProvider provider = new StatusProvider(400);
        ProviderGateway gateway = new ProviderGateway(provider, new CircuitBreaker(3, Duration.ofSeconds(30)), 4,
                Duration.ofSeconds(2));

        gateway.translate("saluton", EO, DE).exceptionally(error -> null).join();
        gateway.translate("saluton denove", EO, DE).exceptionally(error -> null).join();
        provider.status = 0;
        String other = gateway.translate("hallo", DE, EN).join();

        assertEquals(2, provider.calls.get());
        assertEquals("ok", other);
    }

    @Test
    void outagesAreTimeoutsConnectionProblemsServerErrorsAndRateLimits() {
        assertTrue(ProviderGateway.isOutage(new TimeoutException()));
        assertTrue(ProviderGateway.isOutage(new CompletionException(new IOException("refused"))));
        assertTrue(ProviderGateway.isOutage(new CompletionException(new ProviderException(500, "boom"))));
        assertTrue(ProviderGateway.isOutage(new ProviderException(429, "slow down")));
        assertFalse(ProviderGateway.isOutage(new CompletionException(new ProviderException(400, "bad pair"))));
        assertFalse(ProviderGateway.isOutage(new ProviderException(403, "bad key")));
        assertFalse(ProviderGateway.isOutage(new ProviderException(ProviderException.NOT_SENT, "no text")));
    }

    private static final class StatusProvider implements TranslationProvider {

        private final AtomicInteger calls = new AtomicInteger();
        private volatile int status;

        private StatusProvider(int status) {
            this.status = status;
        }

        @Override
        public @NotNull String id() {
            return "status";
        }

        @Override
        public @NotNull CompletableFuture<String> translate(@NotNull String text, @NotNull LanguageCode source,
                                                            @NotNull LanguageCode target) {
            calls.incrementAndGet();
            if (status != 0) {
                return CompletableFuture.failedFuture(new ProviderException(status, "status " + status));
            }
            return CompletableFuture.completedFuture("ok");
        }

        @Override
        public @NotNull CompletableFuture<Set<LanguageCode>> languages() {
            return CompletableFuture.completedFuture(Set.of(DE, EN));
        }

        @Override
        public @NotNull CompletableFuture<Optional<DetectedLanguage>> detect(@NotNull String text) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
}
