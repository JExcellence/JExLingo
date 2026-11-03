package de.jexcellence.lingo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuietSlf4jInternalNoticesTest {

    private static final String KEY = "slf4j.internal.verbosity";
    private String previous;

    @BeforeEach
    void remember() {
        previous = System.getProperty(KEY);
        System.clearProperty(KEY);
    }

    @AfterEach
    void restore() {
        if (previous == null) {
            System.clearProperty(KEY);
        } else {
            System.setProperty(KEY, previous);
        }
    }

    @Test
    void setsErrorWhenUnset() {
        assertNull(System.getProperty(KEY));
        JExLingo.quietSlf4jInternalNotices();
        assertEquals("ERROR", System.getProperty(KEY));
    }

    @Test
    void leavesAnExistingSettingAlone() {
        System.setProperty(KEY, "DEBUG");
        JExLingo.quietSlf4jInternalNotices();
        assertEquals("DEBUG", System.getProperty(KEY));
    }
}
