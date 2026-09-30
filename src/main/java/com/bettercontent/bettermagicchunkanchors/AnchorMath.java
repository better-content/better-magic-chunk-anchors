package com.bettercontent.bettermagicchunkanchors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AnchorMath {
    private static final List<ChunkOffset> CENTERED_3X3;

    static {
        List<ChunkOffset> offsets = new ArrayList<>(9);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) offsets.add(new ChunkOffset(x, z));
        }
        CENTERED_3X3 = Collections.unmodifiableList(offsets);
    }

    public static List<ChunkOffset> centered3x3() { return CENTERED_3X3; }

    public static double chargeFraction(int stored, int capacity) {
        if (capacity <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, stored / (double) capacity));
    }

    public static int serviceTicksForHours(int hours) {
        return Math.multiplyExact(hours, 72_000);
    }

    /**
     * Debits one active service tick from a prepaid timed resource.
     *
     * The returned state is deliberately independent of wall-clock time: a
     * redstone pause or a block reload leaves the remaining active-time credit
     * untouched.  When no credit remains, the first active tick pays for a
     * complete interval and immediately consumes that tick from it.
     */
    public static TimedServiceDebit consumeTimedServiceTick(int units, int creditTicks, int interval) {
        if (units < 0 || creditTicks < 0 || interval <= 0) {
            throw new IllegalArgumentException("Timed service state must be non-negative and have a positive interval");
        }
        if (creditTicks > 0) return new TimedServiceDebit(units, creditTicks - 1, true);
        if (units == 0) return new TimedServiceDebit(units, 0, false);
        return new TimedServiceDebit(units - 1, interval - 1, true);
    }

    public record TimedServiceDebit(int units, int creditTicks, boolean consumed) {}

    public static boolean canSatisfySingleSourceRequest(int stored, int requested) {
        return requested >= 0 && stored >= requested;
    }

    public record ChunkOffset(int x, int z) {}

    private AnchorMath() {}
}
