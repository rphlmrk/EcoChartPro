package com.EcoChartPro;

import com.EcoChartPro.core.gamification.AchievementService;
import com.EcoChartPro.core.plugin.PluginManager;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.theme.ThemeManager;
import com.EcoChartPro.ui.dashboard.DashboardFrame;
import com.EcoChartPro.ui.toolbar.components.SymbolProgressCache;
import com.EcoChartPro.utils.AppDataManager;
import com.EcoChartPro.utils.DataSourceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public class Main {

    // IMPORTANT: logger must be initialized AFTER System.setProperty for log_dir
    // For this reason, we will move its initialization later, or ensure the property is set before its first use.
    // For simplicity, we'll keep the logger here, but ensure the property is set before its usage.
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // [NEW] Configure Logback's log directory FIRST
        AppDataManager.getLogDirectory().ifPresent(path -> {
            System.setProperty("ecochartpro.log.dir", path.toAbsolutePath().toString());
            // This System.out is for critical early debugging, before logback is fully configured
            System.out.println("Set ecochartpro.log.dir to: " + path.toAbsolutePath());
        });

        // [CRITICAL DEBUGGING STEP] This log message will now appear reliably.
        logger.info("Application is running with Java from: {}", System.getProperty("java.home"));

        // shutdown hook to handle application restarts.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if ("true".equals(System.getProperty("app.restart", "false"))) {
                try {
                    logger.info("Restarting application...");
                    restartApplication();
                } catch (IOException e) {
                    logger.error("Failed to restart application.", e);
                }
            } else {
                logger.info("Application shutting down normally.");
                // Ensure gamification and achievement states are saved on normal shutdown.
                com.EcoChartPro.core.gamification.GamificationService.getInstance().saveState();
                AchievementService.getInstance().saveState();
            }
        }));

        // Load settings to get the UI scale factor BEFORE initializing any UI.
        float uiScale = SettingsManager.getInstance().getUiScale();
        System.setProperty("flatlaf.uiScale", String.valueOf(uiScale));
        logger.info("UI scaling set to: {}%", (int)(uiScale * 100));


        final boolean isMacOS = System.getProperty("os.name").toLowerCase().contains("mac");
        if (isMacOS) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            logger.info("macOS detected. Configuring for screen menu bar.");
        }

        // [FIX] Enable custom window decorations for FlatLaf.
        // This is the CRITICAL step that allows custom components in the title bar.
        // It must be called BEFORE the Look and Feel is set.
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);

        // Apply the theme from settings at startup. This sets the Look and Feel.
        ThemeManager.applyTheme(SettingsManager.getInstance().getCurrentTheme());

        PluginManager.getInstance();

        try {
            DataSourceManager.getInstance().scanDataDirectory();

            // [NEW] Build the symbol progress cache on a background thread after scanning.
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    SymbolProgressCache.getInstance().buildCache();
                    return null;
                }
            }.execute();
            
        } catch (Exception e) {
            logger.error("CRITICAL: A fatal error occurred during data source scanning. Application may not function correctly.", e);
        }

        SwingUtilities.invokeLater(() -> {
            DashboardFrame dashboard = new DashboardFrame();
            dashboard.setVisible(true);
            logger.info("Dashboard launched.");
        });
    }

    /**
     * method to handle restarting the application.
     * This finds the java command and the application's classpath to relaunch itself.
     */
    private static void restartApplication() throws IOException {
        String java = System.getProperty("java.home") + "/bin/java";
        String classpath = System.getProperty("java.class.path");
        String mainClass = Main.class.getCanonicalName();

        ArrayList<String> command = new ArrayList<>();
        command.add(java);
        command.add("-cp");
        command.add(classpath);
        command.add(mainClass);

        new ProcessBuilder(command)
            .directory(new File(System.getProperty("user.dir")))
            .start();
    }
}