package com.bettercontent.bettermagicchunkanchors.nativefixture;

import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.ArsDispatchScope;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Real pinned SDK targets + actual compiled observers; no target-class initialization or native invocation. */
public final class ArsDecodeObserverAbiTest {
    private static final String BASE = "com/bettercontent/bettermagicchunkanchors/nativefixture/";
    private static final String HELPER = BASE + "glm/AnchorsFixtureGlm";
    private static final String SCOPE = BASE + "glm/ArsDispatchScope";
    private static final String TARGET = "decode" + ArsDispatchScope.DECODE;
    private static byte[] sdk(String name) throws Exception {
        try (var input = ClassLoader.getSystemResourceAsStream(name + ".class")) { assertNotNull(input); return input.readAllBytes(); }
    }
    private static ClassNode node(byte[] bytes) { var node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES); return node; }
    private static ClassNode compiled(String name) throws Exception {
        List<Path> files = new ArrayList<>();
        for (String dir : System.getProperty("bc.anchors.glm.compiledClasses").split(java.io.File.pathSeparator)) {
            var file = Path.of(dir).resolve(name + ".class"); if (Files.isRegularFile(file)) files.add(file);
        }
        assertEquals(1, files.size()); return node(Files.readAllBytes(files.get(0)));
    }
    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(m -> m.name.equals(name) && (descriptor == null || m.desc.equals(descriptor))).findFirst().orElseThrow();
    }
    private static Object value(AnnotationNode annotation, String name) {
        int index = annotation.values.indexOf(name); return index < 0 ? null : annotation.values.get(index + 1);
    }
    private static AnnotationNode inject(MethodNode method) { return method.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow(); }
    private static List<AbstractInsnNode> real(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>(); for (var i : method.instructions) if (i.getOpcode() >= 0) result.add(i); return result;
    }
    private static List<MethodInsnNode> calls(MethodNode method) {
        return real(method).stream().filter(i -> i instanceof MethodInsnNode).map(i -> (MethodInsnNode) i).toList();
    }
    private static void observer(ClassNode mixin, String name, int returns, String dispatcher, String guard) {
        var targetAnnotation = mixin.invisibleAnnotations.stream().filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        assertEquals(List.of(dispatcher.equals("selectedRegisteredDecoder") ? "net.minecraftforge.registries.ForgeRegistry$RegistryCodec" : "com.mojang.serialization.MapCodec$MapCodecCodec"), value(targetAnnotation, "targets"));
        assertEquals(Boolean.FALSE, value(targetAnnotation, "remap"));
        MethodNode handler = method(mixin, name, null);
        assertEquals("(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", handler.desc);
        assertEquals(Opcodes.ACC_PRIVATE, handler.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED));
        AnnotationNode annotation = inject(handler);
        assertEquals(List.of(TARGET), value(annotation, "method"));
        assertEquals(returns, value(annotation, "require")); assertEquals(returns, value(annotation, "allow"));
        assertEquals(Boolean.FALSE, value(annotation, "remap"));
        assertEquals("RETURN", value((AnnotationNode) ((List<?>) value(annotation, "at")).get(0), "value"));
        assertEquals(1, calls(handler).stream().filter(c -> c.owner.equals(BASE + "glm/OriginalCodecDispatchProof") && c.name.equals("registered")).count());
        var calls = calls(handler).stream().filter(c -> !c.owner.equals(BASE + "glm/OriginalCodecDispatchProof")
            && !c.owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable")).toList();
        assertEquals(SCOPE, calls.get(0).owner); assertEquals("activeArsParse", calls.get(0).name);
        assertEquals(SCOPE, calls.get(1).owner); assertEquals(guard, calls.get(1).name);
        assertTrue(real(handler).subList(0, real(handler).indexOf(calls.get(1))).stream().anyMatch(i -> i.getOpcode() == Opcodes.RETURN), "Inactive early callback must return before helper/FML/input lookup");
        var target = calls.stream().filter(m -> m.owner.equals(HELPER)).findFirst().orElseThrow();
        assertEquals(dispatcher, target.name);
        assertEquals("(Ljava/lang/Object;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;Lcom/mojang/serialization/DataResult;)V", target.desc);
        for (var i : real(handler)) {
            assertNotEquals(Opcodes.ATHROW, i.getOpcode());
            if (i instanceof MethodInsnNode m) assertFalse(List.of("cancel", "setReturnValue", "decode", "parse", "apply").contains(m.name));
            if (i instanceof FieldInsnNode f) assertNotEquals("this$0", f.name);
            if (i instanceof TypeInsnNode t) assertFalse(t.desc.contains("ForgeRegistry$RegistryCodec"), "No private-class Java cast/type");
        }
    }

    private static void rootObserver(ClassNode mixin) {
        var target = mixin.invisibleAnnotations.stream().filter(a -> a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
        assertEquals(List.of(org.objectweb.asm.Type.getObjectType("net/minecraftforge/common/loot/LootModifierManager")), value(target, "value"));
        var handler = method(mixin, "anchors$originalRootBody", null);
        var annotation = handler.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Redirect;")).findFirst().orElseThrow();
        assertEquals(List.of("apply" + ArsDispatchScope.APPLY), value(annotation, "method"));
        assertEquals(1, value(annotation, "require")); assertEquals(1, value(annotation, "allow"));
        var at = (AnnotationNode) value(annotation, "at");
        assertEquals("INVOKE", value(at, "value"));
        assertEquals("Lcom/mojang/serialization/DataResult;resultOrPartial(Ljava/util/function/Consumer;)Ljava/util/Optional;", value(at, "target"));
        var calls = calls(handler);
        assertEquals(BASE + "glm/OriginalCodecDispatchProof", calls.get(0).owner); assertEquals("row", calls.get(0).name);
        assertEquals(1, calls.stream().filter(c -> c.owner.equals(BASE + "glm/OriginalCodecDispatchProof") && c.name.equals("body")).count());
        assertEquals(1, calls.stream().filter(c -> c.owner.equals(HELPER) && c.name.equals("originalRootBody")).count());
        var extraction = calls.stream().filter(c -> c.owner.equals("com/mojang/serialization/DataResult") && c.name.equals("resultOrPartial")).toList();
        assertEquals(1, extraction.size());
        var instructions = real(handler); int i = instructions.indexOf(extraction.get(0));
        assertEquals(1, ((VarInsnNode) instructions.get(i - 2)).var);
        assertEquals(2, ((VarInsnNode) instructions.get(i - 1)).var);
        assertEquals(Opcodes.ARETURN, instructions.get(i + 1).getOpcode());
        assertFalse(calls.stream().anyMatch(c -> List.of("parse", "decode", "cancel", "setReturnValue", "apply").contains(c.name)));
        assertTrue(instructions.stream().noneMatch(insn -> insn.getOpcode() == Opcodes.ATHROW));
    }

    @Test public void actualPinnedGoetyAddLootAccessorIsNotMemoized() throws Exception {
        var goety = node(sdk("com/Polarice3/Goety/common/loot/AddItemLootModifier"));
        assertEquals(1, calls(method(goety, "codec", null)).stream().filter(c -> c.owner.equals("java/util/function/Supplier") && c.name.equals("get")).count());
        assertFalse(calls(method(goety, "<clinit>", null)).stream().anyMatch(c -> c.name.equals("memoize")));
        assertTrue(goety.methods.stream().flatMap(m -> calls(m).stream()).anyMatch(c ->
            c.owner.equals("com/mojang/serialization/codecs/RecordCodecBuilder") && c.name.equals("create")));
    }
    @Test public void actualPublicTargetsOriginalCallCardinalityAndLiveTypedLocals() throws Exception {
        String registry = "net/minecraftforge/registries/ForgeRegistry$RegistryCodec", root = "com/mojang/serialization/MapCodec$MapCodecCodec";
        for (String owner : List.of(registry, root)) {
            byte[] raw = sdk(owner); var method = method(node(raw), "decode", ArsDispatchScope.DECODE);
            assertEquals(Opcodes.ACC_PUBLIC, method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED));
            assertEquals(owner.equals(registry) ? 2 : 1, real(method).stream().filter(i -> i.getOpcode() == Opcodes.ARETURN).count());
            if (owner.equals(root)) assertEquals("d93dce192f408cc96859d964bd8f69f1e8f232c8b2f526e367fea81ff9fe9d7a", java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        }
        var apply = method(node(sdk("net/minecraftforge/common/loot/LootModifierManager")), "apply", ArsDispatchScope.APPLY);
        var instructions = real(apply);
        var parse = calls(apply).stream().filter(m -> m.owner.equals("com/mojang/serialization/Codec") && m.name.equals("parse") && m.desc.equals(ArsDispatchScope.DECODE)).toList();
        assertEquals(1, parse.size()); int i = instructions.indexOf(parse.get(0));
        assertEquals("DIRECT_CODEC", ((FieldInsnNode) instructions.get(i - 3)).name);
        assertEquals("net/minecraftforge/common/loot/IGlobalLootModifier", ((FieldInsnNode) instructions.get(i - 3)).owner);
        assertEquals("INSTANCE", ((FieldInsnNode) instructions.get(i - 2)).name);
        assertEquals(9, ((VarInsnNode) instructions.get(i - 1)).var);
        int point = apply.instructions.indexOf(parse.get(0));
        FrameNode frame = null;
        for (int n = 0; n < point; n++) if (apply.instructions.get(n) instanceof FrameNode f) frame = f;
        assertNotNull(frame);
        assertEquals(List.of("com/google/common/collect/ImmutableMap$Builder", "java/util/List", "net/minecraft/resources/ResourceLocation", "java/util/Iterator"), frame.local.subList(4, 8));
        for (String name : List.of("location", "json")) {
            var var = apply.localVariables.stream().filter(v -> v.name.equals(name) && apply.instructions.indexOf(v.start) <= point && point < apply.instructions.indexOf(v.end)).findFirst().orElseThrow();
            assertEquals(name.equals("location") ? 8 : 9, var.index);
            assertEquals(name.equals("location") ? "Lnet/minecraft/resources/ResourceLocation;" : "Lcom/google/gson/JsonElement;", var.desc);
        }
        var decoderParse = method(node(sdk("com/mojang/serialization/Decoder")), "parse", ArsDispatchScope.DECODE);
        assertEquals(1, calls(decoderParse).stream().filter(m -> m.name.equals("decode")).count());
        assertEquals(1, calls(decoderParse).stream().filter(m -> m.owner.equals("com/mojang/serialization/DataResult") && m.name.equals("map")).count());
        assertTrue(real(decoderParse).stream().anyMatch(insn -> insn instanceof InvokeDynamicInsnNode indy
            && java.util.Arrays.stream(indy.bsmArgs).anyMatch(arg -> arg instanceof org.objectweb.asm.Handle h
                && h.getOwner().equals("com/mojang/datafixers/util/Pair") && h.getName().equals("getFirst"))));
        var rootDecode = method(node(sdk(root)), "decode", ArsDispatchScope.DECODE);
        assertEquals(1, calls(rootDecode).stream().filter(m -> m.name.equals("compressedDecode")).count());
        assertEquals(1, calls(rootDecode).stream().filter(m -> m.owner.equals("com/mojang/serialization/DataResult") && m.name.equals("map")).count());
        assertEquals(1, calls(apply).stream().filter(m -> m.owner.equals("com/mojang/serialization/DataResult") && m.name.equals("resultOrPartial")).count());
        assertEquals(1, calls(apply).stream().filter(m -> m.owner.equals("java/util/Optional") && m.name.equals("ifPresent")).count());
        var put = method(node(sdk("net/minecraftforge/common/loot/LootModifierManager")), "lambda$apply$1", null);
        assertEquals("(Lcom/google/common/collect/ImmutableMap$Builder;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraftforge/common/loot/IGlobalLootModifier;)V", put.desc);
        assertTrue(calls(put).stream().anyMatch(m -> m.owner.equals("com/google/common/collect/ImmutableMap$Builder") && m.name.equals("put")));
    }
    @Test public void actualCompiledObserversBracketOrderPrivateHandlersAndCheapInactiveGuards() throws Exception {
        observer(compiled(BASE + "glmmixin/RegistryCodecDecodeMixin"), "anchors$selectedRegisteredDecoder", 2, "selectedRegisteredDecoder", "confirmOriginalDispatchTrace");
        rootObserver(compiled(BASE + "glmmixin/RootCodecBodyMixin"));
        var owner = compiled(BASE + "glmmixin/FixtureGlmLoadMixin");
        for (String name : List.of("anchors$beforeOriginalParse", "anchors$afterOriginalParse")) {
            var handler = method(owner, name, null); var annotation = inject(handler);
            assertEquals(List.of("apply" + ArsDispatchScope.APPLY), value(annotation, "method"));
            assertEquals(1, value(annotation, "require")); assertEquals(1, value(annotation, "allow"));
            var at = (AnnotationNode) ((List<?>) value(annotation, "at")).get(0);
            assertEquals("INVOKE", value(at, "value"));
            assertEquals(name.contains("before") ? "Lcom/mojang/serialization/Codec;parse" + ArsDispatchScope.DECODE
                : "Lcom/mojang/serialization/DataResult;resultOrPartial(Ljava/util/function/Consumer;)Ljava/util/Optional;", value(at, "target"));
            assertEquals(name.contains("before") ? "BEFORE" : "AFTER", ((String[]) value(at, "shift"))[1]);
            assertEquals("CAPTURE_FAILHARD", ((String[]) value(annotation, "locals"))[1]);
            String captured = "Lcom/google/common/collect/ImmutableMap$Builder;Ljava/util/List;Lnet/minecraft/resources/ResourceLocation;Ljava/util/Iterator;Lnet/minecraft/resources/ResourceLocation;";
            assertEquals("(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;" + captured + (name.contains("before") ? "Lcom/google/gson/JsonElement;" : "") + ")V", handler.desc);
            assertFalse(calls(handler).stream().anyMatch(c -> c.name.equals("parse") || c.name.equals("decode")));
        }
        var generic = compiled(BASE + "glm/OriginalCodecDispatchProof");
        var genericRegistered = real(method(generic, "registered", null));
        var firstCall = calls(method(generic, "registered", null)).get(0);
        assertEquals(generic.name, firstCall.owner); assertEquals("active", firstCall.name);
        int nativeLookup = -1;
        for (int n = 0; n < genericRegistered.size(); n++) if (genericRegistered.get(n) instanceof FieldInsnNode f
            && f.owner.equals("net/minecraftforge/registries/ForgeRegistries")) { nativeLookup = n; break; }
        assertTrue(nativeLookup > 0);
        assertTrue(genericRegistered.subList(0, nativeLookup).stream().anyMatch(i -> i.getOpcode() == Opcodes.RETURN),
            "Generic inactive registry callbacks must return before Forge lookup");
        var scalarScope = compiled(SCOPE);
        assertFalse(scalarScope.fields.stream().anyMatch(f -> f.desc.contains("ThreadLocal")), "Inactive ThreadLocal.get must not create bootstrap entries");
        assertFalse(calls(method(scalarScope, "activeArsParse", null)).stream().anyMatch(m -> List.of("put", "set", "compute", "computeIfAbsent").contains(m.name)));
        for (var m : scalarScope.methods) for (var call : calls(m))
            assertFalse(call.owner.startsWith("net/minecraft") || call.owner.startsWith("com/hollingsworth"), "Scalar bootstrap guard must not link FML/Minecraft/Ars APIs");
        var helper = compiled(HELPER);
        for (String name : List.of("selectedRegisteredDecoder", "originalRootBody", "beforeOriginalParse", "afterOriginalParse")) {
            var callback = method(helper, name, null);
            assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, callback.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE));
            assertFalse(real(callback).stream().anyMatch(i -> i.getOpcode() == Opcodes.ATHROW), "Completeness failures must wait for proof boundary, preserving original native error");
        }
        var root = method(helper, "originalRootBody", null);
        assertTrue(real(root).stream().anyMatch(i -> i instanceof FieldInsnNode f && f.owner.equals("net/minecraftforge/common/loot/IGlobalLootModifier") && f.name.equals("DIRECT_CODEC")));
        assertTrue(calls(root).stream().anyMatch(m -> m.name.equals("successfulParseValue")));
        assertTrue(calls(root).stream().anyMatch(m -> m.name.equals("existingRootReceiver")));
        var selected = method(helper, "selectedRegisteredDecoder", null);
        assertTrue(calls(selected).stream().anyMatch(m -> m.name.equals("getCodec") && m.desc.equals("()Lcom/mojang/serialization/Codec;")));
        assertTrue(calls(selected).stream().anyMatch(m -> m.name.equals("getValue")));
        assertTrue(real(selected).stream().anyMatch(i -> i instanceof FieldInsnNode f && f.owner.equals("com/hollingsworth/arsnouveau/setup/registry/LootRegistry") && f.name.equals("STRUCTURE_MODDED_LOOT_IMPORTER")));
        assertFalse(calls(root).stream().anyMatch(m -> m.name.equals("codec")), "Never manufacture a wrapper/fresh Ars codec");
    }
    @Test public void wrongGuardOrTargetCardinalityRejectsActualCompiledObserver() throws Exception {
        var wrong = compiled(BASE + "glmmixin/RegistryCodecDecodeMixin");
        var handler = method(wrong, "anchors$selectedRegisteredDecoder", null);
        var annotation = inject(handler); annotation.values.set(annotation.values.indexOf("require") + 1, 1);
        assertThrows(AssertionError.class, () -> observer(wrong, "anchors$selectedRegisteredDecoder", 2, "selectedRegisteredDecoder", "confirmOriginalDispatchTrace"));
        var guard = compiled(BASE + "glmmixin/RootCodecBodyMixin");
        calls(method(guard, "anchors$originalRootBody", null)).get(0).name = "wrongInactiveGuard";
        assertThrows(AssertionError.class, () -> rootObserver(guard));
    }
}
