package de.jexcellence.lingo.glossary;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.pipeline.LanguagePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One glossary entry.
 *
 * @param id          the database id, 0 before it is stored
 * @param term        the term as written in chat
 * @param replacement the fixed translation for {@link GlossaryMode#FORCE}, {@code null} for keep terms
 * @param mode        keep or force
 * @param source      source language it applies to, {@code null} for all
 * @param target      target language it applies to, {@code null} for all
 * @author JExcellence
 * @since 0.1.0
 */
public record GlossaryTerm(
        long id,
        @NotNull String term,
        @Nullable String replacement,
        @NotNull GlossaryMode mode,
        @Nullable LanguageCode source,
        @Nullable LanguageCode target
) {

    /**
     * Whether the term applies to a translation direction.
     *
     * @param pair the language pair
     * @return {@code true} when both languages match or are unset
     */
    public boolean appliesTo(@NotNull LanguagePair pair) {
        boolean sourceMatches = source == null || source.equals(pair.source());
        boolean targetMatches = target == null || target.equals(pair.target());
        return sourceMatches && targetMatches;
    }

    /**
     * Returns whether this is a usable force term with a replacement.
     *
     * @return whether this is a usable force term with a replacement
     */
    public boolean forces() {
        return mode == GlossaryMode.FORCE && replacement != null && !replacement.isBlank();
    }
}
