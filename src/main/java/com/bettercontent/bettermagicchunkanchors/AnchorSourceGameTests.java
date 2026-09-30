package com.bettercontent.bettermagicchunkanchors;

import com.bettercontent.bettermagicchunkanchors.blockentity.ArcaneAnchorBlockEntity;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class AnchorSourceGameTests {
    private AnchorSourceGameTests() {}

    @GameTest(templateNamespace = ArcaneChunkLoadersMod.MOD_ID, template = "blank", timeoutTicks = 100)
    public static void fullAnchorPaysOneImpossibleMatterRequest(GameTestHelper helper) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, AnchorRegistries.ANCHORS.get(AnchorVariant.SOURCE).get());
        helper.runAfterDelay(2, () -> {
            if (!(helper.getBlockEntity(relative) instanceof ArcaneAnchorBlockEntity anchor)) {
                helper.fail("Source Chunk Anchor block entity was not created");
                return;
            }
            anchor.setSource(144_000);
            BlockPos absolute = helper.absolutePos(relative);
            helper.assertTrue(SourceUtil.hasSourceNearby(absolute, helper.getLevel(), 4, 100_000),
                    "Ars SourceManager should expose the anchor as one 100,000-Source provider");
            helper.assertTrue(SourceUtil.takeSource(absolute, helper.getLevel(), 4, 100_000) != null,
                    "Impossible Matter request should resolve through the registered provider");
            helper.assertTrue(anchor.getSource() == 44_000,
                    "Expected 44,000 Source after the 100,000-Source payment");
            helper.succeed();
        });
    }
}
