package de.jexcellence.lingo.chat;

import de.jexcellence.lingo.learning.RecentMessageBuffer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Hooks public chat. Runs at {@link EventPriority#HIGHEST} so moderation (mute, filter, ignore lists) and the chat
 * format have run before; cancelled messages are never seen. The event is never cancelled here, so signed chat,
 * chat reports, Discord relays and logs keep working.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class ChatTranslationListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ChatTranslationCoordinator coordinator;
    private final TranslatedLineDecorator decorator;
    private final RecentMessageBuffer recent;

    /**
     * Creates the listener.
     *
     * @param coordinator the coordinator
     * @param decorator   the line decorator
     * @param recent      the recent message buffer
     */
    public ChatTranslationListener(@NotNull ChatTranslationCoordinator coordinator,
                                   @NotNull TranslatedLineDecorator decorator, @NotNull RecentMessageBuffer recent) {
        this.coordinator = coordinator;
        this.decorator = decorator;
        this.recent = recent;
    }

    /**
     * Starts the translations and installs the per-viewer renderer.
     *
     * @param event the chat event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(@NotNull AsyncChatEvent event) {
        String text = PLAIN.serialize(event.message());
        coordinator.begin(event.getPlayer(), text, event.viewers(), event.isAsynchronous())
                .ifPresent(session -> event.renderer(
                        new LingoChatRenderer(event.renderer(), session, decorator, recent)));
    }
}
