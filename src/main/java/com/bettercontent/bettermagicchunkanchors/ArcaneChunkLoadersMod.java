package com.bettercontent.bettermagicchunkanchors;

import com.bettercontent.bettermagicchunkanchors.command.ChunkLoaderCommands;
import com.bettercontent.bettermagicchunkanchors.data.AnchorSavedData;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.ArrayList;

@Mod(ArcaneChunkLoadersMod.MOD_ID)
public final class ArcaneChunkLoadersMod {
    public static final String MOD_ID = "better_magic_chunk_anchors";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ArcaneChunkLoadersMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        AnchorRegistries.BLOCKS.register(modBus);
        AnchorRegistries.ITEMS.register(modBus);
        AnchorRegistries.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(this::addCreativeItems);
        modBus.addListener(this::registerGameTests);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, AnchorConfig.SPEC);

        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        ForgeChunkManager.setForcedChunkLoadingCallback(MOD_ID, (level, helper) -> {
            var invalidOwners = new ArrayList<>(helper.getBlockTickets().keySet());
            invalidOwners.removeIf(pos -> AnchorSavedData.get(level).hasRestorableAnchor(level, pos));
            invalidOwners.forEach(helper::removeAllTickets);
        });
    }

    private void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            AnchorVariant.values();
            AnchorRegistries.ANCHORS.values().forEach(block -> event.accept(block.get()));
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        ChunkLoaderCommands.register(event.getDispatcher());
    }

    private void registerGameTests(RegisterGameTestsEvent event) {
        event.register(AnchorSourceGameTests.class);
        event.register(AnchorLifecycleGameTests.class);
    }
}
