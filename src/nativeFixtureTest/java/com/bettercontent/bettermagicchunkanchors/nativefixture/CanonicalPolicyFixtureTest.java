package com.bettercontent.bettermagicchunkanchors.nativefixture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Actual pinned public Pack/ResourceManager APIs; NOT Forge startup/WorldStem proof. */
class CanonicalPolicyFixtureTest {
    private static Path snapshot;
    private static Path evidence;

    @BeforeAll static void setup() throws Exception {
        SharedConstants.tryDetectVersion();
        snapshot = Path.of(System.getProperty("bc.source.anchors.testSnapshot"));
        evidence = Path.of(System.getProperty("bc.source.anchors.testEvidence")).resolve(UUID.randomUUID().toString());
        Files.createDirectories(evidence);
    }

    private CanonicalPolicyFixture.Inputs inputs() throws Exception {
        JsonObject manifest = JsonParser.parseString(Files.readString(snapshot.resolve("manifest.json"))).getAsJsonObject();
        return new CanonicalPolicyFixture.Inputs(evidence, snapshot.resolve("pack"), evidence.resolve("source-control-only-run"), manifest);
    }

    private PackRepository repository() {
        PackRepository repository = new PackRepository(consumer -> consumer.accept(CanonicalPolicyFixture.makePack(snapshot.resolve("pack"))));
        repository.reload();
        repository.setSelected(List.of()); // Required pack must survive an otherwise empty choice.
        return repository;
    }

    @Test void actualRequiredFixedTopAndAll36EffectiveSourceAndHashes() throws Exception {
        PackRepository repository = repository();
        try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, repository.openAllSelected())) {
            var proof = CanonicalPolicyFixture.verifySelectedResources(inputs(), repository.getSelectedPacks(), manager);
            assertEquals(36, proof.get("count").getAsInt());
            assertEquals(36, proof.getAsJsonArray("effective_resources").size());
            assertEquals(CanonicalPolicyFixture.PACK_ID, proof.getAsJsonArray("selected_pack_order_low_to_high").get(0).getAsString());
        }
    }

    @Test void actualEffectiveShadowingRejectedEvenWithCorrectRawBytes() throws Exception {
        PackRepository repository = repository();
        try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(
                new PathPackResources(CanonicalPolicyFixture.PACK_ID, snapshot.resolve("pack"), true),
                new PathPackResources("source-control-shadow", snapshot.resolve("pack"), true)))) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> CanonicalPolicyFixture.verifySelectedResources(inputs(), repository.getSelectedPacks(), manager));
            assertTrue(failure.getMessage().startsWith("Wrong effective canonical sourcePackId:"));
        }
    }

    @Test void actualMissingEffectiveResourceRejected() throws Exception {
        PackRepository repository = repository();
        try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA, List.of())) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> CanonicalPolicyFixture.verifySelectedResources(inputs(), repository.getSelectedPacks(), manager));
            assertTrue(failure.getMessage().startsWith("Missing effective canonical resource:"));
        }
    }

    @Test void actualWrongRawHashRejectedEvenWithCorrectSourcePackId() throws Exception {
        var inputs = inputs();
        Path damaged = evidence.resolve("wrong-hash-source-control");
        Files.createDirectories(damaged);
        Files.copy(snapshot.resolve("pack/pack.mcmeta"), damaged.resolve("pack.mcmeta"), StandardCopyOption.COPY_ATTRIBUTES);
        boolean first = true;
        for (var item : inputs.manifest().getAsJsonArray("files")) {
            var row = item.getAsJsonObject();
            Path target = damaged.resolve(row.get("pack_path").getAsString());
            Files.createDirectories(target.getParent());
            Files.write(target, first ? "{}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : Files.readAllBytes(snapshot.resolve("pack").resolve(row.get("pack_path").getAsString())));
            first = false;
        }
        PackRepository repository = repository();
        try (var manager = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(new PathPackResources(CanonicalPolicyFixture.PACK_ID, damaged, true)))) {
            var failure = assertThrows(IllegalStateException.class,
                    () -> CanonicalPolicyFixture.verifySelectedResources(inputs, repository.getSelectedPacks(), manager));
            assertTrue(failure.getMessage().startsWith("Wrong effective canonical raw SHA256:"));
        }
    }
}
