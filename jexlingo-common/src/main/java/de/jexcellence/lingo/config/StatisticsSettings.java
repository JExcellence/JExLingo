package de.jexcellence.lingo.config;

/**
 * Daily translation statistics. Only totals per day, language pair and source are stored, never chat text or
 * players.
 *
 * @param enabled       whether statistics are recorded
 * @param retentionDays how many days of statistics are kept
 * @author JExcellence
 * @since 0.3.0
 */
public record StatisticsSettings(boolean enabled, int retentionDays) {
}
