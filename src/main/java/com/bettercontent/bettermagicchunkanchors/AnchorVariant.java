package com.bettercontent.bettermagicchunkanchors;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

public enum AnchorVariant {
    FLUX("flux_chunk_anchor", "Flux Chunk Anchor", "minecraft:redstone_block"),
    KINETIC("kinetic_chunk_anchor", "Kinetic Chunk Anchor", "minecraft:raw_gold_block"),
    SOURCE("source_chunk_anchor", "Source Chunk Anchor", "ars_nouveau:source_gem_block"),
    LIFEFORCE("lifeforce_chunk_anchor", "Lifeforce Chunk Anchor", "bloodmagic:bloodstonebrick"),
    PRESSURE("pressure_chunk_anchor", "Pressure Chunk Anchor", "pneumaticcraft:compressed_iron_block"),
    SOUL("soul_chunk_anchor", "Soul Chunk Anchor", "goety:ominous_stone"),
    SPIRIT("spirit_chunk_anchor", "Spirit Chunk Anchor", "malum:block_of_soulstone");

    private final String id;
    private final String displayName;
    private final ResourceLocation coreBlock;

    AnchorVariant(String id, String displayName, String coreBlock) {
        this.id = id;
        this.displayName = displayName;
        this.coreBlock = new ResourceLocation(coreBlock);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Block coreBlock() {
        Block block = ForgeRegistries.BLOCKS.getValue(coreBlock);
        return block == null || block == Blocks.AIR ? Blocks.AMETHYST_BLOCK : block;
    }
}
