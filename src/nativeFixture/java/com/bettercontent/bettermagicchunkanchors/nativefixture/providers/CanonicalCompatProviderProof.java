package com.bettercontent.bettermagicchunkanchors.nativefixture.providers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/** Canonical loaded-provider/actual merged-target binding, not a replacement implementation or historical-cause proof. */
@Mod.EventBusSubscriber(modid = "better_magic_chunk_anchors")
public final class CanonicalCompatProviderProof {
    private static final String PREFIX = "bc.anchors.providers.";
    private static final String SCHEMA = "bc.anchors.canonical_providers.v1";
    private static final String MIXIN = "com.bettercontent.bettercompatfixes.mixin.registrate.RegistrateTransactionMixin";
    private static final String FIELD = "Lcom/bettercontent/bettercompatfixes/compat/registrate/RegistrateTransaction;";
    private static final List<String> IDS = List.of("better_compat_fixes", "better_gameplay_notices");
    private static final List<String> REPOSITORIES = List.of("better-compat-fixes", "better-gameplay-notices");
    private static final List<String> ARTIFACTS = List.of("better-compat-fixes-0.1.9.jar", "better-gameplay-notices-1.0.0.jar");
    private static final List<String> HANDLERS = List.of("bc$acceptScope", "bc$beginAccept", "bc$endAccept", "bc$addScope", "bc$beginAdd", "bc$endAdd", "bc$readTable", "bc$readAll");
    private static final AtomicInteger APPLY_COUNT = new AtomicInteger();
    private static volatile Applied applied;
    record Applied(boolean targetMatches, int fields, int handlers, String error) { }
    private CanonicalCompatProviderProof() { }

    static void recordApplied(ClassNode node) {
        APPLY_COUNT.incrementAndGet();
        try {
            applied = inspect(node);
        } catch (RuntimeException | LinkageError error) {
            // This read-only observer must not replace the framework's transformation result/exception.
            applied = new Applied(false, 0, 0, error.getClass().getName());
        }
    }
    static Applied inspect(ClassNode node) {
        int fields = 0, mask = 0;
        for (var field : node.fields)
            if (FIELD.equals(field.desc) && (field.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)) == (Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)) fields++;
        for (var method : node.methods) {
            if (!mergedFromRepair(method)) continue;
            for (int i = 0; i < HANDLERS.size(); i++)
                if (method.name.contains(HANDLERS.get(i))) mask |= 1 << i;
        }
        return new Applied(node.name.equals("com/tterrag/registrate/AbstractRegistrate"), fields, mask, "");
    }
    private static boolean mergedFromRepair(MethodNode method) {
        return mergedFromRepair(method.visibleAnnotations) || mergedFromRepair(method.invisibleAnnotations);
    }
    private static boolean mergedFromRepair(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (var annotation : annotations) {
            if (!"Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;".equals(annotation.desc) || annotation.values == null) continue;
            for (int i = 0; i + 1 < annotation.values.size(); i += 2)
                if ("mixin".equals(annotation.values.get(i)) && MIXIN.equals(annotation.values.get(i + 1))) return true;
        }
        return false;
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static Path ordinary(Path path) {
        path = path.toAbsolutePath().normalize();
        for (Path part = path; part != null; part = part.getParent()) require(!Files.isSymbolicLink(part), "Symlink canonical provider input");
        require(Files.isRegularFile(path), "Missing canonical provider input");
        return path;
    }
    @SubscribeEvent public static void starting(ServerStartingEvent event) throws Exception {
        Path receipt = ordinary(Path.of(System.getProperty(PREFIX + "binding")));
        byte[] bytes = Files.readAllBytes(receipt);
        require(bytes.length <= 65536 && sha(bytes).equals(System.getProperty(PREFIX + "binding.sha256")), "Canonical provider receipt changed");
        var input = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        require(SCHEMA.equals(input.get("schema").getAsString()) && input.get("attempt").getAsString().equals(System.getProperty(PREFIX + "attempt")) &&
            receipt.getParent().getFileName().toString().equals(input.get("attempt").getAsString()), "Canonical provider attempt differs");
        var rows = input.getAsJsonArray("providers");
        require(rows.size() == IDS.size(), "Canonical provider population differs");
        var observed = new JsonArray();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i).getAsJsonObject();
            String id = row.get("mod_id").getAsString();
            Path artifact = ordinary(Path.of(row.get("path").getAsString()));
            require(IDS.get(i).equals(id) && ARTIFACTS.get(i).equals(row.get("artifact").getAsString()) && artifact.getFileName().toString().equals(ARTIFACTS.get(i)) &&
                sha(Files.readAllBytes(artifact)).equals(row.get("sha256").getAsString()), "Canonical provider identity/bytes differ");
            byte[] metadata;
            try (var zip = new ZipFile(artifact.toFile()); var stream = zip.getInputStream(zip.getEntry("META-INF/better-content-source.properties"))) { metadata = stream.readAllBytes(); }
            var properties = new Properties();
            properties.load(new java.io.ByteArrayInputStream(metadata));
            require(sha(metadata).equals(row.get("source_metadata_sha256").getAsString()) && "bc.custom_mod_source.v1".equals(properties.getProperty("schema")) &&
                id.equals(properties.getProperty("mod_id")) && REPOSITORIES.get(i).equals(properties.getProperty("repository")), "Canonical source identity differs");
            var loaded = ModList.get().getModFileById(id);
            require(loaded != null && ModList.get().isLoaded(id), "Canonical provider not actually loaded");
            require(Arrays.equals(metadata, Files.readAllBytes(loaded.getFile().findResource("META-INF", "better-content-source.properties"))), "Loaded mapped provider source metadata differs");
            var item = row.deepCopy(); item.addProperty("loadedSourceMetadataMatches", true); observed.add(item);
        }
        Applied snapshot = applied;
        require(APPLY_COUNT.get() == 1 && snapshot != null && snapshot.targetMatches && snapshot.fields == 1 && snapshot.handlers == 255 && snapshot.error.isEmpty(),
            "Actual Registrate target lacks complete canonical production transaction merge");
        var proof = new JsonObject();
        proof.addProperty("schema", SCHEMA); proof.addProperty("attempt", input.get("attempt").getAsString()); proof.addProperty("bindingSha256", sha(bytes));
        proof.addProperty("actualTargetObserved", true); proof.addProperty("productionTransactionFields", snapshot.fields); proof.addProperty("productionHandlerMask", snapshot.handlers);
        proof.addProperty("diagnosticOnlyNotHistoricalCause", true); proof.add("providers", observed);
        Path output = receipt.resolveSibling("canonical-compat-proof.json");
        Files.writeString(output, proof.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println("BC_ANCHORS_CANONICAL_COMPAT_V1 attempt=" + input.get("attempt").getAsString() + " proof_sha256=" + sha(Files.readAllBytes(output)));
    }
}
