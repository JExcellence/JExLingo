package de.jexcellence.lingo.view;

import de.jexcellence.jexplatform.gui.component.CardLore;
import de.jexcellence.jexplatform.gui.style.LockedIcon;
import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.jextranslate.MessageBuilder;
import de.jexcellence.lingo.LingoEdition;
import de.jexcellence.lingo.api.TranslationOrigin;
import de.jexcellence.lingo.stats.StatsPeriod;
import de.jexcellence.lingo.stats.StatsSources;
import de.jexcellence.lingo.stats.StatsSummary;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /lingo stats}: how the translator is doing. Header at 4 shows provider health; the filter at 8 picks today,
 * 7 or 30 days (Premium); the body shows totals, where results came from, the busiest language pairs, the provider
 * and the learning layer. Only daily totals are shown, never texts or players.
 *
 * @author JExcellence
 * @since 0.3.0
 */
public final class StatsView extends LingoBaseView {

    private static final String KEY = "lingo_stats.";
    private static final String LABEL = KEY + "label.";
    private static final String TAG_FILTER = "filter";
    private static final int TOP_PAIRS = 6;
    private static final String SECTION = ".section";
    private static final String NAME = ".name";

    private final Holder holder = new Holder();
    private final StatsSources sources;
    private final LingoEdition edition;
    private final PlatformScheduler scheduler;
    private final Map<UUID, StatsPeriod> periods = new ConcurrentHashMap<>();
    private final Map<UUID, StatsSummary> loaded = new ConcurrentHashMap<>();

