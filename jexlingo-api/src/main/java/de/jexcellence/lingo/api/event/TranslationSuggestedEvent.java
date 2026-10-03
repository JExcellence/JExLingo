package de.jexcellence.lingo.api.event;

import de.jexcellence.lingo.api.LanguageCode;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired after a player submitted a better translation that now waits for staff review (Premium edition). May be
 * fired off the main thread; check {@link #isAsynchronous()}.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class TranslationSuggestedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID submitter;
    private final String sourceText;
    private final String suggestion;
    private final LanguageCode source;
    private final LanguageCode target;

    /**
     * Creates the event.
     *
     * @param async      whether the event is fired off the main thread
     * @param submitter  the player who made the suggestion
     * @param sourceText the original text
     * @param suggestion the suggested translation
     * @param source     the language of the original text
     * @param target     the language of the suggestion
     */
    public TranslationSuggestedEvent(boolean async, @NotNull UUID submitter, @NotNull String sourceText,
                                     @NotNull String suggestion, @NotNull LanguageCode source,
                                     @NotNull LanguageCode target) {
        super(async);
        this.submitter = submitter;
        this.sourceText = sourceText;
        this.suggestion = suggestion;
        this.source = source;
        this.target = target;
    }

    /**
     * Returns the player who made the suggestion.
     *
     * @return the player who made the suggestion
     */
    public @NotNull UUID getSubmitter() {
        return submitter;
    }

    /**
     * Returns the original text.
     *
     * @return the original text
     */
    public @NotNull String getSourceText() {
        return sourceText;
    }

    /**
     * Returns the suggested translation.
     *
     * @return the suggested translation
     */
    public @NotNull String getSuggestion() {
        return suggestion;
    }

    /**
     * Returns the language of the original text.
     *
     * @return the language of the original text
     */
    public @NotNull LanguageCode getSource() {
        return source;
    }

    /**
     * Returns the language of the suggestion.
     *
     * @return the language of the suggestion
     */
    public @NotNull LanguageCode getTarget() {
        return target;
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
