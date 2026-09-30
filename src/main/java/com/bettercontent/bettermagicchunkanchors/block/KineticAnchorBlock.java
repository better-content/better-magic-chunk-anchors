package com.bettercontent.bettermagicchunkanchors.block;

import com.bettercontent.bettermagicchunkanchors.AnchorRegistries;
import com.bettercontent.bettermagicchunkanchors.blockentity.KineticAnchorBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class KineticAnchorBlock extends KineticBlock implements IBE<KineticAnchorBlockEntity> {
    public KineticAnchorBlock() {
        super(BlockBehaviour.Properties.of().strength(5.0f, 12.0f).sound(SoundType.DEEPSLATE).noOcclusion().lightLevel(state -> state.getValue(ArcaneAnchorBlock.ACTIVE) ? 6 : 0));
        registerDefaultState(stateDefinition.any().setValue(ArcaneAnchorBlock.ACTIVE, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) { builder.add(ArcaneAnchorBlock.ACTIVE); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
    @Override public Class<KineticAnchorBlockEntity> getBlockEntityClass() { return KineticAnchorBlockEntity.class; }
    @Override public BlockEntityType<? extends KineticAnchorBlockEntity> getBlockEntityType() { return AnchorRegistries.KINETIC_ANCHOR.get(); }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return onBlockEntityUse(level, pos, be -> be.interact(player, hand));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player) withBlockEntityDo(level, pos, be -> be.placedBy(player));
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) withBlockEntityDo(level, pos, KineticAnchorBlockEntity::removed);
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return getBlockEntityOptional(level, pos).map(be -> (int) Math.ceil(be.chargeFraction() * 15.0)).orElse(0);
    }
}
