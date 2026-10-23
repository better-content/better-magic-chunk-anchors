package com.bettercontent.bettermagicchunkanchors.nativefixture.glmmixin;

import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.RegistryCallbackTrace;
import com.google.common.collect.Multimap;
import net.minecraftforge.registries.RegisterEvent;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pinned Registrate 1.3.3 only. Observers forward unconditionally to the unchanged original body. */
@Mixin(targets = "com.tterrag.registrate.AbstractRegistrate", remap = false)
abstract class RegistryCallbackTraceMixin {
    @Shadow @Final private Multimap<?, ?> registerCallbacks;
    @Shadow @Final private String modid;
    @Shadow @Final private static Logger log;

    @Inject(method = "<init>(Ljava/lang/String;)V", at = @At("RETURN"), require = 1, allow = 1, remap = false)
    private void anchors$registryConstructed(CallbackInfo callback) {
        try { RegistryCallbackTrace.observe("constructed", this, modid, registerCallbacks, log, null); }
        catch (Throwable ignored) { /* No observer failure can replace the original method. */ }
    }

    @Inject(method = "onRegister(Lnet/minecraftforge/registries/RegisterEvent;)V", at = @At("HEAD"), require = 1, allow = 1, remap = false)
    private void anchors$beforeRegister(RegisterEvent event, CallbackInfo callback) {
        try { RegistryCallbackTrace.observe("before_register", this, modid, registerCallbacks, log, event); }
        catch (Throwable ignored) { /* Preserve the original checks and DEV throw. */ }
    }

    @Inject(method = "addRegisterCallback(Ljava/lang/String;Lnet/minecraft/resources/ResourceKey;Lcom/tterrag/registrate/util/nullness/NonNullConsumer;)Lcom/tterrag/registrate/AbstractRegistrate;",
        at = @At("RETURN"), require = 1, allow = 1, remap = false)
    private void anchors$callbackAdded(CallbackInfoReturnable<?> callback) {
        try { RegistryCallbackTrace.observe("after_add_callback", this, modid, registerCallbacks, log, null); }
        catch (Throwable ignored) { /* Observe only; never change the original return value. */ }
    }

    @Inject(method = "accept(Ljava/lang/String;Lnet/minecraft/resources/ResourceKey;Lcom/tterrag/registrate/builders/Builder;Lcom/tterrag/registrate/util/nullness/NonNullSupplier;Lcom/tterrag/registrate/util/nullness/NonNullFunction;)Lcom/tterrag/registrate/util/entry/RegistryEntry;",
        at = @At("RETURN"), require = 1, allow = 1, remap = false)
    private void anchors$registrationCaptured(CallbackInfoReturnable<?> callback) {
        try { RegistryCallbackTrace.observe("after_accept", this, modid, registerCallbacks, log, null); }
        catch (Throwable ignored) { /* Original removeAll/callback capture has already returned. */ }
    }
}
