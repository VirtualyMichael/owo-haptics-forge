package com.owohaptics;

import com.mojang.logging.LogUtils;
import com.owohaptics.haptics.HapticsManager;
import com.owohaptics.owo.OwoClient;
import com.owohaptics.script.Effects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(value = OwoHaptics.MOD_ID, dist = Dist.CLIENT)
public final class OwoHaptics {
    public static final String MOD_ID = "owohaptics";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static OwoClient client;
    public static final Effects effects = new Effects();

    public static void reloadEffects() {
        effects.load(FMLPaths.CONFIGDIR.get().resolve("owohaptics").resolve("effects.json"), LOGGER::info);
    }

    public OwoHaptics(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, HapticsConfig.SPEC);
        modBus.addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        reloadEffects();
        client = new OwoClient(HapticsConfig.GAME_ID.get(), HapticsConfig.EXTRA_HOST.get(), LOGGER::info);
        if (HapticsConfig.ENABLED.get()) client.start();
        Runtime.getRuntime().addShutdownHook(new Thread(client::stop));
        NeoForge.EVENT_BUS.register(new HapticsManager());
        LOGGER.info("OWO Haptics loaded; searching for the OWO app on UDP 54020");
    }
}
