package de.jexcellence.lingo.settings;

import de.jexcellence.lingo.api.LanguageCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A player's translation settings.
 *
 * @param language           the language the player reads in, or {@code null} to follow the client language
 * @param writeLanguage      the language the player writes in, or {@code null} to use the reading language
 * @param incoming           how messages in other languages reach the player
 * @param translateOutgoing  whether the player's own messages may be sent to the translator
 * @param showOriginal       whether the original is shown as a second line under a translation
 * @param suggestionsBlocked whether staff blocked the player from suggesting corrections
 * @param onboarded          whether the player has seen the first-join hint
 * @author JExcellence
 * @since 0.1.0
 */
public record PlayerLanguageSettings(
        @Nullable LanguageCode language,
        @Nullable LanguageCode writeLanguage,
        @NotNull IncomingMode incoming,
        boolean translateOutgoing,
        boolean showOriginal,
        boolean suggestionsBlocked,
        boolean onboarded
) {

    /** Settings of a player who never changed anything. */
    public static final PlayerLanguageSettings DEFAULTS =
            new PlayerLanguageSettings(null, null, IncomingMode.AUTO, true, false, false, false);

    /**
     * Changes the reading language.
     *
     * @param value the new language, or {@code null} for client language
     * @return a copy with the language changed
     */
    public @NotNull PlayerLanguageSettings withLanguage(@Nullable LanguageCode value) {
        return new PlayerLanguageSettings(value, writeLanguage, incoming, translateOutgoing, showOriginal,
                suggestionsBlocked, onboarded);
    }

    /**
     * Changes the writing language.
     *
     * @param value the new language, or {@code null} to use the reading language
     * @return a copy with the writing language changed
     */
    public @NotNull PlayerLanguageSettings withWriteLanguage(@Nullable LanguageCode value) {
        return new PlayerLanguageSettings(language, value, incoming, translateOutgoing, showOriginal,
                suggestionsBlocked, onboarded);
    }

    /**
     * Changes how incoming messages are translated.
     *
     * @param value the new mode
     * @return a copy with the mode changed
     */
    public @NotNull PlayerLanguageSettings withIncoming(@NotNull IncomingMode value) {
        return new PlayerLanguageSettings(language, writeLanguage, value, translateOutgoing, showOriginal,
                suggestionsBlocked, onboarded);
    }

    /**
     * Switches translation of the player's own messages.
     *
     * @param value whether outgoing messages may be translated
     * @return a copy with the option changed
     */
    public @NotNull PlayerLanguageSettings withOutgoing(boolean value) {
        return new PlayerLanguageSettings(language, writeLanguage, incoming, value, showOriginal, suggestionsBlocked,
                onboarded);
    }

    /**
     * Switches the original line under translations.
     *
     * @param value whether the original line is shown
     * @return a copy with the option changed
     */
    public @NotNull PlayerLanguageSettings withShowOriginal(boolean value) {
        return new PlayerLanguageSettings(language, writeLanguage, incoming, translateOutgoing, value,
                suggestionsBlocked, onboarded);
    }

    /**
     * Blocks or allows suggestions.
     *
     * @param value whether suggestions are blocked
     * @return a copy with the option changed
     */
    public @NotNull PlayerLanguageSettings withSuggestionsBlocked(boolean value) {
        return new PlayerLanguageSettings(language, writeLanguage, incoming, translateOutgoing, showOriginal, value,
                onboarded);
    }

    /**
     * Marks the first-join hint as seen.
     *
     * @return a copy marked as onboarded
     */
    public @NotNull PlayerLanguageSettings markOnboarded() {
        return new PlayerLanguageSettings(language, writeLanguage, incoming, translateOutgoing, showOriginal,
                suggestionsBlocked, true);
    }
}
