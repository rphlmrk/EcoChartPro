package com.EcoChartPro.core.plugin;

import com.EcoChartPro.utils.AppDataManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MarketplaceService {

    private static final String MARKETPLACE_URL = "https://raw.githubusercontent.com/rphlmrk/EcoChartPro-Community-Marketplace/main/marketplace.json";
    private static final String INSTALLED_PLUGINS_FILE = "installed_plugins.json";
    private static final long CACHE_DURATION_SECONDS = 3600; // 1 hour

    private List<PluginInfo> cachedPluginIndex;
    private Instant lastFetchTime;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<PluginInfo> fetchPluginIndex() throws IOException {
        if (cachedPluginIndex != null && lastFetchTime != null &&
            Instant.now().getEpochSecond() - lastFetchTime.getEpochSecond() < CACHE_DURATION_SECONDS) {
            return cachedPluginIndex;
        }

        URL url = new URL(MARKETPLACE_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        try (InputStream inputStream = connection.getInputStream()) {
            cachedPluginIndex = objectMapper.readValue(inputStream, new TypeReference<>() {});
            lastFetchTime = Instant.now();
            return cachedPluginIndex;
        }
    }

    public void installPlugin(PluginInfo plugin) throws IOException {
        // 1. Download the source file
        URL sourceUrl = new URL(plugin.sourceUrl());
        Path indicatorsDir = AppDataManager.getIndicatorsDirectory()
            .orElseThrow(() -> new IOException("Could not access indicators directory."));
        String fileName = sourceUrl.getPath().substring(sourceUrl.getPath().lastIndexOf('/') + 1);
        Path targetPath = indicatorsDir.resolve(fileName);

        try (InputStream in = sourceUrl.openStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // 2. Update the local record of installed plugins
        Map<String, String> installed = getInstalledPlugins();
        installed.put(plugin.id(), plugin.version());
        saveInstalledPlugins(installed);
    }

    public void uninstallPlugin(PluginInfo plugin) throws IOException {
        // 1. Delete the source file
        URL sourceUrl = new URL(plugin.sourceUrl());
        String fileName = sourceUrl.getPath().substring(sourceUrl.getPath().lastIndexOf('/') + 1);
        String className = fileName.replace(".java", "");
        
        Optional<Path> indicatorsDirOpt = AppDataManager.getIndicatorsDirectory();
        if (indicatorsDirOpt.isPresent()) {
            Files.deleteIfExists(indicatorsDirOpt.get().resolve(fileName));
        }

        // 2. Delete the compiled class file
        Optional<Path> classesDirOpt = AppDataManager.getClassesDirectory();
        if (classesDirOpt.isPresent()) {
            Files.deleteIfExists(classesDirOpt.get().resolve(className + ".class"));
        }
        
        // 3. Update the local record
        Map<String, String> installed = getInstalledPlugins();
        installed.remove(plugin.id());
        saveInstalledPlugins(installed);
    }

    public boolean isInstalled(String pluginId) {
        return getInstalledPlugins().containsKey(pluginId);
    }

    public boolean isUpdateAvailable(PluginInfo plugin) {
        Map<String, String> installed = getInstalledPlugins();
        if (!installed.containsKey(plugin.id())) {
            return false;
        }
        // Simple version comparison, assumes higher version string is newer.
        // For production, use a proper semantic versioning library.
        String localVersion = installed.get(plugin.id());
        return plugin.version().compareTo(localVersion) > 0;
    }

    private Map<String, String> getInstalledPlugins() {
        Optional<Path> configPathOpt = AppDataManager.getConfigFilePath(INSTALLED_PLUGINS_FILE);
        if (configPathOpt.isEmpty() || Files.notExists(configPathOpt.get())) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(configPathOpt.get().toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            // Log error or handle corruption
            return new HashMap<>();
        }
    }

    private void saveInstalledPlugins(Map<String, String> plugins) throws IOException {
        Optional<Path> configPathOpt = AppDataManager.getConfigFilePath(INSTALLED_PLUGINS_FILE);
        if (configPathOpt.isEmpty()) {
            throw new IOException("Could not resolve path for installed plugins file.");
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPathOpt.get().toFile(), plugins);
    }
}