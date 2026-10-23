package com.bettercontent.bettermagicchunkanchors;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReflectionBoundaryTest {
    private static final List<String> SOURCE_MARKERS = List.of(
            "java.lang.reflect", "kotlin.reflect", "Class.forName(", ".getMethod(",
            ".getDeclaredMethod(", ".getField(", ".getDeclaredField(", ".getConstructor(",
            ".getDeclaredConstructor(", ".setAccessible(", ".trySetAccessible(",
            "Proxy.newProxyInstance(", "MethodHandles", "VarHandle", "sun.misc.Unsafe",
            "jdk.internal.misc.Unsafe");
    private static final List<String> BINARY_MARKERS = List.of(
            "java/lang/reflect", "kotlin/reflect", "java/lang/invoke/VarHandle",
            "sun/misc/Unsafe", "jdk/internal/misc/Unsafe");
    private static final List<String> REFLECTION_METHODS = List.of(
            "forName", "getMethod", "getMethods", "getDeclaredMethod", "getDeclaredMethods",
            "getField", "getFields", "getDeclaredField", "getDeclaredFields",
            "getConstructor", "getConstructors", "getDeclaredConstructor", "getDeclaredConstructors",
            "setAccessible", "trySetAccessible", "newProxyInstance");

    // JVM symbolic names are separate UTF8 constants: getMethodName is not Class.getMethod.
    // Keep namespace/descriptor scanning, and match complete API names rather than byte substrings.
    private static List<String> classTokens(byte[] bytes) throws IOException {
        var result = new ArrayList<String>();
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != 0xCAFEBABE) throw new IOException("Invalid class file");
            input.readUnsignedShort(); input.readUnsignedShort();
            int size = input.readUnsignedShort();
            for (int i = 1; i < size; i++) {
                switch (input.readUnsignedByte()) {
                    case 1 -> result.add(input.readUTF());
                    case 3, 4, 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
                    case 5, 6 -> { input.skipNBytes(8); i++; }
                    case 7, 8, 16, 19, 20 -> input.skipNBytes(2);
                    case 15 -> input.skipNBytes(3);
                    default -> throw new IOException("Unknown constant-pool tag");
                }
            }
        }
        return result;
    }

    private static List<Path> runtimeRoots(String kind) {
        String value = System.getProperty("bc.anchors.reflection." + kind + "Roots");
        assertTrue(value != null && !value.isBlank(), "Missing declared runtime audit roots: " + kind);
        var roots = Arrays.stream(value.split(Pattern.quote(File.pathSeparator))).map(Path::of).toList();
        assertTrue(roots.stream().anyMatch(path -> path.toString().contains("main")), "Main output is mandatory");
        assertTrue(roots.stream().anyMatch(path -> path.toString().contains("nativeFixture")), "Native runtime hooks remain audited");
        assertFalse(roots.stream().anyMatch(path -> path.toString().contains("nativeFixtureTest")), "JUnit ABI controls are not runtime classes");
        return roots;
    }

    private static List<String> findings(List<Path> roots, List<String> markers, boolean binary) throws IOException {
        var result = new ArrayList<String>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .filter(file -> !binary || file.toString().endsWith(".class")).toList()) {
                    byte[] bytes = Files.readAllBytes(path);
                    var tokens = binary ? classTokens(bytes) : List.of(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                    for (String body : tokens) {
                        for (String marker : markers) if (body.contains(marker)) result.add(path + ": " + marker);
                        if (binary && REFLECTION_METHODS.contains(body)) result.add(path + ": " + body);
                    }
                }
            }
        }
        return result;
    }

    @Test
    void productionSourcesDoNotUseReflection() throws IOException {
        assertEquals(List.of(), findings(runtimeRoots("source"), SOURCE_MARKERS, false));
    }

    @Test
    void compiledProductionClassesDoNotReferenceReflection() throws IOException {
        assertEquals(List.of(), findings(runtimeRoots("class"), BINARY_MARKERS, true));
    }

    private static final class ForbiddenForName {
        static Class<?> find(String name) throws ClassNotFoundException { return Class.forName(name); }
    }
    private static final class AllowedStackMetadata {
        static String name(StackWalker.StackFrame frame) { return frame.getMethodName(); }
    }

    @Test
    void stackMethodNameMetadataIsNotAReflectionLookup() throws IOException {
        Path root = Path.of("build/reflection-boundary-controls/allowed");
        Files.createDirectories(root);
        try (var input = ReflectionBoundaryTest.class.getResourceAsStream("ReflectionBoundaryTest$AllowedStackMetadata.class")) {
            Files.write(root.resolve("Allowed.class"), input.readAllBytes());
        }
        assertEquals(List.of(), findings(List.of(root), BINARY_MARKERS, true));
    }

    @Test
    void auditRejectsReflectionInBothMainAndNativeRuntimeRoles() throws IOException {
        // Retained pure controls outside class outputs; no authored input or prior evidence is removed.
        for (String role : List.of("main", "nativeFixture")) {
            Path root = Path.of("build/reflection-boundary-controls", role);
            Files.createDirectories(root);
            Files.writeString(root.resolve("Reflected.java"), "Class.forName(\"example\");");
            try (var input = ReflectionBoundaryTest.class.getResourceAsStream("ReflectionBoundaryTest$ForbiddenForName.class")) {
                Files.write(root.resolve("Reflected.class"), input.readAllBytes());
            }
            assertFalse(findings(List.of(root), SOURCE_MARKERS, false).isEmpty(), role);
            assertFalse(findings(List.of(root), BINARY_MARKERS, true).isEmpty(), role);
        }
    }
}
