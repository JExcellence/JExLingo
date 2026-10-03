package de.jexcellence.lingo.language;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.config.LanguageSettings;
import de.jexcellence.lingo.settings.PlayerSettingsService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Decides which language a player reads and writes in: the stored preference if it is enabled, else the client
 * language if it is enabled, else the configured fallback.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LanguageResolver {

    private final PlayerSettingsService settings;
    private final AtomicReference<LanguageSettings> languages;

    /**
     * Creates the resolver.
     *
     * @param settings  player settings
     * @param languages enabled languages and fallback
     */
    public LanguageResolver(@NotNull PlayerSettingsService settings, @NotNull LanguageSettings languages) {
        this.settings = settings;
        this.languages = new AtomicReference<>(languages);
    }

    /**
     * The pure rule, for tests and callers without a player object.
     *
     * @param preference the stored preference, or {@code null}
     * @param locale     the client locale, or {@code null} when unknown
     * @param languages  enabled languages and fallback
     * @return the resolved language
     */
    public static @NotNull LanguageCode resolve(@Nullable LanguageCode preference, @Nullable Locale locale,
                                                @NotNull LanguageSettings languages) {
        if (preference != null && languages.isEnabled(preference)) {
            return preference;
        }
        if (locale != null) {
            LanguageCode client = LanguageCode.fromLocale(locale).orElse(null);
            if (client != null && languages.isEnabled(client)) {
                return client;
            }
        }
        return languages.fallback();
    }

    /**
     * The pure writing rule: the stored writing language if it is enabled, else the reading language.
     *
     * @param writePreference the stored writing language, or {@code null}
     * @param reading         the resolved reading language
     * @param languages       enabled languages and fallback
     * @return the language the player writes in
     */
    public static @NotNull LanguageCode resolveWriting(@Nullable LanguageCode writePreference,
                                                       @NotNull LanguageCode reading,
                                                       @NotNull LanguageSettings languages) {
        return writePreference != null && languages.isEnabled(writePreference) ? writePreference : reading;
    }

    /**
     * The language an online player writes in, used as the source of their chat lines.
     *
     * @param player the player
     * @return the writing language
     */
    public @NotNull LanguageCode resolveWriting(@NotNull Player player) {
        return resolveWriting(settings.get(player.getUniqueId()).writeLanguage(), resolve(player), languages.get());
    }

    /**
     * The language of an online player.
     *
     * @param player the player
     * @return the resolved language
     */
    public @NotNull LanguageCode resolve(@NotNull Player player) {
        return resolve(settings.get(player.getUniqueId()).language(), player.locale(), languages.get());
    }

    /**
     * The language of any player; offline players have no client locale.
     *
     * @param uuid the player's UUID
     * @return the resolved language
     */
    public @NotNull LanguageCode resolve(@NotNull UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return resolve(online);
        }
        return resolve(settings.get(uuid).language(), null, languages.get());
    }

    /**
     * Returns the enabled languages and fallback.
     *
     * @return the enabled languages and fallback
     */
    public @NotNull LanguageSettings languages() {
        return languages.get();
    }

    /**
     * Replaces the language settings after a reload.
     *
     * @param value the new settings
     */
    public void setLanguages(@NotNull LanguageSettings value) {
        languages.set(value);
    }
}
