package com.bettercontent.bettermagicchunkanchors.nativefixture.glmmixin;

import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.ArsDispatchScope;
import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.AnchorsFixtureGlm;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifierManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Observe the original root parse's DataResult at its Forge extraction call site, not a preloaded DFU class. */
@Mixin(value = LootModifierManager.class, remap = false)
abstract class RootCodecBodyMixin {
    @Redirect(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/DataResult;resultOrPartial(Ljava/util/function/Consumer;)Ljava/util/Optional;"),
        require = 1, allow = 1, remap = false)
    private <A> Optional<A> anchors$originalRootBody(DataResult<A> original, Consumer<String> onError,
            Map<ResourceLocation, JsonElement> definitions, ResourceManager resources, ProfilerFiller profiler) {
        if (ArsDispatchScope.LIVE.activeArsParse()) {
            AnchorsFixtureGlm.originalRootBody(IGlobalLootModifier.DIRECT_CODEC, JsonOps.INSTANCE,
                definitions.get(new ResourceLocation(ArsDispatchScope.ARS)), original);
        }
        // Preserve the native result, error consumer, exceptions and exact extraction invocation.
        return original.resultOrPartial(onError);
    }
}
