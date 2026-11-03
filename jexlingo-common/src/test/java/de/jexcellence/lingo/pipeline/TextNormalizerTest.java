package de.jexcellence.lingo.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TextNormalizerTest {

    @Test
    void sameLineWithOtherSpacingCaseOrMarksSharesAKey() {
        assertEquals(TextNormalizer.key("Hallo   Welt"), TextNormalizer.key("hallo welt!!!"));
        assertEquals(TextNormalizer.key("hiii"), TextNormalizer.key("hii."));
    }

    @Test
    void apostrophesCommasAndRepeatedQuestionMarksDoNotSplitALine() {
        assertEquals(TextNormalizer.key("wie gehts"), TextNormalizer.key("wie geht's"));
        assertEquals(TextNormalizer.key("ja klar"), TextNormalizer.key("Ja, klar!"));
        assertEquals(TextNormalizer.key("kommst du?"), TextNormalizer.key("kommst du???"));
        assertNotEquals(TextNormalizer.key("gg"), TextNormalizer.key("gg 😀"));
    }

    @Test
    void questionMarksStayBecauseTheyChangeTheMeaning() {
        assertNotEquals(TextNormalizer.key("ok"), TextNormalizer.key("ok?"));
    }

    @Test
    void lineOfMarksOnlyKeepsItsMarks() {
        assertEquals("!!", TextNormalizer.key("!!"));
    }
}
