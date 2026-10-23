package com.bettercontent.bettermagicchunkanchors.nativefixture.glm;

import java.util.concurrent.atomic.AtomicInteger;

/** JDK-only launch-bound observer bookkeeping. Never locks or retains a target/native object. */
public final class RegistryCallbackBudget {
    private final AtomicInteger ordinary = new AtomicInteger();
    private final AtomicInteger asymmetric = new AtomicInteger();
    private final AtomicInteger register = new AtomicInteger();

    public static boolean bound(String attempt, String manifestAttempt, String manifestHash) {
        return attempt != null && attempt.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            && attempt.equals(manifestAttempt) && manifestHash != null && manifestHash.matches("[0-9a-f]{64}");
    }

    public boolean reserveRegister() { return take(register, 64); }
    public boolean reserveCapture(boolean observedAsymmetry) { return take(observedAsymmetry ? asymmetric : ordinary, 32); }

    private static boolean take(AtomicInteger counter, int limit) {
        return counter.getAndUpdate(value -> value < limit ? value + 1 : value) < limit;
    }
}
