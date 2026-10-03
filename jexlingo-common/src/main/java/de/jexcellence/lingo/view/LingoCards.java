package de.jexcellence.lingo.view;

import de.jexcellence.jexplatform.gui.component.CardLore;
import de.jexcellence.jexplatform.gui.component.FilterHopperButton;
import de.jexcellence.jexplatform.utility.item.ItemBuilder;
import de.jexcellence.jextranslate.MessageBuilder;
import de.jexcellence.jextranslate.R18nManager;
import de.jexcellence.lingo.text.SafeText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The building blocks of every JExLingo card, matching the suite's card design: wrapped description paragraphs,
 * {@code Label | value} rows, value tones and item builders that hide vanilla tooltip noise. All text and colour
 * come from the {@code gui.common.*} translation keys; player text goes through {@link SafeText}.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LingoCards {

    /** Root of the shared GUI vocabulary. */
    public static final String COMMON = "gui.common.";

    private static final int WRAP_WIDTH = 34;
    private static final String PARAM_VALUE = "value";
    private static final String PARAM_TEXT = "text";

    private LingoCards() {
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
     * A non-italic item component for a key.
     *
     * @param viewer the viewer
     * @param key    the translation key
     * @return a non-italic item component
     */
    public static @NotNull Component ic(@Nullable Player viewer, @NotNull String key) {
        return ic(msg(key), viewer);
    }

    /**
     * A non-italic item component for a prepared builder.
     *
     * @param builder a prepared builder
     * @param viewer  the viewer
     * @return a non-italic item component
     */
    public static @NotNull Component ic(@NotNull MessageBuilder builder, @Nullable Player viewer) {
        return builder.itemComponent(viewer).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * The plain text of a key, for labels and wrapping.
     *
     * @param viewer the viewer
     * @param key    the translation key
     * @return the plain text of the key
     */
    public static @NotNull String text(@Nullable Player viewer, @NotNull String key) {
        return msg(key).text(viewer);
    }

    /**
     * Word-wraps a trusted sentence into muted lore lines.
     *
     * @param viewer    the viewer
     * @param plainText the sentence
     * @return the lines
     */
    public static @NotNull List<Component> paragraph(@Nullable Player viewer, @NotNull String plainText) {
        List<Component> lines = new ArrayList<>();
        for (String line : CardLore.wrap(plainText, WRAP_WIDTH)) {
            lines.add(ic(msg(COMMON + "card.line").with(PARAM_TEXT, line), viewer));
        }
        return lines;
    }

    /**
     * Word-wraps player-written text into muted lore lines without parsing it.
     *
     * @param viewer   the viewer
     * @param userText the player text
     * @return the lines
     */
    public static @NotNull List<Component> userParagraph(@Nullable Player viewer, @NotNull String userText) {
        List<Component> lines = new ArrayList<>();
        for (String line : CardLore.wrap(userText, WRAP_WIDTH)) {
            lines.add(SafeText.item(msg(COMMON + "card.line"), viewer, Map.of(PARAM_TEXT, line)));
        }
        return lines;
    }

    /**
     * Word-wraps the text of a translation key into muted lore lines.
     *
     * @param viewer the viewer
     * @param key    the translation key of a sentence
     * @return the wrapped lines
     */
    public static @NotNull List<Component> paragraphOf(@Nullable Player viewer, @NotNull String key) {
        return paragraph(viewer, text(viewer, key));
    }

    /**
     * A {@code Label | value} row whose label is a translation key.
     *
     * @param viewer    the viewer
     * @param labelKey  the label's translation key
     * @param valueMini the value as MiniMessage fragment
     * @return a {@code Label | value} row
     */
    public static @NotNull Component rowOf(@Nullable Player viewer, @NotNull String labelKey,
                                           @NotNull String valueMini) {
        return ic(msg(COMMON + "card.row").with("label", text(viewer, labelKey)).with(PARAM_VALUE, valueMini),
                viewer);
    }

    /**
     * A value in one of the semantic tones.
     *
     * @param viewer the viewer
     * @param tone   {@code plain}, {@code accent}, {@code ok}, {@code bad}, {@code warn} or {@code muted}
     * @param raw    trusted value text
     * @return the value as MiniMessage fragment
     */
    public static @NotNull String tone(@Nullable Player viewer, @NotNull String tone, @NotNull String raw) {
        return msg(COMMON + "value." + tone).with(PARAM_VALUE, raw).miniMessage(viewer);
    }

    /**
     * A highlighted plain value.
     *
     * @param viewer the viewer
     * @param raw    trusted value text
     * @return a highlighted value
     */
    public static @NotNull String value(@Nullable Player viewer, @NotNull String raw) {
        return tone(viewer, "plain", raw);
    }

    /**
     * A card with name and lore; vanilla attribute and enchant lines are hidden.
     *
     * @param base the base item
     * @param name the name
     * @param lore the lore
     * @return the card
     */
    public static @NotNull ItemStack card(@NotNull ItemStack base, @NotNull Component name,
                                          @NotNull List<Component> lore) {
        return ItemBuilder.from(base).name(name).lore(lore)
                .flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS)
                .build();
    }

    /**
     * A card on a plain material.
     *
     * @param icon the material
     * @param name the name
     * @param lore the lore
     * @return the card
     */
    public static @NotNull ItemStack card(@NotNull Material icon, @NotNull Component name,
                                          @NotNull List<Component> lore) {
        return card(new ItemStack(icon), name, lore);
    }

    /**
     * Adds the enchantment glint without an enchantment line.
     *
     * @param stack the item
     * @return the same item
     */
    public static @NotNull ItemStack glint(@NotNull ItemStack stack) {
        stack.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        return stack;
    }

    /**
     * A card with a wrapped description only, for empty or error states.
     *
     * @param viewer  the viewer
     * @param icon    the material
     * @param keyBase key with {@code .name} and {@code .description}
     * @return the card
     */
    public static @NotNull ItemStack notice(@Nullable Player viewer, @NotNull Material icon, @NotNull String keyBase) {
        return card(new ItemStack(icon), ic(viewer, keyBase + ".name"),
                CardLore.create().block(paragraphOf(viewer, keyBase + ".description")).build());
    }

    /**
     * The shared filter button: a hopper minecart named "Filter" with a "Show" block that marks the active option
     * with a filled dot and the others with an empty one, and the left/right-click hint. The caller tags it.
     *
     * @param viewer the viewer
     * @param labels translated option labels in cycle order
     * @param active index of the active option
     * @return the filter item
     */
    public static @NotNull ItemStack filter(@Nullable Player viewer, @NotNull List<String> labels, int active) {
        return card(FilterHopperButton.ICON, ic(viewer, COMMON + "filter.name"),
                CardLore.create()
                        .section(ic(viewer, COMMON + "filter.title"), options(viewer, labels, active))
                        .block(List.of(ic(viewer, COMMON + "filter.action")))
                        .build());
    }

    /**
     * The active / inactive option dots of the shared filter style.
     *
     * @param viewer the viewer
     * @param labels translated option labels
     * @param active index of the active option
     * @return one line per option
     */
    public static @NotNull List<Component> options(@Nullable Player viewer, @NotNull List<String> labels,
                                                   int active) {
        List<Component> lines = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            String key = i == active ? COMMON + "filter.option-active" : COMMON + "filter.option";
            lines.add(ic(msg(key).with("name", labels.get(i)), viewer));
        }
        return lines;
    }
}
