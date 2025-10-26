package com.github.mashed26.reformedradars.config;

import com.github.mashed26.reformedradars.ReformedRadars;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class ModConfig {
    public String city = "Sedalia";
    public String state = "MO"; // For US cities
    public String country = "US";
    public String weatherUrl = "https://wttr.in/";
    public int checkIntervalMinutes = 5;
    public int shutdownTemperature = 82;
    public boolean enableTemperatureShutdown = true;

    // Alternative weather sites to try
    public String[] backupWeatherUrls = {
            "https://weather.com/weather/today/",
            "https://www.accuweather.com/"
    };

    public Map<String, String> recipeReplacements = new HashMap<>();

    public ModConfig() {
        // Default recipe replacements
        recipeReplacements.put("minecraft:stone", "minecraft:cobblestone");
        recipeReplacements.put("minecraft:oak_planks", "minecraft:birch_planks");
    }

    public static ModConfig load(File configFile) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        if (configFile.exists()) {
            try (Reader reader = new FileReader(configFile)) {
                return gson.fromJson(reader, ModConfig.class);
            } catch (IOException e) {
                ReformedRadars.LOGGER.error("Failed to load config, using defaults", e);
            }
        }

        // Create default config
        ModConfig config = new ModConfig();
        save(config, configFile);
        return config;
    }

    public static void save(ModConfig config, File configFile) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        try (Writer writer = new FileWriter(configFile)) {
            gson.toJson(config, writer);
        } catch (IOException e) {
            ReformedRadars.LOGGER.error("Failed to save config", e);
        }
    }

    public String getLocationString() {
        if ("US".equals(country)) {
            return city.replace(" ", "+") + "," + state;
        }
        return city.replace(" ", "+") + "," + country;
    }
}