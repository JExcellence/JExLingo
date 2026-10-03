package de.jexcellence.lingo.view;

import de.jexcellence.jexplatform.gui.component.CardLore;
import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.lingo.learning.MemoryEntry;
import de.jexcellence.lingo.learning.TranslationMemoryService;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /lingo review}: suggestions waiting for staff review (Premium). One card per suggestion with the original
 * and the suggested text; left-click approves, right-click rejects. Approved entries answer every identical line
 * from then on.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class SuggestionReviewView extends LingoBaseView {

    private static final String KEY = "lingo_review.";
    private static final String TAG_ENTRY = "entry:";

    private final Holder holder = new Holder();
    private final TranslationMemoryService memory;
    private final PlatformScheduler scheduler;
    private final Map<UUID, List<MemoryEntry>> loaded = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pages = new ConcurrentHashMap<>();

    /**
     * Creates the view.
     *
     * @param memory    the translation memory
     * @param scheduler platform scheduler
     */
    public SuggestionReviewView(@NotNull TranslationMemoryService memory, @NotNull PlatformScheduler scheduler) {
        this.memory = memory;
        this.scheduler = scheduler;
    }

    @Override protected @NotNull String title() { return KEY + "title"; }
    @Override protected int rows() { return 6; }
    @Override protected @NotNull InventoryHolder holder() { return holder; }

    @Override
    public void open(@NotNull Player viewer) {
        super.open(viewer);
        reload(viewer);
    }

    @Override
    protected void render(@NotNull Inventory inv, @NotNull Player viewer) {
        navBar(inv, viewer, null);
        List<MemoryEntry> entries = loaded.get(viewer.getUniqueId());
        inv.setItem(SLOT_HEADER, header(viewer, entries));
        if (entries == null) {
            return;
        }
        if (entries.isEmpty()) {
            inv.setItem(SLOT_CENTER, LingoCards.notice(viewer, Material.BOOK, KEY + "empty"));
            return;
        }
        int shown = renderPage(inv, viewer, entries, pages.getOrDefault(viewer.getUniqueId(), 0),
                (index, entry) -> card(viewer, entry));
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
            int delta = TAG_PAGE_NEXT.equals(tag) ? 1 : -1;
            pages.merge(viewer.getUniqueId(), delta, Integer::sum);
            rerender(viewer);
        } else if (tag.startsWith(TAG_ENTRY)) {
            long id = Long.parseLong(tag.substring(TAG_ENTRY.length()));
            decide(viewer, id, !type.isRightClick());
        }
    }

    @Override
    protected void forget(@NotNull UUID viewer) {
        loaded.remove(viewer);
        pages.remove(viewer);
    }

    private void decide(@NotNull Player viewer, long id, boolean approve) {
        CompletableFuture<Boolean> decision = approve
                ? memory.approve(id, null).thenApply(Optional::isPresent)
                : memory.reject(id);
        decision.thenAccept(done -> scheduler.runAtEntity(viewer, () -> {
            String key = approve ? "lingo.review.approved" : "lingo.review.rejected";
            SafeText.msg(Boolean.TRUE.equals(done) ? key : "lingo.review.not_pending")
                    .with("id", id).prefix().send(viewer);
            reload(viewer);
        }));
    }

    private void reload(@NotNull Player viewer) {
        memory.pending().thenAccept(entries -> scheduler.runAtEntity(viewer, () -> {
            loaded.put(viewer.getUniqueId(), entries);
            if (isViewing(viewer)) {
                rerender(viewer);
            }
        }));
    }

    private @NotNull ItemStack header(@NotNull Player viewer, @Nullable List<MemoryEntry> entries) {
        String count = entries == null
                ? LingoCards.msg(LingoCards.COMMON + "value.loading").miniMessage(viewer)
                : LingoCards.value(viewer, Integer.toString(entries.size()));
        CardLore lore = CardLore.create()
                .block(LingoCards.paragraphOf(viewer, KEY + "header.description"))
                .section(LingoCards.ic(viewer, KEY + "header.section"),
                        List.of(LingoCards.rowOf(viewer, KEY + "label.pending", count)));
        return LingoCards.card(Material.WRITABLE_BOOK, LingoCards.ic(viewer, KEY + "header.name"), lore.build());
    }

    private @NotNull ItemStack card(@NotNull Player viewer, @NotNull MemoryEntry entry) {
        Component name = LingoCards.ic(LingoCards.msg(KEY + "card.name")
                .with("id", entry.id())
                .with("from", entry.pair().source().upper())
                .with("to", entry.pair().target().upper()), viewer);
        CardLore lore = CardLore.create()
                .section(LingoCards.ic(viewer, KEY + "card.original"), LingoCards.userParagraph(viewer,
                        entry.sourceText()))
                .section(LingoCards.ic(viewer, KEY + "card.suggestion"), LingoCards.userParagraph(viewer,
                        entry.targetText()))
                .block(List.of(LingoCards.ic(viewer, KEY + "card.approve"), LingoCards.ic(viewer, KEY + "card.reject")));
        ItemStack item = LingoCards.card(Material.PAPER, name, lore.build());
        tag(item, TAG_ENTRY + entry.id());
        return item;
    }

    private static final class Holder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException();
        }
    }
}
