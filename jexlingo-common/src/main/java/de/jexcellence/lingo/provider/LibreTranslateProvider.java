package de.jexcellence.lingo.provider;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.provider.DetectedLanguage;
import de.jexcellence.lingo.api.provider.TranslationProvider;
import de.jexcellence.lingo.config.ProviderSettings;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * {@link TranslationProvider} for a LibreTranslate instance, usually self-hosted next to the server. Uses the JDK
 * {@link HttpClient} with its own small thread pool; every call is asynchronous and bounded by the configured
 * request timeout.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LibreTranslateProvider implements TranslationProvider {

    /** Provider id for {@code provider.type}. */
    public static final String ID = "libretranslate";

    private static final int HTTP_OK = 200;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String JSON = "application/json";
    private static final int CLIENT_THREADS = 4;

    private final URI baseUrl;
    private final String apiKey;
    private final Duration requestTimeout;
    private final ExecutorService executor;
    private final HttpClient client;

    /**
     * Creates the provider.
     *
     * @param settings the provider settings
     */
    public LibreTranslateProvider(@NotNull ProviderSettings settings) {
        this.baseUrl = withTrailingSlash(settings.url());
        this.apiKey = settings.apiKey();
        this.requestTimeout = settings.requestTimeout();
        this.executor = Executors.newFixedThreadPool(CLIENT_THREADS, Thread.ofPlatform()
                .name("JExLingo-http-", 0).daemon(true).factory());
        this.client = HttpClient.newBuilder()
                .connectTimeout(settings.connectTimeout())
                .executor(executor)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public @NotNull String id() {
        return ID;
    }

    @Override
    public @NotNull CompletableFuture<String> translate(@NotNull String text, @NotNull LanguageCode source,
                                                        @NotNull LanguageCode target) {
        return post("translate", LibreTranslateJson.translateRequest(text, source, target, apiKey),
                LibreTranslateJson::parseTranslation);
    }

    @Override
    public @NotNull CompletableFuture<Set<LanguageCode>> languages() {
        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("languages"))
                .timeout(requestTimeout)
                .GET()
                .build();
        return send(request, LibreTranslateJson::parseLanguages);
    }

    @Override
    public @NotNull CompletableFuture<Optional<DetectedLanguage>> detect(@NotNull String text) {
        return post("detect", LibreTranslateJson.detectRequest(text, apiKey), LibreTranslateJson::parseDetection);
    }

    @Override
    public void close() {
        client.shutdownNow();
        executor.shutdownNow();
    }

    private <T> @NotNull CompletableFuture<T> post(@NotNull String path, @NotNull String body,
                                                   @NotNull Function<String, T> parser) {
        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve(path))
                .timeout(requestTimeout)
                .header(CONTENT_TYPE, JSON)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return send(request, parser);
    }

    private <T> @NotNull CompletableFuture<T> send(@NotNull HttpRequest request,
                                                   @NotNull Function<String, T> parser) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() != HTTP_OK) {
                        throw new ProviderException(response.statusCode(),
                                "LibreTranslate answered " + response.statusCode() + ": "
                                        + LibreTranslateJson.parseError(response.body()));
                    }
                    return parser.apply(response.body());
                });
    }

    private static @NotNull URI withTrailingSlash(@NotNull URI url) {
        String raw = url.toString();
        return raw.endsWith("/") ? url : URI.create(raw + "/");
    }
}
