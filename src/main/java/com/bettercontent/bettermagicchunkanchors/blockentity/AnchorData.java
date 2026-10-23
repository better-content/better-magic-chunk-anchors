package com.bettercontent.bettermagicchunkanchors.blockentity;

import com.bettercontent.bettermagicchunkanchors.ArcaneChunkLoadersMod;
import com.bettercontent.bettermagicchunkanchors.diagnostics.PlaytestDiagnostics;
import com.bettercontent.bettermagicchunkanchors.diagnostics.PlaytestLog;
import com.bettercontent.bettermagicchunkanchors.AnchorMath;
import com.bettercontent.bettermagicchunkanchors.data.AnchorSavedData;
import com.bettercontent.bettermagicchunkanchors.api.event.ArcaneAnchorProgressEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.UUID;

public final class AnchorData {
    private UUID id = UUID.randomUUID();
    private UUID placerId;
    private String placerName = "unknown";
    private boolean registered;
    private boolean ticketsActive;
    private boolean lastVisualActive;
    private boolean remoteTicketVerified;
    private int syncCountdown;
    private String lastObservedState;

    public void placedBy(UUID playerId, String playerName) {
        placerId = playerId;
        placerName = playerName;
        registered = false;
    }

    public UUID id() { return id; }
    public UUID placerId() { return placerId; }
    public String placerName() { return placerName; }
    public boolean ticketsActive() { return ticketsActive; }

    public void tick(AnchorAccess owner) {
        ServerLevel level = owner.serverLevel();
        ensureRegistered(owner);
        owner.passiveCharge();
        boolean redstoneDisabled = level.hasNeighborSignal(owner.anchorPos());
        boolean active = false;
        if (!redstoneDisabled) active = owner.consumePower(level.getGameTime());

        if (active != ticketsActive) {
            setTickets(level, owner.anchorPos(), active);
            ticketsActive = active;
        }
        if (active && !remoteTicketVerified && placerId != null) {
            var player = level.getServer().getPlayerList().getPlayer(placerId);
            if (player != null && (player.serverLevel() != level || player.blockPosition().distSqr(owner.anchorPos()) > remoteRangeSquared(player))) {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new ArcaneAnchorProgressEvent(
                        player, id, ArcaneAnchorProgressEvent.Stage.REMOTE_TICKET_VERIFIED));
                remoteTicketVerified = true;
                owner.markAnchorChanged();
            }
        }
        boolean visualChanged = active != lastVisualActive;
        if (visualChanged) {
            owner.setVisualActive(active);
            lastVisualActive = active;
            owner.markAnchorChanged();
        }

        if (--syncCountdown <= 0 || visualChanged) {
            syncCountdown = 20;
            updateSaved(owner, redstoneDisabled ? "redstone_disabled" : active ? "active" : "starved");
        }
    }

    public void onRemoved(AnchorAccess owner) {
        ServerLevel level = owner.serverLevel();
        if (ticketsActive) setTickets(level, owner.anchorPos(), false);
        ticketsActive = false;
        AnchorSavedData.get(level).remove(id);
        if (PlaytestDiagnostics.LOG.enabled("anchor")) PlaytestDiagnostics.LOG.event("anchor", "anchor.removed", "server", "applied", "registry_removed",
            () -> PlaytestLog.fields("anchor", id, "ticket_release", "requested_if_active"));
    }

    private void ensureRegistered(AnchorAccess owner) {
        if (registered) return;
        UUID requested = id;
        id = AnchorSavedData.get(owner.serverLevel()).register(owner.serverLevel(), id, owner.anchorPos(), owner.variant(), placerId, placerName);
        registered = true;
        if (PlaytestDiagnostics.LOG.enabled("anchor")) PlaytestDiagnostics.LOG.event("anchor", "anchor.registered", "server", "applied",
            requested.equals(id) ? "identity_retained" : "identity_collision_reassigned", () -> PlaytestLog.fields("anchor", id, "variant", owner.variant()));
    }

    private void updateSaved(AnchorAccess owner, String state) {
        AnchorSavedData.get(owner.serverLevel()).update(owner.serverLevel(), id, owner.anchorPos(), owner.variant(), placerId, placerName,
                state, owner.chargeFraction(), owner.chargeText(), ticketsActive);
        var log = PlaytestDiagnostics.LOG;
        if (log.enabled("anchor") && !state.equals(lastObservedState)) {
            String before = lastObservedState == null ? "UNKNOWN" : lastObservedState;
            lastObservedState = state;
            ServerLevel level = owner.serverLevel();
            log.debug("anchor", "anchor.state", PlaytestLog.Context.server(level.dimension().location().toString(), level.getGameTime()),
                "observed", state, () -> PlaytestLog.fields("anchor", id, "before", before, "after", state,
                    "tickets_requested_active", ticketsActive, "variant", owner.variant()));
        }
    }

    private static void setTickets(ServerLevel level, BlockPos owner, boolean add) {
        ChunkPos center = new ChunkPos(owner);
        int accepted = 0;
        for (AnchorMath.ChunkOffset offset : AnchorMath.centered3x3()) {
            if (ForgeChunkManager.forceChunk(level, ArcaneChunkLoadersMod.MOD_ID, owner,
                    center.x + offset.x(), center.z + offset.z(), add, true)) accepted++;
        }
        if (PlaytestDiagnostics.LOG.enabled("tickets")) {
            final int returnedTrue = accepted;
            PlaytestDiagnostics.LOG.debug("tickets", "anchor.tickets", PlaytestLog.Context.server(level.dimension().location().toString(), level.getGameTime()),
                "requested", add ? "acquire" : "release", () -> PlaytestLog.fields("owner_pos_long", owner.asLong()) + com.bettercontent.bettermagicchunkanchors.diagnostics.FeatureObservation.fields(returnedTrue));
        }
    }

    private static double remoteRangeSquared(net.minecraft.server.level.ServerPlayer player) {
        double blocks = Math.max(2, player.server.getPlayerList().getViewDistance()) * 16.0;
        return blocks * blocks;
    }

    public void save(CompoundTag tag) {
        tag.putUUID("anchorId", id);
        if (placerId != null) tag.putUUID("placerId", placerId);
        tag.putString("placerName", placerName);
        tag.putBoolean("ticketsActive", ticketsActive);
        tag.putBoolean("remoteTicketVerified", remoteTicketVerified);
    }

    public void load(CompoundTag tag) {
        if (tag.hasUUID("anchorId")) id = tag.getUUID("anchorId");
        placerId = tag.hasUUID("placerId") ? tag.getUUID("placerId") : null;
        placerName = tag.contains("placerName") ? tag.getString("placerName") : "unknown";
        ticketsActive = tag.getBoolean("ticketsActive");
        remoteTicketVerified = tag.getBoolean("remoteTicketVerified");
        registered = false;
    }
}
