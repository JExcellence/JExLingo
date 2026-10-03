package de.jexcellence.lingo.chat;

import de.jexcellence.lingo.api.TranslationResult;
import de.jexcellence.lingo.bedrock.BedrockFormBridge;
import de.jexcellence.lingo.settings.PlayerSettingsService;
import de.jexcellence.lingo.text.SafeText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Builds what a viewer sees for a translated line: a quiet {@code [DE » EN]} marker in front of the server's normal
 * chat line, a hover with the original text and, when corrections are enabled, a click that fills in
 * {@code /lingo suggest <id> }. Bedrock viewers (no hover) and players who chose so get the original as a second
 * line. All text comes from the translation files; player text is inserted with {@link SafeText}.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class TranslatedLineDecorator {

    private static final String KEY = "lingo.chat.";
    private static final String ORIGINAL = "original";
    private static final String FROM = "from";
    private static final String TO = "to";

    private final PlayerSettingsService settings;
    private final BedrockFormBridge bedrock;
    private final BooleanSupplier suggestionsEnabled;
    private final BooleanSupplier bedrockOriginalLine;

    /**
     * Creates the decorator.
     *
     * @param settings            player settings
     * @param bedrock             Floodgate bridge
     * @param suggestionsEnabled  whether corrections can be suggested (Premium)
     * @param bedrockOriginalLine whether Bedrock viewers get the original line
     */
    public TranslatedLineDecorator(@NotNull PlayerSettingsService settings, @NotNull BedrockFormBridge bedrock,
                                   @NotNull BooleanSupplier suggestionsEnabled,
                                   @NotNull BooleanSupplier bedrockOriginalLine) {
        this.settings = settings;
        this.bedrock = bedrock;
        this.suggestionsEnabled = suggestionsEnabled;
        this.bedrockOriginalLine = bedrockOriginalLine;
    }

    /**
     * Decorates a line the server's chat format already rendered with the translated text.
     *
     * @param rendered  the rendered chat line
     * @param result    the translation
     * @param viewer    the viewer
     * @param messageId the recent message id
     * @return the decorated line
     */
    public @NotNull Component decorate(@NotNull Component rendered, @NotNull TranslationResult result,
                                       @NotNull Player viewer, @NotNull String messageId) {
        Component line = Component.text()
                .append(marker(result, viewer))
                .append(Component.space())
                .append(rendered)
                .hoverEvent(HoverEvent.showText(hover(result, viewer)))
                .clickEvent(click(messageId))
                .build();
        return withOriginalLine(line, result, viewer);
    }

    /**
     * The extra line of the follow-up mode.
     *
     * @param senderName the sender's name
     * @param result     the translation
     * @param viewer     the viewer
     * @param messageId  the recent message id
     * @return the line
     */
    public @NotNull Component followUp(@NotNull String senderName, @NotNull TranslationResult result,
                                       @NotNull Player viewer, @NotNull String messageId) {
        Component body = SafeText.component(SafeText.msg(KEY + "follow_up")
                        .with(FROM, result.source().upper())
                        .with(TO, result.target().upper()),
                viewer, Map.of("name", senderName, "text", result.text()));
        Component line = body.hoverEvent(HoverEvent.showText(hover(result, viewer))).clickEvent(click(messageId));
        return withOriginalLine(line, result, viewer);
    }

    /**
     * The small button players in click mode get behind a line in another language.
     *
     * @param viewer    the viewer
     * @param messageId the recent message id
     * @return the button
     */
    public @NotNull Component translateButton(@NotNull Player viewer, @NotNull String messageId) {
        return SafeText.msg(KEY + "translate_button").component(viewer)
                .hoverEvent(HoverEvent.showText(SafeText.msg(KEY + "translate_hover").component(viewer)))
                .clickEvent(ClickEvent.runCommand("/lingo show " + messageId));
    }

    private @NotNull Component marker(@NotNull TranslationResult result, @NotNull Player viewer) {
        return SafeText.msg(KEY + "marker")
                .with(FROM, result.source().upper())
                .with(TO, result.target().upper())
                .component(viewer);
    }

    private @NotNull Component hover(@NotNull TranslationResult result, @NotNull Player viewer) {
        Component hover = SafeText.component(SafeText.msg(KEY + "hover.original")
                        .with(FROM, result.source().upper()),
                viewer, Map.of(ORIGINAL, result.original()));
        if (suggestionsEnabled.getAsBoolean()) {
            hover = hover.append(Component.newline()).append(SafeText.msg(KEY + "hover.suggest").component(viewer));
        }
        return hover;
    }

    private @Nullable ClickEvent click(@NotNull String messageId) {
        return suggestionsEnabled.getAsBoolean() ? ClickEvent.suggestCommand("/lingo suggest " + messageId + " ") : null;
    }

    private @NotNull Component withOriginalLine(@NotNull Component line, @NotNull TranslationResult result,
                                                @NotNull Player viewer) {
        boolean wanted = settings.get(viewer.getUniqueId()).showOriginal()
                || (bedrockOriginalLine.getAsBoolean() && bedrock.isBedrockPlayer(viewer));
        if (!wanted) {
            return line;
        }
        Component original = SafeText.component(SafeText.msg(KEY + "original_line")
                .with(FROM, result.source().upper()), viewer, Map.of(ORIGINAL, result.original()));
        return line.append(Component.newline()).append(original);
    }
}
