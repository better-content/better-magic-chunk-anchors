package com.bettercontent.bettermagicchunkanchors.blockentity;

import com.bettercontent.bettermagicchunkanchors.AnchorVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public interface AnchorAccess {
    ServerLevel serverLevel();
    BlockPos anchorPos();
    AnchorVariant variant();
    UUID anchorId();
    boolean consumePower(long gameTime);
    void passiveCharge();
    double chargeFraction();
    String chargeText();
    void setVisualActive(boolean active);
    void markAnchorChanged();
}
