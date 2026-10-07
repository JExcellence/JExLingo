package de.jexcellence.lingo.command;

import de.jexcellence.lingo.text.LingoPanel;
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
 * {@code /lingo help} as a suite chat panel: centred header and context, one {@code command | description} row per
 * player command with the label column aligned by pixel width, a hover with the full command and a click that runs
 * it or puts it into the chat, and the {@code !} tip as footer. Texts come from {@code lingo.chat-panel-v1.help.*}
 * and {@code lingo.help.*}.
 *
 * @author JExcellence
 * @since 0.4.2
 */
public final class LingoHelp {

    private static final String KEY = "lingo.help.";
    private static final String PANEL = "lingo.chat-panel-v1.help.";
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
        LingoPanel panel = LingoPanel.of(sender);
        Player viewer = panel.viewer();
        panel.header(PANEL + "header")
                .context(SafeText.msg(PANEL + "context").component(viewer))
                .gap();
        for (Entry entry : ENTRIES) {
            String description = SafeText.msg(KEY + "desc." + entry.id()).plain(viewer);
            HoverEvent<Component> hover = HoverEvent.showText(hover(entry, description, viewer));
            ClickEvent click = entry.action() == Action.RUN
                    ? ClickEvent.runCommand(entry.command())
                    : ClickEvent.suggestCommand(entry.command() + " ");
            Component label = SafeText.msg(PANEL + "command").with(PARAM_COMMAND, entry.command()).component(viewer);
            panel.row(label.hoverEvent(hover).clickEvent(click),
                    value(entry, description, viewer).hoverEvent(hover).clickEvent(click));
        }
        panel.footer(KEY + "footer").send(sender);
    }

    private static @NotNull Component value(@NotNull Entry entry, @NotNull String description,
                                            @Nullable Player viewer) {
        if (entry.args().isEmpty()) {
            return SafeText.msg(PANEL + "value").with(PARAM_DESCRIPTION, description).component(viewer);
        }
        return SafeText.component(SafeText.msg(PANEL + "value-with-args").with(PARAM_DESCRIPTION, description),
                viewer, Map.of("args", entry.args()));
    }

    private static @NotNull Component hover(@NotNull Entry entry, @NotNull String description,
                                            @Nullable Player viewer) {
        String full = entry.args().isEmpty() ? entry.command() : entry.command() + " " + entry.args();
        String actionKey = entry.action() == Action.RUN ? "hover-action-run" : "hover-action-suggest";
        return SafeText.component(SafeText.msg(KEY + "hover-base").with(PARAM_DESCRIPTION, description),
                        viewer, Map.of("full", full))
                .append(Component.newline())
                .append(Component.newline())
                .append(SafeText.msg(KEY + actionKey).component(viewer));
    }
}
