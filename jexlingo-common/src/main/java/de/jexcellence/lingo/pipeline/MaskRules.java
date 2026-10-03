package de.jexcellence.lingo.pipeline;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Glossary terms for one language pair, prepared for {@link TokenMasker}. Keep terms stay exactly as written; force
 * terms are replaced by their fixed translation. Lookups ignore case.
 *
 * @param keepTerms  terms that are never translated
 * @param forceTerms term (lower case) to fixed translation
 * @author JExcellence
 * @since 0.1.0
 */
public record MaskRules(@NotNull List<String> keepTerms, @NotNull Map<String, String> forceTerms) {

    /** No glossary terms. */
    public static final MaskRules EMPTY = new MaskRules(List.of(), Map.of());

    /**
     * Copies the collections and lower-cases the force keys.
     *
     * @param keepTerms  terms that are never translated
     * @param forceTerms term to fixed translation
     */
    public MaskRules {
        keepTerms = List.copyOf(keepTerms);
        forceTerms = forceTerms.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                entry -> entry.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue, (first, second) -> first));
    }

    /**
     * Rules from keep terms only.
     *
     * @param keepTerms terms that are never translated
     * @return the rules
     */
    public static @NotNull MaskRules keep(@NotNull Collection<String> keepTerms) {
        return new MaskRules(List.copyOf(keepTerms), Map.of());
    }

    /**
     * The fixed translation of a force term.
     *
     * @param matched the matched text in any case
     * @return the translation, or empty when the text is no force term
     */
    public @NotNull Optional<String> forced(@NotNull String matched) {
        return Optional.ofNullable(forceTerms.get(matched.toLowerCase(Locale.ROOT)));
    }

    /**
     * Returns whether there are no terms at all.
     *
     * @return whether there are no terms at all
     */
    public boolean isEmpty() {
        return keepTerms.isEmpty() && forceTerms.isEmpty();
    }
}
