package de.jexcellence.lingo.command;

import com.raindropcentral.commands.v2.CommandContext;
import com.raindropcentral.commands.v2.CommandHandler;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.bedrock.LingoBedrockForms;
import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.learning.SuggestionService;
import de.jexcellence.lingo.settings.PlayerLanguageSettings;
import de.jexcellence.lingo.settings.PlayerSettingsService;
import de.jexcellence.lingo.text.SafeText;
import de.jexcellence.lingo.text.SuggestionMessages;
import de.jexcellence.lingo.view.LingoSettingsView;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The player part of {@code /lingo}: the settings menu, language and switches, suggestions and help.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LingoCommandHandler {

    private static final String ROOT = "lingo";
    private static final String STATE = "state";
    private static final String PLAYERS_ONLY = "lingo.error.players_only";
    private static final String SETTINGS = "lingo.settings.";

    private final PlayerSettingsService settings;
    private final LanguageResolver resolver;
    private final LingoSettingsView settingsView;
    private final Replies replies;
    private @Nullable LingoBedrockForms forms;
    private @Nullable SuggestionService suggestions;

    /**
     * Creates the handler.
     *
     * @param settings     player settings
     * @param resolver     language resolver
     * @param settingsView settings menu
     * @param replies      reply helper
     */
    public LingoCommandHandler(@NotNull PlayerSettingsService settings, @NotNull LanguageResolver resolver,
                               @NotNull LingoSettingsView settingsView, @NotNull Replies replies) {
        this.settings = settings;
        this.resolver = resolver;
        this.settingsView = settingsView;
        this.replies = replies;
    }

    /**
     * Wires the Bedrock forms when Floodgate is present.
     *
     * @param value the forms
     */
    public void setForms(@Nullable LingoBedrockForms value) {
        this.forms = value;
    }

    /**
     * Wires the suggestion service (Premium).
     *
     * @param value the service
     */
    public void setSuggestions(@Nullable SuggestionService value) {
        this.suggestions = value;
    }

    /**
     * Returns the handlers by tree path.
     *
     * @return the handlers by tree path
     */
    public @NotNull Map<String, CommandHandler> handlerMap() {
        return Map.ofEntries(
                Map.entry(ROOT, this::onRoot),
                Map.entry(ROOT + ".help", this::onHelp),
                Map.entry(ROOT + ".lang", this::onLanguage),
                Map.entry(ROOT + ".incoming", ctx -> onSwitch(ctx, "incoming", PlayerLanguageSettings::withIncoming)),
                Map.entry(ROOT + ".outgoing", ctx -> onSwitch(ctx, "outgoing", PlayerLanguageSettings::withOutgoing)),
                Map.entry(ROOT + ".original", ctx -> onSwitch(ctx, "original",
                        PlayerLanguageSettings::withShowOriginal)),
                Map.entry(ROOT + ".suggest", this::onSuggest));
    }

    private void onRoot(@NotNull CommandContext ctx) {
        Optional<Player> player = ctx.asPlayer();
        if (player.isEmpty()) {
            onHelp(ctx);
        } else if (forms != null && forms.isBedrock(player.get())) {
            forms.openSettings(player.get());
        } else {
            settingsView.open(player.get());
        }
    }

    private void onHelp(@NotNull CommandContext ctx) {
        SafeText.msg("lingo.help").send(ctx.sender());
    }

    private void onLanguage(@NotNull CommandContext ctx) {
        Player player = ctx.asPlayer().orElse(null);
        if (player == null) {
            replies.send(ctx.sender(), PLAYERS_ONLY);
            return;
        }
        String choice = ctx.require("language", String.class);
        LanguageCode language = LingoArgumentTypes.AUTO.equals(choice) ? null : LanguageCode.of(choice);
        settings.update(player.getUniqueId(), current -> current.withLanguage(language)).thenRun(() -> {
            String name = language == null
                    ? SafeText.msg("lingo_settings.value.auto").text(player)
                    : LingoSettingsView.languageName(player, language);
            replies.send(player, SafeText.msg(SETTINGS + "language_set")
                    .with("language", name)
                    .with("reading", resolver.resolve(player).upper()));
        });
    }

    private void onSwitch(@NotNull CommandContext ctx, @NotNull String option,
                          @NotNull SwitchSetter setter) {
        Player player = ctx.asPlayer().orElse(null);
        if (player == null) {
            replies.send(ctx.sender(), PLAYERS_ONLY);
            return;
        }
        boolean enabled = Boolean.TRUE.equals(ctx.require(STATE, Boolean.class));
        UnaryOperator<PlayerLanguageSettings> change = current -> setter.apply(current, enabled);
        settings.update(player.getUniqueId(), change).thenRun(() -> replies.send(player,
                SETTINGS + option + (enabled ? ".enabled" : ".disabled")));
    }

    private void onSuggest(@NotNull CommandContext ctx) {
        Player player = ctx.asPlayer().orElse(null);
        if (player == null) {
            replies.send(ctx.sender(), PLAYERS_ONLY);
            return;
        }
        SuggestionService service = suggestions;
        if (service == null) {
            replies.send(player, "lingo.suggest.unavailable");
            return;
        }
        Optional<String> messageId = ctx.get("message", String.class);
        Optional<String> text = ctx.get("text", String.class);
        if (messageId.isEmpty()) {
            if (forms != null && forms.isBedrock(player)) {
                forms.openSuggest(player);
            } else {
                replies.send(player, "lingo.suggest.how");
            }
            return;
        }
        if (text.isEmpty() || text.get().isBlank()) {
            replies.send(player, "lingo.suggest.missing_text");
            return;
        }
        service.suggest(player, messageId.get(), text.get())
                .thenAccept(result -> replies.run(player, () -> SuggestionMessages.send(player, result)));
    }

    @FunctionalInterface
    private interface SwitchSetter {
        @NotNull PlayerLanguageSettings apply(@NotNull PlayerLanguageSettings settings, boolean enabled);
    }
}
