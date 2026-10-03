package de.jexcellence.lingo.command;

import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.jextranslate.MessageBuilder;
import de.jexcellence.lingo.text.SafeText;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Sends command feedback on the right thread: a player's own thread for players, directly for the console. Async
 * command work (database, provider) ends here.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class Replies {

    private final PlatformScheduler scheduler;

    /**
     * Creates the helper.
     *
     * @param scheduler the platform scheduler
     */
    public Replies(@NotNull PlatformScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Sends a prefixed message.
     *
     * @param sender  the receiver
     * @param builder the prepared builder
     */
    public void send(@NotNull CommandSender sender, @NotNull MessageBuilder builder) {
        run(sender, () -> builder.prefix().send(sender));
    }

    /**
     * Sends a prefixed message by key.
     *
     * @param sender the receiver
     * @param key    the translation key
     */
    public void send(@NotNull CommandSender sender, @NotNull String key) {
        send(sender, SafeText.msg(key));
    }

    /**
     * Sends a prefixed message with player-written values inserted safely.
     *
     * @param sender     the receiver
     * @param builder    the prepared builder
     * @param userValues placeholder name to raw user text
     */
    public void sendUser(@NotNull CommandSender sender, @NotNull MessageBuilder builder,
                         @NotNull Map<String, String> userValues) {
        run(sender, () -> sender.sendMessage(SafeText.component(builder.prefix(),
                sender instanceof Player player ? player : null, userValues)));
    }

    /**
     * Runs a task on the sender's thread.
     *
     * @param sender the sender
     * @param task   the task
     */
    public void run(@NotNull CommandSender sender, @NotNull Runnable task) {
        if (sender instanceof Player player) {
            scheduler.runAtEntity(player, task);
        } else {
            task.run();
        }
    }
}
