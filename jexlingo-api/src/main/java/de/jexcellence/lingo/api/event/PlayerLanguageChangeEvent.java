package de.jexcellence.lingo.api.event;

import de.jexcellence.lingo.api.LanguageCode;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Fired after a player's stored language preference changed. {@code null} means the player follows the client
 * language. May be fired off the main thread; check {@link #isAsynchronous()}.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class PlayerLanguageChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID player;
    private final LanguageCode previous;
    private final LanguageCode current;

    /**
     * Creates the event.
     *
     * @param async    whether the event is fired off the main thread
     * @param player   the player's UUID
     * @param previous the preference before, or {@code null} for client language
     * @param current  the preference now, or {@code null} for client language
     */
    public PlayerLanguageChangeEvent(boolean async, @NotNull UUID player, @Nullable LanguageCode previous,
                                     @Nullable LanguageCode current) {
        super(async);
        this.player = player;
        this.previous = previous;
        this.current = current;
    }

    /**
     * Returns the player's UUID.
     *
     * @return the player's UUID
     */
    public @NotNull UUID getPlayer() {
        return player;
    }

    /**
     * Returns the preference before, or {@code null} for client language.
     *
     * @return the preference before, or {@code null} for client language
     */
    public @Nullable LanguageCode getPrevious() {
        return previous;
    }

    /**
     * Returns the preference now, or {@code null} for client language.
     *
     * @return the preference now, or {@code null} for client language
     */
    public @Nullable LanguageCode getCurrent() {
        return current;
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
