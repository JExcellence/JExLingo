package de.jexcellence.lingo.text;

import de.jexcellence.jexplatform.gui.chat.ChatPanel;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The JExLingo chat panel: header, context, one blank line, {@code Label | value} rows, optional section titles and a
 * footer. Players get it through the suite {@link ChatPanel} (centred header, label column padded by pixel width);
 * the console and Bedrock forms get the same lines without padding. Labels and values are inserted as plain text
 * through {@link SafeText}, so a value such as a player name or provider error is never parsed as markup.
 *
 * @author JExcellence
 * @since 0.4.2
 */
public final class LingoPanel {

    private static final String KEY = "lingo.panel.";
    private static final String PARAM_LABEL = "label";
    private static final String PARAM_VALUE = "value";

    /** Colour of a row value, from {@code lingo.panel.value.<tone>}. */
    public enum Tone {
        /** Neutral highlighted value. */
        PLAIN,
        /** Feature accent. */
        ACCENT,
        /** Healthy or done. */
        OK,
        /** Failed or offline. */
        BAD,
        /** Needs attention. */
        WARN,
        /** De-emphasised, for empty or automatic values. */
        MUTED;

        @NotNull String key() {
            return KEY + "value." + name().toLowerCase(Locale.ROOT);
        }
    }

    private enum Kind { CENTERED, GAP, ROW, LINE, FOOTER }

    private record Entry(@NotNull Kind kind, @NotNull Component first, @NotNull Component second) {
    }

    private final @Nullable Player viewer;
    private final List<Entry> entries = new ArrayList<>();

    private LingoPanel(@Nullable Player viewer) {
        this.viewer = viewer;
    }

    /**
     * Starts a panel for a receiver.
     *
     * @param sender the receiver; a player gets the centred panel, anything else plain lines
     * @return the panel
     */
    public static @NotNull LingoPanel of(@NotNull CommandSender sender) {
        return new LingoPanel(sender instanceof Player player ? player : null);
    }

    /**
     * Starts a panel for a viewer, for callers that only need {@link #plainLines()}.
     *
     * @param viewer the viewer, or {@code null} for the default locale
     * @return the panel
     */
    public static @NotNull LingoPanel forViewer(@Nullable Player viewer) {
        return new LingoPanel(viewer);
    }

    /**
     * Returns the viewer whose locale the panel uses.
     *
     * @return the viewer, or {@code null} for the console
     */
    public @Nullable Player viewer() {
        return viewer;
    }

    /**
     * Adds the header line: what this panel shows.
     *
     * @param header the translated header
     * @return this panel
     */
    public @NotNull LingoPanel header(@NotNull Component header) {
        return add(Kind.CENTERED, header, Component.empty());
    }

    /**
     * Adds the header line from a key.
     *
     * @param key the translation key
     * @return this panel
     */
    public @NotNull LingoPanel header(@NotNull String key) {
        return header(SafeText.msg(key).component(viewer));
    }

    /**
     * Adds the scope line directly below the header.
     *
     * @param context the translated context
     * @return this panel
     */
    public @NotNull LingoPanel context(@NotNull Component context) {
        return add(Kind.CENTERED, context, Component.empty());
    }

    /**
     * Adds one blank line between groups; leading, trailing and repeated gaps collapse.
     *
     * @return this panel
     */
    public @NotNull LingoPanel gap() {
        return add(Kind.GAP, Component.empty(), Component.empty());
    }

    /**
     * Starts a titled group: one blank line and the muted section title.
     *
     * @param titleKey translation key of the section name
     * @return this panel
     */
    public @NotNull LingoPanel section(@NotNull String titleKey) {
        gap();
        return line(SafeText.component(SafeText.msg(KEY + "section"), viewer,
                Map.of("name", SafeText.msg(titleKey).plain(viewer))));
    }

    /**
     * Adds a {@code Label | value} row with a neutral value.
     *
     * @param labelKey translation key of the label
     * @param value    the value as plain text
     * @return this panel
     */
    public @NotNull LingoPanel row(@NotNull String labelKey, @NotNull String value) {
        return row(labelKey, value, Tone.PLAIN);
    }

