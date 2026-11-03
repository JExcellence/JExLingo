package de.jexcellence.lingo.chat;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderBudgetTest {

    private static final Duration REFILL = Duration.ofMillis(500);
    private static final UUID PLAYER = UUID.randomUUID();

    private final AtomicLong now = new AtomicLong();
    private final ProviderBudget budget = new ProviderBudget(now::get);

    @Test
    void aShortBurstPassesAndSpamIsHeldBack() {
        assertTrue(budget.tryTake(PLAYER, 3, REFILL));
        assertTrue(budget.tryTake(PLAYER, 3, REFILL));
        assertTrue(budget.tryTake(PLAYER, 3, REFILL));
        assertFalse(budget.tryTake(PLAYER, 3, REFILL));
    }

    @Test
    void tokensComeBackOverTime() {
        for (int i = 0; i < 3; i++) {
            budget.tryTake(PLAYER, 3, REFILL);
        }
        now.addAndGet(REFILL.toNanos());

        assertTrue(budget.tryTake(PLAYER, 3, REFILL));
        assertFalse(budget.tryTake(PLAYER, 3, REFILL));
    }

    @Test
    void aMessageUsesOneTokenForAllItsLanguagesAndOnlyWhenAsked() {
        budget.gateFor(PLAYER, 1, REFILL);
        BooleanSupplier gate = budget.gateFor(PLAYER, 1, REFILL);

        assertTrue(gate.getAsBoolean());
        assertTrue(gate.getAsBoolean());
        assertFalse(budget.gateFor(PLAYER, 1, REFILL).getAsBoolean());
    }

    @Test
    void zeroRefillTurnsTheLimitOff() {
        for (int i = 0; i < 20; i++) {
            assertTrue(budget.tryTake(PLAYER, 1, Duration.ZERO));
        }
    }
}
