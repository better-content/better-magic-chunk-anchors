package com.bettercontent.bettermagicchunkanchors.nativefixture;

import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.RegistryCallbackSnapshot;
import com.bettercontent.bettermagicchunkanchors.nativefixture.glm.RegistryCallbackBudget;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Reads class bytes only: no Forge/Registrate class loading or initialization. */
public final class RegistryCallbackTraceTest {
    private static final String OWNER = "com/tterrag/registrate/AbstractRegistrate";
    private static final String BASE = "com/bettercontent/bettermagicchunkanchors/nativefixture/";
    private static final String ADD = "(Ljava/lang/String;Lnet/minecraft/resources/ResourceKey;Lcom/tterrag/registrate/util/nullness/NonNullConsumer;)Lcom/tterrag/registrate/AbstractRegistrate;";
    private static final String ACCEPT = "(Ljava/lang/String;Lnet/minecraft/resources/ResourceKey;Lcom/tterrag/registrate/builders/Builder;Lcom/tterrag/registrate/util/nullness/NonNullSupplier;Lcom/tterrag/registrate/util/nullness/NonNullFunction;)Lcom/tterrag/registrate/util/entry/RegistryEntry;";
    private static final Map<String, String> TARGETS = Map.of(
        "anchors$registryConstructed", "<init>(Ljava/lang/String;)V",
        "anchors$beforeRegister", "onRegister(Lnet/minecraftforge/registries/RegisterEvent;)V",
        "anchors$callbackAdded", "addRegisterCallback" + ADD,
        "anchors$registrationCaptured", "accept" + ACCEPT);

