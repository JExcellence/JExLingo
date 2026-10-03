package de.jexcellence.lingo.api.event;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.api.TranslationResult;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Fired asynchronously once every translation of one public chat message is done. Relay and logging plugins can
 * use it to forward the message in other languages. The event is informational and cannot be cancelled.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class ChatTranslatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player sender;
    private final String original;
    private final LanguageCode sourceLanguage;
    private final Map<LanguageCode, TranslationResult> results;

    /**
     * Creates the event.
     *
     * @param sender         the player who wrote the message
     * @param original       the message as written
     * @param sourceLanguage the language of the message
     * @param results        one result per target language
     */
    public ChatTranslatedEvent(@NotNull Player sender, @NotNull String original, @NotNull LanguageCode sourceLanguage,
                               @NotNull Map<LanguageCode, TranslationResult> results) {
        super(true);
        this.sender = sender;
        this.original = original;
        this.sourceLanguage = sourceLanguage;
        this.results = Map.copyOf(results);
    }

    /**
     * Returns the player who wrote the message.
     *
     * @return the player who wrote the message
     */
    public @NotNull Player getSender() {
        return sender;
    }

    /**
     * Returns the message as written.
     *
     * @return the message as written
     */
    public @NotNull String getOriginal() {
        return original;
    }

    /**
     * Returns the language of the message.
     *
     * @return the language of the message
     */
    public @NotNull LanguageCode getSourceLanguage() {
        return sourceLanguage;
    }

    /**
     * Returns one result per target language; unmodifiable.
     *
     * @return one result per target language; unmodifiable
     */
    public @NotNull Map<LanguageCode, TranslationResult> getResults() {
        return results;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /**
     * Returns the handler list of this event type.
     *
     * @return the handler list of this event type
     */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
