package de.jexcellence.lingo.provider;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.provider.DetectedLanguage;
import de.jexcellence.lingo.api.provider.TranslationProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderHealthMonitorTest {

    private static final LanguageCode DE = LanguageCode.of("de");
    private static final LanguageCode EN = LanguageCode.of("en");
    private static final LanguageCode FR = LanguageCode.of("fr");

    private final List<String> lines = new CopyOnWriteArrayList<>();

    @Test
    void anUnreachableProviderIsReportedWithItsAddress() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.languages = CompletableFuture.failedFuture(new ClosedChannelException());

        monitor(provider, List.of(DE, EN)).check();

        assertEquals(1, lines.size());
        assertTrue(lines.getFirst().contains("http://127.0.0.1:5000"));
        assertTrue(lines.getFirst().contains("connection refused"));
    }

    @Test
    void missingModelsNameTheExactLoadOnlyValue() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.languages = CompletableFuture.completedFuture(Set.of(DE, EN));

        monitor(provider, List.of(EN, DE, FR)).check();

        assertTrue(lines.stream().anyMatch(line -> line.contains("no model for fr")
                && line.contains("--load-only de,en,fr")), () -> String.join("\n", lines));
    }

    @Test
    void aRejectedKeyIsReportedOnce() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.languages = CompletableFuture.completedFuture(Set.of(DE, EN));
        provider.translation = CompletableFuture.failedFuture(new ProviderException(403, "Invalid API key"));

        monitor(provider, List.of(DE, EN)).check();

        assertEquals(1, lines.stream().filter(line -> line.contains("rejected the API key")).count());
        assertTrue(lines.stream().anyMatch(line -> line.contains("JEXLINGO_API_KEY")));
    }

    @Test
    void reasonsAreWrittenForPeople() {
        assertTrue(ProviderHealthMonitor.reason(new ConnectException()).contains("connection refused"));
        assertEquals("boom", ProviderHealthMonitor.reason(new IllegalStateException("boom")));
    }

    private ProviderHealthMonitor monitor(TranslationProvider provider, List<LanguageCode> enabled) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                lines.add(record.getMessage() != null ? record.getMessage() : "");
            }

            @Override
            public void flush() {
                // Nothing buffered.
            }

            @Override
            public void close() {
                // Nothing to release.
            }
        });
        ProviderGateway gateway = new ProviderGateway(provider, new CircuitBreaker(3, Duration.ofSeconds(30)), 4,
                Duration.ofSeconds(2));
        ProviderHealthMonitor monitor = new ProviderHealthMonitor(gateway, enabled, logger);
        monitor.setLocation("http://127.0.0.1:5000");
        return monitor;
    }

    private static final class ScriptedProvider implements TranslationProvider {

        private CompletableFuture<Set<LanguageCode>> languages = CompletableFuture.completedFuture(Set.of());
        private CompletableFuture<String> translation = CompletableFuture.completedFuture("Hallo");

        @Override
        public @NotNull String id() {
            return "scripted";
        }

        @Override
        public @NotNull CompletableFuture<String> translate(@NotNull String text, @NotNull LanguageCode source,
                                                            @NotNull LanguageCode target) {
            return translation;
        }

        @Override
        public @NotNull CompletableFuture<Set<LanguageCode>> languages() {
            return languages;
        }

        @Override
        public @NotNull CompletableFuture<Optional<DetectedLanguage>> detect(@NotNull String text) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
}
