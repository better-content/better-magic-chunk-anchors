package com.bettercontent.bettermagicchunkanchors.nativefixture;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

/** Nonshipping, gameTestServer-only transport of an already approved exact policy snapshot. */
public final class CanonicalPolicyFixture {
    public static final String MOD_ID = "better_magic_chunk_anchors";
    public static final String PACK_ID = "zzzz_bc_source_anchors_malum_policy";
    public static final String MANIFEST_SHA256 = "07b0208546fdde2e1c74bd4d336a39b4a690d5ec2fd231904bbdc17269a36186";
    public static final String INPUT_PROPERTY = "bc.source.anchors.canonicalInputs";
    public static final String RUN_PROPERTY = "bc.source.anchors.nativeDirectory";
    public static final String PROOF_FILE = "native-canonical-malum-resources.json";

    private CanonicalPolicyFixture() {}

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    static Path propertyPath(String name) {
        String value = System.getProperty(name);
        require(value != null && !value.isBlank(), "Missing configured native fixture property: " + name);
        Path path = Path.of(value);
        require(path.isAbsolute(), "Native fixture path must be absolute");
        return path.normalize();
    }

    static record Inputs(Path tokenRoot, Path packRoot, Path run, JsonObject manifest) {
        static Inputs read(Path tokenRoot) throws IOException {
            byte[] raw = Files.readAllBytes(tokenRoot.resolve("manifest.json"));
            require(MANIFEST_SHA256.equals(sha256(raw)), "Unapproved canonical manifest");
            JsonObject manifest = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject prepared = JsonParser.parseString(Files.readString(tokenRoot.resolve("preparation.json"))).getAsJsonObject();
            require(manifest.get("count").getAsInt() == 36 && manifest.getAsJsonArray("files").size() == 36,
                    "Canonical input population changed");
            require(PACK_ID.equals(prepared.get("pack_id").getAsString())
                            && MANIFEST_SHA256.equals(prepared.get("manifest_sha256").getAsString())
                            && tokenRoot.getFileName().toString().equals(prepared.get("token").getAsString())
                            && prepared.getAsJsonObject("registration").get("exit").getAsInt() == 0,
                    "Missing token-bound registered input preparation");
            Path packRoot = tokenRoot.resolve("pack");
            require(manifest.get("fixture_pack_metadata_sha256").getAsString().equals(sha256(Files.readAllBytes(packRoot.resolve("pack.mcmeta")))),
                    "Canonical format15 metadata changed");
            for (var item : manifest.getAsJsonArray("files")) {
                JsonObject row = item.getAsJsonObject();
                Path path = packRoot.resolve(row.get("pack_path").getAsString()).normalize();
                require(path.startsWith(packRoot) && !Files.isSymbolicLink(path), "Escaping/symlinked canonical resource");
                require(row.get("sha256").getAsString().equals(sha256(Files.readAllBytes(path))), "Canonical raw input changed");
            }
            return new Inputs(tokenRoot, packRoot, Path.of(prepared.get("actual_configured_run").getAsString()), manifest);
        }
    }

    static Pack makePack(Path packRoot) {
        return Pack.create(PACK_ID, Component.literal("Canonical source Malum policy (nonshipping)"), true,
                id -> new PathPackResources(id, packRoot, true),
                new Pack.Info(Component.literal("Approved exact36 authored inputs"), 15, FeatureFlagSet.of()),
                PackType.SERVER_DATA, Pack.Position.TOP, true, PackSource.BUILT_IN);
    }

    /** Reads the EFFECTIVE selected Resource objects, not candidate files or namespace guesses. */
    static JsonObject verifySelectedResources(Inputs inputs, Collection<Pack> selected, ResourceManager manager) throws IOException {
        List<Pack> order = List.copyOf(selected);
        require(!order.isEmpty() && PACK_ID.equals(order.get(order.size() - 1).getId()),
                "Canonical pack is not the effective highest selected pack");
        Pack canonical = order.get(order.size() - 1);
        require(canonical.isRequired() && canonical.isFixedPosition() && canonical.getDefaultPosition() == Pack.Position.TOP,
                "Canonical pack lost required/fixed TOP contract");
        JsonArray selectedIds = new JsonArray();
        for (Pack pack : order) selectedIds.add(pack.getId());
        JsonArray resources = new JsonArray();
        for (var item : inputs.manifest().getAsJsonArray("files")) {
            JsonObject row = item.getAsJsonObject();
            ResourceLocation id = new ResourceLocation(row.get("resource_id").getAsString());
            Resource resource = manager.getResource(id).orElseThrow(() -> new IllegalStateException("Missing effective canonical resource: " + id));
            require(PACK_ID.equals(resource.sourcePackId()), "Wrong effective canonical sourcePackId: " + id);
            String actual;
            try (var stream = resource.open()) {
                actual = sha256(stream.readAllBytes());
            }
            require(row.get("sha256").getAsString().equals(actual), "Wrong effective canonical raw SHA256: " + id);
            JsonObject proof = new JsonObject();
            proof.addProperty("resource_id", id.toString());
            proof.addProperty("source_pack_id", resource.sourcePackId());
            proof.addProperty("sha256", actual);
            resources.add(proof);
        }
        require(resources.size() == 36, "Incomplete effective resource proof");
        JsonObject proof = new JsonObject();
        proof.add("selected_pack_order_low_to_high", selectedIds);
        proof.add("effective_resources", resources);
        proof.addProperty("count", resources.size());
        proof.addProperty("manifest_sha256", MANIFEST_SHA256);
        return proof;
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class PackRegistration {
        private PackRegistration() {}
        @SubscribeEvent
        public static void addPacks(AddPackFindersEvent event) throws IOException {
            if (event.getPackType() != PackType.SERVER_DATA) return;
            Inputs inputs = Inputs.read(propertyPath(INPUT_PROPERTY));
            event.addRepositorySource(consumer -> consumer.accept(makePack(inputs.packRoot())));
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class BeforeTests {
        private BeforeTests() {}
        @SubscribeEvent
        public static void aboutToStart(ServerAboutToStartEvent event) throws IOException {
            Inputs inputs = Inputs.read(propertyPath(INPUT_PROPERTY));
            Path run = propertyPath(RUN_PROPERTY);
            require(run.equals(inputs.run()) && run.equals(Path.of("").toAbsolutePath().normalize()),
                    "Canonical inputs do not belong to actual launched native working directory");
            JsonObject marker = JsonParser.parseString(Files.readString(run.resolve("source-fixture.json"))).getAsJsonObject();
            require(run.toString().equals(marker.get("working_directory").getAsString())
                            && marker.get("expected_native_tests").getAsInt() == 12
                            && marker.get("old_world_absent").getAsBoolean()
                            && marker.get("old_latest_log_absent").getAsBoolean(),
                    "Missing actual unchanged12-test fresh source-fixture binding");
            JsonObject proof = verifySelectedResources(inputs, event.getServer().getPackRepository().getSelectedPacks(),
                    event.getServer().getResourceManager());
            proof.addProperty("phase", "ServerAboutToStartEvent before GameTestServer.loadLevel/start/first test tick");
            proof.addProperty("token", run.getFileName().toString());
            proof.addProperty("actual_working_directory", run.toString());
            Files.writeString(run.resolve(PROOF_FILE), new GsonBuilder().setPrettyPrinting().create().toJson(proof) + "\n",
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
    }
}
