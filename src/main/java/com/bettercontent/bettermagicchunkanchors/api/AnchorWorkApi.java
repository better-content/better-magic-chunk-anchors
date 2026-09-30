package com.bettercontent.bettermagicchunkanchors.api;

import com.bettercontent.bettermagicchunkanchors.ArcaneChunkLoadersMod;
import com.bettercontent.bettermagicchunkanchors.blockentity.AnchorAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import java.util.Optional;
import java.util.UUID;

/** Read-only support evidence. Callers must independently prove that an owned operation did work. */
public final class AnchorWorkApi {
 private AnchorWorkApi(){}
 public static Optional<UUID> supportingAnchor(ServerLevel level,BlockPos operation){
  var chunk=new ChunkPos(operation);
  int view=level.getServer().getPlayerList().getViewDistance()+1;
  for(var player:level.getServer().getPlayerList().getPlayers())if(player.serverLevel()==level){var position=player.chunkPosition();if(Math.abs(position.x-chunk.x)<=view&&Math.abs(position.z-chunk.z)<=view)return Optional.empty();}
  var forced=level.getDataStorage().get(ForcedChunksSavedData::load,ForcedChunksSavedData.FILE_ID);
  if(forced==null)return Optional.empty();
  // Forge exposes ticket ownership through its serialized public state; require this mod's
  // actual fully ticking ticket for the operation chunk and the live corresponding anchor.
  for(var raw:forced.save(new CompoundTag()).getList("ForgeForced",Tag.TAG_COMPOUND)){
   var mod=(CompoundTag)raw;if(!mod.getString("Mod").equals(ArcaneChunkLoadersMod.MOD_ID))continue;
   for(var entry:mod.getList("ModForced",Tag.TAG_COMPOUND)){
    var ticket=(CompoundTag)entry;if(ticket.getLong("Chunk")!=chunk.toLong())continue;
    for(var owner:ticket.getList("TickingBlocks",Tag.TAG_COMPOUND)){
     var pos=NbtUtils.readBlockPos((CompoundTag)owner);
     if(level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof AnchorAccess anchor)return Optional.of(anchor.anchorId());
    }
   }
  }
  return Optional.empty();
 }
}
