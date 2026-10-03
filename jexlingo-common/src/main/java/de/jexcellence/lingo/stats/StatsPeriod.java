package de.jexcellence.lingo.stats;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

/**
 * The time spans the statistics menu offers.
 *
 * @author JExcellence
 * @since 0.3.0
 */
public enum StatsPeriod {

    /** Today since midnight. */
    TODAY(1),

    /** Today and the six days before. */
    WEEK(7),

    /** Today and the 29 days before. */
    MONTH(30);

    private final int days;

    StatsPeriod(int days) {
        this.days = days;
    }

    /**
     * Returns how many days the period covers.
     *
     * @return the number of days, including today
     */
    public int days() {
        return days;
    }

    /**
     * Returns the first day of the period.
     *
     * @param today the current day
     * @return the first day included
     */
    public @NotNull LocalDate since(@NotNull LocalDate today) {
        return today.minusDays(days - 1L);
    }

    /**
     * Returns the lower-case id used in commands and translation keys.
     *
     * @return {@code today}, {@code week} or {@code month}
     */
    public @NotNull String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the period after this one, for the filter button.
     *
     * @param forward {@code true} for the next period
     * @return the neighbouring period
     */
    public @NotNull StatsPeriod cycle(boolean forward) {
        StatsPeriod[] periods = values();
        return periods[Math.floorMod(ordinal() + (forward ? 1 : -1), periods.length)];
    }

    /**
     * Parses a typed period.
     *
     * @param raw the text in any case
     * @return the period, or empty for unknown input
     */
    public static @NotNull Optional<StatsPeriod> parse(@Nullable String raw) {
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
