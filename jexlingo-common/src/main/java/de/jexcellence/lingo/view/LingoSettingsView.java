package de.jexcellence.lingo.view;

import de.jexcellence.jexplatform.gui.component.CardLore;
import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.settings.PlayerLanguageSettings;
import de.jexcellence.lingo.settings.PlayerSettingsService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /lingo}: the player's translation settings. Header at 4 lists every setting as a {@code Label | Value}
 * row; the body has one card per setting in the shared filter style (filled dot for the active value, left-click
 * next, right-click back). Changes apply at once and are stored off the server thread.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LingoSettingsView extends LingoBaseView {

    private static final String KEY = "lingo_settings.";
    private static final String OPTION = KEY + "option.";
    private static final String VALUE = KEY + "value.";
    private static final String TAG_PREFIX = "setting:";
    private static final String ENABLED = "enabled";
    private static final String DISABLED = "disabled";
    private static final String AUTO = "auto";

    private enum Setting {
        LANGUAGE(Material.WRITABLE_BOOK),
        INCOMING(Material.SPYGLASS),
        OUTGOING(Material.FEATHER),
        ORIGINAL(Material.PAPER);

        private final Material icon;

        Setting(@NotNull Material icon) {
            this.icon = icon;
        }

        @NotNull String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private final Holder holder = new Holder();
    private final PlayerSettingsService settings;
    private final LanguageResolver resolver;
    private final PlatformScheduler scheduler;

    /**
     * Creates the view.
     *
     * @param settings  player settings
     * @param resolver  language resolver
     * @param scheduler platform scheduler
     */
    public LingoSettingsView(@NotNull PlayerSettingsService settings, @NotNull LanguageResolver resolver,
                             @NotNull PlatformScheduler scheduler) {
        this.settings = settings;
        this.resolver = resolver;
        this.scheduler = scheduler;
    }

    @Override protected @NotNull String title() { return KEY + "title"; }
    @Override protected int rows() { return 6; }
    @Override protected @NotNull InventoryHolder holder() { return holder; }

    @Override
    protected void render(@NotNull Inventory inv, @NotNull Player viewer) {
        navBar(inv, viewer, null);
        PlayerLanguageSettings current = settings.get(viewer.getUniqueId());
        inv.setItem(SLOT_HEADER, header(viewer, current));
        List<ItemStack> cards = new ArrayList<>();
        for (Setting setting : Setting.values()) {
            cards.add(card(viewer, setting, current));
        }
        int[] slots = LingoLayout.hub(cards.size());
        for (int i = 0; i < slots.length; i++) {
            inv.setItem(slots[i], cards.get(i));
        }
    }

    @Override
    protected void onClick(@NotNull Player viewer, int slot, @NotNull ItemStack clicked) {
        onClick(viewer, slot, clicked, ClickType.LEFT);
    }

    @Override
    protected void onClick(@NotNull Player viewer, int slot, @NotNull ItemStack clicked, @NotNull ClickType type) {
        String tag = tagOf(clicked);
        if (tag == null || !tag.startsWith(TAG_PREFIX)) {
            return;
        }
        Setting setting = Setting.valueOf(tag.substring(TAG_PREFIX.length()));
        boolean forward = !type.isRightClick();
        settings.update(viewer.getUniqueId(), current -> cycle(setting, current, forward))
                .thenRun(() -> scheduler.runAtEntity(viewer, () -> {
                    if (isViewing(viewer)) {
                        rerender(viewer);
                    }
                }));
    }

    private @NotNull ItemStack header(@NotNull Player viewer, @NotNull PlayerLanguageSettings current) {
        List<Component> rows = new ArrayList<>();
        for (Setting setting : Setting.values()) {
            rows.add(LingoCards.rowOf(viewer, OPTION + setting.id() + ".label", valueText(viewer, setting, current)));
        }
        rows.add(LingoCards.rowOf(viewer, KEY + "header.reading", LingoCards.tone(viewer, "accent",
                languageName(viewer, resolver.resolve(viewer)))));
        CardLore lore = CardLore.create()
                .block(LingoCards.paragraphOf(viewer, KEY + "header.description"))
                .section(LingoCards.ic(viewer, KEY + "header.section"), rows);
        return LingoCards.card(Material.COMPARATOR, LingoCards.ic(viewer, KEY + "header.name"), lore.build());
    }

    private @NotNull ItemStack card(@NotNull Player viewer, @NotNull Setting setting,
                                    @NotNull PlayerLanguageSettings current) {
        String base = OPTION + setting.id();
        List<String> labels = choiceLabels(viewer, setting);
        CardLore lore = CardLore.create()
                .block(LingoCards.paragraphOf(viewer, base + ".description"))
                .section(LingoCards.ic(viewer, KEY + "card.section"),
                        LingoCards.options(viewer, labels, activeIndex(setting, current)))
                .block(List.of(LingoCards.ic(viewer, LingoCards.COMMON + "filter.action")));
        ItemStack item = LingoCards.card(setting.icon,
                LingoCards.ic(LingoCards.msg(base + ".name").with("value", valueText(viewer, setting, current)),
                        viewer),
                lore.build());
        tag(item, TAG_PREFIX + setting.name());
        return isActive(setting, current) ? LingoCards.glint(item) : item;
    }

    private @NotNull List<String> choiceLabels(@NotNull Player viewer, @NotNull Setting setting) {
        if (setting != Setting.LANGUAGE) {
            return List.of(LingoCards.text(viewer, VALUE + ENABLED), LingoCards.text(viewer, VALUE + DISABLED));
        }
        List<String> labels = new ArrayList<>();
        labels.add(LingoCards.text(viewer, VALUE + AUTO));
        for (LanguageCode language : resolver.languages().enabled()) {
            labels.add(languageName(viewer, language));
        }
        return labels;
    }

    private int activeIndex(@NotNull Setting setting, @NotNull PlayerLanguageSettings current) {
        if (setting == Setting.LANGUAGE) {
            LanguageCode language = current.language();
            return language == null ? 0 : resolver.languages().enabled().indexOf(language) + 1;
        }
        return isActive(setting, current) ? 0 : 1;
    }

    private static boolean isActive(@NotNull Setting setting, @NotNull PlayerLanguageSettings current) {
        return switch (setting) {
            case LANGUAGE -> current.language() != null;
            case INCOMING -> current.translateIncoming();
            case OUTGOING -> current.translateOutgoing();
            case ORIGINAL -> current.showOriginal();
            default -> throw new IllegalStateException("Unexpected setting: " + setting);
        };
    }

    private @NotNull PlayerLanguageSettings cycle(@NotNull Setting setting, @NotNull PlayerLanguageSettings current,
                                                  boolean forward) {
        return switch (setting) {
            case LANGUAGE -> current.withLanguage(nextLanguage(current.language(), forward));
            case INCOMING -> current.withIncoming(!current.translateIncoming());
            case OUTGOING -> current.withOutgoing(!current.translateOutgoing());
            case ORIGINAL -> current.withShowOriginal(!current.showOriginal());
            default -> throw new IllegalStateException("Unexpected setting: " + setting);
        };
    }

    private @Nullable LanguageCode nextLanguage(@Nullable LanguageCode current, boolean forward) {
        List<LanguageCode> enabled = resolver.languages().enabled();
        int size = enabled.size() + 1;
        int index = current == null ? 0 : enabled.indexOf(current) + 1;
        int next = Math.floorMod(index + (forward ? 1 : -1), size);
        return next == 0 ? null : enabled.get(next - 1);
    }

    private @NotNull String valueText(@NotNull Player viewer, @NotNull Setting setting,
                                      @NotNull PlayerLanguageSettings current) {
        if (setting == Setting.LANGUAGE) {
            LanguageCode language = current.language();
            String label = language == null ? LingoCards.text(viewer, VALUE + AUTO) : languageName(viewer, language);
            return LingoCards.tone(viewer, language == null ? "muted" : "accent", label);
        }
        boolean active = isActive(setting, current);
        return LingoCards.tone(viewer, active ? "ok" : "muted",
                LingoCards.text(viewer, VALUE + (active ? ENABLED : DISABLED)));
    }

    /**
     * The display name of a language, from {@code lingo.language.<code>} or the upper-case code.
     *
     * @param viewer   the viewer
     * @param language the language
     * @return the name
     */
    public static @NotNull String languageName(@Nullable Player viewer, @NotNull LanguageCode language) {
        var builder = LingoCards.msg("lingo.language." + language.code());
        return builder.exists(viewer) ? builder.text(viewer) : language.upper();
    }

    private static final class Holder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException();
        }
    }
}
