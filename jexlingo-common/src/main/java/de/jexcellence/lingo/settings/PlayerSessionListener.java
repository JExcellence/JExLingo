package de.jexcellence.lingo.settings;

import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.lingo.chat.ChatTranslationCoordinator;
import de.jexcellence.lingo.language.LanguageResolver;
import de.jexcellence.lingo.text.SafeText;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;

/**
 * Loads a player's settings on join, shows the one-time hint to new players and drops the cached state on quit.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class PlayerSessionListener implements Listener {

    private final PlayerSettingsService settings;
    private final LanguageResolver resolver;
    private final ChatTranslationCoordinator coordinator;
    private final PlatformScheduler scheduler;
    private final BooleanSupplier onboarding;

    /**
     * Creates the listener.
     *
     * @param settings    player settings
     * @param resolver    language resolver
     * @param coordinator chat coordinator
     * @param scheduler   platform scheduler
     * @param onboarding  whether the first-join hint is enabled
     */
    public PlayerSessionListener(@NotNull PlayerSettingsService settings, @NotNull LanguageResolver resolver,
                                 @NotNull ChatTranslationCoordinator coordinator,
                                 @NotNull PlatformScheduler scheduler, @NotNull BooleanSupplier onboarding) {
        this.settings = settings;
        this.resolver = resolver;
        this.coordinator = coordinator;
        this.scheduler = scheduler;
        this.onboarding = onboarding;
    }

    /**
     * Loads the joining player's settings.
     *
     * @param event the join event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        settings.load(player.getUniqueId()).thenAccept(loaded -> {
            if (!loaded.onboarded() && onboarding.getAsBoolean()) {
                scheduler.runAtEntity(player, () -> greet(player));
            }
        });
    }

    /**
     * Drops the leaving player's cached state.
     *
     * @param event the quit event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        settings.forget(event.getPlayer().getUniqueId());
        coordinator.forget(event.getPlayer().getUniqueId());
    }

    private void greet(@NotNull Player player) {
        if (!player.isOnline()) {
            return;
        }
        SafeText.msg("lingo.onboarding.message")
                .with("language", resolver.resolve(player).upper())
                .prefix()
                .send(player);
        settings.update(player.getUniqueId(), PlayerLanguageSettings::markOnboarded);
    }
}
