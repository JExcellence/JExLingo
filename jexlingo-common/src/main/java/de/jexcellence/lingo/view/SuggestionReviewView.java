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
 * from then on. The filter at slot 8 switches to the approved corrections, where right-click takes one back.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class SuggestionReviewView extends LingoBaseView {

    private static final String KEY = "lingo_review.";
    private static final String TAG_ENTRY = "entry:";
    private static final String TAG_FILTER = "filter";

    private enum Mode {
        PENDING,
        APPROVED
    }

    private final Holder holder = new Holder();
    private final TranslationMemoryService memory;
    private final PlatformScheduler scheduler;
    private final Map<UUID, List<MemoryEntry>> loaded = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pages = new ConcurrentHashMap<>();
    private final Map<UUID, Mode> modes = new ConcurrentHashMap<>();

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
        Mode mode = mode(viewer.getUniqueId());
        List<MemoryEntry> entries = loaded.get(viewer.getUniqueId());
        inv.setItem(SLOT_HEADER, header(viewer, entries, mode));
        ItemStack filter = LingoCards.filter(viewer, List.of(LingoCards.text(viewer, KEY + "filter.pending"),
                LingoCards.text(viewer, KEY + "filter.approved")), mode.ordinal());
        tag(filter, TAG_FILTER);
        inv.setItem(SLOT_FILTER, filter);
        if (entries == null) {
            return;
        }
        if (entries.isEmpty()) {
            inv.setItem(SLOT_CENTER, LingoCards.notice(viewer, Material.BOOK,
                    mode == Mode.PENDING ? KEY + "empty" : KEY + "empty-approved"));
            return;
        }
        int shown = renderPage(inv, viewer, entries, pages.getOrDefault(viewer.getUniqueId(), 0),
                (index, entry) -> card(viewer, entry, mode));
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
        } else if (TAG_FILTER.equals(tag)) {
            Mode next = mode(viewer.getUniqueId()) == Mode.PENDING ? Mode.APPROVED : Mode.PENDING;
            modes.put(viewer.getUniqueId(), next);
            pages.remove(viewer.getUniqueId());
            loaded.remove(viewer.getUniqueId());
            rerender(viewer);
            reload(viewer);
        } else if (tag.startsWith(TAG_ENTRY)) {
            long id = Long.parseLong(tag.substring(TAG_ENTRY.length()));
            onEntry(viewer, id, type);
        }
    }

    @Override
    protected void forget(@NotNull UUID viewer) {
        loaded.remove(viewer);
        pages.remove(viewer);
        modes.remove(viewer);
    }

    private @NotNull Mode mode(@NotNull UUID viewer) {
        return modes.getOrDefault(viewer, Mode.PENDING);
    }

    private void onEntry(@NotNull Player viewer, long id, @NotNull ClickType type) {
        if (mode(viewer.getUniqueId()) == Mode.PENDING) {
            decide(viewer, id, !type.isRightClick());
        } else if (type.isRightClick()) {
            memory.revoke(id).thenAccept(done -> scheduler.runAtEntity(viewer, () -> {
                SafeText.msg(Boolean.TRUE.equals(done) ? "lingo.review.revoked" : "lingo.review.not_approved")
                        .with("id", id).prefix().send(viewer);
                reload(viewer);
            }));
        }
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
        CompletableFuture<List<MemoryEntry>> entries = mode(viewer.getUniqueId()) == Mode.PENDING
                ? memory.pending()
                : memory.approvedEntries().thenApply(List::reversed);
        entries.thenAccept(list -> scheduler.runAtEntity(viewer, () -> {
            loaded.put(viewer.getUniqueId(), list);
            if (isViewing(viewer)) {
                rerender(viewer);
            }
        }));
    }

    private @NotNull ItemStack header(@NotNull Player viewer, @Nullable List<MemoryEntry> entries,
                                      @NotNull Mode mode) {
        String count = entries == null
                ? LingoCards.msg(LingoCards.COMMON + "value.loading").miniMessage(viewer)
                : LingoCards.value(viewer, Integer.toString(entries.size()));
        CardLore lore = CardLore.create()
                .block(LingoCards.paragraphOf(viewer, KEY + "header.description"))
                .section(LingoCards.ic(viewer, KEY + "header.section"),
                        List.of(LingoCards.rowOf(viewer, KEY + (mode == Mode.PENDING ? "label.pending"
                                : "label.approved"), count)));
        return LingoCards.card(Material.WRITABLE_BOOK, LingoCards.ic(viewer, KEY + "header.name"), lore.build());
    }

    private @NotNull ItemStack card(@NotNull Player viewer, @NotNull MemoryEntry entry, @NotNull Mode mode) {
        Component name = LingoCards.ic(LingoCards.msg(KEY + "card.name")
                .with("id", entry.id())
                .with("from", entry.pair().source().upper())
                .with("to", entry.pair().target().upper()), viewer);
        CardLore lore = CardLore.create()
                .section(LingoCards.ic(viewer, KEY + "card.original"), LingoCards.userParagraph(viewer,
                        entry.sourceText()))
                .section(LingoCards.ic(viewer, KEY + "card.suggestion"), LingoCards.userParagraph(viewer,
                        entry.targetText()))
                .block(actions(viewer, mode));
        Material icon = mode == Mode.PENDING ? Material.PAPER : Material.ENCHANTED_BOOK;
        ItemStack item = LingoCards.card(icon, name, lore.build());
        tag(item, TAG_ENTRY + entry.id());
        return item;
    }

    private static @NotNull List<Component> actions(@NotNull Player viewer, @NotNull Mode mode) {
        if (mode == Mode.APPROVED) {
            return List.of(LingoCards.ic(viewer, KEY + "card.revoke"));
        }
        return List.of(LingoCards.ic(viewer, KEY + "card.approve"), LingoCards.ic(viewer, KEY + "card.reject"));
    }

    private static final class Holder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException();
        }
    }
}
