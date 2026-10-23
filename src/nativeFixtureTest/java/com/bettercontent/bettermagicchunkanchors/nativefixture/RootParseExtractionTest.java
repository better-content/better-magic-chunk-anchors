package com.bettercontent.bettermagicchunkanchors.nativefixture;

import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.ArsDispatchScope;
import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.DecodedRowProofRules;
import com.mojang.serialization.DataResult;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RootParseExtractionTest {
    @Test void originalParseBodyMustBeFullSuccessNotPartialOrAbsent() {
        Object body = new Object();
        assertSame(body, DecodedRowProofRules.successfulParseValue(DataResult.success(body)));
        assertNull(DecodedRowProofRules.successfulParseValue(DataResult.error(() -> "failed", body)));
        assertNull(DecodedRowProofRules.successfulParseValue(DataResult.error(() -> "failed")));
        assertNull(DecodedRowProofRules.successfulParseValue(null));
    }
    @Test void originalExtractionStackRequiresExactCallSiteAndNoSecondParse() {
        var callback = new ArsDispatchScope.Trace("com.bettercontent.bettermagicchunkanchors.nativefixture.glm.AnchorsFixtureGlm",
            "originalRootBody", "(Ljava/lang/Object;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;Lcom/mojang/serialization/DataResult;)V");
        var redirect = new ArsDispatchScope.Trace("net.minecraftforge.common.loot.LootModifierManager",
            "redirect$fixture$anchors$originalRootBody",
            "(Lcom/mojang/serialization/DataResult;Ljava/util/function/Consumer;Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Optional;");
        var apply = new ArsDispatchScope.Trace("net.minecraftforge.common.loot.LootModifierManager", "apply", ArsDispatchScope.APPLY);
        assertTrue(ArsDispatchScope.pinnedRootResultTrace(List.of(callback, redirect, apply)));
        assertFalse(ArsDispatchScope.pinnedRootResultTrace(List.of(callback, apply)));
        assertFalse(ArsDispatchScope.pinnedRootResultTrace(List.of(callback, redirect, apply, apply)));
        assertFalse(ArsDispatchScope.pinnedRootResultTrace(List.of(callback, redirect,
            new ArsDispatchScope.Trace(apply.owner(), "unrelated", apply.descriptor()))));
        assertFalse(ArsDispatchScope.pinnedRootResultTrace(List.of(callback, redirect, apply,
            new ArsDispatchScope.Trace("com.mojang.serialization.Decoder", "parse", ArsDispatchScope.DECODE))));
    }
}
