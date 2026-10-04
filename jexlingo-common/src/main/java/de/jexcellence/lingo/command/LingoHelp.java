package de.jexcellence.lingo.command;

import de.jexcellence.lingo.text.SafeText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * {@code /lingo help} in the suite help style (as JExVote's help): a banner, one line per player command with its
 * arguments and description, a hover with the full command and a click that runs it or puts it into the chat, and
 * the {@code !} tip at the end. Every text comes from {@code lingo.help.*}.
 *
 * @author JExcellence
 * @since 0.4.2
 */
public final class LingoHelp {

    private static final String KEY = "lingo.help.";
    private static final String ROOT = "/lingo";
    private static final String PARAM_COMMAND = "command";
    private static final String PARAM_DESCRIPTION = "description";

    private enum Action { RUN, SUGGEST }

    private record Entry(@NotNull String command, @NotNull String args, @NotNull String id, @NotNull Action action) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry(ROOT, "", "menu", Action.RUN),
            new Entry(ROOT + " lang", "<auto|code>", "lang", Action.SUGGEST),
            new Entry(ROOT + " write", "<auto|code>", "write", Action.SUGGEST),
            new Entry(ROOT + " incoming", "[auto|click|off]", "incoming", Action.SUGGEST),
            new Entry(ROOT + " outgoing", "[enable|disable]", "outgoing", Action.SUGGEST),
            new Entry(ROOT + " original", "[enable|disable]", "original", Action.SUGGEST),
            new Entry(ROOT + " suggest", "", "suggest", Action.RUN));

    private LingoHelp() {
    }

    /**
     * Sends the help.
     *
     * @param sender the receiver
     */
    public static void send(@NotNull CommandSender sender) {
        Player viewer = sender instanceof Player player ? player : null;
        SafeText.msg(KEY + "banner").send(sender);
        for (Entry entry : ENTRIES) {
            sender.sendMessage(line(entry, viewer));
        }
        SafeText.msg(KEY + "footer").send(sender);
    }

    private static @NotNull Component line(@NotNull Entry entry, @Nullable Player viewer) {
        String description = SafeText.msg(KEY + "desc." + entry.id()).plain(viewer);
        Component line = entry.args().isEmpty()
                ? SafeText.msg(KEY + "entry").with(PARAM_COMMAND, entry.command())
                        .with(PARAM_DESCRIPTION, description).component(viewer)
                : SafeText.component(SafeText.msg(KEY + "entry-with-args").with(PARAM_COMMAND, entry.command())
                        .with(PARAM_DESCRIPTION, description), viewer, Map.of("args", entry.args()));
        String full = entry.args().isEmpty() ? entry.command() : entry.command() + " " + entry.args();
        Component hover = SafeText.component(SafeText.msg(KEY + "hover-base").with(PARAM_DESCRIPTION, description),
                        viewer, Map.of("full", full))
                .append(Component.newline())
                .append(Component.newline())
                .append(SafeText.msg(KEY + (entry.action() == Action.RUN ? "hover-action-run"
                        : "hover-action-suggest")).component(viewer));
        ClickEvent click = entry.action() == Action.RUN
                ? ClickEvent.runCommand(entry.command())
                : ClickEvent.suggestCommand(entry.command() + " ");
        return line.hoverEvent(HoverEvent.showText(hover)).clickEvent(click);
    }
}
