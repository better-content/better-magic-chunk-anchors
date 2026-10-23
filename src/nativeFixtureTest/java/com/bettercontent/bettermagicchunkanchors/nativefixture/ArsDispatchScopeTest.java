package com.bettercontent.bettermagicchunkanchors.nativefixture;

import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.ArsDispatchScope;
import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.DecodedRowProofRules;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

/** Actual scalar-state/predicate SOURCE controls. Synthetic scalar rows are NOT native/SDK observations. */
public final class ArsDispatchScopeTest {
    private static final String HASH = "1".repeat(64);
    private static final List<String> IDS = IntStream.range(0, 16).mapToObj(i -> i == 0 ? ArsDispatchScope.ARS : "control:row_" + i).toList();
    private static final List<String> TYPES = IntStream.range(0, 16).mapToObj(i -> i == 0 ? ArsDispatchScope.ARS : "control:type_" + i).toList();
    private static void before(ArsDispatchScope scope, int row) { scope.before(IDS.get(row), TYPES.get(row), HASH, true, IDS, TYPES); }
    private static void first(ArsDispatchScope scope) { before(scope, 0); scope.selectedDecoder(true, true); scope.originalRootBody(true, true); scope.after(IDS.get(0)); }
    private static void remaining(ArsDispatchScope scope) {
        for (int i = 1; i < 16; i++) { before(scope, i); assertFalse(scope.activeArsParse()); scope.after(IDS.get(i)); }
    }
    private static void rejected(ArsDispatchScope scope) { assertThrows(IllegalStateException.class, scope::finish); assertThrows(IllegalStateException.class, scope::assertClosed); }
    private static ArsDispatchScope.Trace decode(String owner) { return new ArsDispatchScope.Trace(owner, "decode", ArsDispatchScope.DECODE); }
    private static ArsDispatchScope.Trace parse() { return new ArsDispatchScope.Trace("com.mojang.serialization.Decoder", "parse", ArsDispatchScope.DECODE); }
    private static ArsDispatchScope.Trace apply() { return new ArsDispatchScope.Trace("net.minecraftforge.common.loot.LootModifierManager", "apply", ArsDispatchScope.APPLY); }
    private static List<ArsDispatchScope.Trace> registryTrace() {
        return List.of(decode("net.minecraftforge.registries.ForgeRegistry$RegistryCodec"),
            decode("net.minecraft.util.ExtraCodecs$LazyInitializedCodec"),
            new ArsDispatchScope.Trace("com.mojang.serialization.codecs.KeyDispatchCodec", "decode", "(Lcom/mojang/serialization/DynamicOps;Lcom/mojang/serialization/MapLike;)Lcom/mojang/serialization/DataResult;"),
            parse(), apply());
    }
    private static List<ArsDispatchScope.Trace> rootTrace() { return List.of(decode("com.mojang.serialization.MapCodec$MapCodecCodec"), parse(), apply()); }

