package com.EcoChartPro;

import com.EcoChartPro.core.gamification.AchievementService;
import com.EcoChartPro.core.plugin.PluginManager;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.theme.ThemeManager;
import com.EcoChartPro.data.LiveDataManager;
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
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public class Main {

    // [MODIFIED] Delay logger initialization
    private static Logger logger;
    // [NEW] Flag to prevent multiple initializations.
    private static final AtomicBoolean hasInitialized = new AtomicBoolean(false);


    public static void main(String[] args) {
        // [NEW] Ensure this block runs only once.
        if (hasInitialized.getAndSet(true)) {
            System.out.println("Application already initialized. Ignoring subsequent main() call.");
            return;
        }

        // Configure Logback's log directory FIRST
        AppDataManager.getLogDirectory().ifPresent(path -> {
            System.setProperty("ecochartpro.log.dir", path.toAbsolutePath().toString());
            System.out.println("Set ecochartpro.log.dir to: " + path.toAbsolutePath());
        });

        // [MODIFIED] Initialize logger AFTER setting the property.
        logger = LoggerFactory.getLogger(Main.class);

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
                com.EcoChartPro.core.gamification.GamificationService.getInstance().saveState();
                AchievementService.getInstance().saveState();
                com.EcoChartPro.core.controller.ReplaySessionManager.getInstance().shutdown();
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

        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);

        ThemeManager.applyTheme(SettingsManager.getInstance().getCurrentTheme());

        PluginManager.getInstance();

        try {
            DataSourceManager.getInstance().scanDataDirectory();
            
            LiveDataManager.getInstance().initialize(DataSourceManager.getInstance().getAvailableSources());

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