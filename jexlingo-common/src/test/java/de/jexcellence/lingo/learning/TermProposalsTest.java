package de.jexcellence.lingo.learning;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.pipeline.LanguagePair;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TermProposalsTest {

    private static final LanguagePair EN_DE = new LanguagePair(LanguageCode.of("en"), LanguageCode.of("de"));

    @Test
    void proposesAWordKeptInEnoughCorrections() {
        List<MemoryEntry> entries = List.of(
                entry(1, "meet me at spawn", "Treffen wir uns am Spawn"),
                entry(2, "spawn is crowded", "Am Spawn ist viel los"),
                entry(3, "tp to Spawn please", "Teleportier mich bitte zum Spawn"),
                entry(4, "my island is big", "Meine Insel ist groß"));

        List<TermProposals.Proposal> proposals = TermProposals.find(entries, Set.of(), 3);

        assertEquals(List.of(new TermProposals.Proposal("spawn", 3)), proposals);
    }

    @Test
    void skipsCommonWordsShortWordsAndKnownTerms() {
        List<MemoryEntry> entries = List.of(
                entry(1, "gg and OneBlock", "gg and OneBlock"),
                entry(2, "gg and OneBlock", "gg and OneBlock"));

        assertTrue(TermProposals.find(entries, Set.of("oneblock"), 2).isEmpty());
    }

    @Test
    void needsTheThreshold() {
        List<MemoryEntry> entries = List.of(entry(1, "spawn now", "jetzt Spawn"));

        assertTrue(TermProposals.find(entries, Set.of(), 2).isEmpty());
    }

    private static MemoryEntry entry(long id, String source, String target) {
        return new MemoryEntry(id, EN_DE, source, target, MemoryStatus.APPROVED, null, null);
    }
}
