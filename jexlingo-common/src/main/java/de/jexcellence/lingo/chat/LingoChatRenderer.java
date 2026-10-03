package de.jexcellence.lingo.chat;

import de.jexcellence.lingo.api.TranslationResult;
import de.jexcellence.lingo.learning.RecentMessageBuffer;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Wraps the chat renderer that was active before JExLingo (the JExEssentials format, or Paper's default) and only
 * swaps the message part per viewer. The server's chat format stays exactly as configured; viewers in the sender's
 * language, the console and players who turned translation off get the line unchanged.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LingoChatRenderer implements ChatRenderer {

    private final ChatRenderer previous;
    private final ChatSession session;
    private final TranslatedLineDecorator decorator;
    private final RecentMessageBuffer recent;

    /**
     * Creates the renderer.
     *
     * @param previous  the renderer active before
     * @param session   the translations of this message
     * @param decorator line decorator
     * @param recent    recent message buffer, to note what each viewer saw
     */
    public LingoChatRenderer(@NotNull ChatRenderer previous, @NotNull ChatSession session,
                             @NotNull TranslatedLineDecorator decorator, @NotNull RecentMessageBuffer recent) {
        this.previous = previous;
        this.session = session;
        this.decorator = decorator;
        this.recent = recent;
    }

    @Override
    public @NotNull Component render(@NotNull Player source, @NotNull Component sourceDisplayName,
                                     @NotNull Component message, @NotNull Audience viewer) {
        if (!(viewer instanceof Player player)) {
            return previous.render(source, sourceDisplayName, message, viewer);
        }
        if (session.offersButton(player.getUniqueId())) {
            return previous.render(source, sourceDisplayName, message, viewer)
                    .append(Component.space())
                    .append(decorator.translateButton(player, session.message().id()));
        }
        Optional<TranslationResult> result = session.inlineResult(player.getUniqueId());
        if (result.isEmpty()) {
            return previous.render(source, sourceDisplayName, message, viewer);
        }
        Component translated = Component.text(result.get().text());
        Component rendered = previous.render(source, sourceDisplayName, translated, viewer);
        recent.markSeen(player.getUniqueId(), session.message().id());
        return decorator.decorate(rendered, result.get(), player, session.message().id());
    }
}
