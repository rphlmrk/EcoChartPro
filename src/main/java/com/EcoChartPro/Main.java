package com.EcoChartPro;

import com.EcoChartPro.core.controller.LiveSessionTrackerService;
import com.EcoChartPro.core.gamification.AchievementService;
import com.EcoChartPro.core.plugin.PluginManager;
import com.EcoChartPro.core.service.InternetConnectivityService;
import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.theme.ThemeManager;
import com.EcoChartPro.data.LiveDataManager;
import com.EcoChartPro.ui.dashboard.DashboardFrame;
import com.EcoChartPro.ui.toolbar.components.SymbolProgressCache;
import com.EcoChartPro.utils.AppDataManager;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
// MODIFIED: Removed AtomicBoolean as it's ineffective across classloaders
// import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import com.EcoChartPro.utils.DatabaseManager;

public class Main {

    private static Logger logger;
    // MODIFIED: Replaced AtomicBoolean with a System Property check for a true global lock.
    private static final String INITIALIZED_PROPERTY = "ecochartpro.initialized";


    public static void main(String[] args) {
        // MODIFIED: Use a system property to ensure single initialization across the entire JVM.
        if ("true".equals(System.getProperty(INITIALIZED_PROPERTY))) {
            System.out.println("Application already initialized. Ignoring subsequent main() call.");
            return;
        }
        System.setProperty(INITIALIZED_PROPERTY, "true");

        // Configure Logback's log directory FIRST
        AppDataManager.getLogDirectory().ifPresent(path -> {
            System.setProperty("ecochartpro.log.dir", path.toAbsolutePath().toString());
            System.out.println("Set ecochartpro.log.dir to: " + path.toAbsolutePath());
        });

        logger = LoggerFactory.getLogger(Main.class);

        // [MODIFIED] Add a startup task to prune old trade candle data
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                logger.info("Performing startup maintenance: Pruning old trade candle data...");
                int retentionMonths = SettingsService.getInstance().getTradeCandleRetentionMonths();
                if (retentionMonths == -1) {
                    logger.info("Trade candle retention is set to 'Forever'. Skipping pruning.");
                    return null;
                }
                Instant olderThan = Instant.now().minus(retentionMonths * 30L, java.time.temporal.ChronoUnit.DAYS);
                DatabaseManager.getInstance().pruneOldTradeCandles(olderThan);
                return null;
            }
        }.execute();

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
                // [MODIFIED] Save the live session state on normal shutdown
                LiveSessionTrackerService.getInstance().stop(); // This will perform a final save
                com.EcoChartPro.core.gamification.GamificationService.getInstance().saveState();
                AchievementService.getInstance().saveState();
                com.EcoChartPro.core.controller.ReplaySessionManager.getInstance().shutdown();
                InternetConnectivityService.getInstance().stop(); 
            }
        }));

        // Load settings to get the UI scale factor BEFORE initializing any UI.
        float uiScale = SettingsService.getInstance().getUiScale();
        System.setProperty("flatlaf.uiScale", String.valueOf(uiScale));
        logger.info("UI scaling set to: {}%", (int)(uiScale * 100));


        final boolean isMacOS = System.getProperty("os.name").toLowerCase().contains("mac");
        if (isMacOS) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            logger.info("macOS detected. Configuring for screen menu bar.");
        }

        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);

        ThemeManager.applyTheme(SettingsService.getInstance().getCurrentTheme());

        PluginManager.getInstance();

        // [NEW] Start the internet connectivity checker
        InternetConnectivityService.getInstance().start();
        
        // [NEW] Start the live session tracker
        LiveSessionTrackerService.getInstance().start();

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