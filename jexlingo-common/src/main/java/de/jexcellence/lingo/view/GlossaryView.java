package de.jexcellence.lingo.view;

import de.jexcellence.jexplatform.gui.component.CardLore;
import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.lingo.LingoEdition;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.glossary.GlossaryMode;
import de.jexcellence.lingo.glossary.GlossaryService;
import de.jexcellence.lingo.glossary.GlossaryTerm;
import de.jexcellence.lingo.text.SafeText;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /lingo glossary}: every glossary term as a card with its mode, replacement and languages. Right-click
 * removes a term. New terms are added with {@code /lingo glossary add}.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class GlossaryView extends LingoBaseView {

    private static final String KEY = "lingo_glossary.";
    private static final String TAG_TERM = "term:";
    private static final String ALL = "all";

    private final Holder holder = new Holder();
    private final GlossaryService glossary;
    private final LingoEdition edition;
    private final PlatformScheduler scheduler;
    private final Map<UUID, Integer> pages = new ConcurrentHashMap<>();

    /**
     * Creates the view.
     *
     * @param glossary  the glossary
     * @param edition   the edition, for the term limit
     * @param scheduler platform scheduler
     */
    public GlossaryView(@NotNull GlossaryService glossary, @NotNull LingoEdition edition,
                        @NotNull PlatformScheduler scheduler) {
        this.glossary = glossary;
        this.edition = edition;
        this.scheduler = scheduler;
    }

    @Override protected @NotNull String title() { return KEY + "title"; }
    @Override protected int rows() { return 6; }
    @Override protected @NotNull InventoryHolder holder() { return holder; }

    @Override
    protected void render(@NotNull Inventory inv, @NotNull Player viewer) {
        navBar(inv, viewer, null);
        List<GlossaryTerm> terms = glossary.terms();
        inv.setItem(SLOT_HEADER, header(viewer, terms.size()));
        if (terms.isEmpty()) {
            inv.setItem(SLOT_CENTER, LingoCards.notice(viewer, Material.BOOK, KEY + "empty"));
            return;
        }
        int shown = renderPage(inv, viewer, terms, pages.getOrDefault(viewer.getUniqueId(), 0),
                (index, term) -> card(viewer, term));
        pages.put(viewer.getUniqueId(), shown);
    }

    @Override
    protected void onClick(@NotNull Player viewer, int slot, @NotNull ItemStack clicked) {
        onClick(viewer, slot, clicked, ClickType.LEFT);
    }

    @Override
    protected void onClick(@NotNull Player viewer, int slot, @NotNull ItemStack clicked, @NotNull ClickType type) {
        String tag = tagOf(clicked);
        if (tag == null) {
            return;
        }
        if (TAG_PAGE_PREV.equals(tag) || TAG_PAGE_NEXT.equals(tag)) {
            pages.merge(viewer.getUniqueId(), TAG_PAGE_NEXT.equals(tag) ? 1 : -1, Integer::sum);
            rerender(viewer);
        } else if (tag.startsWith(TAG_TERM) && type.isRightClick()) {
            long id = Long.parseLong(tag.substring(TAG_TERM.length()));
            glossary.removeById(id).thenAccept(removed -> scheduler.runAtEntity(viewer, () -> {
                if (Boolean.TRUE.equals(removed)) {
                    SafeText.msg("lingo.glossary.removed_one").prefix().send(viewer);
                }
                if (isViewing(viewer)) {
                    rerender(viewer);
                }
            }));
        }
    }

    @Override
    protected void forget(@NotNull UUID viewer) {
        pages.remove(viewer);
    }

    private @NotNull ItemStack header(@NotNull Player viewer, int count) {
        String limit = edition.maxGlossaryTerms() <= 0
                ? LingoCards.value(viewer, Integer.toString(count))
                : LingoCards.value(viewer, count + " / " + edition.maxGlossaryTerms());
        CardLore lore = CardLore.create()
                .block(LingoCards.paragraphOf(viewer, KEY + "header.description"))
                .section(LingoCards.ic(viewer, KEY + "header.section"),
                        List.of(LingoCards.rowOf(viewer, KEY + "label.terms", limit)));
        return LingoCards.card(Material.KNOWLEDGE_BOOK, LingoCards.ic(viewer, KEY + "header.name"), lore.build());
    }

    private @NotNull ItemStack card(@NotNull Player viewer, @NotNull GlossaryTerm term) {
        Component name = SafeText.item(LingoCards.msg(KEY + "card.name"), viewer, Map.of("term", term.term()));
        List<Component> rows = new ArrayList<>();
        rows.add(LingoCards.rowOf(viewer, KEY + "label.mode",
                LingoCards.tone(viewer, "accent", LingoCards.text(viewer, KEY + "mode." + modeKey(term.mode())))));
        if (term.forces()) {
            rows.add(SafeText.item(LingoCards.msg(KEY + "card.replacement"), viewer,
                    Map.of("replacement", term.replacement())));
        }
        rows.add(LingoCards.rowOf(viewer, KEY + "label.languages", LingoCards.value(viewer,
                languageLabel(viewer, term.source()) + " » " + languageLabel(viewer, term.target()))));
        CardLore lore = CardLore.create()
                .section(LingoCards.ic(viewer, KEY + "card.section"), rows)
                .block(List.of(LingoCards.ic(viewer, KEY + "card.remove")));
        ItemStack item = LingoCards.card(term.mode() == GlossaryMode.FORCE ? Material.NAME_TAG : Material.PAPER,
                name, lore.build());
        tag(item, TAG_TERM + term.id());
        return item;
    }

    private static @NotNull String modeKey(@NotNull GlossaryMode mode) {
        return mode == GlossaryMode.FORCE ? "force" : "keep";
    }

    private static @NotNull String languageLabel(@Nullable Player viewer, @Nullable LanguageCode language) {
        return language == null ? LingoCards.text(viewer, KEY + "language." + ALL) : language.upper();
    }

    private static final class Holder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException();
        }
    }
}
