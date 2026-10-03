package de.jexcellence.lingo.language;

import de.jexcellence.lingo.api.LanguageCode;
import de.jexcellence.lingo.config.LanguageSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageVotesTest {

    private static final LanguageCode DE = LanguageCode.of("de");
    private static final LanguageCode EN = LanguageCode.of("en");

    @Test
    void winnerNeedsTheThreshold() {
        LanguageVotes votes = new LanguageVotes(8);
        votes.add(DE);
        votes.add(EN);
        votes.add(DE);
        assertTrue(votes.winner(3).isEmpty());

        votes.add(DE);
        assertEquals(Optional.of(DE), votes.winner(3));
    }

    @Test
    void samplingStopsAtTheCapacity() {
        LanguageVotes votes = new LanguageVotes(2);

        assertTrue(votes.trySample());
        assertTrue(votes.trySample());
        assertFalse(votes.trySample());
    }

    @Test
    void writingLanguageFallsBackToReading() {
        LanguageSettings languages = new LanguageSettings(List.of(DE, EN), EN);

        assertEquals(DE, LanguageResolver.resolveWriting(DE, EN, languages));
        assertEquals(EN, LanguageResolver.resolveWriting(null, EN, languages));
        assertEquals(EN, LanguageResolver.resolveWriting(LanguageCode.of("fr"), EN, languages));
    }
}
