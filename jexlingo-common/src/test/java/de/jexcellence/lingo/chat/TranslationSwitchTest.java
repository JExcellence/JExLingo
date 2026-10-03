package de.jexcellence.lingo.chat;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationSwitchTest {

    private final MovableClock clock = new MovableClock();
    private final TranslationSwitch toggle = new TranslationSwitch(clock);

    @Test
    void timedPauseEndsByItself() {
        toggle.pause(Duration.ofMinutes(10));
        assertTrue(toggle.isPaused());
        assertEquals(Optional.of(Duration.ofMinutes(10)), toggle.remaining());

        clock.now = clock.now.plus(Duration.ofMinutes(11));

        assertFalse(toggle.isPaused());
    }

    @Test
    void openPauseRunsUntilResumed() {
        toggle.pause(null);
        clock.now = clock.now.plus(Duration.ofDays(3));
        assertTrue(toggle.isPaused());
        assertTrue(toggle.remaining().isEmpty());

        toggle.resume();

        assertFalse(toggle.isPaused());
    }

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-10-03T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
