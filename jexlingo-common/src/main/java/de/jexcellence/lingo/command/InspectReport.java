package de.jexcellence.lingo.command;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.settings.IncomingMode;
import de.jexcellence.lingo.settings.PlayerLanguageSettings;
import de.jexcellence.lingo.text.LingoPanel;
import de.jexcellence.lingo.text.SafeText;
import de.jexcellence.lingo.view.LingoSettingsView;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * {@code /lingo inspect <player>}: the stored choices and, for an online player, what the resolver makes of them, as
 * one chat panel. The player name is inserted as plain text.
 *
 * @author JExcellence
 * @since 0.4.2
 */
final class InspectReport {

    private static final String KEY = "lingo.ops.inspect.";
    private static final String LABEL = KEY + "label.";

    private InspectReport() {
    }

    /**
     * Sends the report.
     *
     * @param sender   the receiver
     * @param name     the inspected player's name
     * @param loaded   the stored settings
     * @param online   the player when online, for the resolved languages
     * @param resolver the language resolver
     */
    static void send(@NotNull CommandSender sender, @NotNull String name, @NotNull PlayerLanguageSettings loaded,
                     @Nullable Player online, @NotNull LanguageResolver resolver) {
        LingoPanel panel = LingoPanel.of(sender);
        Player viewer = panel.viewer();
        panel.header(SafeText.component(SafeText.msg(KEY + "header"), viewer, Map.of("name", name)))
                .context(SafeText.msg(KEY + (online == null ? "context_offline" : "context_online"))
                        .component(viewer))
                .section(KEY + "section.choices")
                .row(LABEL + "reading_choice", choice(viewer, loaded.language()), choiceTone(loaded.language()))
                .row(LABEL + "writing_choice", choice(viewer, loaded.writeLanguage()),
                        choiceTone(loaded.writeLanguage()))
                .row(LABEL + "incoming", LingoSettingsView.incomingName(viewer, loaded.incoming()),
                        loaded.incoming() == IncomingMode.OFF ? LingoPanel.Tone.MUTED : LingoPanel.Tone.ACCENT);
        flagRow(panel, "outgoing", loaded.translateOutgoing());
        flagRow(panel, "original", loaded.showOriginal());
        flagRow(panel, "suggestions", !loaded.suggestionsBlocked());
        if (online != null) {
            panel.section(KEY + "section.resolved")
                    .row(LABEL + "client", online.locale().toString())
                    .row(LABEL + "reads", LingoSettingsView.languageName(viewer, resolver.resolve(online)),
                            LingoPanel.Tone.ACCENT)
                    .row(LABEL + "writes", LingoSettingsView.languageName(viewer, resolver.resolveWriting(online)),
                            LingoPanel.Tone.ACCENT);
        }
        panel.send(sender);
    }

    private static void flagRow(@NotNull LingoPanel panel, @NotNull String label, boolean value) {
        String text = SafeText.msg("lingo_settings.value." + (value ? "enabled" : "disabled")).plain(panel.viewer());
        panel.row(LABEL + label, text, value ? LingoPanel.Tone.OK : LingoPanel.Tone.MUTED);
    }

    private static @NotNull String choice(@Nullable Player viewer, @Nullable LanguageCode language) {
        return language == null ? SafeText.msg("lingo_settings.value.auto").plain(viewer)
                : LingoSettingsView.languageName(viewer, language);
    }

    private static @NotNull LingoPanel.Tone choiceTone(@Nullable LanguageCode language) {
        return language == null ? LingoPanel.Tone.MUTED : LingoPanel.Tone.ACCENT;
    }
}
