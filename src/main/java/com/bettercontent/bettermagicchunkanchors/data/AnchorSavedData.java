package com.bettercontent.bettermagicchunkanchors.data;

import com.bettercontent.bettermagicchunkanchors.AnchorVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnchorSavedData extends SavedData {
    private static final String KEY = "better_magic_chunk_anchors_anchors";
    private static final int SCHEMA_VERSION = 1;
    private final Map<UUID, Record> records = new LinkedHashMap<>();

    public static AnchorSavedData get(ServerLevel level) {
        MinecraftServer server = level.getServer();
        return server.overworld().getDataStorage().computeIfAbsent(AnchorSavedData::load, AnchorSavedData::new, KEY);
    }

    public UUID register(ServerLevel level, UUID requested, BlockPos pos, AnchorVariant variant, UUID placerId, String placerName) {
        String dimension = level.dimension().location().toString();
        Record collision = records.get(requested);
        UUID id = collision != null && (!collision.dimension.equals(dimension) || !collision.pos.equals(pos)) ? UUID.randomUUID() : requested;
        records.put(id, new Record(id, dimension, pos.immutable(), variant, placerId, placerName, "starved", 0.0, "empty", false));
        setDirty();
        return id;
    }

    public void update(ServerLevel level, UUID id, BlockPos pos, AnchorVariant variant, UUID placerId, String placerName,
                       String state, double chargeFraction, String chargeText, boolean restorable) {
        records.put(id, new Record(id, level.dimension().location().toString(), pos.immutable(), variant, placerId, placerName,
                state, chargeFraction, chargeText, restorable));
        setDirty();
    }

    public void remove(UUID id) {
        if (records.remove(id) != null) setDirty();
    }

    public boolean hasRestorableAnchor(ServerLevel level, BlockPos pos) {
        String dimension = level.dimension().location().toString();
        return records.values().stream().anyMatch(record -> record.restorable && record.dimension.equals(dimension) && record.pos.equals(pos));
    }

    public boolean hasRestorableAnchor(BlockPos pos) {
        return records.values().stream().anyMatch(record -> record.restorable && record.pos.equals(pos));
    }

    public Record byId(UUID id) { return records.get(id); }

    public List<Record> sortedRecords() {
        List<Record> result = new ArrayList<>(records.values());
        result.sort(Comparator.comparing(Record::dimension)
                .thenComparingInt(record -> record.pos.getX())
                .thenComparingInt(record -> record.pos.getZ())
                .thenComparingInt(record -> record.pos.getY()));
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("schema", SCHEMA_VERSION);
        ListTag list = new ListTag();
        records.values().forEach(record -> list.add(record.save()));
        tag.put("anchors", list);
        return tag;
    }

    public static AnchorSavedData load(CompoundTag tag) {
        int schema = tag.contains("schema", Tag.TAG_INT) ? tag.getInt("schema") : 0;
        if (schema != SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported Arcane Chunkloaders save schema " + schema
                    + "; expected " + SCHEMA_VERSION + ". Refusing to rewrite world data.");
        }
        AnchorSavedData data = new AnchorSavedData();
        for (Tag raw : tag.getList("anchors", Tag.TAG_COMPOUND)) {
            Record record = Record.load((CompoundTag) raw);
            if (record != null) data.records.put(record.id, record);
        }
        return data;
    }

    public record Record(UUID id, String dimension, BlockPos pos, AnchorVariant variant, UUID placerId, String placerName,
                         String state, double chargeFraction, String chargeText, boolean restorable) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("id", id);
            tag.putString("dimension", dimension);
            tag.putLong("pos", pos.asLong());
            tag.putString("variant", variant.name());
            if (placerId != null) tag.putUUID("placerId", placerId);
            tag.putString("placerName", placerName == null ? "unknown" : placerName);
            tag.putString("state", state);
            tag.putDouble("chargeFraction", chargeFraction);
            tag.putString("chargeText", chargeText);
            tag.putBoolean("restorable", restorable);
            return tag;
        }

        static Record load(CompoundTag tag) {
            if (!tag.hasUUID("id") || !tag.contains("dimension") || !tag.contains("variant")) return null;
            try {
                return new Record(tag.getUUID("id"), new ResourceLocation(tag.getString("dimension")).toString(),
                        BlockPos.of(tag.getLong("pos")), AnchorVariant.valueOf(tag.getString("variant")),
                        tag.hasUUID("placerId") ? tag.getUUID("placerId") : null, tag.getString("placerName"),
                        tag.getString("state"), tag.getDouble("chargeFraction"), tag.getString("chargeText"), tag.getBoolean("restorable"));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }
}
