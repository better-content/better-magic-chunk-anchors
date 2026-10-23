package com.bettercontent.bettermagicchunkanchors.nativefixture.glm;

import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.registries.RegisterEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Logger;

/** Diagnostic only. No callback/map mutation, locking, resolution, replacement or failure suppression. */
public final class RegistryCallbackTrace {
    private static final RegistryCallbackBudget BUDGET = new RegistryCallbackBudget();
    private RegistryCallbackTrace() {}

    public static void observe(String phase, Object source, String modid, Multimap<?, ?> callbacks,
            Logger originalLogger, RegisterEvent event) {
        // The whole observer is guarded, including binding/iteration/logger failures. In particular a
        // racing public view may throw CME: report that, never replace the original registry failure.
        try {
            String attempt = System.getProperty("bc.anchors.registryTrace.attempt", "");
            if (!RegistryCallbackBudget.bound(attempt, System.getProperty("bc.anchors.fixtureGlm.manifest.attempt"),
                    System.getProperty("bc.anchors.fixtureGlm.manifest.sha256", ""))) return;
            boolean before = phase.equals("before_register");
            // Always reserve pre-register observations; bootstrap/capture samples cannot consume them.
            if (before && !BUDGET.reserveRegister()) return;
            String context = prefix(attempt, phase, source, modid, callbacks, event)
                + " warn_enabled=" + originalLogger.isWarnEnabled();
            RegistryCallbackSnapshot snapshot;
            try {
                snapshot = RegistryCallbackSnapshot.read(callbacks.size(), callbacks.isEmpty(),
                    callbacks.asMap(), RegistryCallbackTrace::keyScalar);
            } catch (Throwable failure) {
                if (before || BUDGET.reserveCapture(true)) emit(originalLogger, context
                    + " projection_error=" + RegistryCallbackSnapshot.scalar(failure.getClass().getName()));
                return;
            }
            if (!before && !BUDGET.reserveCapture(snapshot.asymmetric())) return;
            emit(originalLogger, context + " " + snapshot.fields());
        } catch (Throwable ignored) {
            // Intentionally no throw or fallback: the original method, including its exception, runs.
        }
    }

    private static String prefix(String attempt, String phase, Object source, String modid,
            Multimap<?, ?> callbacks, RegisterEvent event) {
        Thread thread = Thread.currentThread();
        return "BC_ANCHORS_REGISTRY_CALLBACK_V1 attempt=" + attempt + " phase=" + phase
            + " manifest_sha256=" + System.getProperty("bc.anchors.fixtureGlm.manifest.sha256")
            + " at_nanos=" + System.nanoTime() + " source_class=" + RegistryCallbackSnapshot.scalar(source.getClass().getName())
            + " source_id=" + Integer.toUnsignedString(System.identityHashCode(source))
            + " map_id=" + Integer.toUnsignedString(System.identityHashCode(callbacks))
            + " thread_id=" + thread.getId() + " thread=" + RegistryCallbackSnapshot.scalar(thread.getName())
            + " modid=" + RegistryCallbackSnapshot.scalar(modid)
            + " registry=" + (event == null ? "none" : registryScalar(event.getRegistryKey()))
            + " nonatomic=true ordinary_limit=32 asymmetric_limit=32 register_limit=64";
    }

    private static String registryScalar(Object key) {
        // ResourceKey.location is a scalar ResourceLocation, not a registry entry/object resolution.
        return key instanceof ResourceKey<?> resource ? RegistryCallbackSnapshot.scalar(resource.location().toString()) : "unsupported";
    }

    private static String keyScalar(Object key) {
        // Never call Pair/RegistryObject/arbitrary-key toString or a deferred value supplier.
        if (key instanceof Pair<?, ?> pair && pair.getLeft() instanceof String name
                && pair.getRight() instanceof ResourceKey<?> registry)
            return RegistryCallbackSnapshot.scalar(name) + "@" + registryScalar(registry);
        return key == null ? "null" : RegistryCallbackSnapshot.scalar(key.getClass().getName());
    }

    private static void emit(Logger originalLogger, String line) {
        originalLogger.info(line); // Original native file-backed Log4j route; no private sink/configuration.
    }
}
