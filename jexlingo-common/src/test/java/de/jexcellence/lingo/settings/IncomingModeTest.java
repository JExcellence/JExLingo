package de.jexcellence.lingo.settings;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncomingModeTest {

    @Test
    void cyclesBothWays() {
        assertEquals(IncomingMode.CLICK, IncomingMode.AUTO.cycle(true));
        assertEquals(IncomingMode.AUTO, IncomingMode.OFF.cycle(true));
        assertEquals(IncomingMode.OFF, IncomingMode.AUTO.cycle(false));
    }

    @Test
    void parsesCommandValues() {
        assertEquals(Optional.of(IncomingMode.CLICK), IncomingMode.parse(" Click "));
        assertTrue(IncomingMode.parse("sometimes").isEmpty());
    }

    @Test
    void translationKeysAvoidBooleanWords() {
        assertEquals("disabled", IncomingMode.OFF.key());
        assertEquals("on_click", IncomingMode.CLICK.key());
    }

    @Test
    void defaultsTranslateAutomatically() {
        assertEquals(IncomingMode.AUTO, PlayerLanguageSettings.DEFAULTS.incoming());
    }
}
