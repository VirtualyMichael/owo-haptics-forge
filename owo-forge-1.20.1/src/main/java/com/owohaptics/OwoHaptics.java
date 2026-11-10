package com.owohaptics;

import com.mojang.logging.LogUtils;
import com.owohaptics.haptics.HapticsManager;
import com.owohaptics.owo.OwoClient;
import com.owohaptics.script.Effects;
import com.owohaptics.script.WebUi;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(OwoHaptics.MOD_ID)
public final class OwoHaptics {
    public static final String MOD_ID = "owohaptics";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static OwoClient client;
    public static final Effects effects = new Effects();
    private static WebUi webUi;

    /** Address of the settings page, or null if it is off. */
    public static String webUrl() {
        return webUi == null ? null : webUi.url();
    }

    public static void reloadEffects() {
        effects.load(FMLPaths.CONFIGDIR.get().resolve("owohaptics").resolve("effects.json"), LOGGER::info);
    }

    public OwoHaptics() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HapticsConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        reloadEffects();
        client = new OwoClient(HapticsConfig.GAME_ID.get(), HapticsConfig.EXTRA_HOST.get(), LOGGER::info);
        if (HapticsConfig.ENABLED.get()) client.start();
        if (HapticsConfig.WEB_ENABLED.get())
            webUi = WebUi.start(effects, client, HapticsConfig.WEB_PORT.get(), HapticsConfig.WEB_LAN.get(), LOGGER::info);
        Runtime.getRuntime().addShutdownHook(new Thread(client::stop));
        MinecraftForge.EVENT_BUS.register(new HapticsManager());
        LOGGER.info("OWO Haptics loaded; searching for the OWO app on UDP 54020");
    }
}
