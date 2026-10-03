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
    void questionMarksStayBecauseTheyChangeTheMeaning() {
        assertNotEquals(TextNormalizer.key("ok"), TextNormalizer.key("ok?"));
    }

    @Test
    void lineOfMarksOnlyKeepsItsMarks() {
        assertEquals("!!", TextNormalizer.key("!!"));
    }
}
