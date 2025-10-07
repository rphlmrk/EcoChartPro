package com.EcoChartPro.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public final class AppDataManager {

    private static final Logger logger = LoggerFactory.getLogger(AppDataManager.class);
    private static final String APP_NAME = "EcoChartPro";
    private static final String FALLBACK_DIR_NAME = "." + APP_NAME;
    private static final String CONFIG_FILE_NAME = "app_state.properties";
    private static final String INDICATORS_DIR_NAME = "indicators";
    private static final String SCRIPTS_DIR_NAME = "scripts";
    private static final String CLASSES_DIR_NAME = "classes";
    private static final String AUTO_SAVE_FILE_NAME = "autosave.json";
    private static final String LOGS_DIR_NAME = "logs"; // [NEW] Log directory name


    private AppDataManager() {}

    public static Path getAppDataDirectory() throws IOException {
        String osName = System.getProperty("os.name").toLowerCase();
        Path appDataPath;

        if (osName.contains("win")) {
            String appDataEnv = System.getenv("APPDATA");
            if (appDataEnv != null && !appDataEnv.isEmpty()) {
                appDataPath = Paths.get(appDataEnv, APP_NAME);
            } else {
                appDataPath = Paths.get(System.getProperty("user.home"), "AppData", "Roaming", APP_NAME);
            }
        } else if (osName.contains("mac")) {
            appDataPath = Paths.get(System.getProperty("user.home"), "Library", "Application Support", APP_NAME);
        } else {
            appDataPath = Paths.get(System.getProperty("user.home"), FALLBACK_DIR_NAME);
        }
        
        if (Files.notExists(appDataPath)) {
            Files.createDirectories(appDataPath);
            logger.info("Created application data directory at: {}", appDataPath.toAbsolutePath());
        }

        return appDataPath;
    }

    public static Optional<Path> getIndicatorsDirectory() {
        try {
            Path appDataDir = getAppDataDirectory();
            Path indicatorsDir = appDataDir.resolve(INDICATORS_DIR_NAME);
            if (Files.notExists(indicatorsDir)) {
                Files.createDirectories(indicatorsDir);
                logger.info("Created custom indicators directory at: {}", indicatorsDir.toAbsolutePath());
            }
            return Optional.of(indicatorsDir);
        } catch (IOException e) {
            logger.error("Could not create or access custom indicators directory.", e);
            return Optional.empty();
        }
    }
    
    public static Optional<Path> getScriptsDirectory() {
        try {
            Path appDataDir = getAppDataDirectory();
            Path scriptsDir = appDataDir.resolve(SCRIPTS_DIR_NAME);
            if (Files.notExists(scriptsDir)) {
                Files.createDirectories(scriptsDir);
                logger.info("Created custom scripts directory at: {}", scriptsDir.toAbsolutePath());
            }
            return Optional.of(scriptsDir);
        } catch (IOException e) {
            logger.error("Could not create or access custom scripts directory.", e);
            return Optional.empty();
        }
    }

    public static Optional<Path> getClassesDirectory() {
        try {
            Path appDataDir = getAppDataDirectory();
            Path classesDir = appDataDir.resolve(CLASSES_DIR_NAME);
            if (Files.notExists(classesDir)) {
                Files.createDirectories(classesDir);
                logger.info("Created compiled classes directory at: {}", classesDir.toAbsolutePath());
            }
            return Optional.of(classesDir);
        } catch (IOException e) {
            logger.error("Could not create or access compiled classes directory.", e);
            return Optional.empty();
        }
    }

    public static Optional<Path> getConfigFilePath(String fileName) {
        try {
            Path appDataDir = getAppDataDirectory();
            return Optional.of(appDataDir.resolve(fileName));
        } catch (IOException e) {
            logger.error("Could not create or access application data directory to get config path for {}", fileName, e);
            return Optional.empty();
        }
    }

    public static Optional<Path> getAppConfigPath() {
        try {
            Path appDataDir = getAppDataDirectory();
            return Optional.of(appDataDir.resolve(CONFIG_FILE_NAME));
        } catch (IOException e) {
            logger.error("Could not create or access application data directory to get app config path", e);
            return Optional.empty();
        }
    }

    public static Optional<Path> getAutoSaveFilePath() {
        try {
            Path sessionsDir = SessionManager.getInstance().getSessionsDirectory();
            return Optional.of(sessionsDir.resolve(AUTO_SAVE_FILE_NAME));
        } catch (IOException e) {
            logger.error("Could not create or access sessions directory to get auto-save path", e);
            return Optional.empty();
        }
    }
    
    /**
     * [NEW] Helper method to get the path for the log directory.
     * It creates the directory if it doesn't exist.
     * @return An Optional containing the full Path, or empty if an error occurs.
     */
    public static Optional<Path> getLogDirectory() {
        try {
            Path appDataDir = getAppDataDirectory();
            Path logsDir = appDataDir.resolve(LOGS_DIR_NAME);
            if (Files.notExists(logsDir)) {
                Files.createDirectories(logsDir);
                System.out.println("Created log directory at: " + logsDir.toAbsolutePath()); // Use System.out as logger might not be init yet
            }
            return Optional.of(logsDir);
        } catch (IOException e) {
            System.err.println("CRITICAL: Could not create or access log directory: " + e.getMessage()); // Use System.err
            return Optional.empty();
        }
    }
}