    @Test public void completeOriginal16RequiresOneActualKeyAndRootResult() {
        var scope = new ArsDispatchScope(); first(scope); remaining(scope);
        assertEquals(new ArsDispatchScope.Proof(16, 1, 1), scope.finish()); scope.assertClosed();
        assertFalse(scope.activeArsParse());
    }
    @Test public void inactiveOutsideFrameIsCheapAndCannotSupplyProof() {
        var scope = new ArsDispatchScope();
        assertFalse(scope.activeArsParse()); assertFalse(scope.confirmOriginalDispatchTrace()); assertFalse(scope.confirmOriginalParseOnStack());
        scope.assertClosed();
        scope.selectedDecoder(true, true); rejected(scope);
    }
    @Test public void missingDuplicateAndReentrantCallbacksRejectAtBoundaryNotInOriginalParse() {
        var missing = new ArsDispatchScope(); before(missing, 0); assertDoesNotThrow(() -> missing.after(IDS.get(0))); rejected(missing);
        var unmatched = new ArsDispatchScope(); assertDoesNotThrow(() -> unmatched.after(IDS.get(0))); rejected(unmatched);
        var duplicate = new ArsDispatchScope(); before(duplicate, 0); duplicate.selectedDecoder(true, true);
        assertDoesNotThrow(() -> duplicate.selectedDecoder(true, true)); rejected(duplicate);
        var rootDuplicate = new ArsDispatchScope(); before(rootDuplicate, 0); rootDuplicate.selectedDecoder(true, true);
        rootDuplicate.originalRootBody(true, true); assertDoesNotThrow(() -> rootDuplicate.originalRootBody(true, true)); rejected(rootDuplicate);
        var reentrant = new ArsDispatchScope(); before(reentrant, 0); assertDoesNotThrow(() -> before(reentrant, 0)); rejected(reentrant);
    }
    @Test public void wrongRowTypeOrderInputAndRegistryFactsReject() {
        var row = new ArsDispatchScope(); row.before("control:wrong", TYPES.get(0), HASH, true, IDS, TYPES); rejected(row);
        var type = new ArsDispatchScope(); type.before(IDS.get(0), "control:wrong", HASH, true, IDS, TYPES); rejected(type);
        var input = new ArsDispatchScope(); input.before(IDS.get(0), TYPES.get(0), HASH, false, IDS, TYPES); rejected(input);
        var order = new ArsDispatchScope(); first(order); before(order, 2); rejected(order);
        var registry = new ArsDispatchScope(); before(registry, 0); registry.selectedDecoder(false, true); rejected(registry);
        var keyError = new ArsDispatchScope(); before(keyError, 0); keyError.selectedDecoder(true, false); rejected(keyError);
    }
    @Test public void rootMissingWrongOrBeforeKeyCannotQualify() {
        var missing = new ArsDispatchScope(); before(missing, 0); missing.selectedDecoder(true, true); missing.after(IDS.get(0)); rejected(missing);
        var wrong = new ArsDispatchScope(); before(wrong, 0); wrong.selectedDecoder(true, true); wrong.originalRootBody(false, true); rejected(wrong);
        var beforeKey = new ArsDispatchScope(); before(beforeKey, 0); beforeKey.originalRootBody(true, true); rejected(beforeKey);
        var partial = new ArsDispatchScope(); before(partial, 0); partial.selectedDecoder(true, true); partial.originalRootBody(true, false); rejected(partial);
        var late = new ArsDispatchScope(); first(late); late.originalRootBody(true, true); rejected(late);
        var bodyCoupling = new ArsDispatchScope(); before(bodyCoupling, 0); assertTrue(bodyCoupling.originalInput(HASH)); assertFalse(bodyCoupling.originalInput("2".repeat(64)));
        bodyCoupling.selectedDecoder(true, true); bodyCoupling.originalRootBody(bodyCoupling.originalInput("2".repeat(64)), true); rejected(bodyCoupling);
    }
    @Test public void actualPublicDataResultPartialWithBodyWrongNullImplementationAndWrongLazyReceiverReject() {
        var body = "pure-SOURCE-body";
        assertSame(body, DecodedRowProofRules.successfulPairValue(DataResult.success(Pair.of(body, "remainder"))));
        assertNull(DecodedRowProofRules.successfulPairValue(DataResult.error(() -> "error with partial", Pair.of(body, "remainder"))));
        assertNull(DecodedRowProofRules.successfulPairValue(DataResult.error(() -> "error without partial")));
        assertNull(DecodedRowProofRules.successfulPairValue(DataResult.success("not a Pair")));
        assertNull(DecodedRowProofRules.successfulPairValue(DataResult.success(Pair.of(null, "remainder"))));
        assertTrue(DecodedRowProofRules.exactBody(body, String.class));
        assertFalse(DecodedRowProofRules.exactBody(body, Integer.class)); assertFalse(DecodedRowProofRules.exactBody(null, String.class));
        Object direct = new Object(), lazyKeyCodec = new Object();
        assertTrue(DecodedRowProofRules.existingRootReceiver(direct, direct));
        assertFalse(DecodedRowProofRules.existingRootReceiver(lazyKeyCodec, direct));
    }
    @Test public void pinnedTraceRejectsNestedDecoyWrongDefaultOwnerAndAllowsActualSyntheticApplyBridge() {
        assertTrue(ArsDispatchScope.pinnedDispatchTrace(registryTrace())); assertTrue(ArsDispatchScope.pinnedRootTrace(rootTrace()));
        assertFalse(ArsDispatchScope.pinnedRootTrace(List.of(decode("net.minecraft.util.ExtraCodecs$LazyInitializedCodec"), parse(), apply())));
        var nested = new ArrayList<>(registryTrace()); nested.add(3, parse()); assertFalse(ArsDispatchScope.pinnedDispatchTrace(nested));
        var decoy = new ArrayList<>(registryTrace()); decoy.add(2, new ArsDispatchScope.Trace("control.Constructor", "body", "()V"));
        assertFalse(ArsDispatchScope.pinnedDispatchTrace(decoy));
        var wrongDefault = new ArrayList<>(rootTrace()); wrongDefault.set(1, new ArsDispatchScope.Trace("com.mojang.serialization.Codec", "parse", ArsDispatchScope.DECODE));
        assertFalse(ArsDispatchScope.pinnedRootTrace(wrongDefault));
        var bridge = new ArrayList<>(rootTrace()); bridge.add(new ArsDispatchScope.Trace("net.minecraftforge.common.loot.LootModifierManager", "apply", "(Ljava/lang/Object;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V"));
        assertTrue(ArsDispatchScope.pinnedRootTrace(bridge));
        var reentrant = new ArrayList<>(rootTrace()); reentrant.add(apply()); assertFalse(ArsDispatchScope.pinnedRootTrace(reentrant));
    }
    @Test public void exceptionUnmatchedAndStaleNextRowRemainClosedWithoutChangingOriginalError() {
        var scope = new ArsDispatchScope(); before(scope, 0);
        RuntimeException original = new RuntimeException("retained original native error model");
        RuntimeException seen = assertThrows(RuntimeException.class, () -> { throw original; }); assertSame(original, seen);
        assertThrows(IllegalStateException.class, scope::assertClosed); // no AFTER/RETURN occurred
        before(scope, 1); rejected(scope); assertFalse(scope.activeArsParse());
        var stale = new ArsDispatchScope(); before(stale, 0);
        assertFalse(stale.confirmOriginalParseOnStack()); rejected(stale); // actual JDK SOURCE caller is not original native parse
        var otherThread = new ArsDispatchScope();
        var thread = new Thread(() -> before(otherThread, 0), "source-scalar-unmatched-frame"); thread.start();
        assertDoesNotThrow(() -> thread.join(5000)); assertFalse(thread.isAlive());
        assertThrows(IllegalStateException.class, otherThread::assertClosed); // no thread-local-only false completeness
    }
    @Test public void jdkDescriptorMetadataWorksWithoutClassRetentionAndBudgetNeverRenews() {
        assertTrue(ArsDispatchScope.captureTrace().stream().allMatch(frame -> frame.descriptor().startsWith("(")));
        var scope = new ArsDispatchScope();
        for (int i = 0; i < 4; i++) { first(scope); remaining(scope); scope.finish(); scope.assertClosed(); }
        before(scope, 0); rejected(scope);
    }
}
