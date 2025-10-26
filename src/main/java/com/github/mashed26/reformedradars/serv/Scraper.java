package com.github.mashed26.reformedradars.serv;

import com.github.mashed26.reformedradars.config.ModConfig;
import com.github.mashed26.reformedradars.ReformedRadars;
import com.github.mashed26.reformedradars.ReformedRadars;
import org.apache.logging.log4j.Level;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Scraper {
    private final ModConfig config;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

    public Scraper(ModConfig config) {
        this.config = config;
    }

    public Double getCurrentTemperature() {
        Double temperature = null;

        // Try wttr.in first (simple and reliable)
        temperature = tryWttrIn();
        if (temperature != null) return temperature;

        // Try backup methods if wttr.in fails
        for (String backupUrl : config.backupWeatherUrls) {
            temperature = tryBackupScraper(backupUrl);
            if (temperature != null) return temperature;
        }

        return null;
    }

    private Double tryWttrIn() {
        try {
            String urlString = config.weatherUrl + config.getLocationString() + "?format=%t";
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String response = in.readLine();
                in.close();

                if (response != null && !response.isEmpty()) {
                    // wttr.in returns temperature in format like "+68°F" or "-5°C"
                    String tempString = response.replaceAll("[^\\d-+]", "").trim();
                    if (!tempString.isEmpty()) {
                        // Remove + sign if present
                        if (tempString.startsWith("+")) {
                            tempString = tempString.substring(1);
                        }
                        return Double.parseDouble(tempString);
                    }
                }
            }
        } catch (Exception e) {
            ReformedRadars.LOGGER.log(Level.valueOf(""), e);
        }
        return null;
    }

    private Double tryBackupScraper(String baseUrl) {
        try {
            String urlString;
            if (baseUrl.contains("weather.com")) {
                urlString = baseUrl + config.getLocationString().replace("+", "%20");
            } else if (baseUrl.contains("accuweather.com")) {
                urlString = "https://www.accuweather.com/en/search-locations?query=" + config.getLocationString();
            } else {
                urlString = baseUrl;
            }

            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                Document doc = Jsoup.parse(connection.getInputStream(), "UTF-8", urlString);

                // Try to find temperature using various CSS selectors
                Double temp = extractTemperatureFromDocument(doc);
                if (temp != null) return temp;

            }
        } catch (Exception e) {
            ReformedRadars.LOGGER.debug("" + baseUrl, e);
        }
        return null;
    }

    private Double extractTemperatureFromDocument(Document doc) {
        // Common CSS selectors for temperature across weather sites
        String[] temperatureSelectors = {
                ".temperature",
                ".temp",
                ".current-temp",
                ".today_nowcard-temp",
                ".CurrentConditions--tempValue--1RYJJ",
                ".feels-like-temperature",
                "[data-testid='TemperatureValue']",
                ".wx-temperature"
        };

        // Try each selector
        for (String selector : temperatureSelectors) {
            Elements elements = doc.select(selector);
            for (Element element : elements) {
                Double temp = parseTemperatureFromText(element.text());
                if (temp != null) return temp;
            }
        }

        // Fallback: look for temperature patterns in the entire document
        return findTemperatureInText(doc.text());
    }

    private Double parseTemperatureFromText(String text) {
        if (text == null || text.isEmpty()) return null;

        Pattern pattern = Pattern.compile("(-?\\d{1,3})\\s*[°]?\\s*[CF]");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            try {
                double temp = Double.parseDouble(matcher.group(1));

                // If it's in Celsius and we need Fahrenheit, convert it
                if (text.toUpperCase().contains("C") && !text.toUpperCase().contains("F")) {
                    temp = (temp * 9/5) + 32;
                }

                return temp;
            } catch (NumberFormatException e) {
                ReformedRadars.LOGGER.debug("Failed to parse from text: " + text);
            }
        }

        return null;
    }

    private Double findTemperatureInText(String text) {
        if (text == null || text.isEmpty()) return null;

        // Look for temperature mentions in the text
        String[] words = text.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            String word = words[i].replaceAll("[^\\d-]", "");
            if (!word.isEmpty()) {
                try {
                    double temp = Double.parseDouble(word);
                    // Check if this might be a temperature (reasonable range for weather)
                    if (temp >= -50 && temp <= 130) {
                        // Check surrounding context for F/C indicators
                        String context = text.toLowerCase();
                        int start = Math.max(0, i - 3);
                        int end = Math.min(words.length, i + 3);

                        StringBuilder contextBuilder = new StringBuilder();
                        for (int j = start; j < end; j++) {
                            contextBuilder.append(words[j]).append(" ");
                        }
                        String tempContext = contextBuilder.toString().toLowerCase();

                        // Assume Fahrenheit by default, convert if Celsius is mentioned
                        if (tempContext.contains("c") && !tempContext.contains("f")) {
                            temp = (temp * 9/5) + 32;
                        }

                        return temp;
                    }
                } catch (NumberFormatException e) {
                    // Continue to next word
                }
            }
        }

        return null;
    }
}