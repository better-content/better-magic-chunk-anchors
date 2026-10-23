package com.bettercontent.bettermagicchunkanchors.nativefixture.glmmixin;

import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.ArsDispatchScope;
import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.AnchorsFixtureGlm;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Existing PUBLIC Codec.decode implementation only; no Java/private-member reference to its private class. */
@Mixin(targets = "net.minecraftforge.registries.ForgeRegistry$RegistryCodec", remap = false)
abstract class RegistryCodecDecodeMixin {
    @Inject(method = "decode(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;",
        at = @At("RETURN"), require = 2, allow = 2, remap = false)
    private void anchors$selectedRegisteredDecoder(DynamicOps<?> ops, Object input,
            CallbackInfoReturnable<DataResult<?>> callback) {
        com.bettercontent.bettermagicchunkanchors.nativefixture.glm.OriginalCodecDispatchProof.registered(
            this, ops, input, callback.getReturnValue());
        if (!ArsDispatchScope.LIVE.activeArsParse()) return;
        if (!ArsDispatchScope.LIVE.confirmOriginalDispatchTrace()) return;
        AnchorsFixtureGlm.selectedRegisteredDecoder(this, ops, input, callback.getReturnValue());
    }
}
