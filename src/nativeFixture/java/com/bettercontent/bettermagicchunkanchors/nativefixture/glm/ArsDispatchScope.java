package com.bettercontent.bettermagicchunkanchors.nativefixture.glm;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** B-local original16/Ars-only scalar bracket. No JSON, codec, registry, receiver or world retained. */
public final class ArsDispatchScope {
    public static final ArsDispatchScope LIVE = new ArsDispatchScope();
    public static final String ARS = "ars_nouveau:dungeon_loot";
    public static final String DECODE = "(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;";
    public static final String APPLY = "(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V";
    private static final int ROWS = 16, APPLIES = 4, STACK_LIMIT = 64;
    // At most four original frames. Scalar thread IDs only; inactive lookups never insert null entries.
    private final ConcurrentHashMap<Long, Frame> frames = new ConcurrentHashMap<>();
    private final AtomicInteger starts = new AtomicInteger();
    private final AtomicInteger active = new AtomicInteger();
    private volatile boolean failed;
    private static final class Frame {
        final List<String> ids, types;
        int completed, keyWitnesses, bodyWitnesses;
        boolean parsing;
        String inputHash;
        Frame(List<String> ids, List<String> types) { this.ids = List.copyOf(ids); this.types = List.copyOf(types); }
    }
    public record Trace(String owner, String method, String descriptor) {}
    public record Proof(int originalParseBrackets, int selectedArsDecoderReturns, int originalRootBodyReturns) {}
    // No reset/teardown budget renewal. Independent SOURCE tests construct independent controllers.
    public ArsDispatchScope() {}
    public void reject() { failed = true; discard(); }
    private Frame current() { return frames.get(Thread.currentThread().getId()); }
    private void discard() {
        if (frames.remove(Thread.currentThread().getId()) != null) active.decrementAndGet();
    }
    public void before(String id, String type, String inputHash, boolean actualOriginalInput,
            List<String> ids, List<String> types) {
        if (failed) { discard(); return; }
        Frame frame = current();
        if (frame == null) {
            if (ids.size() != ROWS || types.size() != ROWS || !ARS.equals(ids.get(0)) || !ARS.equals(types.get(0))
                    || !ARS.equals(id) || starts.incrementAndGet() > APPLIES) { reject(); return; }
            frame = new Frame(ids, types); frames.put(Thread.currentThread().getId(), frame); active.incrementAndGet();
        }
        if (frame.parsing || frame.completed >= ROWS || !actualOriginalInput || inputHash == null || inputHash.length() != 64
                || !frame.ids.equals(ids) || !frame.types.equals(types)
                || !frame.ids.get(frame.completed).equals(id) || !frame.types.get(frame.completed).equals(type)) {
            reject(); return;
        }
        frame.inputHash = inputHash; frame.parsing = true;
    }
    /** Early bootstrap path: ONLY bounded scalar/JDK state, no fixture/Ars/FML lookup. */
    public boolean activeArsParse() {
        if (failed || active.get() == 0) return false;
        Frame frame = current();
        return frame != null && frame.parsing && frame.completed == 0;
    }
    public boolean originalInput(String hash) {
        Frame frame = current();
        return activeArsParse() && frame.inputHash.equals(hash);
    }
    public static List<Trace> captureTrace() {
        // Descriptor metadata does not request RETAIN_CLASS_REFERENCE, declaring Class or MethodType.
        return StackWalker.getInstance().walk(stream -> stream.limit(STACK_LIMIT)
            .map(frame -> new Trace(frame.getClassName(), frame.getMethodName(), frame.getDescriptor())).toList());
    }
    private static int count(List<Trace> trace, Trace wanted) { return (int) trace.stream().filter(wanted::equals).count(); }
    public static boolean pinnedDispatchTrace(List<Trace> trace) {
        Trace registry = new Trace("net.minecraftforge.registries.ForgeRegistry$RegistryCodec", "decode", DECODE);
        Trace lazy = new Trace("net.minecraft.util.ExtraCodecs$LazyInitializedCodec", "decode", DECODE);
        Trace dispatch = new Trace("com.mojang.serialization.codecs.KeyDispatchCodec", "decode",
            "(Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/MapLike;)Lcom/mojang/serialization/DataResult;");
        Trace parse = new Trace("com.mojang.serialization.Decoder", "parse", DECODE); // inherited default, not Codec.parse
        Trace apply = new Trace("net.minecraftforge.common.loot.LootModifierManager", "apply", APPLY);
        if (trace.size() > STACK_LIMIT || count(trace, registry) != 1 || count(trace, lazy) != 1
                || count(trace, dispatch) != 1 || count(trace, parse) != 1 || count(trace, apply) != 1) return false;
        int i = trace.indexOf(registry), p = trace.indexOf(parse), a = trace.indexOf(apply);
        // Direct pinned parents reject constructor/body decoys; descriptors distinguish the legitimate apply(Object) bridge.
        return i + 2 < trace.size() && trace.get(i + 1).equals(lazy) && trace.get(i + 2).equals(dispatch)
            && i + 2 < p && p + 1 == a;
    }
    public static boolean pinnedRootTrace(List<Trace> trace) {
        Trace root = new Trace("com.mojang.serialization.MapCodec$MapCodecCodec", "decode", DECODE);
        Trace parse = new Trace("com.mojang.serialization.Decoder", "parse", DECODE);
        Trace apply = new Trace("net.minecraftforge.common.loot.LootModifierManager", "apply", APPLY);
        if (trace.size() > STACK_LIMIT || count(trace, root) != 1 || count(trace, parse) != 1 || count(trace, apply) != 1) return false;
        int i = trace.indexOf(root);
        return i + 2 < trace.size() && trace.get(i + 1).equals(parse) && trace.get(i + 2).equals(apply);
    }
    public static boolean originalParseOnStack(List<Trace> trace) {
        Trace parse = new Trace("com.mojang.serialization.Decoder", "parse", DECODE);
        Trace apply = new Trace("net.minecraftforge.common.loot.LootModifierManager", "apply", APPLY);
        if (trace.size() > STACK_LIMIT || count(trace, apply) != 1) return false;
        int a = trace.indexOf(apply);
        return a > 0 && trace.get(a - 1).equals(parse);
    }
    /** Stale exception frames cannot resolve any FML/Ars helper in a later unrelated callback. */
    public boolean confirmOriginalParseOnStack() {
        if (!activeArsParse()) return false;
        if (!originalParseOnStack(captureTrace())) { reject(); return false; }
        return true;
    }
    public boolean confirmOriginalDispatchTrace() {
        if (!activeArsParse()) return false;
        if (!pinnedDispatchTrace(captureTrace())) { reject(); return false; }
        return true;
    }
    public static boolean pinnedRootResultTrace(List<Trace> trace) {
        Trace apply = new Trace("net.minecraftforge.common.loot.LootModifierManager", "apply", APPLY);
        Trace callback = new Trace("com.bettercontent.bettermagicchunkanchors.nativefixture.glm.AnchorsFixtureGlm",
            "originalRootBody", "(Ljava/lang/Object;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;Lcom/mojang/serialization/DataResult;)V");
        if (trace.size() > STACK_LIMIT || count(trace, apply) != 1 || count(trace, callback) != 1) return false;
        int a = trace.indexOf(apply);
        if (a < 2 || !trace.get(a - 2).equals(callback)) return false;
        Trace redirect = trace.get(a - 1);
        return redirect.owner().equals(apply.owner()) && redirect.method().endsWith("$anchors$originalRootBody")
            && redirect.descriptor().equals("(Lcom/mojang/serialization/DataResult;Ljava/util/function/Consumer;Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)Ljava/util/Optional;")
            && trace.stream().noneMatch(t -> t.owner().equals("com.mojang.serialization.Decoder") && t.method().equals("parse"));
    }
    public boolean confirmOriginalRootTrace() {
        if (!activeArsParse()) return false;
        if (!pinnedRootResultTrace(captureTrace())) { reject(); return false; }
        return true;
    }
    public void selectedDecoder(boolean correctReceiverOpsKeyAndRegisteredValue, boolean fullSuccessfulResult) {
        Frame frame = current();
        if (!activeArsParse() || !correctReceiverOpsKeyAndRegisteredValue || !fullSuccessfulResult
                || ++frame.keyWitnesses != 1) reject();
    }
    public void originalRootBody(boolean exactDirectReceiverOpsInputAndClass, boolean fullSuccessfulResult) {
        Frame frame = current();
        if (!activeArsParse() || !exactDirectReceiverOpsInputAndClass || !fullSuccessfulResult
                || frame.keyWitnesses != 1 || ++frame.bodyWitnesses != 1) reject();
    }
    public void after(String id) {
        Frame frame = current();
        if (failed || frame == null || !frame.parsing || !frame.ids.get(frame.completed).equals(id)
                || (frame.completed == 0 && (frame.keyWitnesses != 1 || frame.bodyWitnesses != 1))) { reject(); return; }
        frame.parsing = false; frame.inputHash = null; frame.completed++;
    }
    /** Existing apply RETURN boundary. Observers never replace original parse/results/exceptions. */
    public Proof finish() {
        Frame frame = current(); discard();
        if (failed || frame == null || frame.parsing || frame.completed != ROWS || frame.keyWitnesses != 1 || frame.bodyWitnesses != 1)
            throw new IllegalStateException("Ars original apply/row/parse/key/decoder/body completeness failed");
        return new Proof(frame.completed, frame.keyWitnesses, frame.bodyWitnesses);
    }
    /** Catches exception/unmatched frames even if original apply did not RETURN on this thread. */
    public void assertClosed() {
        if (failed || active.get() != 0) throw new IllegalStateException("Failed/unclosed original Ars dispatch frame");
    }
}
