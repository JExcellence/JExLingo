package de.jexcellence.lingo.api;

import de.jexcellence.lingo.api.provider.TranslationProvider;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Entry point for other plugins. Registered in the Bukkit {@code ServicesManager} while JExLingo is enabled.
 *
 * <pre>{@code
 * JExLingoApi.lookup().ifPresent(lingo -> lingo
 *         .translate(TranslationRequest.of(text, LanguageCode.of("de"), lingo.languageOf(viewer)))
 *         .thenAccept(result -> viewer.sendMessage(result.text())));
 * }</pre>
 *
 * @author JExcellence
 * @since 0.1.0
 */
public interface JExLingoApi {

    /**
     * Finds the running JExLingo instance.
     *
     * @return the API, or empty when JExLingo is not enabled
     */
    static @NotNull Optional<JExLingoApi> lookup() {
        return Optional.ofNullable(Bukkit.getServicesManager().load(JExLingoApi.class));
    }

    /**
     * Translates a text through the full pipeline (glossary, memory, cache, provider). The future never completes
     * exceptionally; on failure the result holds the original text with origin {@link TranslationOrigin#FALLBACK}.
     *
     * @param request what to translate
     * @return the result
     */
    @NotNull CompletableFuture<TranslationResult> translate(@NotNull TranslationRequest request);

    /**
     * The language a player reads in: the stored preference, else the client language, else the fallback.
     *
     * @param player the player's UUID
     * @return the language
     */
    @NotNull LanguageCode languageOf(@NotNull UUID player);

    /**
     * Stores a player's language preference.
     *
     * @param player   the player's UUID
     * @param language the language, or {@code null} to follow the client language again
     * @return completes when the preference is stored
     */
    @NotNull CompletableFuture<Void> setLanguage(@NotNull UUID player, @Nullable LanguageCode language);

    /**
     * Returns the languages this server translates between.
     *
     * @return the languages this server translates between
     */
    @NotNull Set<LanguageCode> enabledLanguages();

    /**
     * Registers an additional translation provider. It becomes active when {@code provider.type} in the config
     * names its {@link TranslationProvider#id()} (Premium edition).
     *
     * @param provider the provider
     */
    void registerProvider(@NotNull TranslationProvider provider);
}
