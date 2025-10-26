package com.github.mashed26.reformedradars;

import com.github.mashed26.reformedradars.config.ModConfig;
import com.github.mashed26.reformedradars.serv.Watcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import java.nio.file.Path;

public class ReformedRadars implements ModInitializer {
    public static final String MOD_ID = "reformedradars";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static Watcher tempWatcher;
    public static com.github.mashed26.reformedradars.serv.Scraper scraper;
    public static ModConfig config;

    @Override
    public void onInitialize() {
        LOGGER.info("Reformed Radars Mod Initializing!");

        // Load configuration
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("reformedradars.json");
        config = ModConfig.load(configPath.toFile());

        // Check if we're in a server environment
        boolean isServerEnvironment = FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.SERVER;

        if (isServerEnvironment) {
            ServerLifecycleEvents.SERVER_STARTING.register(server -> {
                // For dedicated servers, always initialize watcher
                tempWatcher = new Watcher(config, scraper, server);
                tempWatcher.startWatching();
            });

            ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
                if (tempWatcher != null) {
                    tempWatcher.stopWatching();
                }
            });
        }
    }
}
