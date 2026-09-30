package com.bettercontent.bettermagicchunkanchors.client;

import com.bettercontent.bettermagicchunkanchors.block.ArcaneAnchorBlock;
import com.bettercontent.bettermagicchunkanchors.blockentity.AnchorAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class AnchorRenderer<T extends BlockEntity & AnchorAccess> implements BlockEntityRenderer<T> {
    public AnchorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(T anchor, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (anchor.getLevel() == null) return;
        boolean active = anchor.getBlockState().getValue(ArcaneAnchorBlock.ACTIVE);
        float time = anchor.getLevel().getGameTime() + partialTick;
        float rotation = active ? time * 0.35f : 22.5f;
        float bob = active ? (float) Math.sin(time * 0.08f) * 0.035f : 0.0f;

        pose.pushPose();
        pose.translate(0.5, 0.72 + bob, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        renderScaled(anchor.variant().coreBlock().defaultBlockState(), pose, buffers, packedLight, 0.25f, 0.25f, 0.25f);
        pose.popPose();

        renderRing(pose, buffers, packedLight, rotation, false);
        renderRing(pose, buffers, packedLight, -rotation * 0.72f, true);
        renderChargeRunes(anchor, pose, buffers, packedLight);
    }

    private static void renderRing(PoseStack pose, MultiBufferSource buffers, int light, float rotation, boolean vertical) {
        for (int index = 0; index < 8; index++) {
            pose.pushPose();
            pose.translate(0.5, 0.76, 0.5);
            if (vertical) pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.mulPose(Axis.YP.rotationDegrees(rotation + index * 45.0f));
            pose.translate(0.31, 0, 0);
            renderScaled(Blocks.GILDED_BLACKSTONE.defaultBlockState(), pose, buffers, light, 0.055f, 0.055f, 0.16f);
            pose.popPose();
        }
    }

    private static void renderChargeRunes(AnchorAccess anchor, PoseStack pose, MultiBufferSource buffers, int light) {
        int lit = (int) Math.ceil(anchor.chargeFraction() * 9.0);
        for (int index = 0; index < lit; index++) {
            int x = index % 3;
            int z = index / 3;
            pose.pushPose();
            pose.translate(0.29 + x * 0.21, 0.39, 0.29 + z * 0.21);
            renderScaled(anchor.variant().coreBlock().defaultBlockState(), pose, buffers, light, 0.045f, 0.025f, 0.045f);
            pose.popPose();
        }
    }

    private static void renderScaled(BlockState state, PoseStack pose, MultiBufferSource buffers, int light, float x, float y, float z) {
        pose.pushPose();
        pose.scale(x, y, z);
        pose.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    @Override public boolean shouldRenderOffScreen(T blockEntity) { return false; }
}
