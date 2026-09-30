package com.bettercontent.bettermagicchunkanchors.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import java.util.Objects;
import java.util.UUID;

/** Observes an authoritative lifecycle transition for one physical arcane anchor. */
public final class ArcaneAnchorProgressEvent extends Event {
    public enum Stage { PLACED, REMOTE_TICKET_VERIFIED }

    private final ServerPlayer player;
    private final UUID anchorId;
    private final Stage stage;

    public ArcaneAnchorProgressEvent(ServerPlayer player, UUID anchorId, Stage stage) {
        this.player = Objects.requireNonNull(player, "player");
        this.anchorId = Objects.requireNonNull(anchorId, "anchorId");
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    public ServerPlayer getPlayer() { return player; }
    public UUID getAnchorId() { return anchorId; }
    public Stage getStage() { return stage; }
}
