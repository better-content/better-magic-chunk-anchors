package com.bettercontent.bettermagicchunkanchors.nativefixture;

import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.DecodedRowProofRules;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Actual compiled owning handler/dispatcher + shared predicate controls. Never starts Forge or decodes GLMs. */
public final class FixtureGlmAbiTest {
    private static final String ROOT = "com/bettercontent/bettermagicchunkanchors/nativefixture/";
    private static final String HELPER = ROOT + "glm/AnchorsFixtureGlm";
    private static final String MIXIN = ROOT + "glmmixin/FixtureGlmLoadMixin";
    private static final String RESERVED = ROOT + "glmmixin/";
    private static final String DISPATCH = "(Lnet/minecraftforge/common/loot/LootModifierManager;Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;)V";
    private static final String TARGET = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V";
    private static final String HANDLER = "(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V";
    private static byte[] bytes(String name) throws Exception {
        List<Path> found = new ArrayList<>();
        for (String root : System.getProperty("bc.anchors.glm.compiledClasses").split(java.io.File.pathSeparator)) {
            Path path = Path.of(root).resolve(name + ".class");
            if (Files.isRegularFile(path)) found.add(path);
        }
        assertEquals(1, found.size(), "Must inspect exactly the actual compiled owning class");
        return Files.readAllBytes(found.get(0));
    }
    private static ClassNode node(String name) throws Exception {
        var node = new ClassNode(); new ClassReader(bytes(name)).accept(node, 0); return node;
    }
    private static MethodNode method(ClassNode node, String name) {
        return node.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }
    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (var instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }
    private static Object annotationValue(AnnotationNode annotation, String name) {
        int at = annotation.values.indexOf(name); return at < 0 ? null : annotation.values.get(at + 1);
    }
    private static void shape(ClassNode mixin, ClassNode helper) {
        assertFalse(helper.name.startsWith(RESERVED), "Ordinary helpers must be outside the ENTIRE reserved prefix");
        assertTrue((helper.access & Opcodes.ACC_PUBLIC) != 0);
        MethodNode dispatch = method(helper, "atNativeApplyReturn");
        assertEquals(DISPATCH, dispatch.desc);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED,
            dispatch.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNCHRONIZED));
        MethodNode handler = method(mixin, "anchors$observeActualNativeReturn");
        assertEquals(HANDLER, handler.desc);
        assertEquals(Opcodes.ACC_PRIVATE, handler.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED));
        var inject = handler.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        assertEquals(List.of(TARGET), annotationValue(inject, "method"));
        assertEquals(1, annotationValue(inject, "require"));
        assertEquals(Boolean.FALSE, annotationValue(inject, "remap"));
        var at = (AnnotationNode) ((List<?>) annotationValue(inject, "at")).get(0);
        assertEquals("RETURN", annotationValue(at, "value"));
        var instructions = real(handler);
        assertEquals(List.of(Opcodes.ALOAD, Opcodes.CHECKCAST, Opcodes.ALOAD, Opcodes.ALOAD, Opcodes.INVOKESTATIC, Opcodes.RETURN),
            instructions.stream().map(AbstractInsnNode::getOpcode).toList());
        assertEquals(0, ((VarInsnNode) instructions.get(0)).var);
        assertEquals(1, ((VarInsnNode) instructions.get(2)).var); // original apply Map, not a manufactured input
        assertEquals(2, ((VarInsnNode) instructions.get(3)).var); // original ResourceManager
        var call = (MethodInsnNode) instructions.get(4);
        assertEquals(helper.name, call.owner); assertEquals(dispatch.name, call.name); assertEquals(dispatch.desc, call.desc);
        for (var m : helper.methods) for (var instruction : m.instructions) if (instruction instanceof MethodInsnNode invoke) {
            assertFalse(invoke.owner.startsWith("java/lang/reflect/"));
            assertFalse(invoke.owner.startsWith("com/mojang/serialization/") && List.of("parse", "decode").contains(invoke.name), "No second decode");
            assertFalse(invoke.owner.equals("net/minecraftforge/common/loot/LootModifierManager") && invoke.name.equals("apply"), "No second apply");
        }
        var code = real(dispatch);
        int literal = -1;
        for (int i = 0; i < code.size(); i++) if (code.get(i) instanceof LdcInsnNode ldc && "ars_nouveau:dungeon_loot".equals(ldc.cst)) literal = i;
        assertTrue(literal >= 0);
        assertEquals("equals", ((MethodInsnNode) code.get(literal + 1)).name);
        var branch = (JumpInsnNode) code.get(literal + 2);
        assertEquals(Opcodes.IFEQ, branch.getOpcode());
        boolean field = false, getter = false, rule = false, skipsMemoizedArm = false;
        for (var i = branch.getNext(); i != branch.label; i = i.getNext()) {
            assertNotNull(i);
            if (i instanceof FieldInsnNode f && f.owner.equals("com/hollingsworth/arsnouveau/setup/registry/LootRegistry")) {
                assertEquals("STRUCTURE_MODDED_LOOT_IMPORTER", f.name); field = true;
            }
            if (i instanceof MethodInsnNode m) {
                assertFalse(m.owner.equals("net/minecraftforge/common/loot/IGlobalLootModifier") && m.name.equals("codec"), "Never call Ars fresh codec accessor");
                if (m.owner.equals("net/minecraftforge/registries/RegistryObject") && m.name.equals("get")) getter = true;
                if (m.owner.endsWith("/DecodedRowProofRules") && m.name.equals("arsRegistration")) rule = true;
            }
            if (i instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.GOTO) skipsMemoizedArm = true;
        }
        assertTrue(field && getter && rule && skipsMemoizedArm);
        assertEquals(1, code.stream().filter(i -> i instanceof MethodInsnNode m && m.owner.equals("net/minecraftforge/common/loot/IGlobalLootModifier") && m.name.equals("codec")).count(), "Other eight types keep direct memoized identity route");
    }

    @Test public void actualCompiledPrivateHandlerPublicDispatcherAndArsOnlyBranch() throws Exception { shape(node(MIXIN), node(HELPER)); }
    @Test public void inaccessibleOrReservedHelperAndWrongReceiverAbiReject() throws Exception {
        var h = node(HELPER); method(h, "atNativeApplyReturn").access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
        assertThrows(AssertionError.class, () -> shape(node(MIXIN), h));
        var reserved = node(HELPER); reserved.name = RESERVED + "BadOrdinaryHelper";
        assertThrows(AssertionError.class, () -> shape(node(MIXIN), reserved));
        var wrong = node(MIXIN); method(wrong, "anchors$observeActualNativeReturn").desc = "()V";
        assertThrows(AssertionError.class, () -> shape(wrong, node(HELPER)));
    }
    @Test public void wrongRegistrationAndWrongDispatchBranchReject() throws Exception {
        var registration = node(HELPER);
        for (var i : method(registration, "atNativeApplyReturn").instructions)
            if (i instanceof FieldInsnNode f && f.name.equals("STRUCTURE_MODDED_LOOT_IMPORTER")) f.name = "WRONG_REGISTRATION";
        assertThrows(AssertionError.class, () -> shape(node(MIXIN), registration));
        var dispatch = node(HELPER); var code = real(method(dispatch, "atNativeApplyReturn"));
        for (int i = 0; i < code.size(); i++) if (code.get(i) instanceof LdcInsnNode ldc && "ars_nouveau:dungeon_loot".equals(ldc.cst)) ((JumpInsnNode) code.get(i + 2)).setOpcode(Opcodes.IFNE);
        assertThrows(AssertionError.class, () -> shape(node(MIXIN), dispatch));
    }
    @Test public void realSharedPredicatesRejectWrongKeyRegistrationBodyAndImplementation() {
        var body = JsonParser.parseString("{\"type\":\"ars_nouveau:dungeon_loot\",\"conditions\":[]}");
        var changed = JsonParser.parseString("{\"type\":\"ars_nouveau:dungeon_loot\",\"conditions\":[{}]}");
        var registered = new Object();
        DecodedRowProofRules.arsRegistration("ars_nouveau:dungeon_loot", registered, registered, "ars_nouveau:dungeon_loot");
        assertThrows(IllegalStateException.class, () -> DecodedRowProofRules.arsRegistration("other:key", registered, registered, "other:key"));
        assertThrows(IllegalStateException.class, () -> DecodedRowProofRules.arsRegistration("ars_nouveau:dungeon_loot", registered, new Object(), "ars_nouveau:dungeon_loot"));
        assertThrows(IllegalStateException.class, () -> DecodedRowProofRules.arsRegistration("ars_nouveau:dungeon_loot", registered, registered, "ars_nouveau:wrong_key"));
        DecodedRowProofRules.inputAndImplementation("ars_nouveau:dungeon_loot", "actual.pinned.Implementation", "actual.pinned.Implementation", body, body);
        assertThrows(IllegalStateException.class, () -> DecodedRowProofRules.inputAndImplementation("ars_nouveau:dungeon_loot", "actual.pinned.Implementation", "wrong.Implementation", body, body));
        assertThrows(IllegalStateException.class, () -> DecodedRowProofRules.inputAndImplementation("ars_nouveau:dungeon_loot", "actual.pinned.Implementation", "actual.pinned.Implementation", changed, body));
        assertThrows(IllegalStateException.class, () -> DecodedRowProofRules.inputAndImplementation("ars_nouveau:wrong_key", "actual.pinned.Implementation", "actual.pinned.Implementation", body, body));
    }
}
