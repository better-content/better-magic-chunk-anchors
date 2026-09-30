package com.bettercontent.bettermagicchunkanchors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnchorSourcePolicyTest {
    @Test
    void oneFullSourceAnchorCanPayImpossibleMatter() {
        assertTrue(AnchorMath.canSatisfySingleSourceRequest(144_000, 100_000));
        assertFalse(AnchorMath.canSatisfySingleSourceRequest(99_999, 100_000));
    }

    @Test
    void interruptedAndContinuousServiceConsumeTheSameActiveTicks() {
        AnchorMath.TimedServiceDebit continuous = new AnchorMath.TimedServiceDebit(1, 0, true);
        AnchorMath.TimedServiceDebit interrupted = new AnchorMath.TimedServiceDebit(1, 0, true);
        for (int i = 0; i < 16; i++) {
            continuous = AnchorMath.consumeTimedServiceTick(continuous.units(), continuous.creditTicks(), 20);
        }
        for (int i = 0; i < 20; i++) {
            if (i < 8 || i >= 12) {
                interrupted = AnchorMath.consumeTimedServiceTick(interrupted.units(), interrupted.creditTicks(), 20);
            }
        }
        assertEquals(continuous, interrupted);
        while (continuous.creditTicks() > 0) {
            continuous = AnchorMath.consumeTimedServiceTick(continuous.units(), continuous.creditTicks(), 20);
        }
        assertFalse(AnchorMath.consumeTimedServiceTick(continuous.units(), continuous.creditTicks(), 20).consumed());
    }

    @Test
    void refillAndReloadPreserveRemainingActiveTimeWithoutFreeInterval() {
        AnchorMath.TimedServiceDebit credit = AnchorMath.consumeTimedServiceTick(1, 0, 20);
        for (int i = 0; i < 8; i++) credit = AnchorMath.consumeTimedServiceTick(credit.units(), credit.creditTicks(), 20);
        AnchorMath.TimedServiceDebit reloaded = new AnchorMath.TimedServiceDebit(credit.units(), credit.creditTicks(), credit.consumed());
        AnchorMath.TimedServiceDebit refilled = AnchorMath.consumeTimedServiceTick(reloaded.units() + 1, reloaded.creditTicks(), 20);
        assertEquals(1, refilled.units());
        assertEquals(10, refilled.creditTicks());
        assertTrue(refilled.consumed());
    }

    @Test
    void timedServiceRejectsInvalidIntervals() {
        assertThrows(IllegalArgumentException.class, () -> AnchorMath.consumeTimedServiceTick(1, 0, 0));
    }
}
