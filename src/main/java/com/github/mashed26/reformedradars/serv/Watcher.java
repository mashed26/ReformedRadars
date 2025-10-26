package com.github.mashed26.reformedradars.serv;

import com.github.mashed26.reformedradars.config.ModConfig;
import com.github.mashed26.reformedradars.ReformedRadars;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Watcher {
    private final ModConfig config;
    private final Scraper weatherScraper;
    private final MinecraftServer server;
    private ScheduledExecutorService scheduler;
    private boolean isShutdown = false;

    public Watcher(ModConfig config, Scraper weatherScraper, MinecraftServer server) {
        this.config = config;
        this.weatherScraper = weatherScraper;
        this.server = server;
    }

    public Watcher(ModConfig config, ModConfig config1, Scraper weatherScraper, MinecraftServer server) {
        this.config = config1;
        this.weatherScraper = weatherScraper;
        this.server = server;
    }

    public void startWatching() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::checkTemperature, 0, config.checkIntervalMinutes, TimeUnit.MINUTES);
        ReformedRadars.LOGGER.info("Temperature monitoring started for {}", config.city);
    }

    public void stopWatching() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        ReformedRadars.LOGGER.info("Temperature monitoring stopped");
    }

    private void checkTemperature() {
        if (server == null || !config.enableTemperatureShutdown || server.isStopped()) {
            return;
        }

        Double temperature = weatherScraper.getCurrentTemperature();
        if (temperature != null) {
            ReformedRadars.LOGGER.info("Current temperature in {}: {}°F", config.city, temperature);

            if (temperature >= config.shutdownTemperature && !isShutdown) {
                // Temperature reached threshold, shutdown server
                ReformedRadars.LOGGER.warn("Temperature reached {}°F, shutting down server", config.shutdownTemperature);
                isShutdown = true;

                server.execute(() -> {
                    if (server.getPlayerManager() != null) {
                        server.getPlayerManager().broadcast(
                                net.minecraft.text.Text.literal("§cSorry, the current temp outside is " + temperature + "°F, wait until that changes"),
                                false
                        );
                    }

                    // Schedule server shutdown
                    new Thread(() -> {
                        try {
                            Thread.sleep(5000); // Give players 5 seconds to see the message
                            server.stop(false);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                });
            } else if (temperature < config.shutdownTemperature && isShutdown) {
                // Temperature dropped below threshold
                ReformedRadars.LOGGER.info("Temperature dropped to {}°F, server can be restarted", temperature);
                isShutdown = false;
            }
        } else {
            ReformedRadars.LOGGER.warn("Failed to retrieve temperature data for {}", config.city);
        }
    }
}