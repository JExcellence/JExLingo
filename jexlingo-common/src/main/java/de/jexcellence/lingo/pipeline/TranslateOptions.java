package de.jexcellence.lingo.pipeline;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Per-call options of the pipeline.
 *
 * @param providerAllowed whether the provider may be called; {@code false} still uses glossary, memory and cache
 * @param protectedNames  player names that must not be translated
 * @author JExcellence
 * @since 0.1.0
 */
public record TranslateOptions(boolean providerAllowed, @NotNull Collection<String> protectedNames) {

    /** Provider allowed, no names to protect. */
    public static final TranslateOptions DEFAULT = new TranslateOptions(true, List.of());

    /**
     * Copies the names.
     *
     * @param providerAllowed whether the provider may be called
     * @param protectedNames  player names that must not be translated
     */
    public TranslateOptions {
        protectedNames = List.copyOf(protectedNames);
    }
}
