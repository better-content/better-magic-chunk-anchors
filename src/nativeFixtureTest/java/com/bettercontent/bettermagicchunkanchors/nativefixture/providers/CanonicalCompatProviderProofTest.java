package com.bettercontent.bettermagicchunkanchors.nativefixture.providers;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/** Tests read-only classification of supplied ASM metadata, not an original/native invocation. */
final class CanonicalCompatProviderProofTest {
    private static ClassNode complete() {
        var node = new ClassNode(); node.name = "com/tterrag/registrate/AbstractRegistrate";
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "field$generated$bc$registrateTransaction",
            "Lcom/bettercontent/bettercompatfixes/compat/registrate/RegistrateTransaction;", null, null));
        for (String name : List.of("bc$acceptScope", "bc$beginAccept", "bc$endAccept", "bc$addScope", "bc$beginAdd", "bc$endAdd", "bc$readTable", "bc$readAll")) {
            var method = new MethodNode(Opcodes.ACC_PRIVATE, "handler$generated$" + name, "()V", null, null);
            var annotation = new AnnotationNode("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;");
            annotation.values = new ArrayList<>(List.of("mixin", "com.bettercontent.bettercompatfixes.mixin.registrate.RegistrateTransactionMixin"));
            method.visibleAnnotations = new ArrayList<>(List.of(annotation)); node.methods.add(method);
        }
        return node;
    }
    @Test void completeFinalNodeHasExactFieldAndAllHandlerBits() {
        var result = CanonicalCompatProviderProof.inspect(complete());
        assertTrue(result.targetMatches()); assertEquals(1, result.fields()); assertEquals(255, result.handlers());
    }
    @Test void inspectionDoesNotMutateSuppliedNode() {
        var node = complete(); var field = node.fields.get(0); var method = node.methods.get(0); var annotation = method.visibleAnnotations.get(0);
        CanonicalCompatProviderProof.inspect(node);
        assertSame(field, node.fields.get(0)); assertSame(method, node.methods.get(0)); assertSame(annotation, method.visibleAnnotations.get(0));
        assertEquals(1, node.fields.size()); assertEquals(8, node.methods.size());
    }
    @Test void missingFieldIsNotComplete() {
        var node = complete(); node.fields.clear(); assertEquals(0, CanonicalCompatProviderProof.inspect(node).fields());
    }
    @Test void wrongFieldTypeAndFlagsDoNotCount() {
        var node = complete(); node.fields.get(0).desc = "Ljava/lang/Object;"; assertEquals(0, CanonicalCompatProviderProof.inspect(node).fields());
        node = complete(); node.fields.get(0).access = Opcodes.ACC_PUBLIC; assertEquals(0, CanonicalCompatProviderProof.inspect(node).fields());
    }
    @Test void duplicateTransactionFieldsRemainVisible() {
        var node = complete(); node.fields.add(node.fields.get(0)); assertEquals(2, CanonicalCompatProviderProof.inspect(node).fields());
    }
    @Test void methodNameWithoutActualOwnerAnnotationIsNotEvidence() {
        var node = complete(); node.methods.get(0).visibleAnnotations = null; assertEquals(254, CanonicalCompatProviderProof.inspect(node).handlers());
    }
    @Test void otherMixinAndAnnotationTypesCannotClaimProductionOwnership() {
        var node = complete(); node.methods.get(0).visibleAnnotations.get(0).values.set(1, "unrelated.Mixin");
        assertEquals(254, CanonicalCompatProviderProof.inspect(node).handlers());
        node = complete(); node.methods.get(0).visibleAnnotations.get(0).desc = "Lunrelated/MixinMerged;";
        assertEquals(254, CanonicalCompatProviderProof.inspect(node).handlers());
    }
    @Test void wrongTargetAndMissingHandlerAreExplicit() {
        var node = complete(); node.name = "unrelated/Target"; node.methods.remove(7);
        var result = CanonicalCompatProviderProof.inspect(node); assertFalse(result.targetMatches()); assertEquals(127, result.handlers());
    }
}
