package com.owohaptics;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class HapticsConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> GAME_ID;
    public static final ModConfigSpec.ConfigValue<String> EXTRA_HOST;
    public static final ModConfigSpec.IntValue MASTER_INTENSITY;
    public static final ModConfigSpec.BooleanValue DAMAGE;
    public static final ModConfigSpec.BooleanValue ENVIRONMENT;
    public static final ModConfigSpec.BooleanValue HEARTBEAT;
    public static final ModConfigSpec.BooleanValue ARMS;
    public static final ModConfigSpec.BooleanValue EATING;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("connection");
        ENABLED = b.comment("Master switch for all haptics.").define("enabled", true);
        GAME_ID = b.comment("OWO game id. \"0\" is an anonymous connection.").define("gameId", "0");
        EXTRA_HOST = b.comment("Optional IP of the PC running the OWO app, if broadcast discovery fails (e.g. other subnet). Leave empty normally.")
                .define("owoAppHost", "");
        b.pop();
        b.push("effects");
        MASTER_INTENSITY = b.comment("Global intensity percent (0-150) applied to every sensation.").defineInRange("masterIntensity", 100, 0, 150);
        DAMAGE = b.comment("Directional hit and damage-type haptics.").define("damage", true);
        ENVIRONMENT = b.comment("Fire, drowning and freezing haptics.").define("environment", true);
        HEARTBEAT = b.comment("Heartbeat when health is low.").define("lowHealthHeartbeat", true);
        ARMS = b.comment("Per-arm haptics for swings, mining and bow draw/release (follows your main hand; works with Vivecraft).")
                .define("armHaptics", true);
        EATING = b.comment("Gentle pulses while eating or drinking.").define("eating", true);
        b.pop();
        SPEC = b.build();
    }

    private HapticsConfig() { }
}
