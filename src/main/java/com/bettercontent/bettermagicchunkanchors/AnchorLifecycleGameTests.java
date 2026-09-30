package com.bettercontent.bettermagicchunkanchors;

import com.Polarice3.Goety.utils.SEHelper;
import com.bettercontent.bettermagicchunkanchors.block.ArcaneAnchorBlock;
import com.bettercontent.bettermagicchunkanchors.blockentity.AnchorAccess;
import com.bettercontent.bettermagicchunkanchors.blockentity.ArcaneAnchorBlockEntity;
import com.bettercontent.bettermagicchunkanchors.blockentity.KineticAnchorBlockEntity;
import com.bettercontent.bettermagicchunkanchors.data.AnchorSavedData;
import com.mojang.authlib.GameProfile;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class AnchorLifecycleGameTests {
    private static final BlockPos ANCHOR = new BlockPos(1, 1, 1);
    private static final BlockPos SWITCH = ANCHOR.above();

    private AnchorLifecycleGameTests() {}

    @GameTestGenerator
    public static Collection<TestFunction> powerVariants() {
        return Arrays.stream(AnchorVariant.values()).map(variant -> new TestFunction(
                "anchor_power", "anchorlifecyclegametests.power_" + variant.name().toLowerCase(Locale.ROOT),
                ArcaneChunkLoadersMod.MOD_ID + ":blank", 100, 0, true,
                helper -> powerAcceptanceAndDepletion(helper, variant))).toList();
    }

    private static void powerAcceptanceAndDepletion(GameTestHelper helper, AnchorVariant variant) {
        helper.setBlock(SWITCH, Blocks.REDSTONE_BLOCK);
        helper.setBlock(ANCHOR, AnchorRegistries.ANCHORS.get(variant).get());
        try {
            AnchorAccess anchor = (AnchorAccess) helper.getBlockEntity(ANCHOR);
            helper.assertTrue(!anchor.consumePower(0), variant + " must refuse to run empty");
            int serviceTicks = chargeOnePayment(helper, anchor);
            helper.assertTrue(anchor.chargeFraction() > 0, variant + " accepted power must reach the real buffer");
            for (int tick = 0; tick < serviceTicks; tick++) {
                helper.assertTrue(anchor.consumePower(0), variant + " must pay its configured service cost");
            }
            helper.assertTrue(!anchor.consumePower(0), variant + " must stop once its buffer is exhausted");
            helper.assertTrue(anchor.chargeFraction() == 0, variant + " depleted buffer must report zero charge");
            helper.succeed();
        } finally {
            helper.setBlock(ANCHOR, Blocks.AIR);
            helper.setBlock(SWITCH, Blocks.AIR);
        }
    }

    /** Charge through the public integration boundary, with exactly one resource payment. */
    private static int chargeOnePayment(GameTestHelper helper, AnchorAccess access) {
        if (access instanceof KineticAnchorBlockEntity kinetic) {
            kinetic.setSpeed(AnchorConfig.KINETIC_MIN_RPM.get() - 1);
            kinetic.passiveCharge();
            helper.assertTrue(kinetic.chargeFraction() == 0, "sub-threshold RPM must not charge the anchor");
            kinetic.setSpeed(AnchorConfig.KINETIC_MIN_RPM.get());
            kinetic.passiveCharge();
            kinetic.setSpeed(0);
            return Math.min(AnchorConfig.KINETIC_CAPACITY.get(), AnchorConfig.KINETIC_CHARGE_RATE.get());
        }
        ArcaneAnchorBlockEntity anchor = (ArcaneAnchorBlockEntity) access;
        switch (anchor.variant()) {
            case FLUX -> {
                var energy = anchor.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new);
                int cost = AnchorConfig.FE_PER_TICK.get();
                helper.assertTrue(energy.receiveEnergy(cost, true) == cost && energy.getEnergyStored() == 0,
                        "simulated FE insertion must not charge the anchor");
                helper.assertTrue(energy.receiveEnergy(cost, false) == cost, "FE capability must accept the service cost");
            }
            case SOURCE -> {
                helper.assertTrue(anchor.canAcceptSource(), "empty Source anchor must advertise acceptance");
                anchor.addSource(AnchorConfig.SOURCE_PER_TICK.get());
                helper.assertTrue(anchor.getSource() == AnchorConfig.SOURCE_PER_TICK.get(), "Ars source insertion must charge exactly the requested amount");
            }
            case LIFEFORCE -> {
                var fluid = anchor.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new);
                helper.assertTrue(fluid.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 0,
                        "lifeforce tank must reject water");
                var essence = ForgeRegistries.FLUIDS.getValue(new ResourceLocation("bloodmagic", "life_essence_fluid"));
                helper.assertTrue(essence != null && essence != Fluids.EMPTY, "Blood Magic life essence must be loaded");
                int cost = AnchorConfig.LIFEFORCE_PER_TICK.get();
                helper.assertTrue(fluid.fill(new FluidStack(essence, cost), FluidAction.EXECUTE) == cost,
                        "lifeforce capability must accept life essence");
            }
            case PRESSURE -> {
                var air = anchor.getCapability(PNCCapabilities.AIR_HANDLER_CAPABILITY).orElseThrow(IllegalStateException::new);
                air.addAir(AnchorConfig.AIR_PER_TICK.get());
                helper.assertTrue(air.getAir() == AnchorConfig.AIR_PER_TICK.get(), "PneumaticCraft capability must retain accepted air");
            }
            case SOUL -> {
                var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "anchor_soul"));
                player.setShiftKeyDown(true);
                SEHelper.setSESouls(player, 1);
                helper.assertTrue(SEHelper.getSESouls(player) == 1, "Goety player soul capability must be available");
                helper.assertTrue(anchor.interact(player, InteractionHand.MAIN_HAND).consumesAction(), "sneak interaction must transfer soul energy");
                helper.assertTrue(SEHelper.getSESouls(player) == 0, "soul transfer must debit the player exactly once");
            }
            case SPIRIT -> {
                var items = anchor.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
                helper.assertTrue(items.insertItem(0, new ItemStack(Items.DIRT), false).getCount() == 1,
                        "spirit inventory must reject non-spirit items");
                var spirit = ForgeRegistries.ITEMS.getValue(new ResourceLocation("malum", "eldritch_spirit"));
                helper.assertTrue(spirit != null && spirit != Items.AIR, "exotic Malum spirit item must be loaded");
                helper.assertTrue(items.insertItem(0, new ItemStack(spirit), false).isEmpty(), "spirit capability must accept a tagged exotic spirit");
            }
            default -> throw new IllegalStateException("Unhandled anchor " + anchor.variant());
        }
        return switch (anchor.variant()) {
            case SOUL -> AnchorConfig.SOUL_INTERVAL.get();
            case SPIRIT -> AnchorConfig.SPIRIT_INTERVAL.get();
            default -> 1;
        };
    }

    @GameTestGenerator
    public static Collection<TestFunction> timedCreditRoundTrips() {
        return java.util.List.of(AnchorVariant.SOUL, AnchorVariant.SPIRIT).stream().map(variant -> new TestFunction(
                "anchor_credit", "anchorlifecyclegametests.credit_" + variant.name().toLowerCase(Locale.ROOT),
                ArcaneChunkLoadersMod.MOD_ID + ":blank", 100, 0, true,
                helper -> timedCreditRoundTrip(helper, variant))).toList();
    }

    private static void timedCreditRoundTrip(GameTestHelper helper, AnchorVariant variant) {
        helper.setBlock(SWITCH, Blocks.REDSTONE_BLOCK);
        helper.setBlock(ANCHOR, AnchorRegistries.ANCHORS.get(variant).get());
        try {
            var anchor = (ArcaneAnchorBlockEntity) helper.getBlockEntity(ANCHOR);
            int interval = chargeOnePayment(helper, anchor);
            int used = Math.max(1, interval / 2);
            for (int i = 0; i < used; i++) helper.assertTrue(anchor.consumePower(i), "Paid credit must serve the first half");
            CompoundTag tag = anchor.saveWithoutMetadata();
            int remaining = tag.getInt(variant == AnchorVariant.SOUL ? "soulServiceCreditTicks" : "spiritServiceCreditTicks");
            helper.assertTrue(remaining == interval - used, "Paid service must be accounted in active ticks");
            var restored = (ArcaneAnchorBlockEntity) ((ArcaneAnchorBlock) AnchorRegistries.ANCHORS.get(variant).get())
                    .newBlockEntity(helper.absolutePos(ANCHOR), helper.getBlockState(ANCHOR));
            restored.load(tag);
            helper.assertTrue(restored.chargeFraction() > 0, "Reloaded prepaid service must remain visible");
            for (int i = 0; i < remaining; i++) helper.assertTrue(restored.consumePower(i), "Reloaded credit must serve exactly the remainder");
            helper.assertTrue(!restored.consumePower(0), "A reload must not grant another free payment interval");
            helper.assertTrue(restored.chargeFraction() == 0, "Exhausted credit must be zero");
            helper.succeed();
        } finally {
            helper.setBlock(ANCHOR, Blocks.AIR);
            helper.setBlock(SWITCH, Blocks.AIR);
        }
    }

    @GameTest(templateNamespace = ArcaneChunkLoadersMod.MOD_ID, template = "blank", timeoutTicks = 160)
    public static void tickingTicketsFollowPowerRedstoneAndRemoval(GameTestHelper helper) {
        helper.setBlock(ANCHOR, AnchorRegistries.ANCHORS.get(AnchorVariant.FLUX).get());
        var anchor = (ArcaneAnchorBlockEntity) helper.getBlockEntity(ANCHOR);
        var energy = anchor.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new);
        BlockPos owner = helper.absolutePos(ANCHOR);
        Set<Long> expected = centeredChunks(owner);
        energy.receiveEnergy(AnchorConfig.FE_PER_TICK.get() * 3, false);
        helper.startSequence()
                .thenWaitUntil(() -> assertTickets(helper, owner, expected, "powered anchor"))
                .thenWaitUntil(() -> assertTickets(helper, owner, Set.of(), "starved anchor"))
                .thenExecute(() -> energy.receiveEnergy(AnchorConfig.FE_PER_TICK.get() * 100, false))
                .thenWaitUntil(() -> assertTickets(helper, owner, expected, "refilled anchor"))
                .thenExecute(() -> helper.setBlock(SWITCH, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> {
                    assertTickets(helper, owner, Set.of(), "redstone-disabled anchor");
                    helper.assertTrue(energy.getEnergyStored() > 0, "redstone release must happen before fuel starvation");
                })
                .thenExecute(() -> helper.setBlock(SWITCH, Blocks.AIR))
                .thenWaitUntil(() -> assertTickets(helper, owner, expected, "re-enabled anchor"))
                .thenExecute(() -> helper.setBlock(ANCHOR, Blocks.AIR))
                .thenExecute(() -> {
                    assertTickets(helper, owner, Set.of(), "removed anchor");
                    helper.assertTrue(AnchorSavedData.get(helper.getLevel()).byId(anchor.anchorId()) == null,
                            "block removal must remove the saved ownership record");
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = ArcaneChunkLoadersMod.MOD_ID, template = "blank", timeoutTicks = 100)
    public static void savedOwnershipAndBlockIdentityRoundTrip(GameTestHelper helper) {
        helper.setBlock(ANCHOR, AnchorRegistries.ANCHORS.get(AnchorVariant.FLUX).get());
        var anchor = (ArcaneAnchorBlockEntity) helper.getBlockEntity(ANCHOR);
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "anchor_owner"));
        var state = helper.getBlockState(ANCHOR);
        state.getBlock().setPlacedBy(helper.getLevel(), helper.absolutePos(ANCHOR), state, player, ItemStack.EMPTY);
        anchor.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new)
                .receiveEnergy(AnchorConfig.FE_PER_TICK.get() * 100, false);
        helper.startSequence()
                .thenWaitUntil(() -> assertTickets(helper, helper.absolutePos(ANCHOR), centeredChunks(helper.absolutePos(ANCHOR)), "owned anchor"))
                .thenExecute(() -> {
                    try {
                        AnchorSavedData live = AnchorSavedData.get(helper.getLevel());
                        AnchorSavedData restored = AnchorSavedData.load(live.save(new CompoundTag()));
                        var record = restored.byId(anchor.anchorId());
                        helper.assertTrue(record != null && record.equals(live.byId(anchor.anchorId())),
                                "saved record must preserve every ownership, location, power and restoration field");
                        helper.assertTrue(record.placerId().equals(player.getUUID()) && record.placerName().equals("anchor_owner"),
                                "real block placement must record the player identity");
                        helper.assertTrue(record.restorable() && restored.hasRestorableAnchor(helper.getLevel(), helper.absolutePos(ANCHOR)),
                                "powered saved ownership must remain eligible for Forge ticket restoration");
                        helper.assertTrue(!restored.hasRestorableAnchor(helper.getLevel(), helper.absolutePos(ANCHOR).offset(32, 0, 0)),
                                "another block position must not inherit ownership");
                        CompoundTag savedBlock = anchor.saveWithoutMetadata();
                        BlockEntity restoredBlock = ((ArcaneAnchorBlock) AnchorRegistries.ANCHORS.get(AnchorVariant.FLUX).get())
                                .newBlockEntity(helper.absolutePos(ANCHOR), state);
                        restoredBlock.load(savedBlock);
                        helper.assertTrue(((AnchorAccess) restoredBlock).anchorId().equals(anchor.anchorId()),
                                "block entity NBT must preserve stable anchor identity");
                        helper.assertTrue(restoredBlock.saveWithoutMetadata().getUUID("placerId").equals(player.getUUID()),
                                "block entity NBT must preserve its placer");
                    } finally {
                        helper.setBlock(ANCHOR, Blocks.AIR);
                    }
                })
                .thenSucceed();
    }

    private static Set<Long> centeredChunks(BlockPos pos) {
        ChunkPos center = new ChunkPos(pos);
        Set<Long> expected = new HashSet<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) expected.add(ChunkPos.asLong(center.x + x, center.z + z));
        return expected;
    }

    private static void assertTickets(GameTestHelper helper, BlockPos owner, Set<Long> expected, String phase) {
        ForcedChunksSavedData data = helper.getLevel().getDataStorage().get(ForcedChunksSavedData::load, ForcedChunksSavedData.FILE_ID);
        Set<Long> actual = new HashSet<>();
        var support=com.bettercontent.bettermagicchunkanchors.api.AnchorWorkApi.supportingAnchor(helper.getLevel(),owner);
        if(!expected.isEmpty()) helper.assertTrue(support.isPresent(), phase+" work query must observe real active ticket support");
        helper.assertTrue(com.bettercontent.bettermagicchunkanchors.api.AnchorWorkApi.supportingAnchor(helper.getLevel(),owner.offset(1000000,0,1000000)).isEmpty(), "Uncovered operations cannot claim anchor support");
        // Query Forge's real owned tickets by mod id AND block position; fixture tickets cannot satisfy this.
        if (data != null) for (Tag rawMod : data.save(new CompoundTag()).getList("ForgeForced", Tag.TAG_COMPOUND)) {
            CompoundTag mod = (CompoundTag) rawMod;
            if (!mod.getString("Mod").equals(ArcaneChunkLoadersMod.MOD_ID)) continue;
            for (Tag rawChunk : mod.getList("ModForced", Tag.TAG_COMPOUND)) {
                CompoundTag chunk = (CompoundTag) rawChunk;
                for (Tag rawOwner : chunk.getList("TickingBlocks", Tag.TAG_COMPOUND)) {
                    if (NbtUtils.readBlockPos((CompoundTag) rawOwner).equals(owner)) actual.add(chunk.getLong("Chunk"));
                }
                for (Tag rawOwner : chunk.getList("Blocks", Tag.TAG_COMPOUND)) {
                    helper.assertTrue(!NbtUtils.readBlockPos((CompoundTag) rawOwner).equals(owner),
                            phase + " must use fully ticking tickets, not ordinary forced tickets");
                }
            }
        }
        helper.assertTrue(actual.equals(expected), phase + " expected owned chunks " + expected + " but found " + actual);
    }
}