    /**
     * Adds a {@code Label | value} row.
     *
     * @param labelKey translation key of the label
     * @param value    the value as plain text
     * @param tone     the value colour
     * @return this panel
     */
    public @NotNull LingoPanel row(@NotNull String labelKey, @NotNull String value, @NotNull Tone tone) {
        Component label = SafeText.component(SafeText.msg(KEY + PARAM_LABEL), viewer,
                Map.of(PARAM_LABEL, SafeText.msg(labelKey).plain(viewer)));
        Component styled = SafeText.component(SafeText.msg(tone.key()), viewer, Map.of(PARAM_VALUE, value));
        return add(Kind.ROW, label, styled);
    }

    /**
     * Adds a {@code Label | value} row from finished components, for rows that carry their own hover or click.
     *
     * @param label the translated label
     * @param value the translated value
     * @return this panel
     */
    public @NotNull LingoPanel row(@NotNull Component label, @NotNull Component value) {
        return add(Kind.ROW, label, value);
    }

    /**
     * Adds a free left-aligned body line, such as one list entry.
     *
     * @param line the line
     * @return this panel
     */
    public @NotNull LingoPanel line(@NotNull Component line) {
        return add(Kind.LINE, line, Component.empty());
    }

    /**
     * Adds the action line, separated from the body by one blank line.
     *
     * @param footer the translated footer
     * @return this panel
     */
    public @NotNull LingoPanel footer(@NotNull Component footer) {
        return add(Kind.FOOTER, footer, Component.empty());
    }

    /**
     * Adds the action line from a key.
     *
     * @param key the translation key
     * @return this panel
     */
    public @NotNull LingoPanel footer(@NotNull String key) {
        return footer(SafeText.msg(key).component(viewer));
    }

    /**
     * Sends the panel: centred for a player, plain lines for the console.
     *
     * @param sender the receiver
     */
    public void send(@NotNull CommandSender sender) {
        if (sender instanceof Player player) {
            chatPanel().send(player);
        } else {
            plainLines().forEach(sender::sendMessage);
        }
    }

    /**
     * Renders the panel without padding: header and context first, rows as {@code Label | value}, one empty line
     * per gap. Used for the console and for Bedrock form text.
     *
     * @return the lines
     */
    public @NotNull List<Component> plainLines() {
        Component separator = SafeText.msg(KEY + "separator").component(viewer);
        List<Component> lines = new ArrayList<>();
        for (Entry entry : trimmed()) {
            lines.add(switch (entry.kind()) {
                case ROW -> entry.first().append(separator).append(entry.second());
                case GAP -> Component.empty();
                default -> entry.first();
            });
        }
        return lines;
    }

    private @NotNull ChatPanel chatPanel() {
        ChatPanel panel = ChatPanel.create();
        for (Entry entry : entries) {
            switch (entry.kind()) {
                case CENTERED -> panel.context(entry.first());
                case GAP -> panel.gap();
                case ROW -> panel.row(entry.first(), entry.second());
                case LINE -> panel.line(entry.first());
                case FOOTER -> panel.footer(entry.first());
                default -> throw new IllegalStateException("Unexpected panel entry: " + entry.kind());
            }
        }
        return panel;
    }

    private @NotNull List<Entry> trimmed() {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.kind() == Kind.GAP || entry.kind() == Kind.FOOTER) {
                appendGap(out);
            }
            if (entry.kind() != Kind.GAP) {
                out.add(entry);
            }
        }
        while (!out.isEmpty() && out.getLast().kind() == Kind.GAP) {
            out.removeLast();
        }
        return out;
    }

    private static void appendGap(@NotNull List<Entry> out) {
        if (!out.isEmpty() && out.getLast().kind() != Kind.GAP) {
            out.add(new Entry(Kind.GAP, Component.empty(), Component.empty()));
        }
    }

    private @NotNull LingoPanel add(@NotNull Kind kind, @NotNull Component first, @NotNull Component second) {
        entries.add(new Entry(kind, first, second));
        return this;
    }
}
