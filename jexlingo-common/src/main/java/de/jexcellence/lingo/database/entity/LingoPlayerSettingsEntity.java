package de.jexcellence.lingo.database.entity;

import de.jexcellence.jehibernate.entity.base.LongIdEntity;
import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.settings.IncomingMode;
import de.jexcellence.lingo.settings.PlayerLanguageSettings;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A player's translation settings: one row per player with the UUID and the setting values only. Every setting
 * column is nullable; {@code null} means the default applies, so new options can be added as new nullable columns.
 *
 * @author JExcellence
 * @since 0.1.0
 */
@Entity
@Table(name = "jexlingo_player_settings", indexes = {
        @Index(name = "idx_lingo_settings_uuid", columnList = "player_uuid", unique = true)
})
public class LingoPlayerSettingsEntity extends LongIdEntity {

    @Column(name = "player_uuid", nullable = false, unique = true, length = 36)
    private UUID playerUuid;

    @Column(name = "language", length = 8)
    private String language;

    @Column(name = "write_language", length = 8)
    private String writeLanguage;

    @Column(name = "translate_incoming")
    private Boolean translateIncoming;

    @Column(name = "incoming_mode", length = 16)
    private String incomingMode;

    @Column(name = "translate_outgoing")
    private Boolean translateOutgoing;

    @Column(name = "show_original")
    private Boolean showOriginal;

    @Column(name = "suggestions_blocked")
    private Boolean suggestionsBlocked;

    @Column(name = "onboarded_at")
    private Instant onboardedAt;

    protected LingoPlayerSettingsEntity() {
    }

    /**
     * Creates an empty row.
     *
     * @param playerUuid the player's UUID
     */
    public LingoPlayerSettingsEntity(@NotNull UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    /**
     * Returns the player's UUID.
     *
     * @return the player's UUID
     */
    public @NotNull UUID getPlayerUuid() {
        return playerUuid;
    }

    /**
     * Returns the stored settings with defaults for every column that is still {@code null}.
     *
     * @return the stored settings with defaults for every column that is still {@code null}
     */
    public @NotNull PlayerLanguageSettings toSettings() {
        PlayerLanguageSettings defaults = PlayerLanguageSettings.DEFAULTS;
        return new PlayerLanguageSettings(
                LanguageCode.parse(language).orElse(null),
                LanguageCode.parse(writeLanguage).orElse(null),
                incomingMode(defaults.incoming()),
                orDefault(translateOutgoing, defaults.translateOutgoing()),
                orDefault(showOriginal, defaults.showOriginal()),
                orDefault(suggestionsBlocked, defaults.suggestionsBlocked()),
                onboardedAt != null);
    }

    /**
     * Writes every value of {@code settings} into this row.
     *
     * @param settings the settings
     */
    public void apply(@NotNull PlayerLanguageSettings settings) {
        this.language = code(settings.language());
        this.writeLanguage = code(settings.writeLanguage());
        this.incomingMode = settings.incoming().name();
        this.translateIncoming = settings.incoming() != IncomingMode.OFF;
        this.translateOutgoing = settings.translateOutgoing();
        this.showOriginal = settings.showOriginal();
        this.suggestionsBlocked = settings.suggestionsBlocked();
        if (settings.onboarded() && onboardedAt == null) {
            this.onboardedAt = Instant.now();
        }
    }

    private @NotNull IncomingMode incomingMode(@NotNull IncomingMode fallback) {
        IncomingMode stored = IncomingMode.parse(incomingMode).orElse(null);
        if (stored != null) {
            return stored;
        }
        if (Boolean.FALSE.equals(translateIncoming)) {
            return IncomingMode.OFF;
        }
        return fallback;
    }

    private static @Nullable String code(@Nullable LanguageCode value) {
        return value == null ? null : value.code();
    }

    private static boolean orDefault(@Nullable Boolean stored, boolean fallback) {
        return stored == null ? fallback : stored;
    }
}
