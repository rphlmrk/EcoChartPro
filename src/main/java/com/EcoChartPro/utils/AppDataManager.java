package com.EcoChartPro.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * A utility class to manage platform-specific application data directories.
 * This ensures the application stores its data in the conventional location
 * for each operating system.
 */
public final class AppDataManager {

    private static final Logger logger = LoggerFactory.getLogger(AppDataManager.class);
    private static final String APP_NAME = "EcoChartPro";
    private static final String FALLBACK_DIR_NAME = "." + APP_NAME;
    private static final String CONFIG_FILE_NAME = "app_state.properties";
    private static final String INDICATORS_DIR_NAME = "indicators";
    private static final String SCRIPTS_DIR_NAME = "scripts";
    // Constant for compiled class files from the in-app editor ---
    private static final String CLASSES_DIR_NAME = "classes";
    // Constant for the auto-save session file ---
    private static final String AUTO_SAVE_FILE_NAME = "autosave.json";


    // Private constructor to prevent instantiation.
    private AppDataManager() {}

    /**
     * Determines the appropriate OS-specific directory for application data.
     * It creates the directory if it does not already exist.
     *
     * @return A Path to the application data directory.
     * @throws IOException if the directory cannot be created.
     */
    public static Path getAppDataDirectory() throws IOException {
        String osName = System.getProperty("os.name").toLowerCase();
        Path appDataPath;

        if (osName.contains("win")) {
            // Windows: %APPDATA%\EcoChartPro
            String appDataEnv = System.getenv("APPDATA");
            if (appDataEnv != null && !appDataEnv.isEmpty()) {
                appDataPath = Paths.get(appDataEnv, APP_NAME);
            } else {
                // Fallback for Windows if APPDATA is not set for some reason
                appDataPath = Paths.get(System.getProperty("user.home"), "AppData", "Roaming", APP_NAME);
            }
        } else if (osName.contains("mac")) {
            // macOS: ~/Library/Application Support/EcoChartPro
            appDataPath = Paths.get(System.getProperty("user.home"), "Library", "Application Support", APP_NAME);
        } else {
            // Linux and other Unix-like systems: ~/.EcoChartPro (common convention)
            appDataPath = Paths.get(System.getProperty("user.home"), FALLBACK_DIR_NAME);
        }
        
        if (Files.notExists(appDataPath)) {
            Files.createDirectories(appDataPath);
            logger.info("Created application data directory at: {}", appDataPath.toAbsolutePath());
        }

        return appDataPath;
    }

    /**
     * Helper method to get the path for the custom indicators directory.
     * It creates the directory if it doesn't exist.
     * @return An Optional containing the full Path, or empty if an error occurs.
     */
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
    
    /**
     * Helper method to get the path for the custom scripts directory.
     * It creates the directory if it doesn't exist.
     * @return An Optional containing the full Path, or empty if an error occurs.
     */
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

    /**
     * New helper method to get the path for the compiled classes directory.
     * It creates the directory if it doesn't exist.
     * @return An Optional containing the full Path, or empty if an error occurs.
     */
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


    /**
     * New helper method to get the full path for a specific config file.
     * @param fileName The name of the config file (e.g., "checklists.json").
     * @return An Optional containing the full Path, or empty if an error occurs.
     */
    public static Optional<Path> getConfigFilePath(String fileName) {
        try {
            Path appDataDir = getAppDataDirectory();
            return Optional.of(appDataDir.resolve(fileName));
        } catch (IOException e) {
            logger.error("Could not create or access application data directory to get config path for {}", fileName, e);
            return Optional.empty();
        }
    }

    /**
     * New helper method to get the path for the main app config file.
     * @return An Optional containing the full Path, or empty if an error occurs.
     */
    public static Optional<Path> getAppConfigPath() {
        try {
            Path appDataDir = getAppDataDirectory();
            return Optional.of(appDataDir.resolve(CONFIG_FILE_NAME));
        } catch (IOException e) {
            logger.error("Could not create or access application data directory to get app config path", e);
            return Optional.empty();
        }
    }

    /**
     * New helper method to get the path for the auto-save session file.
     * @return An Optional containing the full Path, or empty if an error occurs.
     */
    public static Optional<Path> getAutoSaveFilePath() {
        try {
            // The auto-save file lives inside the "sessions" directory.
            Path sessionsDir = SessionManager.getInstance().getSessionsDirectory();
            return Optional.of(sessionsDir.resolve(AUTO_SAVE_FILE_NAME));
        } catch (IOException e) {
            logger.error("Could not create or access sessions directory to get auto-save path", e);
            return Optional.empty();
        }
    }
}