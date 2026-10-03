package de.jexcellence.lingo;

import de.jexcellence.lingo.api.LanguageCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LingoEditionTest {

    private static final List<LanguageCode> FOUR = List.of(LanguageCode.of("de"), LanguageCode.of("en"),
            LanguageCode.of("fr"), LanguageCode.of("es"));

    @Test
    void freeKeepsTheFirstThreeLanguages() {
        List<LanguageCode> limited = new LingoEdition.FreeEdition().limitLanguages(FOUR);

        assertEquals(FOUR.subList(0, 3), limited);
    }

    @Test
    void premiumKeepsEveryLanguage() {
        assertEquals(FOUR, new LingoEdition.PremiumEdition().limitLanguages(FOUR));
    }

    @Test
    void freeGlossaryStopsAtFifty() {
        LingoEdition free = new LingoEdition.FreeEdition();

        assertTrue(free.acceptsGlossaryTerm(49));
        assertFalse(free.acceptsGlossaryTerm(50));
        assertTrue(new LingoEdition.PremiumEdition().acceptsGlossaryTerm(10_000));
    }

    @Test
    void learningIsPremiumOnly() {
        assertFalse(new LingoEdition.FreeEdition().learningEnabled());
        assertTrue(new LingoEdition.PremiumEdition().learningEnabled());
    }
}