    /**
     * Creates the view.
     *
     * @param sources   statistics sources
     * @param edition   the edition, for the history filter
     * @param scheduler platform scheduler
     */
    public StatsView(@NotNull StatsSources sources, @NotNull LingoEdition edition,
                     @NotNull PlatformScheduler scheduler) {
        this.sources = sources;
        this.edition = edition;
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
        inv.setItem(SLOT_HEADER, header(viewer));
        StatsPeriod period = period(viewer.getUniqueId());
        if (edition.statisticsHistory()) {
            ItemStack filter = LingoCards.filter(viewer, Arrays.stream(StatsPeriod.values())
                    .map(value -> LingoCards.text(viewer, KEY + "period." + value.id())).toList(), period.ordinal());
            tag(filter, TAG_FILTER);
            inv.setItem(SLOT_FILTER, filter);
        }
        StatsSummary summary = loaded.get(viewer.getUniqueId());
        if (summary == null) {
            inv.setItem(SLOT_CENTER, LingoCards.notice(viewer, Material.CLOCK, KEY + "loading"));
            return;
        }
        List<ItemStack> cards = new ArrayList<>();
        cards.add(overview(viewer, summary, period));
        cards.add(origins(viewer, summary));
        cards.add(pairs(viewer, summary));
        cards.add(provider(viewer, summary));
        cards.add(edition.learningEnabled() ? learning(viewer) : locked(viewer, "learning-locked"));
        if (!edition.statisticsHistory()) {
            cards.add(locked(viewer, "history-locked"));
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
        if (TAG_FILTER.equals(tagOf(clicked)) && edition.statisticsHistory()) {
            periods.put(viewer.getUniqueId(), period(viewer.getUniqueId()).cycle(!type.isRightClick()));
            loaded.remove(viewer.getUniqueId());
            rerender(viewer);
            reload(viewer);
        }
    }

    @Override
    protected void forget(@NotNull UUID viewer) {
        periods.remove(viewer);
        loaded.remove(viewer);
    }

    private @NotNull StatsPeriod period(@NotNull UUID viewer) {
        return edition.statisticsHistory() ? periods.getOrDefault(viewer, StatsPeriod.TODAY) : StatsPeriod.TODAY;
    }

    private void reload(@NotNull Player viewer) {
        sources.stats().summary(period(viewer.getUniqueId())).thenAccept(summary -> scheduler.runAtEntity(viewer,
                () -> {
                    loaded.put(viewer.getUniqueId(), summary);
                    if (isViewing(viewer)) {
                        rerender(viewer);
                    }
                }));
    }

    private @NotNull ItemStack header(@NotNull Player viewer) {
        boolean reachable = sources.health().snapshot().reachable();
        List<Component> rows = new ArrayList<>();
        rows.add(LingoCards.rowOf(viewer, LABEL + "provider", LingoCards.value(viewer,
                sources.gateway().provider().id())));
        rows.add(LingoCards.rowOf(viewer, LABEL + "state", LingoCards.tone(viewer, reachable ? "ok" : "bad",
                LingoCards.text(viewer, KEY + "value." + (reachable ? "online" : "offline")))));
        rows.add(LingoCards.rowOf(viewer, LABEL + "breaker", LingoCards.value(viewer,
                sources.gateway().breaker().state().name().toLowerCase(Locale.ROOT))));
        rows.add(LingoCards.rowOf(viewer, LABEL + "latency", LingoCards.value(viewer,
                sources.gateway().latency().p50() + " / " + sources.gateway().latency().p95() + " ms")));
        rows.add(LingoCards.rowOf(viewer, LABEL + "inflight", LingoCards.value(viewer,
                Integer.toString(sources.pipeline().inFlight()))));
        if (sources.toggle().isPaused()) {
            rows.add(LingoCards.rowOf(viewer, LABEL + "paused", LingoCards.tone(viewer, "warn",
                    LingoCards.text(viewer, KEY + "value.paused"))));
        }
        CardLore lore = CardLore.create()
                .block(LingoCards.paragraphOf(viewer, KEY + "header.description"))
                .section(LingoCards.ic(viewer, KEY + "header" + SECTION), rows);
        return LingoCards.card(Material.SPYGLASS, LingoCards.ic(viewer, KEY + "header" + NAME), lore.build());
    }

    private @NotNull ItemStack overview(@NotNull Player viewer, @NotNull StatsSummary summary,
                                        @NotNull StatsPeriod period) {
        List<Component> rows = List.of(
                LingoCards.rowOf(viewer, LABEL + "translations", number(viewer, summary.translations())),
                LingoCards.rowOf(viewer, LABEL + "characters", number(viewer, summary.characters())),
                LingoCards.rowOf(viewer, LABEL + "saved", LingoCards.tone(viewer, "ok", summary.savedPercent() + "%")),
                LingoCards.rowOf(viewer, LABEL + "fallback", LingoCards.tone(viewer,
                        summary.fallbackPercent() > 10 ? "warn" : "plain", summary.fallbackPercent() + "%")));
        return card(viewer, Material.BOOK, "overview", rows,
                LingoCards.msg(KEY + "overview" + NAME).with("period", LingoCards.text(viewer,
                        KEY + "period." + period.id())));
    }

    private @NotNull ItemStack origins(@NotNull Player viewer, @NotNull StatsSummary summary) {
        List<Component> rows = new ArrayList<>();
        for (TranslationOrigin origin : TranslationOrigin.values()) {
            if (origin.translates() || origin == TranslationOrigin.FALLBACK) {
                rows.add(LingoCards.rowOf(viewer, "lingo.origin." + origin.name().toLowerCase(Locale.ROOT),
                        number(viewer, summary.count(origin))));
            }
        }
        return card(viewer, Material.HOPPER, "origins", rows, null);
    }

    private @NotNull ItemStack pairs(@NotNull Player viewer, @NotNull StatsSummary summary) {
        List<Component> rows = new ArrayList<>();
        for (Map.Entry<String, Long> pair : summary.topPairs(TOP_PAIRS)) {
            rows.add(LingoCards.ic(LingoCards.msg(KEY + "pair.row")
                    .with("pair", pair.getKey().toUpperCase(Locale.ROOT).replace(">", " » "))
                    .with("value", number(viewer, pair.getValue())), viewer));
        }
        if (rows.isEmpty()) {
            rows.add(LingoCards.ic(viewer, KEY + "pair.none"));
        }
        return card(viewer, Material.FILLED_MAP, "pairs", rows, null);
    }

    private @NotNull ItemStack provider(@NotNull Player viewer, @NotNull StatsSummary summary) {
        List<Component> rows = List.of(
                LingoCards.rowOf(viewer, LABEL + "calls", number(viewer, summary.count(TranslationOrigin.PROVIDER))),
                LingoCards.rowOf(viewer, LABEL + "average", LingoCards.value(viewer,
                        summary.averageProviderLatency() + " ms")),
                LingoCards.rowOf(viewer, LABEL + "failed-since-start", number(viewer,
                        sources.gateway().failureCount())),
                LingoCards.rowOf(viewer, LABEL + "refused-since-start", number(viewer,
                        sources.gateway().refusedCount())),
                LingoCards.rowOf(viewer, LABEL + "cache", LingoCards.value(viewer,
                        sources.pipeline().cache().size() + " / " + Math.round(sources.pipeline().cache().hitRate()
                                * 100.0) + "%")));
        return card(viewer, Material.BEACON, "provider", rows, null);
    }

    private @NotNull ItemStack learning(@NotNull Player viewer) {
        List<Component> rows = new ArrayList<>();
        if (sources.memory() != null) {
            rows.add(LingoCards.rowOf(viewer, LABEL + "approved", number(viewer, sources.memory().approvedCount())));
        }
        if (sources.phrases() != null) {
            rows.add(LingoCards.rowOf(viewer, LABEL + "pinned", number(viewer, sources.phrases().pinnedCount())));
        }
        return card(viewer, Material.ENCHANTED_BOOK, "learning", rows, null);
    }

    private static @NotNull ItemStack locked(@NotNull Player viewer, @NotNull String key) {
        ItemStack base = LockedIcon.item(viewer);
        return LingoCards.card(base, LingoCards.ic(viewer, KEY + key + NAME),
                CardLore.create().block(LingoCards.paragraphOf(viewer, KEY + key + ".description")).build());
    }

    private static @NotNull ItemStack card(@NotNull Player viewer, @NotNull Material icon, @NotNull String key,
                                           @NotNull List<Component> rows,
                                           @Nullable MessageBuilder name) {
        Component title = name == null ? LingoCards.ic(viewer, KEY + key + NAME) : LingoCards.ic(name, viewer);
        CardLore lore = CardLore.create()
                .block(LingoCards.paragraphOf(viewer, KEY + key + ".description"))
                .section(LingoCards.ic(viewer, KEY + key + SECTION), rows);
        return LingoCards.card(icon, title, lore.build());
    }

    private static @NotNull String number(@NotNull Player viewer, long value) {
        return LingoCards.value(viewer, NumberFormat.getIntegerInstance(viewer.locale()).format(value));
    }

    private static final class Holder implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() {
            throw new UnsupportedOperationException();
        }
    }
}
