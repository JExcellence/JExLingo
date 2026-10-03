package de.jexcellence.lingo.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * How a player receives messages in other languages.
 *
 * @author JExcellence
 * @since 0.2.0
 */
public enum IncomingMode {

    /** Every line is translated automatically. */
    AUTO,

    /** Lines stay as written with a small translate button; one click translates that line. */
    CLICK,

    /** Lines stay as written. */
    OFF;

    /**
     * Returns the lower-case id used in commands and translation keys.
     *
     * @return the lower-case id used in commands and translation keys
     */
    public @NotNull String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the translation key suffix of this mode.
     *
     * @return {@code automatic}, {@code on_click} or {@code disabled}
     */
    public @NotNull String key() {
        return switch (this) {
            case AUTO -> "automatic";
            case CLICK -> "on_click";
            case OFF -> "disabled";
            default -> throw new IllegalStateException("Unexpected mode: " + this);
        };
    }

    /**
     * Returns the mode after this one, for menu cycling.
     *
     * @param forward {@code true} for the next mode, {@code false} for the previous one
     * @return the neighbouring mode
     */
    public @NotNull IncomingMode cycle(boolean forward) {
        IncomingMode[] modes = values();
        return modes[Math.floorMod(ordinal() + (forward ? 1 : -1), modes.length)];
    }

    /**
     * Parses a stored or typed mode.
     *
     * @param raw the text in any case
     * @return the mode, or empty for unknown input
     */
    public static @NotNull Optional<IncomingMode> parse(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
