package de.jexcellence.lingo.pipeline;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Per-call options of the pipeline.
 *
 * @param providerAllowed whether the provider may be called; {@code false} still uses glossary, memory and cache
 * @param protectedNames  player names that must not be translated
 * @param providerGate    asked only when the provider is really needed, after memory, phrases and cache missed;
 *                        lets spam protection count only real provider calls
 * @author JExcellence
 * @since 0.1.0
 */
public record TranslateOptions(boolean providerAllowed, @NotNull Collection<String> protectedNames,
                               @NotNull BooleanSupplier providerGate) {

    /** Provider allowed, no names to protect. */
    public static final TranslateOptions DEFAULT = new TranslateOptions(true, List.of());

    /**
     * Copies the names.
     *
     * @param providerAllowed whether the provider may be called
     * @param protectedNames  player names that must not be translated
     * @param providerGate    asked before a provider call
     */
    public TranslateOptions {
        protectedNames = List.copyOf(protectedNames);
    }

    /**
     * Options without a gate.
     *
     * @param providerAllowed whether the provider may be called
     * @param protectedNames  player names that must not be translated
     */
    public TranslateOptions(boolean providerAllowed, @NotNull Collection<String> protectedNames) {
        this(providerAllowed, protectedNames, () -> true);
    }

    /**
     * Returns whether this call may go to the provider; asks the gate, so call it only when the provider is needed.
     *
     * @return whether the provider may be called
     */
    public boolean mayCallProvider() {
        return providerAllowed && providerGate.getAsBoolean();
    }
}
