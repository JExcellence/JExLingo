package de.jexcellence.lingo.learning;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Review status of a translation memory entry.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public enum MemoryStatus {

    /** Waiting for staff review. */
    PENDING,

    /** Used for every identical line from now on. */
    APPROVED,

    /** Declined; deleted after a grace period. */
    REJECTED;

    /**
     * Parses a stored status.
     *
     * @param raw the stored value
     * @return the status, {@link #PENDING} for unknown values
     */
    public static @NotNull MemoryStatus parse(@Nullable String raw) {
        if (raw == null) {
            return PENDING;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PENDING;
        }
    }
}
