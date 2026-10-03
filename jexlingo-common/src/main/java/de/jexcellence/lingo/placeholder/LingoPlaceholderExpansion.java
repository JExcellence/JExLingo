package de.jexcellence.lingo.placeholder;

import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.provider.ProviderHealthMonitor;
import de.jexcellence.lingo.settings.PlayerLanguageSettings;
import de.jexcellence.lingo.settings.PlayerSettingsService;
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
 * </ul>
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class LingoPlaceholderExpansion extends PlaceholderExpansion {

    private final PlayerSettingsService settings;
    private final LanguageResolver resolver;
    private final ProviderHealthMonitor health;
    private final String version;

    /**
     * Creates the expansion.
     *
     * @param settings player settings
     * @param resolver language resolver
     * @param health   provider health
     * @param version  plugin version
     */
    public LingoPlaceholderExpansion(@NotNull PlayerSettingsService settings, @NotNull LanguageResolver resolver,
                                     @NotNull ProviderHealthMonitor health, @NotNull String version) {
        this.settings = settings;
        this.resolver = resolver;
        this.health = health;
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

    private @NotNull String resolveWriting(@NotNull OfflinePlayer player) {
        Player online = player.getPlayer();
        if (online != null) {
            return resolver.resolveWriting(online).code();
        }
        return resolver.resolve(player.getUniqueId()).code();
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        if ("provider".equals(params)) {
            return health.snapshot().reachable() ? "online" : "offline";
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
