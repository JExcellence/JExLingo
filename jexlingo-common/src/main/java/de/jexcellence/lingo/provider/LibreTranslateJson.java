package de.jexcellence.lingo.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.provider.DetectedLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Request bodies and response parsing of the LibreTranslate HTTP API. Pure functions, no I/O, so the format is
 * covered by unit tests.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LibreTranslateJson {

    private static final String API_KEY = "api_key";
    private static final String LANGUAGE = "language";
    private static final String CONFIDENCE = "confidence";

    private LibreTranslateJson() {
    }

    /**
     * Body of {@code POST /translate} with {@code format: text}, so the backend never interprets markup.
     *
     * @param text   the text
     * @param source the source language
     * @param target the target language
     * @param apiKey the API key, or {@code null}
     * @return the JSON body
     */
    public static @NotNull String translateRequest(@NotNull String text, @NotNull LanguageCode source,
                                                   @NotNull LanguageCode target, @Nullable String apiKey) {
        JsonObject body = new JsonObject();
        body.addProperty("q", text);
        body.addProperty("source", source.code());
        body.addProperty("target", target.code());
        body.addProperty("format", "text");
        addKey(body, apiKey);
        return body.toString();
    }

    /**
     * Body of {@code POST /detect}.
     *
     * @param text   the text
     * @param apiKey the API key, or {@code null}
     * @return the JSON body
     */
    public static @NotNull String detectRequest(@NotNull String text, @Nullable String apiKey) {
        JsonObject body = new JsonObject();
        body.addProperty("q", text);
        addKey(body, apiKey);
        return body.toString();
    }

    /**
     * Reads {@code translatedText} from a translate answer.
     *
     * @param body the response body
     * @return the translated text
     * @throws ProviderException when the field is missing
     */
    public static @NotNull String parseTranslation(@NotNull String body) {
        JsonObject object = object(body);
        JsonElement text = object.get("translatedText");
        if (text == null || !text.isJsonPrimitive()) {
            throw new ProviderException(ProviderException.NOT_SENT, "Answer has no translatedText");
        }
        return text.getAsString();
    }

    /**
     * Reads the language codes of a {@code GET /languages} answer.
     *
     * @param body the response body
     * @return the available languages
     */
    public static @NotNull Set<LanguageCode> parseLanguages(@NotNull String body) {
        Set<LanguageCode> languages = new LinkedHashSet<>();
        for (JsonElement entry : array(body)) {
            if (entry.isJsonObject() && entry.getAsJsonObject().has("code")) {
                LanguageCode.parse(entry.getAsJsonObject().get("code").getAsString()).ifPresent(languages::add);
            }
        }
        return languages;
    }

    /**
     * Reads the best guess of a {@code POST /detect} answer.
     *
     * @param body the response body
     * @return the most confident language, or empty for an empty answer
     */
    public static @NotNull Optional<DetectedLanguage> parseDetection(@NotNull String body) {
        DetectedLanguage best = null;
        for (JsonElement entry : array(body)) {
            Optional<DetectedLanguage> guess = detection(entry);
            if (guess.isPresent() && (best == null || guess.get().confidence() > best.confidence())) {
                best = guess.get();
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Reads the {@code error} field of an error answer.
     *
     * @param body the response body
     * @return the error text, or a short default when the body has none
     */
    public static @NotNull String parseError(@Nullable String body) {
        if (body == null || body.isBlank()) {
            return "empty answer";
        }
        try {
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonObject() && root.getAsJsonObject().has("error")) {
                return root.getAsJsonObject().get("error").getAsString();
            }
        } catch (JsonParseException | IllegalStateException ex) {
            return "unreadable answer";
        }
        return "unknown error";
    }

    private static @NotNull Optional<DetectedLanguage> detection(@NotNull JsonElement entry) {
        if (!entry.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = entry.getAsJsonObject();
        if (!object.has(LANGUAGE) || !object.has(CONFIDENCE)) {
            return Optional.empty();
        }
        return LanguageCode.parse(object.get(LANGUAGE).getAsString())
                .map(language -> new DetectedLanguage(language, object.get(CONFIDENCE).getAsDouble()));
    }

    private static void addKey(@NotNull JsonObject body, @Nullable String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            body.addProperty(API_KEY, apiKey);
        }
    }

    private static @NotNull JsonObject object(@NotNull String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonObject()) {
                return root.getAsJsonObject();
            }
        } catch (JsonParseException ex) {
            throw new ProviderException(ProviderException.NOT_SENT, "Unreadable answer: " + ex.getMessage());
        }
        throw new ProviderException(ProviderException.NOT_SENT, "Answer is no JSON object");
    }

    private static @NotNull JsonArray array(@NotNull String body) {
        try {
            JsonElement root = JsonParser.parseString(body);
            if (root.isJsonArray()) {
                return root.getAsJsonArray();
            }
        } catch (JsonParseException ex) {
            throw new ProviderException(ProviderException.NOT_SENT, "Unreadable answer: " + ex.getMessage());
        }
        throw new ProviderException(ProviderException.NOT_SENT, "Answer is no JSON array");
    }
}
