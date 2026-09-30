package com.bettercontent.bettermagicchunkanchors.blockentity;

import com.bettercontent.bettermagicchunkanchors.AnchorConfig;
import com.bettercontent.bettermagicchunkanchors.AnchorRegistries;
import com.bettercontent.bettermagicchunkanchors.AnchorVariant;
import com.bettercontent.bettermagicchunkanchors.block.ArcaneAnchorBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class KineticAnchorBlockEntity extends KineticBlockEntity implements AnchorAccess {
    private final AnchorData anchor = new AnchorData();
    private int charge;

    public KineticAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(AnchorRegistries.KINETIC_ANCHOR.get(), pos, state);
    }

    @Override public void tick() {
        super.tick();
        if (level instanceof ServerLevel) {
            anchor.tick(this);
        } else if (level != null && getBlockState().getValue(ArcaneAnchorBlock.ACTIVE)
                && level.getGameTime() % 20 == 0 && level.random.nextInt(3) == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5,
                    (level.random.nextDouble() - 0.5) * 0.08, 0.03, (level.random.nextDouble() - 0.5) * 0.08);
        }
    }

    public void placedBy(Player player) { anchor.placedBy(player.getUUID(), player.getGameProfile().getName()); setChanged(); }
    public void removed() { if (level instanceof ServerLevel) anchor.onRemoved(this); }

    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level == null || level.isClientSide) return InteractionResult.SUCCESS;
        if (player.getItemInHand(hand).isEmpty()) {
            player.displayClientMessage(Component.literal("Kinetic Chunk Anchor: " + chargeText() +
                    (level.hasNeighborSignal(worldPosition) ? " (redstone disabled)" : anchor.ticketsActive() ? " (active)" : " (starved)")), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override public ServerLevel serverLevel() { return (ServerLevel) level; }
    @Override public BlockPos anchorPos() { return worldPosition; }
    @Override public AnchorVariant variant() { return AnchorVariant.KINETIC; }
    @Override public UUID anchorId() { return anchor.id(); }
    @Override public void passiveCharge() {
        if (!isOverStressed() && Math.abs(getSpeed()) >= AnchorConfig.KINETIC_MIN_RPM.get()) {
            charge = Math.min(AnchorConfig.KINETIC_CAPACITY.get(), charge + AnchorConfig.KINETIC_CHARGE_RATE.get());
            setChanged();
        }
    }
    @Override public boolean consumePower(long gameTime) {
        if (charge <= 0) return false;
        charge--;
        setChanged();
        return true;
    }
    @Override public double chargeFraction() { return com.bettercontent.bettermagicchunkanchors.AnchorMath.chargeFraction(charge, AnchorConfig.KINETIC_CAPACITY.get()); }
    @Override public String chargeText() { return charge + " / " + AnchorConfig.KINETIC_CAPACITY.get() + " service ticks"; }
    @Override public void setVisualActive(boolean active) {
        if (level != null && getBlockState().getValue(ArcaneAnchorBlock.ACTIVE) != active)
            level.setBlock(worldPosition, getBlockState().setValue(ArcaneAnchorBlock.ACTIVE, active), 3);
    }
    @Override public void markAnchorChanged() { setChanged(); sendData(); }
    @Override public float calculateStressApplied() { return AnchorConfig.KINETIC_STRESS_IMPACT.get().floatValue(); }

    @Override protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        anchor.save(tag);
        tag.putInt("charge", charge);
    }
    @Override protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        anchor.load(tag);
        charge = tag.getInt("charge");
    }
}
