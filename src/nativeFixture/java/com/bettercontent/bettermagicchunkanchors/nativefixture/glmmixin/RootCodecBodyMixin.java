package com.bettercontent.bettermagicchunkanchors.nativefixture.glmmixin;

import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.ArsDispatchScope;
import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.AnchorsFixtureGlm;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Actual public DIRECT_CODEC wrapper decode, never the lazy registry-key codec. */
@Mixin(targets = "com.mojang.serialization.MapCodec$MapCodecCodec", remap = false)
abstract class RootCodecBodyMixin {
    @Inject(method = "decode(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;",
        at = @At("RETURN"), require = 1, allow = 1, remap = false)
    private void anchors$originalRootBody(DynamicOps<?> ops, Object input,
            CallbackInfoReturnable<DataResult<?>> callback) {
        if (!ArsDispatchScope.LIVE.activeArsParse()) return;
        if (!ArsDispatchScope.LIVE.confirmOriginalParseOnStack()) return;
        AnchorsFixtureGlm.originalRootBody(this, ops, input, callback.getReturnValue());
    }
}
