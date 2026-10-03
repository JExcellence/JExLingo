package de.jexcellence.lingo.placeholder;

import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.settings.PlayerLanguageSettings;
import de.jexcellence.lingo.settings.PlayerSettingsService;
import de.jexcellence.lingo.stats.StatsSources;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion {@code %jexlingo_<name>%}. Reads only in-memory state, never the database.
 *
 * <ul>
 *     <li>{@code language} / {@code language_upper}: the language the player reads in</li>
 *     <li>{@code preference}: the stored preference, {@code auto} when following the client</li>
 *     <li>{@code writes}: the language the player writes in</li>
 *     <li>{@code incoming}: {@code auto}, {@code click} or {@code off}</li>
 *     <li>{@code outgoing}, {@code original}: the switches, {@code true} or {@code false}</li>
 *     <li>{@code provider}: {@code online} or {@code offline}</li>
 *     <li>{@code paused}: whether staff paused chat translation</li>
 *     <li>{@code stats_today}: translations today</li>
 *     <li>{@code latency}: median provider latency in milliseconds</li>
 * </ul>
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LingoPlaceholderExpansion extends PlaceholderExpansion {

    private final PlayerSettingsService settings;
    private final LanguageResolver resolver;
    private final StatsSources sources;
    private final String version;

    /**
     * Creates the expansion.
     *
     * @param settings player settings
     * @param resolver language resolver
     * @param sources  statistics and provider state
     * @param version  plugin version
     */
    public LingoPlaceholderExpansion(@NotNull PlayerSettingsService settings, @NotNull LanguageResolver resolver,
                                     @NotNull StatsSources sources, @NotNull String version) {
        this.settings = settings;
        this.resolver = resolver;
        this.sources = sources;
        this.version = version;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "jexlingo";
    }

    @Override
    public @NotNull String getAuthor() {
        return "JExcellence";
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    private @Nullable String global(@NotNull String params) {
        return switch (params) {
            case "provider" -> sources.health().snapshot().reachable() ? "online" : "offline";
            case "paused" -> Boolean.toString(sources.toggle().isPaused());
            case "stats_today" -> Long.toString(sources.stats().recorder().todayTotal());
            case "latency" -> Long.toString(Math.max(0L, sources.gateway().latency().p50()));
            default -> null;
        };
    }

    private @NotNull String resolveWriting(@NotNull OfflinePlayer player) {
        Player online = player.getPlayer();
        if (online != null) {
            return resolver.resolveWriting(online).code();
        }
        return resolver.resolve(player.getUniqueId()).code();
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        String global = global(params);
        if (global != null) {
            return global;
        }
        if (player == null) {
            return null;
        }
        PlayerLanguageSettings current = settings.get(player.getUniqueId());
        return switch (params) {
            case "language" -> resolver.resolve(player.getUniqueId()).code();
            case "language_upper" -> resolver.resolve(player.getUniqueId()).upper();
            case "preference" -> current.language() == null ? "auto" : current.language().code();
            case "incoming" -> current.incoming().id();
            case "writes" -> resolveWriting(player);
            case "outgoing" -> Boolean.toString(current.translateOutgoing());
            case "original" -> Boolean.toString(current.showOriginal());
            default -> null;
        };
    }
}
