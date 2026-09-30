package com.bettercontent.bettermagicchunkanchors;

import com.bettercontent.bettermagicchunkanchors.block.ArcaneAnchorBlock;
import com.bettercontent.bettermagicchunkanchors.block.KineticAnchorBlock;
import com.bettercontent.bettermagicchunkanchors.blockentity.ArcaneAnchorBlockEntity;
import com.bettercontent.bettermagicchunkanchors.blockentity.KineticAnchorBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class AnchorRegistries {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ArcaneChunkLoadersMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ArcaneChunkLoadersMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ArcaneChunkLoadersMod.MOD_ID);
    public static final Map<AnchorVariant, RegistryObject<Block>> ANCHORS = new EnumMap<>(AnchorVariant.class);

    static {
        for (AnchorVariant variant : AnchorVariant.values()) {
            RegistryObject<Block> block = BLOCKS.register(variant.id(), () -> variant == AnchorVariant.KINETIC
                    ? new KineticAnchorBlock()
                    : new ArcaneAnchorBlock(variant));
            ANCHORS.put(variant, block);
            ITEMS.register(variant.id(), () -> new com.bettercontent.bettermagicchunkanchors.item.AnchorBlockItem(block.get(), variant, new Item.Properties()));
        }
    }

    public static final RegistryObject<BlockEntityType<ArcaneAnchorBlockEntity>> ARCANE_ANCHOR = BLOCK_ENTITIES.register(
            "arcane_chunk_anchor",
            () -> BlockEntityType.Builder.of(
                    ArcaneAnchorBlockEntity::new,
                    ANCHORS.entrySet().stream().filter(e -> e.getKey() != AnchorVariant.KINETIC).map(e -> e.getValue().get()).toArray(Block[]::new)
            ).build(null)
    );

    public static final RegistryObject<BlockEntityType<KineticAnchorBlockEntity>> KINETIC_ANCHOR = BLOCK_ENTITIES.register(
            "kinetic_chunk_anchor",
            () -> BlockEntityType.Builder.of(KineticAnchorBlockEntity::new, ANCHORS.get(AnchorVariant.KINETIC).get()).build(null)
    );

    private AnchorRegistries() {}
}
