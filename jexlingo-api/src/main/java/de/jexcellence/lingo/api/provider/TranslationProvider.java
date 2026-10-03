package de.jexcellence.lingo.api.provider;

import de.jexcellence.lingo.api.LanguageCode;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A machine translation backend. Implementations must never block the calling thread; every method returns a
 * future that completes on the provider's own threads. A failed future means the call failed; JExLingo then shows
 * the original text.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public interface TranslationProvider {

    /**
     * Returns the id used in {@code provider.type}, lower case, for example {@code libretranslate}.
     *
     * @return the id used in {@code provider.type}, lower case, for example {@code libretranslate}
     */
    @NotNull String id();

    /**
     * Translates one text.
     *
     * @param text   the text, already masked by JExLingo
     * @param source the source language
     * @param target the target language
     * @return the translated text
     */
    @NotNull CompletableFuture<String> translate(@NotNull String text, @NotNull LanguageCode source,
                                                 @NotNull LanguageCode target);

    /**
     * Returns the languages the backend can translate between right now.
     *
     * @return the languages the backend can translate between right now
     */
    @NotNull CompletableFuture<Set<LanguageCode>> languages();

    /**
     * Detects the language of a text.
     *
     * @param text the text
     * @return the best guess, or empty when the backend cannot detect languages
     */
    @NotNull CompletableFuture<Optional<DetectedLanguage>> detect(@NotNull String text);

    /** Releases connections and threads. Called when JExLingo disables or switches providers. */
    default void close() {
        // Providers without resources have nothing to release.
    }
}
