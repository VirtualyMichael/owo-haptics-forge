package com.owohaptics;

import net.minecraftforge.common.ForgeConfigSpec;

public final class HapticsConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> GAME_ID;
    public static final ForgeConfigSpec.ConfigValue<String> EXTRA_HOST;
    public static final ForgeConfigSpec.BooleanValue WEB_ENABLED;
    public static final ForgeConfigSpec.IntValue WEB_PORT;
    public static final ForgeConfigSpec.BooleanValue WEB_LAN;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("connection");
        ENABLED = b.comment("Master switch for all haptics.").define("enabled", true);
        GAME_ID = b.comment("OWO game id. \"0\" is an anonymous connection.").define("gameId", "0");
        EXTRA_HOST = b.comment("Optional IP of the PC running the OWO app, if broadcast discovery fails (e.g. other subnet). Leave empty normally.")
                .define("owoAppHost", "");
        b.pop();
        b.push("webUi");
        WEB_ENABLED = b.comment("Serve the settings page (strengths, zones, test buttons) while the game runs. Type /owo ui for the address.")
                .define("enabled", true);
        WEB_PORT = b.comment("Preferred port; the next free one is used if it is taken.").defineInRange("port", 8765, 1024, 65000);
        WEB_LAN = b.comment("Allow other devices on your network (phone, second PC) to open the page. Anyone on that network could then change your settings.")
                .define("allowNetworkAccess", false);
        b.pop();
        SPEC = b.build();
    }

    private HapticsConfig() { }
}
