package com.bettercontent.bettermagicchunkanchors.diagnostics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class FeatureObservationTest {
    @Test public void nativeScalarsHaveExplicitStage() { var fields = FeatureObservation.fields(9); assertTrue(fields.contains("calls=9")); assertTrue(fields.contains("native_return_true=9")); assertTrue(fields.contains("fully_ticking_observation=UNKNOWN")); }
    @Test public void uncertaintyAndPrivacyStayExplicit() { var fields = FeatureObservation.fields(0); assertTrue(fields.contains("native_return_true=0")); assertFalse(fields.contains("fully_ticking=true")); assertFalse(fields.contains("tickets_owned=true")); }
}
