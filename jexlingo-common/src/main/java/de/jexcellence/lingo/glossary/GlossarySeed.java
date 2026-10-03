package de.jexcellence.lingo.glossary;

import de.jexcellence.lingo.api.LanguageCode;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads the default terms from {@code glossary.yml}. Used once, when the glossary table is still empty; after that
 * the glossary is managed in game.
 *
 * <pre>
 * keep:
 *   - OneBlock
 * force:
 *   - { term: Insel, replacement: Island, source: de, target: en }
 * </pre>
 *
 * @author JExcellence
 * @since 0.1.0
 */
public final class GlossarySeed {

    private static final String TERM = "term";

    private GlossarySeed() {
    }

    /**
     * Parses the seed file.
     *
     * @param root    the root of {@code glossary.yml}
     * @param warning receives a message for every skipped entry
     * @return the terms in file order
     */
    public static @NotNull List<GlossaryTerm> parse(@NotNull ConfigurationSection root,
                                                    @NotNull Consumer<String> warning) {
        List<GlossaryTerm> terms = new ArrayList<>();
        for (String keep : root.getStringList("keep")) {
            if (!keep.isBlank()) {
                terms.add(new GlossaryTerm(0L, keep.trim(), null, GlossaryMode.KEEP, null, null));
            }
        }
        for (Map<?, ?> entry : root.getMapList("force")) {
            GlossaryTerm term = forceTerm(entry);
            if (term == null) {
                warning.accept("glossary.yml: force entry " + entry + " needs 'term' and 'replacement'; skipped");
            } else {
                terms.add(term);
            }
        }
        return terms;
    }

    private static @Nullable GlossaryTerm forceTerm(@NotNull Map<?, ?> entry) {
        String term = text(entry.get(TERM));
        String replacement = text(entry.get("replacement"));
        if (term == null || replacement == null) {
            return null;
        }
        return new GlossaryTerm(0L, term, replacement, GlossaryMode.FORCE,
                LanguageCode.parse(text(entry.get("source"))).orElse(null),
                LanguageCode.parse(text(entry.get("target"))).orElse(null));
    }

    private static @Nullable String text(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
