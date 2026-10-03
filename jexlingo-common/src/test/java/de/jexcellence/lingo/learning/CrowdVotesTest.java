package de.jexcellence.lingo.learning;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.pipeline.LanguagePair;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrowdVotesTest {

    private static final LanguagePair PAIR = new LanguagePair(LanguageCode.of("de"), LanguageCode.of("en"));
    private static final UUID ALEX = UUID.randomUUID();
    private static final UUID BEA = UUID.randomUUID();
    private static final UUID CEM = UUID.randomUUID();

    @Test
    void sameTextFromEnoughPlayersWins() {
        List<MemoryEntry> pending = List.of(entry(1, "Who sells iron?", ALEX), entry(2, "who sells iron?!", BEA),
                entry(3, "Anyone selling iron?", CEM));

        Optional<CrowdVotes.Outcome> outcome = CrowdVotes.count(pending, 2);

        assertTrue(outcome.isPresent());
        assertEquals(1L, outcome.get().winner().id());
        assertEquals(List.of(2L), outcome.get().duplicates().stream().map(MemoryEntry::id).toList());
    }

    @Test
    void onePlayerRepeatingCountsOnce() {
        List<MemoryEntry> pending = List.of(entry(1, "Who sells iron?", ALEX), entry(2, "Who sells iron?", ALEX));

        assertTrue(CrowdVotes.count(pending, 2).isEmpty());
    }

    @Test
    void zeroDisablesCrowdApproval() {
        assertTrue(CrowdVotes.count(List.of(entry(1, "x y", ALEX)), 0).isEmpty());
    }

    private static MemoryEntry entry(long id, String text, UUID submitter) {
        return new MemoryEntry(id, PAIR, "Wer verkauft Eisen?", text, MemoryStatus.PENDING, submitter, null);
    }
}
