package com.bettercontent.bettermagicchunkanchors;

import net.minecraftforge.common.ForgeConfigSpec;

public final class AnchorConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue FE_CAPACITY;
    public static final ForgeConfigSpec.IntValue FE_PER_TICK;
    public static final ForgeConfigSpec.IntValue SOURCE_CAPACITY;
    public static final ForgeConfigSpec.IntValue SOURCE_PER_TICK;
    public static final ForgeConfigSpec.IntValue LIFEFORCE_CAPACITY;
    public static final ForgeConfigSpec.IntValue LIFEFORCE_PER_TICK;
    public static final ForgeConfigSpec.IntValue AIR_CAPACITY;
    public static final ForgeConfigSpec.IntValue AIR_PER_TICK;
    public static final ForgeConfigSpec.IntValue SOUL_CAPACITY;
    public static final ForgeConfigSpec.IntValue SOUL_INTERVAL;
    public static final ForgeConfigSpec.IntValue SPIRIT_CAPACITY;
    public static final ForgeConfigSpec.IntValue SPIRIT_INTERVAL;
    public static final ForgeConfigSpec.IntValue KINETIC_CAPACITY;
    public static final ForgeConfigSpec.IntValue KINETIC_MIN_RPM;
    public static final ForgeConfigSpec.DoubleValue KINETIC_STRESS_IMPACT;
    public static final ForgeConfigSpec.IntValue KINETIC_CHARGE_RATE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("All defaults provide two hours of unattended 3x3 chunk loading at 20 TPS.");
        builder.push("buffers");
        FE_CAPACITY = builder.defineInRange("fe_capacity", 36_864_000, 256, Integer.MAX_VALUE);
        FE_PER_TICK = builder.defineInRange("fe_per_tick", 256, 1, 1_000_000);
        SOURCE_CAPACITY = builder.defineInRange("source_capacity", 144_000, 1, Integer.MAX_VALUE);
        SOURCE_PER_TICK = builder.defineInRange("source_per_tick", 1, 1, 10_000);
        LIFEFORCE_CAPACITY = builder.defineInRange("lifeforce_capacity_mb", 144_000, 1, Integer.MAX_VALUE);
        LIFEFORCE_PER_TICK = builder.defineInRange("lifeforce_mb_per_tick", 1, 1, 10_000);
        AIR_CAPACITY = builder.defineInRange("air_capacity", 720_000, 1, Integer.MAX_VALUE);
        AIR_PER_TICK = builder.defineInRange("air_per_tick", 5, 1, 10_000);
        SOUL_CAPACITY = builder.defineInRange("goety_soul_capacity", 7_200, 1, Integer.MAX_VALUE);
        SOUL_INTERVAL = builder.defineInRange("goety_soul_interval_ticks", 20, 1, 72_000);
        SPIRIT_CAPACITY = builder.defineInRange("malum_spirit_capacity", 24, 1, 64);
        SPIRIT_INTERVAL = builder.defineInRange("malum_spirit_interval_ticks", 6_000, 1, 720_000);
        KINETIC_CAPACITY = builder.defineInRange("kinetic_service_tick_capacity", 144_000, 1, Integer.MAX_VALUE);
        builder.pop();
        builder.push("kinetic");
        KINETIC_MIN_RPM = builder.defineInRange("minimum_rpm", 32, 1, 256);
        KINETIC_STRESS_IMPACT = builder.defineInRange("stress_impact", 64.0, 0.0, 4096.0);
        KINETIC_CHARGE_RATE = builder.defineInRange("service_ticks_gained_per_powered_tick", 2, 1, 100);
        builder.pop();
        SPEC = builder.build();
    }

    private AnchorConfig() {}
}
