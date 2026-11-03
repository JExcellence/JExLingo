package de.jexcellence.lingo.glossary;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlossaryImportTest {

    @Test
    void addsOnlyNewTermsAndCountsDuplicates() {
        List<GlossaryTerm> existing = List.of(keep(1, "OneBlock"), keep(2, "gg"));
        List<GlossaryTerm> file = List.of(keep(0, "oneblock"), keep(0, "Ironman"), keep(0, "afk"), keep(0, "Afk"));

        GlossaryService.ImportResult result = GlossaryService.plan(existing, file, 0);

        assertEquals(List.of("Ironman", "afk"), result.toAdd().stream().map(GlossaryTerm::term).toList());
        assertEquals(2, result.duplicates());
        assertEquals(0, result.overLimit());
    }

    @Test
    void stopsAtTheEditionLimit() {
        List<GlossaryTerm> existing = List.of(keep(1, "OneBlock"));
        List<GlossaryTerm> file = List.of(keep(0, "a1"), keep(0, "a2"), keep(0, "a3"));

        GlossaryService.ImportResult result = GlossaryService.plan(existing, file, 3);

        assertEquals(2, result.toAdd().size());
        assertEquals(1, result.overLimit());
    }

    private static GlossaryTerm keep(long id, String term) {
        return new GlossaryTerm(id, term, null, GlossaryMode.KEEP, null, null);
    }
}
