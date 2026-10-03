package de.jexcellence.lingo.language;

import de.jexcellence.jexplatform.scheduler.PlatformScheduler;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.config.DetectionSettings;
import de.jexcellence.lingo.config.LanguageSettings;
import de.jexcellence.lingo.provider.ProviderGateway;
import de.jexcellence.lingo.text.SafeText;
import de.jexcellence.lingo.view.LingoSettingsView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Learns which language a player really writes in. Many players use an English game client but chat in German;
 * their lines would then be sent to the translator as English. For players without a stored writing language this
 * samples the first messages of a session with the provider's detection. When enough samples agree on another
 * enabled language, the player gets one clickable suggestion to set it. Nothing is changed without the player's
 * click, and nothing is stored.
 *
 * @author JExcellence
 * @since 0.2.0
 */
public final class WritingLanguageLearner {

    private final ProviderGateway gateway;
    private final PlatformScheduler scheduler;
    private final AtomicReference<DetectionSettings> settings;
    private final Map<UUID, LanguageVotes> votes = new ConcurrentHashMap<>();
    private final Set<UUID> suggested = ConcurrentHashMap.newKeySet();

    /**
     * Creates the learner.
     *
     * @param gateway   the provider gateway
     * @param scheduler the platform scheduler
     * @param settings  detection settings
     */
    public WritingLanguageLearner(@NotNull ProviderGateway gateway, @NotNull PlatformScheduler scheduler,
                                  @NotNull DetectionSettings settings) {
        this.gateway = gateway;
        this.scheduler = scheduler;
        this.settings = new AtomicReference<>(settings);
    }

    /**
     * Samples a chat line of a player whose writing language is not set.
     *
     * @param sender    the sender
     * @param text      the plain line
     * @param assumed   the language the line is currently treated as
     * @param languages enabled languages
     */
    public void observe(@NotNull Player sender, @NotNull String text, @NotNull LanguageCode assumed,
                        @NotNull LanguageSettings languages) {
        DetectionSettings current = settings.get();
        UUID uuid = sender.getUniqueId();
        if (!current.learnWriting() || text.trim().length() < current.minLength() || suggested.contains(uuid)) {
            return;
        }
        LanguageVotes tally = votes.computeIfAbsent(uuid, ignored -> new LanguageVotes(current.learnSamples()));
        if (!tally.trySample()) {
            return;
        }
        gateway.detect(text).thenAccept(guess -> guess
                .filter(found -> found.confidence() >= current.minConfidence())
                .filter(found -> languages.isEnabled(found.language()))
                .ifPresent(found -> {
                    tally.add(found.language());
                    tally.winner(current.learnThreshold())
                            .filter(winner -> !winner.equals(assumed))
                            .filter(winner -> suggested.add(uuid))
                            .ifPresent(winner -> scheduler.runAtEntity(sender, () -> suggest(sender, winner)));
                }));
    }

    /**
     * Forgets a player, for example on quit.
     *
     * @param player the player's UUID
     */
    public void forget(@NotNull UUID player) {
        votes.remove(player);
        suggested.remove(player);
    }

    /**
     * Replaces the settings after a reload.
     *
     * @param value the new settings
     */
    public void setSettings(@NotNull DetectionSettings value) {
        settings.set(value);
    }

    private static void suggest(@NotNull Player player, @NotNull LanguageCode language) {
        if (!player.isOnline()) {
            return;
        }
        String name = LingoSettingsView.languageName(player, language);
        Component hint = SafeText.msg("lingo.learn.hint").with("language", name).prefix().component(player)
                .clickEvent(ClickEvent.runCommand("/lingo write " + language.code()))
                .hoverEvent(HoverEvent.showText(SafeText.msg("lingo.learn.hover").with("language", name)
                        .component(player)));
        player.sendMessage(hint);
    }
}
