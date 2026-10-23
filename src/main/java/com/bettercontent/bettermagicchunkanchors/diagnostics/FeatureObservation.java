package com.bettercontent.bettermagicchunkanchors.diagnostics;

/** Pure projection of values already observed by the owning native decision. */
public final class FeatureObservation {
    private FeatureObservation() {}
    public static String fields(int returnedTrue) { return PlaytestLog.fields("calls", 9, "native_return_true", returnedTrue, "fully_ticking_observation", "UNKNOWN"); }

}
