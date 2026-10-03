package de.jexcellence.lingo.glossary;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * How a glossary term is handled.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public enum GlossaryMode {

    /** The term stays exactly as written. */
    KEEP,

    /** The term is replaced with a fixed translation. */
    FORCE;

    /**
     * Parses a mode name.
     *
     * @param raw the name in any case
     * @return the mode, or empty for unknown input
     */
    public static @NotNull Optional<GlossaryMode> find(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Parses a stored mode.
     *
     * @param raw the stored value
     * @return the mode, {@link #KEEP} for unknown values
     */
    public static @NotNull GlossaryMode parse(@Nullable String raw) {
        return find(raw).orElse(KEEP);
    }
}
