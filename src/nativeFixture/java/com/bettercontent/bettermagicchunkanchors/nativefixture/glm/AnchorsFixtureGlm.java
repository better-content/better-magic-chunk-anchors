package com.bettercontent.bettermagicchunkanchors.nativefixture.glm;

import com.bettercontent.bettermagicchunkanchors.ArcaneChunkLoadersMod;
import com.hollingsworth.arsnouveau.setup.registry.LootRegistry;
import com.google.gson.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.common.loot.LootModifierManager;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Nonshipping exact own-profile policy and public native load proof; no gameplay-effect verdict. */
public final class AnchorsFixtureGlm {
    private static final String PROPERTY = "bc.anchors.fixtureGlm.manifest";
    private static final ResourceLocation INDEX = new ResourceLocation("forge", "loot_modifiers/global_loot_modifiers.json");
    private static final int MAX_BYTES = 256 * 1024;
    // Only bounded scalars cross the original apply-return boundary. Never retain its receiver,
    // ResourceManager, registry codecs, worlds or entities, and never decode/apply modifiers twice.
    private static int applyReturns;
    private static boolean failed;
    private static String lastScalarProof;
    private AnchorsFixtureGlm() {}
    private static void require(boolean valid, String reason) {
        if (!valid) throw new IllegalStateException(reason);
    }
    private static byte[] read(InputStream input) throws Exception {
        try (input) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            require(bytes.length <= MAX_BYTES, "Oversize Anchors fixture proof input");
            return bytes;
        }
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static JsonObject json(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static Path manifestPath() {
        String value = System.getProperty(PROPERTY);
        require(value != null && !value.isBlank(), "Owning GLM preparation missing");
        return Path.of(value).toAbsolutePath().normalize();
    }
    private static JsonObject manifest() throws Exception {
        Path path = manifestPath();
        Path inputRoot = Path.of(System.getProperty("bc.source.anchors.canonicalInputs")).toAbsolutePath().normalize();
        require(path.equals(inputRoot.resolve("glm/manifest.json")), "GLM manifest left actual canonical input token");
        byte[] bytes = read(Files.newInputStream(path));
        require(sha(bytes).equals(System.getProperty(PROPERTY + ".sha256")), "Fixture manifest drift");
        JsonObject data = json(bytes);
        require(data.get("pack_id").getAsString().equals("zzzz_bc_source_anchors_malum_policy"), "Wrong combined canonical pack");
        require(data.get("schema").getAsString().equals("bc.anchors.fixture_glm_manifest.v1"), "Fixture manifest schema");
        require(!data.get("source_controls_only").getAsBoolean(), "Pure SOURCE controls cannot stand in for native application");
        String profile = "anchors";
        require(data.get("profile").getAsString().equals(profile), "Wrong fixture GLM profile");
        require(data.get("attempt").getAsString().equals(System.getProperty(PROPERTY + ".attempt"))
            && path.getParent().getParent().getFileName().toString().equals(data.get("attempt").getAsString()), "Fixture attempt drift");
        byte[] scopeBytes = read(AnchorsFixtureGlm.class.getResourceAsStream("/bc_anchors_fixture_glm_scope.json"));
        require(sha(scopeBytes).equals(data.get("scope_sha256").getAsString()), "Owning scope drift");
        JsonObject scope = json(scopeBytes);
        JsonArray expected = scope.getAsJsonObject("profiles").getAsJsonObject(profile).getAsJsonArray("ordered_ids");
        require(expected.equals(data.getAsJsonObject("projection").getAsJsonArray("ordered_ids"))
            && expected.size() == 16, "Fixed own-domain membership/order drift");
        return data;
    }
    private static void classBytes(JsonObject scope, Class<?> anchor, String binaryName) throws Exception {
        String expected = scope.getAsJsonObject("runtime_class_witnesses").get(binaryName).getAsString();
        require(sha(read(anchor.getResourceAsStream("/" + binaryName.replace('.', '/') + ".class"))).equals(expected),
            "Pinned actual decoder/registration/order class differs: " + binaryName);
    }
    private static void verifyDecoderAndOrderBytes(JsonObject scope) throws Exception {
        require(scope.getAsJsonObject("runtime_class_witnesses").size() == 23, "Incomplete decoder/ordering chain");
        classBytes(scope, LootModifierManager.class, LootModifierManager.class.getName());
        classBytes(scope, net.minecraft.util.ExtraCodecs.class, "net.minecraft.util.ExtraCodecs");
        classBytes(scope, net.minecraft.util.ExtraCodecs.class, "net.minecraft.util.ExtraCodecs$LazyInitializedCodec");
        classBytes(scope, net.minecraftforge.common.loot.IGlobalLootModifier.class, "net.minecraftforge.common.loot.IGlobalLootModifier");
        classBytes(scope, net.minecraftforge.registries.ForgeRegistry.class, "net.minecraftforge.registries.ForgeRegistry");
        classBytes(scope, net.minecraftforge.registries.RegistryObject.class, "net.minecraftforge.registries.RegistryObject");
        classBytes(scope, net.minecraftforge.registries.DeferredRegister.class, "net.minecraftforge.registries.DeferredRegister$EventDispatcher");
        classBytes(scope, net.minecraftforge.registries.RegisterEvent.class, "net.minecraftforge.registries.RegisterEvent");
        classBytes(scope, net.minecraftforge.registries.ForgeRegistry.class, "net.minecraftforge.registries.ForgeRegistry$RegistryCodec");
        classBytes(scope, com.mojang.serialization.Codec.class, "com.mojang.serialization.Codec");
        classBytes(scope, com.mojang.serialization.MapCodec.class, "com.mojang.serialization.MapCodec");
        classBytes(scope, com.mojang.serialization.MapCodec.class, "com.mojang.serialization.MapCodec$MapCodecCodec");
        classBytes(scope, com.mojang.serialization.Decoder.class, "com.mojang.serialization.Decoder");
        classBytes(scope, com.mojang.serialization.codecs.RecordCodecBuilder.class, "com.mojang.serialization.codecs.RecordCodecBuilder");
        classBytes(scope, com.mojang.serialization.codecs.KeyDispatchCodec.class, "com.mojang.serialization.codecs.KeyDispatchCodec");
        for (String name : List.of("ImmutableMap$Builder", "RegularImmutableMap", "RegularImmutableMap$Values", "ImmutableList", "ImmutableMapValues", "ImmutableMapValues$1"))
            classBytes(scope, com.google.common.collect.ImmutableMap.class, "com.google.common.collect." + name);
        classBytes(scope, LootRegistry.class, "com.hollingsworth.arsnouveau.setup.registry.LootRegistry");
        classBytes(scope, com.hollingsworth.arsnouveau.api.loot.DungeonLootEnhancerModifier.class,
            "com.hollingsworth.arsnouveau.api.loot.DungeonLootEnhancerModifier");
    }
    private static JsonObject verifyResources(ResourceManager resources, JsonObject data) throws Exception {
        String pack = data.get("pack_id").getAsString();
        var top = resources.getResource(INDEX).orElseThrow();
        byte[] bytes = read(top.open());
        require(top.sourcePackId().equals(pack) && sha(bytes).equals(data.get("index_sha256").getAsString()), "Actual effective index origin/bytes differ");
        JsonObject index = json(bytes);
        JsonArray expected = data.getAsJsonObject("projection").getAsJsonArray("ordered_ids");
        require(index.get("replace").getAsBoolean() && index.getAsJsonArray("entries").equals(expected), "Actual effective index order differs");
        // Reconstruct the actual pinned native stack algorithm, not just highest-resource lookup.
        List<String> effective = new ArrayList<>();
        var stack = resources.getResourceStack(INDEX);
        require(!stack.isEmpty() && stack.size() <= 64, "Unexpected index stack size");
        for (var resource : stack) {
            JsonObject part = json(read(resource.open()));
            if (part.get("replace").getAsBoolean()) effective.clear();
            for (var entry : part.getAsJsonArray("entries")) {
                String id = entry.getAsString();
                effective.remove(id);
                effective.add(id);
            }
        }
        require(new Gson().toJsonTree(effective).equals(expected), "Actual index-stack membership/order differs");
        JsonArray rows = new JsonArray();
        JsonArray declaredRows = data.getAsJsonObject("projection").getAsJsonArray("rows");
        require(declaredRows.size() == expected.size(), "Included resource coverage differs");
        for (int ordinal = 0; ordinal < declaredRows.size(); ordinal++) {
            var row = declaredRows.get(ordinal).getAsJsonObject();
            require(row.get("id").equals(expected.get(ordinal)), "Included resource identity/order differs");
            ResourceLocation id = new ResourceLocation(row.get("id").getAsString());
            String path = row.get("resource").getAsString();
            require(path.equals("data/" + id.getNamespace() + "/loot_modifiers/" + id.getPath() + ".json"), "Included resource path differs");
            var resource = resources.getResource(new ResourceLocation(id.getNamespace(), "loot_modifiers/" + id.getPath() + ".json")).orElseThrow();
            byte[] definition = read(resource.open());
            require(sha(definition).equals(row.get("sha256").getAsString())
                && json(definition).get("type").equals(row.get("codec")), "Effective included definition/type absent/different");
            var proof = new JsonObject();
            proof.add("id", row.get("id"));
            proof.addProperty("sourcePackId", resource.sourcePackId());
            proof.add("sha256", row.get("sha256"));
            rows.add(proof);
        }
        JsonObject proof = new JsonObject();
        proof.addProperty("profile", data.get("profile").getAsString());
        proof.addProperty("indexSourcePackId", top.sourcePackId());
        proof.add("index_sha256", data.get("index_sha256"));
        proof.add("orderedIds", expected.deepCopy());
        proof.add("effectiveDefinitions", rows);
        return proof;
    }
    public static void beginOriginalCodecProof() {
        try {
            List<String> ids = new ArrayList<>();
            for (var row : manifest().getAsJsonObject("projection").getAsJsonArray("rows"))
                ids.add(row.getAsJsonObject().get("id").getAsString());
            OriginalCodecDispatchProof.begin(ids);
        } catch (Exception error) { failed = true; }
    }
    /** Scope markers only: never redirect, invoke, cancel or change the original parse. */
    public static void beforeOriginalParse(LootModifierManager manager, Map<ResourceLocation, JsonElement> definitions,
            ResourceLocation location, JsonElement input) {
        try {
            OriginalCodecDispatchProof.before(location, input);
            require(manager.getClass() == LootModifierManager.class, "Unexpected original parse owner");
            JsonArray rows = manifest().getAsJsonObject("projection").getAsJsonArray("rows");
            List<String> ids = new ArrayList<>(), types = new ArrayList<>();
            for (var row : rows) {
                ids.add(row.getAsJsonObject().get("id").getAsString());
                types.add(row.getAsJsonObject().get("codec").getAsString());
            }
            ArsDispatchScope.LIVE.before(location.toString(), input.getAsJsonObject().get("type").getAsString(),
                sha(input.toString().getBytes(StandardCharsets.UTF_8)), definitions.get(location) == input, ids, types);
        } catch (Exception error) { failed = true; ArsDispatchScope.LIVE.reject(); }
    }
    public static void afterOriginalParse(ResourceLocation location) {
        try { OriginalCodecDispatchProof.after(location); ArsDispatchScope.LIVE.after(location.toString()); }
        catch (Exception error) { failed = true; ArsDispatchScope.LIVE.reject(); }
    }
    /** Only reached after the pure scalar/JDK guard confirms the actual pinned original dispatch stack. */
    public static void selectedRegisteredDecoder(Object decoderReceiver, com.mojang.serialization.DynamicOps<?> ops,
            Object input, com.mojang.serialization.DataResult<?> result) {
        if (!ArsDispatchScope.LIVE.activeArsParse()) return;
        try {
            var registry = ForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS.get();
            ResourceLocation key = new ResourceLocation(ArsDispatchScope.ARS);
            var registered = registry.getValue(key);
            Object selected = DecodedRowProofRules.successfulPairValue(result);
            boolean full = selected != null;
            boolean actual = decoderReceiver == registry.getCodec() && ops == com.mojang.serialization.JsonOps.INSTANCE
                && input instanceof JsonPrimitive primitive && primitive.isString() && ArsDispatchScope.ARS.equals(primitive.getAsString())
                && registered != null && selected == registered && registered == LootRegistry.STRUCTURE_MODDED_LOOT_IMPORTER.get()
                && key.equals(registry.getKey(registered));
            ArsDispatchScope.LIVE.selectedDecoder(actual, full);
        } catch (Exception error) { failed = true; ArsDispatchScope.LIVE.reject(); }
        // Original DataResult/Pair is transient; no mutation, replacement, second decode or thrown observer error.
    }
    public static void originalRootBody(Object receiver, com.mojang.serialization.DynamicOps<?> ops,
            Object input, com.mojang.serialization.DataResult<?> result) {
        if (!ArsDispatchScope.LIVE.activeArsParse()) return;
        try {
            // Legitimate inner RecordCodecBuilder wrappers share this public class. Only the EXISTING
            // public DIRECT constant can supply the root result; never manufacture MapCodec.codec().
            if (!DecodedRowProofRules.existingRootReceiver(receiver, net.minecraftforge.common.loot.IGlobalLootModifier.DIRECT_CODEC)) return;
            if (!ArsDispatchScope.LIVE.confirmOriginalRootTrace()) return;
            Object body = DecodedRowProofRules.successfulParseValue(result);
            boolean full = body != null;
            boolean actual = ops == com.mojang.serialization.JsonOps.INSTANCE && input instanceof JsonElement jsonInput
                && ArsDispatchScope.LIVE.originalInput(sha(jsonInput.toString().getBytes(StandardCharsets.UTF_8)))
                && DecodedRowProofRules.exactBody(body, com.hollingsworth.arsnouveau.api.loot.DungeonLootEnhancerModifier.class);
            ArsDispatchScope.LIVE.originalRootBody(actual, full);
        } catch (Exception error) { failed = true; ArsDispatchScope.LIVE.reject(); }
    }
    /** INTERNAL fixture dispatcher: actual native receiver at original apply RETURN, read-only APIs. */
    public static synchronized void atNativeApplyReturn(LootModifierManager manager, Map<ResourceLocation, JsonElement> definitions, ResourceManager resources) {
        try {
            ArsDispatchScope.Proof dispatchProof = ArsDispatchScope.LIVE.finish();
            require(!failed && ++applyReturns <= 4, "Fixture reload proof overflow/previous failure");
            JsonObject data = manifest();
            JsonObject proof = verifyResources(resources, data);
            byte[] scopeBytes = read(AnchorsFixtureGlm.class.getResourceAsStream("/bc_anchors_fixture_glm_scope.json"));
            require(manager.getClass() == LootModifierManager.class, "Unexpected native loader implementation");
            verifyDecoderAndOrderBytes(json(scopeBytes));
            // Pinned apply traverses effective IDs -> ImmutableMap.Builder -> values(), in order.
            List<?> loaded = new ArrayList<>(manager.getAllLootMods());
            JsonArray rows = data.getAsJsonObject("projection").getAsJsonArray("rows");
            require(loaded.size() == rows.size(), "Actual included decoded count incomplete/extra");
            var registry = ForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS.get();
            require(registry != null, "Actual native codec registry missing");
            require(registry.getClass() == net.minecraftforge.registries.ForgeRegistry.class
                && registry.getCodec().getClass().getName().equals("net.minecraftforge.registries.ForgeRegistry$RegistryCodec"),
                "Actual registered dispatch codec implementation differs");
            boolean arsProved = false;
            Set<String> otherTypesProved = new HashSet<>();
            for (int i = 0; i < rows.size(); i++) {
                ResourceLocation key = new ResourceLocation(rows.get(i).getAsJsonObject().get("codec").getAsString());
                var registered = registry.getValue(key);
                require(registered != null && key.equals(registry.getKey(registered)), "Actual registered decoder key missing/different");
                JsonObject owner = json(scopeBytes).getAsJsonObject("outer_codecs").getAsJsonObject(key.toString());
                ResourceLocation inputId = new ResourceLocation(rows.get(i).getAsJsonObject().get("id").getAsString());
                var actualInput = definitions.get(inputId);
                var inputResource = resources.getResource(new ResourceLocation(inputId.getNamespace(), "loot_modifiers/" + inputId.getPath() + ".json")).orElseThrow();
                DecodedRowProofRules.inputAndImplementation(key.toString(), owner.get("implementation_class").getAsString(),
                    loaded.get(i).getClass().getName(), actualInput, json(read(inputResource.open())));
                OriginalCodecDispatchProof.requireLoaded(i, inputId, key,
                    json(read(inputResource.open())), (net.minecraftforge.common.loot.IGlobalLootModifier) loaded.get(i));
                if (key.toString().equals("ars_nouveau:dungeon_loot")) {
                    // Pinned DIRECT_CODEC decodes through the actual registered decoder; Ars codec()
                    // creates a fresh codec and MUST NOT be called by this observer.
                    DecodedRowProofRules.arsRegistration(key.toString(), registered,
                        LootRegistry.STRUCTURE_MODDED_LOOT_IMPORTER.get(), registry.getKey(registered).toString());
                    arsProved = true;
                } else {
                    // Goety add_loot's pinned Supplier creates a fresh codec on every accessor call.
                    // Its ORIGINAL registered decoder identity/root return is proved above, not guessed by codec().
                    if (!key.toString().equals("goety:add_loot")) {
                        var codec = ((net.minecraftforge.common.loot.IGlobalLootModifier) loaded.get(i)).codec();
                        require(codec != null && registered == codec && key.equals(registry.getKey(codec)), "Actual memoized codec identity/order differs");
                    }
                    otherTypesProved.add(key.toString());
                }
            }
            require(arsProved && otherTypesProved.size() == 8, "Incomplete Ars/eight other original dispatch type proofs");
            proof.addProperty("arsRegisteredDecoderInputImplementationOrder", arsProved);
            proof.addProperty("nonArsOriginalDispatchTypeCount", otherTypesProved.size());
            proof.addProperty("allRowsOriginalRegisteredDispatchAndRootReturn", true);
            proof.addProperty("originalDirectParseBrackets", dispatchProof.originalParseBrackets());
            proof.addProperty("selectedArsRegistryDecoderReturns", dispatchProof.selectedArsDecoderReturns());
            proof.addProperty("originalArsRootBodyReturns", dispatchProof.originalRootBodyReturns());
            proof.addProperty("actualDecodedCount", loaded.size());
            proof.addProperty("scalarOnly", true);
            proof.addProperty("actualCodecIdentitiesAndOrder", true);
            proof.addProperty("gameplayEffectsAccepted", false);
            String scalar = new Gson().toJson(proof);
            require(scalar.length() <= MAX_BYTES, "Oversize scalar fixture proof");
            lastScalarProof = scalar;
        } catch (Exception error) {
            failed = true;
            throw new IllegalStateException("Required Anchors native GLM load proof failed", error);
        } finally { OriginalCodecDispatchProof.end(); }
    }
    @Mod.EventBusSubscriber(modid = "better_magic_chunk_anchors", bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Verify {
        private Verify() {}
        @SubscribeEvent
        public static void starting(ServerStartingEvent event) {
            try {
                ArsDispatchScope.LIVE.assertClosed();
                require(AnchorsFixtureGlm.class.getModule().isNamed()
                    && AnchorsFixtureGlm.class.getModule() == ArcaneChunkLoadersMod.class.getModule(), "Fixture left owning named module");
                JsonObject data = manifest();
                var server = event.getServer();
                String id = data.get("pack_id").getAsString();
                var selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
                require(!selected.isEmpty() && selected.get(selected.size() - 1).equals(id)
                    && Collections.frequency(selected, id) == 1, "Required fixture not selected at actual TOP");
                Pack pack = server.getPackRepository().getPack(id);
                require(pack != null && pack.isRequired() && pack.isFixedPosition() && pack.getDefaultPosition() == Pack.Position.TOP, "Fixture pack attributes differ");
                JsonObject current = verifyResources(server.getResourceManager(), data);
                require(!failed && applyReturns > 0 && applyReturns <= 4 && lastScalarProof != null, "Original apply-return proof missing/failed");
                JsonObject proof = JsonParser.parseString(lastScalarProof).getAsJsonObject();
                require(current.get("index_sha256").equals(proof.get("index_sha256"))
                    && current.get("effectiveDefinitions").equals(proof.get("effectiveDefinitions")), "Startup resources differ from actual apply return");
                proof.addProperty("schema", "bc.anchors.fixture_glm_load_proof.v1");
                proof.add("attempt", data.get("attempt"));
                proof.addProperty("originalApplyReturnsProved", applyReturns);
                proof.addProperty("requiredFixedTopSelected", true);
                Path output = Path.of(System.getProperty(PROPERTY + ".proof")).toAbsolutePath().normalize();
                Path run = Path.of(System.getProperty("bc.source.anchors.nativeDirectory")).toAbsolutePath().normalize();
                require(run.equals(Path.of("").toAbsolutePath().normalize())
                    && run.getFileName().toString().equals(data.get("attempt").getAsString())
                    && output.equals(run.resolve("fixture-glm-load-proof.json")), "GLM proof left actual fresh owning native working directory");
                Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(proof) + "\n", StandardOpenOption.CREATE_NEW);
            } catch (Exception error) { throw new IllegalStateException("Anchors owning profile GLM startup proof failed", error); }
        }
    }
}
