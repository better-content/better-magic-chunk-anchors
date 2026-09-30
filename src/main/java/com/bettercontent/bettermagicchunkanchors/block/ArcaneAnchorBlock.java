package com.bettercontent.bettermagicchunkanchors.block;

import com.bettercontent.bettermagicchunkanchors.AnchorVariant;
import com.bettercontent.bettermagicchunkanchors.blockentity.ArcaneAnchorBlockEntity;
import com.bettercontent.bettermagicchunkanchors.api.event.ArcaneAnchorProgressEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ArcaneAnchorBlock extends BaseEntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private final AnchorVariant variant;

    public ArcaneAnchorBlock(AnchorVariant variant) {
        super(BlockBehaviour.Properties.of().strength(5.0f, 12.0f).sound(SoundType.DEEPSLATE).noOcclusion().lightLevel(state -> state.getValue(ACTIVE) ? 6 : 0));
        this.variant = variant;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    public AnchorVariant variant() { return variant; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) { builder.add(ACTIVE); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ArcaneAnchorBlockEntity(pos, state); }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, com.bettercontent.bettermagicchunkanchors.AnchorRegistries.ARCANE_ANCHOR.get(),
                level.isClientSide ? ArcaneAnchorBlockEntity::clientTick : ArcaneAnchorBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof ArcaneAnchorBlockEntity anchor ? anchor.interact(player, hand) : InteractionResult.PASS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof ArcaneAnchorBlockEntity anchor) {
            anchor.placedBy(player);
            if (player instanceof ServerPlayer serverPlayer) {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new ArcaneAnchorProgressEvent(
                        serverPlayer, anchor.anchorId(), ArcaneAnchorProgressEvent.Stage.PLACED));
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ArcaneAnchorBlockEntity anchor) anchor.removed();
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ArcaneAnchorBlockEntity anchor ? (int) Math.ceil(anchor.chargeFraction() * 15.0) : 0;
    }
}
