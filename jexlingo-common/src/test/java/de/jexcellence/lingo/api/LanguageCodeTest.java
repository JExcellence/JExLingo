package de.jexcellence.lingo.api;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageCodeTest {

    @Test
    void parsesCodesAndLocales() {
        assertEquals(Optional.of(LanguageCode.of("de")), LanguageCode.parse("DE"));
        assertEquals(Optional.of(LanguageCode.of("de")), LanguageCode.parse("de_DE"));
        assertEquals(Optional.of(LanguageCode.of("en")), LanguageCode.parse(" en-US "));
        assertEquals(Optional.of(LanguageCode.of("de")), LanguageCode.fromLocale(Locale.GERMANY));
    }

    @Test
    void rejectsInvalidInput() {
        assertTrue(LanguageCode.parse(null).isEmpty());
        assertTrue(LanguageCode.parse("").isEmpty());
        assertTrue(LanguageCode.parse("d").isEmpty());
        assertTrue(LanguageCode.parse("x1").isEmpty());
        assertTrue(LanguageCode.fromLocale(Locale.ROOT).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> LanguageCode.of("deutsch"));
    }

    @Test
    void upperIsForLabels() {
        assertEquals("EN", LanguageCode.of("en").upper());
    }
}
