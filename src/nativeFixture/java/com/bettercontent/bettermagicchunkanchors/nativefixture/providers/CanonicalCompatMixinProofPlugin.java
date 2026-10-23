package com.bettercontent.bettermagicchunkanchors.nativefixture.providers;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** SOURCE only: inspect the framework-supplied final node, never change it or load a target. */
public final class CanonicalCompatMixinProofPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> own, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (target.equals("com.tterrag.registrate.AbstractRegistrate") && mixin.endsWith(".RegistryCallbackTraceMixin"))
            CanonicalCompatProviderProof.recordApplied(node);
    }
}
