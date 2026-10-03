package de.jexcellence.lingo.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.provider.DetectedLanguage;
import de.jexcellence.lingo.config.BreakerSettings;
import de.jexcellence.lingo.config.ProviderSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LibreTranslateProviderTest {

    private static final LanguageCode DE = LanguageCode.of("de");
    private static final LanguageCode EN = LanguageCode.of("en");

    private HttpServer server;
    private LibreTranslateProvider provider;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private final AtomicReference<String> answer = new AtomicReference<>("{\"translatedText\":\"Hallo\"}");
    private final AtomicReference<Long> delayMillis = new AtomicReference<>(0L);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/translate", this::respond);
        server.createContext("/detect", this::respond);
        server.createContext("/languages", this::respond);
        server.start();
        provider = new LibreTranslateProvider(settings("secret"));
    }

    @AfterEach
    void stop() {
        provider.close();
        server.stop(0);
    }

    @Test
    void sendsTextModeAndApiKeyAndReadsTheTranslation() {
        String result = provider.translate("Hello", EN, DE).join();

        assertEquals("Hallo", result);
        JsonObject body = JsonParser.parseString(lastBody.get()).getAsJsonObject();
        assertEquals("Hello", body.get("q").getAsString());
        assertEquals("en", body.get("source").getAsString());
        assertEquals("de", body.get("target").getAsString());
        assertEquals("text", body.get("format").getAsString());
        assertEquals("secret", body.get("api_key").getAsString());
    }

    @Test
    void leavesTheApiKeyOutWhenNoneIsSet() {
        provider.close();
        provider = new LibreTranslateProvider(settings(null));

        provider.translate("Hello", EN, DE).join();

        assertFalse(JsonParser.parseString(lastBody.get()).getAsJsonObject().has("api_key"));
    }

    @Test
    void errorStatusFailsWithTheBackendMessage() {
        status.set(403);
        answer.set("{\"error\":\"Invalid API key\"}");

        CompletionException error = assertThrows(CompletionException.class,
                () -> provider.translate("Hello", EN, DE).join());

        ProviderException cause = assertInstanceOf(ProviderException.class, error.getCause());
        assertEquals(403, cause.status());
        assertEquals("LibreTranslate answered 403: Invalid API key", cause.getMessage());
    }

    @Test
    void slowAnswersTimeOut() {
        delayMillis.set(1500L);

        assertThrows(CompletionException.class, () -> provider.translate("Hello", EN, DE).join());
    }

    @Test
    void readsTheLanguageList() {
        answer.set("[{\"code\":\"en\",\"name\":\"English\"},{\"code\":\"de\",\"name\":\"German\"},{\"x\":1}]");

        assertEquals(Set.of(EN, DE), provider.languages().join());
    }

    @Test
    void picksTheMostConfidentDetection() {
        answer.set("[{\"confidence\":40.0,\"language\":\"en\"},{\"confidence\":92.5,\"language\":\"de\"}]");

        Optional<DetectedLanguage> detected = provider.detect("Wie geht es dir").join();

        assertEquals(Optional.of(new DetectedLanguage(DE, 92.5)), detected);
    }

    @Test
    void unreadableAnswerFails() {
        answer.set("<html>proxy error</html>");

        assertThrows(CompletionException.class, () -> provider.translate("Hello", EN, DE).join());
    }

    private void respond(HttpExchange exchange) throws IOException {
        lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        sleep(delayMillis.get());
        byte[] bytes = answer.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status.get(), bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private ProviderSettings settings(String apiKey) {
        URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        return new ProviderSettings("libretranslate", url, apiKey, Duration.ofSeconds(1), Duration.ofMillis(500), 4,
                new BreakerSettings(3, Duration.ofSeconds(5), Duration.ofSeconds(60)));
    }
}
