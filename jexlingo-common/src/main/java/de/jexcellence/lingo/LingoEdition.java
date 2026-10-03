package de.jexcellence.lingo;

import de.jexcellence.lingo.api.LanguageCode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * What an edition of JExLingo may do. Free translates up to {@link #maxLanguages()} languages with a glossary of
 * {@link #maxGlossaryTerms()} terms; Premium adds the learning layer (corrections, translation memory, pinned
 * phrases, training export), extra providers and has no limits.
 *
 * @author JExcellence
 * @since 0.1.0
 */
public sealed interface LingoEdition permits LingoEdition.FreeEdition, LingoEdition.PremiumEdition {

    /**
     * Returns the edition name for logs, {@code Free} or {@code Premium}.
     *
     * @return the edition name for logs, {@code Free} or {@code Premium}
     */
    @NotNull String name();

    /**
     * Returns the most languages this edition loads, or 0 for no limit.
     *
     * @return the most languages this edition loads, or 0 for no limit
     */
    int maxLanguages();

    /**
     * Returns the most glossary terms this edition stores, or 0 for no limit.
     *
     * @return the most glossary terms this edition stores, or 0 for no limit
     */
    int maxGlossaryTerms();

    /**
     * Returns whether corrections, translation memory, pinned phrases and the training export are available.
     *
     * @return whether corrections, translation memory, pinned phrases and the training export are available
     */
    boolean learningEnabled();

    /**
     * Returns whether providers registered through the API may be selected.
     *
     * @return whether providers registered through the API may be selected
     */
    boolean customProvidersEnabled();

    /**
     * Keeps the first {@link #maxLanguages()} languages in config order.
     *
     * @param languages the configured languages
     * @return the languages this edition loads
     */
    default @NotNull List<LanguageCode> limitLanguages(@NotNull List<LanguageCode> languages) {
        if (maxLanguages() <= 0 || languages.size() <= maxLanguages()) {
            return List.copyOf(languages);
        }
        return List.copyOf(languages.subList(0, maxLanguages()));
    }

    /**
     * Whether one more glossary term fits.
     *
     * @param currentCount the stored term count
     * @return whether a new term may be added
     */
    default boolean acceptsGlossaryTerm(int currentCount) {
        return maxGlossaryTerms() <= 0 || currentCount < maxGlossaryTerms();
    }

    /** The free edition. */
    record FreeEdition() implements LingoEdition {

        private static final int LANGUAGE_LIMIT = 3;
        private static final int GLOSSARY_LIMIT = 50;

        @Override public @NotNull String name() { return "Free"; }
        @Override public int maxLanguages() { return LANGUAGE_LIMIT; }
        @Override public int maxGlossaryTerms() { return GLOSSARY_LIMIT; }
        @Override public boolean learningEnabled() { return false; }
        @Override public boolean customProvidersEnabled() { return false; }
    }

    /** The premium edition: everything, no limits. */
    record PremiumEdition() implements LingoEdition {

        @Override public @NotNull String name() { return "Premium"; }
        @Override public int maxLanguages() { return 0; }
        @Override public int maxGlossaryTerms() { return 0; }
        @Override public boolean learningEnabled() { return true; }
        @Override public boolean customProvidersEnabled() { return true; }
    }
}
