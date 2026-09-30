package com.bettercontent.bettermagicchunkanchors.command;

import com.bettercontent.bettermagicchunkanchors.blockentity.AnchorAccess;
import com.bettercontent.bettermagicchunkanchors.data.AnchorSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.UUID;

public final class ChunkLoaderCommands {
    private static final int PAGE_SIZE = 10;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chunkloaders")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> list(context.getSource(), IntegerArgumentType.getInteger(context, "page")))))
                .then(Commands.literal("teleport")
                        .then(Commands.argument("loader_uuid", UuidArgument.uuid())
                                .executes(context -> teleport(context.getSource(), UuidArgument.getUuid(context, "loader_uuid"))))));
    }

    private static int list(CommandSourceStack source, int requestedPage) {
        List<AnchorSavedData.Record> records = AnchorSavedData.get(source.getServer().overworld()).sortedRecords();
        int pages = Math.max(1, (records.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.min(requestedPage, pages);
        source.sendSuccess(() -> Component.literal("Chunk anchors: " + records.size() + " total (page " + page + "/" + pages + ")")
                .withStyle(ChatFormatting.GOLD), false);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(records.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            AnchorSavedData.Record record = records.get(index);
            String command = "/chunkloaders teleport " + record.id();
            MutableComponent teleport = Component.literal("[TP]").withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport safely to this anchor"))));
            MutableComponent line = Component.empty().append(teleport).append(" ")
                    .append(Component.literal(record.variant().displayName()).withStyle(ChatFormatting.LIGHT_PURPLE))
                    .append(Component.literal(" " + record.dimension() + " " + format(record.pos()) + " "))
                    .append(Component.literal(record.state()).withStyle(stateColor(record.state())))
                    .append(Component.literal(" | " + record.chargeText() + " | placed by " + record.placerName()));
            source.sendSuccess(() -> line, false);
        }
        return records.size();
    }

    private static int teleport(CommandSourceStack source, UUID id) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception error) {
            source.sendFailure(Component.literal("Only an in-game operator can teleport."));
            return 0;
        }
        AnchorSavedData data = AnchorSavedData.get(source.getServer().overworld());
        AnchorSavedData.Record record = data.byId(id);
        if (record == null) {
            source.sendFailure(Component.literal("Unknown chunk anchor " + id));
            return 0;
        }
        ResourceKey<net.minecraft.world.level.Level> dimension = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(record.dimension()));
        ServerLevel level = source.getServer().getLevel(dimension);
        if (level == null) {
            source.sendFailure(Component.literal("Dimension is not available: " + record.dimension()));
            return 0;
        }

        level.getChunkAt(record.pos());
        BlockEntity blockEntity = level.getBlockEntity(record.pos());
        if (!(blockEntity instanceof AnchorAccess anchor) || !anchor.anchorId().equals(id)) {
            data.remove(id);
            source.sendFailure(Component.literal("The recorded anchor no longer exists; its stale entry was removed."));
            return 0;
        }
        BlockPos safe = findSafeDestination(level, record.pos());
        if (safe == null) {
            source.sendFailure(Component.literal("No collision-free destination exists near " + format(record.pos()) + "."));
            return 0;
        }
        player.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.literal("Teleported to " + record.variant().displayName() + " at " + format(record.pos()) + "."), false);
        return 1;
    }

    private static BlockPos findSafeDestination(ServerLevel level, BlockPos anchor) {
        for (int dy = 1; dy <= 5; dy++) {
            for (int radius = 0; radius <= 4; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                        BlockPos feet = anchor.offset(dx, dy, dz);
                        if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                                && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) return feet;
                    }
                }
            }
        }
        return null;
    }

    private static ChatFormatting stateColor(String state) {
        return switch (state) {
            case "active" -> ChatFormatting.GREEN;
            case "redstone_disabled" -> ChatFormatting.YELLOW;
            default -> ChatFormatting.RED;
        };
    }

    private static String format(BlockPos pos) { return pos.getX() + ", " + pos.getY() + ", " + pos.getZ(); }
    private ChunkLoaderCommands() {}
}
