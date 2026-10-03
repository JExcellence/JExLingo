package de.jexcellence.lingo.config;

import de.jexcellence.lingo.api.LanguageCode;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads {@code config.yml} into a {@link LingoConfig}. The defaults live here, in code; a missing key falls back to
 * its default, an out-of-range number is clamped, an invalid value falls back with a warning. Environment
 * variables win over the file for deployment values: {@code JEXLINGO_PROVIDER_URL} and {@code JEXLINGO_API_KEY}.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LingoConfigLoader {

    /** Environment variable that overrides {@code provider.url}. */
    public static final String ENV_PROVIDER_URL = "JEXLINGO_PROVIDER_URL";

    /** Environment variable that overrides {@code provider.api-key}. */
    public static final String ENV_API_KEY = "JEXLINGO_API_KEY";

    private static final String DEFAULT_PROVIDER = "libretranslate";
    private static final String DEFAULT_URL = "http://127.0.0.1:5000";
    private static final LanguageCode DEFAULT_FALLBACK = LanguageCode.of("en");
    private static final List<LanguageCode> DEFAULT_LANGUAGES = List.of(LanguageCode.of("de"), DEFAULT_FALLBACK);
    private static final String PROVIDER = "provider.";
    private static final String BREAKER = PROVIDER + "circuit-breaker.";
    private static final String CHAT = "chat.";
    private static final String LEARNING = "learning.";
    private static final String DETECTION = "detection.";
    private static final String CACHE = "cache.";

    private LingoConfigLoader() {
    }

    /**
     * The result of loading: the config and every warning about values that were replaced.
     *
     * @param config   the loaded config
     * @param warnings human-readable warnings, never containing secrets
     */
    public record LoadResult(@NotNull LingoConfig config, @NotNull List<String> warnings) {
    }

    /**
     * Loads the config.
     *
     * @param root        the root section of {@code config.yml}
     * @param environment the process environment, usually {@code System.getenv()}
     * @return the config with warnings
     */
    public static @NotNull LoadResult load(@NotNull ConfigurationSection root, @NotNull Map<String, String> environment) {
        Reader reader = new Reader(root);
        LingoConfig config = new LingoConfig(
                provider(reader, environment),
                languages(reader),
                detection(reader, root),
                chat(reader, root),
                new CacheSettings(
                        reader.intIn(CACHE + "max-entries", 5000, 0, 1_000_000),
                        Duration.ofMinutes(reader.intIn(CACHE + "ttl-minutes", 60, 1, 10_080))),
                learning(reader),
                new BedrockSettings(root.getBoolean("bedrock.show-original-line", true)));
        return new LoadResult(config, List.copyOf(reader.warnings));
    }

    private static @NotNull ProviderSettings provider(@NotNull Reader reader, @NotNull Map<String, String> env) {
        ConfigurationSection root = reader.root;
        String type = root.getString(PROVIDER + "type", DEFAULT_PROVIDER).trim().toLowerCase(Locale.ROOT);
        String rawUrl = firstNonBlank(env.get(ENV_PROVIDER_URL), root.getString(PROVIDER + "url"), DEFAULT_URL);
        URI url = parseUrl(rawUrl).orElseGet(() -> {
            reader.warn("provider.url is no valid http(s) URL; using " + DEFAULT_URL);
            return URI.create(DEFAULT_URL);
        });
        String apiKey = firstNonBlank(env.get(ENV_API_KEY), root.getString(PROVIDER + "api-key"), null);
        BreakerSettings breaker = new BreakerSettings(
                reader.intIn(BREAKER + "failure-threshold", 5, 1, 100),
                Duration.ofSeconds(reader.intIn(BREAKER + "open-duration-seconds", 30, 1, 3600)),
                Duration.ofSeconds(reader.intIn(PROVIDER + "health-check-seconds", 60, 10, 3600)));
        return new ProviderSettings(
                type.isEmpty() ? DEFAULT_PROVIDER : type,
                url,
                apiKey,
                Duration.ofMillis(reader.intIn(PROVIDER + "connect-timeout-ms", 2000, 100, 60_000)),
                Duration.ofMillis(reader.intIn(PROVIDER + "request-timeout-ms", 3000, 100, 60_000)),
                reader.intIn(PROVIDER + "max-concurrent-requests", 8, 1, 256),
                breaker);
    }

    private static @NotNull LanguageSettings languages(@NotNull Reader reader) {
        ConfigurationSection root = reader.root;
        List<LanguageCode> enabled = new ArrayList<>();
        if (root.isList("languages.enabled")) {
            for (String raw : root.getStringList("languages.enabled")) {
                Optional<LanguageCode> code = LanguageCode.parse(raw);
                if (code.isEmpty()) {
                    reader.warn("languages.enabled contains '" + raw + "', which is no language code; ignored");
                } else if (!enabled.contains(code.get())) {
                    enabled.add(code.get());
                }
            }
        }
        if (enabled.isEmpty()) {
            reader.warn("languages.enabled is empty; using de and en");
            enabled.addAll(DEFAULT_LANGUAGES);
        }
        LanguageCode fallback = LanguageCode.parse(root.getString("languages.fallback")).orElse(DEFAULT_FALLBACK);
        if (!enabled.contains(fallback)) {
            reader.warn("languages.fallback '" + fallback + "' is not enabled; using " + enabled.getFirst());
            fallback = enabled.getFirst();
        }
        return new LanguageSettings(enabled, fallback);
    }

    private static @NotNull ChatSettings chat(@NotNull Reader reader, @NotNull ConfigurationSection root) {
        int minLength = reader.intIn(CHAT + "min-length", 2, 1, 256);
        int maxLength = reader.intIn(CHAT + "max-length", 256, minLength, 2000);
        return new ChatSettings(
                reader.chatMode(),
                Duration.ofMillis(reader.intIn(CHAT + "inline-wait-ms", 400, 0, 5000)),
                minLength,
                maxLength,
                root.getString(CHAT + "skip-prefix", "!"),
                Duration.ofMillis(reader.intIn(CHAT + "player-cooldown-ms", 500, 0, 60_000)),
                root.getBoolean(CHAT + "onboarding", true));
    }

    private static @NotNull DetectionSettings detection(@NotNull Reader reader, @NotNull ConfigurationSection root) {
        int samples = reader.intIn(DETECTION + "learn-samples", 8, 1, 50);
        return new DetectionSettings(
                root.getBoolean(DETECTION + "enabled", false),
                reader.intIn(DETECTION + "min-length", 12, 1, 500),
                reader.intIn(DETECTION + "min-confidence", 70, 0, 100),
                root.getBoolean(DETECTION + "learn-writing-language", true),
                samples,
                reader.intIn(DETECTION + "learn-threshold", 5, 1, samples));
    }

    private static @NotNull LearningSettings learning(@NotNull Reader reader) {
        return new LearningSettings(
                reader.intIn(LEARNING + "suggestions-per-hour", 5, 0, 1000),
                Duration.ofMinutes(reader.intIn(LEARNING + "min-playtime-minutes", 30, 0, 100_000)),
                reader.intIn(LEARNING + "promote-after", 10, 2, 10_000),
                Duration.ofHours(reader.intIn(LEARNING + "promote-window-hours", 24, 1, 720)),
                reader.intIn(LEARNING + "promote-max-length", 40, 1, 256),
                reader.intIn(LEARNING + "auto-approve-votes", 3, 0, 100));
    }

    private static @NotNull Optional<URI> parseUrl(@NotNull String raw) {
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme();
            boolean http = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            return http && uri.getHost() != null ? Optional.of(uri) : Optional.empty();
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static @Nullable String firstNonBlank(@Nullable String first, @Nullable String second,
                                                  @Nullable String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return fallback;
    }

    private static final class Reader {

        private final ConfigurationSection root;
        private final List<String> warnings = new ArrayList<>();

        private Reader(@NotNull ConfigurationSection root) {
            this.root = root;
        }

        private void warn(@NotNull String warning) {
            warnings.add(warning);
        }

        private int intIn(@NotNull String path, int fallback, int min, int max) {
            if (!root.contains(path)) {
                return fallback;
            }
            if (!root.isInt(path)) {
                warn(path + " must be a whole number; using " + fallback);
                return fallback;
            }
            int value = root.getInt(path);
            if (value < min || value > max) {
                int clamped = Math.clamp(value, min, max);
                warn(path + " must be between " + min + " and " + max + "; using " + clamped);
                return clamped;
            }
            return value;
        }

        private @NotNull ChatMode chatMode() {
            String raw = root.getString(CHAT + "mode", ChatMode.INLINE.name());
            try {
                return ChatMode.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ex) {
                warn("chat.mode '" + raw + "' is unknown (INLINE, FOLLOW_UP); using INLINE");
                return ChatMode.INLINE;
            }
        }
    }
}