    private static ClassNode node(byte[] bytes) {
        var n = new ClassNode(); new ClassReader(bytes).accept(n, 0); return n;
    }
    private static byte[] sdk() throws Exception {
        try (var input = ClassLoader.getSystemResourceAsStream(OWNER + ".class")) {
            assertNotNull(input); return input.readAllBytes();
        }
    }
    private static ClassNode compiled(String name) throws Exception {
        List<Path> matches = new ArrayList<>();
        for (var dir : System.getProperty("bc.anchors.glm.compiledClasses").split(java.io.File.pathSeparator)) {
            Path path = Path.of(dir).resolve(BASE + name + ".class");
            if (Files.isRegularFile(path)) matches.add(path);
        }
        assertEquals(1, matches.size()); return node(Files.readAllBytes(matches.get(0)));
    }
    private static MethodNode method(ClassNode n, String target) {
        return n.methods.stream().filter(m -> (m.name + m.desc).equals(target)).findFirst().orElseThrow();
    }
    private static Object value(AnnotationNode a, String key) {
        int index = a.values.indexOf(key); return index < 0 ? null : a.values.get(index + 1);
    }
    private static List<MethodInsnNode> calls(MethodNode m) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (var i : m.instructions) if (i instanceof MethodInsnNode c) calls.add(c);
        return calls;
    }
    private static void original(byte[] bytes, String hash) throws Exception {
        assertEquals(hash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        var n = node(bytes);
        for (var target : TARGETS.values()) {
            var m = method(n, target);
            long returns = 0; for (var i : m.instructions) if (i.getOpcode() == (target.startsWith("<init>") ? Opcodes.RETURN : Opcodes.ARETURN)) returns++;
            if (!target.startsWith("onRegister")) assertEquals(1, returns, target);
        }
        var onRegister = method(n, TARGETS.get("anchors$beforeRegister"));
        var registerCalls = calls(onRegister);
        assertEquals("getRegistryKey", registerCalls.get(0).name);
        assertEquals(1, registerCalls.stream().filter(c -> c.name.equals("isEmpty") && c.owner.equals("com/google/common/collect/Multimap")).count());
        assertEquals(1, registerCalls.stream().filter(c -> c.name.equals("asMap")).count());
        assertEquals(1, registerCalls.stream().filter(c -> c.name.equals("clear")).count());
        assertEquals(1, calls(method(n, "addRegisterCallback" + ADD)).stream().filter(c -> c.owner.equals("com/google/common/collect/Multimap") && c.name.equals("put")).count());
        assertEquals(1, calls(method(n, "accept" + ACCEPT)).stream().filter(c -> c.owner.equals("com/google/common/collect/Multimap") && c.name.equals("removeAll")).count());
        for (var field : Map.of("modid", "Ljava/lang/String;", "registerCallbacks", "Lcom/google/common/collect/Multimap;", "log", "Lorg/apache/logging/log4j/Logger;").entrySet())
            assertTrue(n.fields.stream().anyMatch(f -> f.name.equals(field.getKey()) && f.desc.equals(field.getValue())));
    }
    @Test public void rawAndMappedPinnedTargetsIndependentlyMatch() throws Exception {
        try (var zip = new ZipFile(System.getProperty("bc.anchors.registryTrace.rawRegistrate"))) {
            original(zip.getInputStream(zip.getEntry(OWNER + ".class")).readAllBytes(), "7bfa4965a2cf3fc790227e6d94bb47d5b9a898b532f4f489570db2ae158c4147");
        }
        original(sdk(), "a2052e843b729659493d725127556df6931ddf5a3aab2937ef1e48cf1318034f");
        var wrong = sdk(); wrong[wrong.length - 1] ^= 1;
        assertThrows(AssertionError.class, () -> original(wrong, "a2052e843b729659493d725127556df6931ddf5a3aab2937ef1e48cf1318034f"));
    }
    private static void observer(ClassNode mixin) {
        var annotation = mixin.invisibleAnnotations.stream().filter(a -> a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
        assertEquals(List.of(OWNER.replace('/', '.')), value(annotation, "targets"));
        assertEquals(Boolean.FALSE, value(annotation, "remap"));
        for (var target : TARGETS.entrySet()) {
            var handler = mixin.methods.stream().filter(m -> m.name.equals(target.getKey())).findFirst().orElseThrow();
            assertTrue((handler.access & Opcodes.ACC_PRIVATE) != 0);
            var inject = handler.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
            assertEquals(List.of(target.getValue()), value(inject, "method"));
            assertEquals(1, value(inject, "require")); assertEquals(1, value(inject, "allow"));
            assertEquals(Boolean.FALSE, value(inject, "remap"));
            assertNotEquals(Boolean.TRUE, value(inject, "cancellable"));
            var at = (AnnotationNode) ((List<?>) value(inject, "at")).get(0);
            assertEquals(target.getKey().equals("anchors$beforeRegister") ? "HEAD" : "RETURN", value(at, "value"));
            assertTrue(handler.tryCatchBlocks.stream().anyMatch(t -> t.type.equals("java/lang/Throwable")));
            assertEquals(1, calls(handler).size());
            assertEquals(BASE + "glm/RegistryCallbackTrace", calls(handler).get(0).owner);
            assertEquals("observe", calls(handler).get(0).name);
            for (var i : handler.instructions) {
                assertNotEquals(Opcodes.ATHROW, i.getOpcode());
                assertFalse(i instanceof FieldInsnNode f && (f.getOpcode() == Opcodes.PUTFIELD || f.getOpcode() == Opcodes.PUTSTATIC));
            }
        }
    }
    @Test public void compiledRequiredNoncancellingObserversAndNegativeControls() throws Exception {
        observer(compiled("glmmixin/RegistryCallbackTraceMixin"));
        var broken = compiled("glmmixin/RegistryCallbackTraceMixin");
        var handler = broken.methods.stream().filter(m -> m.name.equals("anchors$beforeRegister")).findFirst().orElseThrow();
        var a = handler.visibleAnnotations.stream().filter(x -> x.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        a.values.set(a.values.indexOf("require") + 1, 0);
        assertThrows(AssertionError.class, () -> observer(broken));
        var trace = compiled("glm/RegistryCallbackTrace");
        for (var m : trace.methods) for (var c : calls(m)) {
            assertFalse(List.of("getValue", "get", "clear", "put", "removeAll", "setReturnValue", "cancel", "synchronizedMap").contains(c.name), c.name);
            assertFalse(c.owner.equals("net/minecraftforge/registries/RegistryObject"));
        }
        var observe = trace.methods.stream().filter(m -> m.name.equals("observe")).findFirst().orElseThrow();
        assertTrue(observe.tryCatchBlocks.stream().anyMatch(t -> t.type.equals("java/lang/Throwable")));
        assertTrue(calls(observe).stream().anyMatch(c -> c.name.equals("isWarnEnabled")));
        assertTrue(trace.methods.stream().flatMap(m -> calls(m).stream()).anyMatch(c -> c.owner.equals("org/apache/logging/log4j/Logger") && c.name.equals("info")));
    }
    @Test public void purePublicViewProjectionShowsAccountingAsymmetryWithoutResolution() {
        var source = new LinkedHashMap<Object, Collection<?>>(); source.put("entry", List.of(1, 2));
        var before = new LinkedHashMap<>(source);
        var good = RegistryCallbackSnapshot.read(2, false, source, k -> (String) k);
        assertFalse(good.asymmetric()); assertEquals(2, good.collectionSum()); assertEquals(before, source);
        assertTrue(RegistryCallbackSnapshot.read(1, false, Map.of(), k -> "unused").asymmetric());
        assertTrue(RegistryCallbackSnapshot.read(3, false, source, k -> "entry").asymmetric());
        Object hostile = new Object() { @Override public String toString() { throw new AssertionError("Must not resolve"); } };
        assertDoesNotThrow(() -> RegistryCallbackSnapshot.read(1, false, Map.of(hostile, List.of(1)), k -> k.getClass().getName()));
        var large = new LinkedHashMap<Object, Collection<?>>();
        for (int i = 0; i < 129; i++) large.put("k" + i, List.of(1));
        var truncated = RegistryCallbackSnapshot.read(129, false, large, k -> (String) k);
        assertTrue(truncated.truncated()); assertFalse(truncated.asymmetric()); assertEquals(128, truncated.visitedKeys());
        assertFalse(RegistryCallbackSnapshot.scalar("unsafe\n key").contains("\n"));
    }
    @Test public void pureNonceAndIndependentQuotasAreBounded() {
        String attempt = "71e8fcae-65a5-4cbb-9cc1-440b9b046d5e", hash = "a".repeat(64);
        assertTrue(RegistryCallbackBudget.bound(attempt, attempt, hash));
        assertFalse(RegistryCallbackBudget.bound(attempt, "foreign", hash));
        assertFalse(RegistryCallbackBudget.bound("", "", hash));
        assertFalse(RegistryCallbackBudget.bound(attempt, attempt, "stale"));
        var budget = new RegistryCallbackBudget();
        int ordinary = 0, asymmetric = 0, register = 0;
        // Saturating bootstrap observations must not consume the pre-register reserve.
        for (int i = 0; i < 1000; i++) if (budget.reserveCapture(false)) ordinary++;
        for (int i = 0; i < 1000; i++) if (budget.reserveCapture(true)) asymmetric++;
        for (int i = 0; i < 1000; i++) if (budget.reserveRegister()) register++;
        assertEquals(32, ordinary); assertEquals(32, asymmetric); assertEquals(64, register);
        assertEquals(128, ordinary + asymmetric + register);
        assertFalse(budget.reserveCapture(false)); assertFalse(budget.reserveCapture(true)); assertFalse(budget.reserveRegister());
    }
    public static void main(String[] args) throws Exception {
        var test = new RegistryCallbackTraceTest();
        test.rawAndMappedPinnedTargetsIndependentlyMatch();
        test.compiledRequiredNoncancellingObserversAndNegativeControls();
        test.purePublicViewProjectionShowsAccountingAsymmetryWithoutResolution();
        test.pureNonceAndIndependentQuotasAreBounded();
        System.out.println("RegistryCallbackTraceTest: 4 pure controls passed; no native launch/acceptance");
    }
}
