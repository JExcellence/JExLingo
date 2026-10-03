package de.jexcellence.lingo.text;

import de.jexcellence.jextranslate.MessageBuilder;
import de.jexcellence.jextranslate.R18nManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * Puts player-written text into translated messages without letting it be read as MiniMessage. JExTranslate
 * replaces {@code {placeholders}} before parsing, so a chat line such as {@code <click:run_command:/op me>} would
 * become markup. Here every user value is first replaced by an inert marker, the message is parsed, and the marker
 * is swapped for a plain {@link Component#text(String)} that inherits the colour of its position in the template.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class SafeText {

    private static final String MARKER_PREFIX = "JEXLINGOUSERTEXT";

    private SafeText() {
    }

    /**
     * The translation builder for a key.
     *
     * @param key the translation key
     * @return the builder
     */
    public static @NotNull MessageBuilder msg(@NotNull String key) {
        return R18nManager.getInstance().msg(key);
    }

    /**
     * A chat component with user text inserted safely.
     *
     * @param builder    the prepared builder (trusted placeholders already set)
     * @param viewer     the viewer, for the locale
     * @param userValues placeholder name to raw user text
     * @return the component
     */
    public static @NotNull Component component(@NotNull MessageBuilder builder, @Nullable Player viewer,
                                               @NotNull Map<String, String> userValues) {
        userValues.keySet().forEach(name -> builder.with(name, marker(name)));
        return insert(builder.component(viewer), userValues);
    }

    /**
     * A non-italic item component with user text inserted safely, for item names and lore.
     *
     * @param builder    the prepared builder
     * @param viewer     the viewer
     * @param userValues placeholder name to raw user text
     * @return the component
     */
    public static @NotNull Component item(@NotNull MessageBuilder builder, @Nullable Player viewer,
                                          @NotNull Map<String, String> userValues) {
        userValues.keySet().forEach(name -> builder.with(name, marker(name)));
        return insert(builder.itemComponent(viewer), userValues).decoration(TextDecoration.ITALIC, false);
    }

    private static @NotNull Component insert(@NotNull Component parsed, @NotNull Map<String, String> userValues) {
        Component result = parsed;
        for (Map.Entry<String, String> value : userValues.entrySet()) {
            String marker = marker(value.getKey());
            Component replacement = Component.text(value.getValue());
            result = result.replaceText(config -> config.matchLiteral(marker).replacement(replacement));
        }
        return result;
    }

    private static @NotNull String marker(@NotNull String name) {
        return MARKER_PREFIX + name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }
}
