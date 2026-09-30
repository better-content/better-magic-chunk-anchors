package com.bettercontent.bettermagicchunkanchors;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class ReflectionBoundaryTest {
    private static final List<String> SOURCE_MARKERS = List.of(
            "java.lang.reflect", "kotlin.reflect", "Class.forName(", ".getMethod(",
            ".getDeclaredMethod(", ".getField(", ".getDeclaredField(", ".getConstructor(",
            ".getDeclaredConstructor(", ".setAccessible(", ".trySetAccessible(",
            "Proxy.newProxyInstance(", "MethodHandles", "VarHandle", "sun.misc.Unsafe",
            "jdk.internal.misc.Unsafe");
    private static final List<String> BINARY_MARKERS = List.of(
            "java/lang/reflect", "kotlin/reflect", "forName", "getMethod", "getDeclaredMethod",
            "getField", "getDeclaredField", "getConstructor", "getDeclaredConstructor",
            "setAccessible", "trySetAccessible", "newProxyInstance", "java/lang/invoke/VarHandle",
            "sun/misc/Unsafe", "jdk/internal/misc/Unsafe");

    @Test
    void productionSourcesDoNotUseReflection() throws IOException {
        try (var paths = Files.walk(Path.of("src"))) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> !file.getFileName().toString().equals("ReflectionBoundaryTest.java"))
                    .toList()) {
                String source = Files.readString(path);
                for (String marker : SOURCE_MARKERS) assertFalse(source.contains(marker), path + ": " + marker);
            }
        }
    }

    @Test
    void compiledProductionClassesDoNotReferenceReflection() throws IOException {
        Path classes = Path.of("build/classes");
        try (var paths = Files.walk(classes)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".class") && !file.toString().contains("/test/"))
                    .toList()) {
                String bytecode = new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.ISO_8859_1);
                for (String marker : BINARY_MARKERS) assertFalse(bytecode.contains(marker), path + ": " + marker);
            }
        }
    }
}
