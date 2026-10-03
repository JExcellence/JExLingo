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
     * Translates a text one player wrote for one player who reads it, for private messages, mail or relays.
     * Uses the writer's writing language as source and the reader's reading language as target, and respects
     * both players' settings (the writer allowed translation, the reader did not switch it off) and the staff
     * pause. Never completes exceptionally; when nothing is translated the result holds the original.
     *
     * @param writer  the player who wrote the text
     * @param reader  the player who reads it
     * @param text    the text
     * @param context where the text comes from
     * @return the result
     * @since 0.4.0
     */
    @NotNull CompletableFuture<TranslationResult> translateFor(@NotNull UUID writer, @NotNull UUID reader,
                                                               @NotNull String text,
                                                               @NotNull TranslationContext context);

    /**
     * Translates a text a player wrote into a fixed language, for example a Discord channel. Respects the writer's
     * "translate my messages" setting and the staff pause.
     *
     * @param writer  the player who wrote the text
     * @param text    the text
     * @param target  the language to translate into
     * @param context where the text goes
     * @return the result, the original when nothing was translated
     * @since 0.4.0
     */
    @NotNull CompletableFuture<TranslationResult> translateFrom(@NotNull UUID writer, @NotNull String text,
                                                                @NotNull LanguageCode target,
                                                                @NotNull TranslationContext context);

    /**
     * Translates a text from outside the game (for example Discord) for one player. Respects the reader's incoming
     * setting and the staff pause.
     *
     * @param reader  the player who reads the text
     * @param text    the text
     * @param source  the language of the text, or {@code null} to detect it
     * @param context where the text comes from
     * @return the result, the original when nothing was translated
     * @since 0.4.0
     */
    @NotNull CompletableFuture<TranslationResult> translateTo(@NotNull UUID reader, @NotNull String text,
                                                              @Nullable LanguageCode source,
                                                              @NotNull TranslationContext context);

    /**
     * The language a player writes in: the stored writing language, else the reading language.
     *
     * @param player the player's UUID
     * @return the language
     * @since 0.4.0
     */
    @NotNull LanguageCode writingLanguageOf(@NotNull UUID player);

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
