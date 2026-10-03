package de.jexcellence.lingo.pipeline;

import de.jexcellence.lingo.api.TranslationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkipRulesTest {

    private final SkipRules rules = new SkipRules(2, 20, "!");

    @Test
    void normalTextIsTranslated() {
        assertFalse(rules.skips("Hallo Welt", TranslationContext.CHAT));
    }

    @Test
    void tooShortOrTooLongIsSkipped() {
        assertTrue(rules.skips("a", TranslationContext.CHAT));
        assertTrue(rules.skips("x".repeat(21), TranslationContext.CHAT));
    }

    @Test
    void textWithoutLettersIsSkipped() {
        assertTrue(rules.skips("123 :) !!", TranslationContext.API));
    }

    @Test
    void skipPrefixOnlyAppliesToChat() {
        assertTrue(rules.skips("!kein Text", TranslationContext.CHAT));
        assertFalse(rules.skips("!kein Text", TranslationContext.API));
    }

    @Test
    void emptyPrefixIsDisabled() {
        assertFalse(new SkipRules(2, 20, "").skips("!kein Text", TranslationContext.CHAT));
    }

    @Test
    void lettersOfAnyScriptCount() {
        assertFalse(rules.skips("Привет мир", TranslationContext.CHAT));
    }
}
