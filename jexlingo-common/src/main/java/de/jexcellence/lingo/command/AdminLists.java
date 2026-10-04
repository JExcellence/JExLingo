package de.jexcellence.lingo.command;

import de.jexcellence.lingo.glossary.GlossaryMode;
import de.jexcellence.lingo.glossary.GlossaryTerm;
import de.jexcellence.lingo.learning.PinnedPhrase;
import de.jexcellence.lingo.text.LingoPanel;
import de.jexcellence.lingo.text.SafeText;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The staff lists in chat ({@code /lingo phrases}, {@code /lingo glossary list}) as chat panels: header with the
 * count, a context line, one line per entry and the next action as footer. Phrases and terms are player or staff
 * text and are inserted as plain text.
 *
 * @author JExcellence
 * @since 0.4.2
 */
final class AdminLists {

    private static final String PHRASES = "lingo.phrases.";
    private static final String GLOSSARY = "lingo.glossary.list.";
    private static final String COUNT = "count";

    private AdminLists() {
    }

    /**
     * Sends the newest pinned phrases.
     *
     * @param sender the receiver
     * @param pinned every pinned phrase, newest first
     * @param limit  how many are listed
     */
    static void phrases(@NotNull CommandSender sender, @NotNull List<PinnedPhrase> pinned, int limit) {
        LingoPanel panel = LingoPanel.of(sender);
        Player viewer = panel.viewer();
        int shown = Math.min(limit, pinned.size());
        panel.header(SafeText.msg(PHRASES + "header").with(COUNT, pinned.size()).component(viewer))
                .context(SafeText.msg(PHRASES + "context").with("shown", shown).with(COUNT, pinned.size())
                        .component(viewer))
                .gap();
        if (pinned.isEmpty()) {
            panel.line(SafeText.msg(PHRASES + "empty").component(viewer));
        }
        pinned.stream().limit(limit).forEach(phrase -> panel.line(SafeText.component(
                SafeText.msg(PHRASES + "entry")
                        .with("id", phrase.id())
                        .with("pair", phrase.pair().source().upper() + " » " + phrase.pair().target().upper()),
                viewer, Map.of("text", phrase.sourceKey(), "translation", phrase.targetText()))));
        panel.footer(PHRASES + "footer").send(sender);
    }

    /**
     * Sends every glossary term.
     *
     * @param sender the receiver
     * @param terms  the terms
     */
    static void glossary(@NotNull CommandSender sender, @NotNull List<GlossaryTerm> terms) {
        LingoPanel panel = LingoPanel.of(sender);
        Player viewer = panel.viewer();
        panel.header(SafeText.msg(GLOSSARY + "header").with(COUNT, terms.size()).component(viewer))
                .context(SafeText.msg(GLOSSARY + "context").component(viewer))
                .gap();
        if (terms.isEmpty()) {
            panel.line(SafeText.msg(GLOSSARY + "empty").component(viewer));
        }
        for (GlossaryTerm term : terms) {
            String mode = SafeText.msg("lingo_glossary.mode." + modeKey(term.mode())).plain(viewer);
            String key = GLOSSARY + (term.forces() ? "entry_force" : "entry");
            String replacement = Objects.requireNonNullElse(term.replacement(), "");
            panel.line(SafeText.component(SafeText.msg(key).with("mode", mode), viewer,
                    Map.of("term", term.term(), "replacement", replacement)));
        }
        panel.footer(GLOSSARY + "footer").send(sender);
    }

    private static @NotNull String modeKey(@NotNull GlossaryMode mode) {
        return mode == GlossaryMode.FORCE ? "force" : "keep";
    }
}
