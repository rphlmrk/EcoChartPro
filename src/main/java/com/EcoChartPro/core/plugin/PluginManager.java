package com.EcoChartPro.core.plugin;

import com.EcoChartPro.api.indicator.CustomIndicator;
import com.EcoChartPro.utils.AppDataManager;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * A singleton service that discovers and loads custom indicator plugins.
 * It scans a designated 'indicators' directory for JARs and a 'classes'
 * directory for .class files (from the in-app editor) at startup.
 */
public final class PluginManager {

    private static final Logger logger = LoggerFactory.getLogger(PluginManager.class);
    private static volatile PluginManager instance;

    private final List<CustomIndicator> loadedIndicators = new CopyOnWriteArrayList<>();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private URLClassLoader pluginClassLoader;

    private PluginManager() {
        scanAndLoadPlugins();
    }

    public static PluginManager getInstance() {
        if (instance == null) {
            synchronized (PluginManager.class) {
                if (instance == null) {
                    instance = new PluginManager();
                }
            }
        }
        return instance;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.removePropertyChangeListener(listener);
    }

    /**
     * Scans all plugin locations (JARs and the classes directory) and loads
     * any found indicators into a new ClassLoader.
     */
    private synchronized void scanAndLoadPlugins() {
        List<URL> pluginLocations = new ArrayList<>();

        // 1. Scan for JARs in the 'indicators' directory
        Optional<Path> indicatorsDirOpt = AppDataManager.getIndicatorsDirectory();
        if (indicatorsDirOpt.isPresent()) {
            logger.info("Scanning for JAR plugins in: {}", indicatorsDirOpt.get().toAbsolutePath());
            try (Stream<Path> stream = Files.walk(indicatorsDirOpt.get(), 1)) {
                stream
                    .filter(path -> path.toString().toLowerCase().endsWith(".jar"))
                    .forEach(jarPath -> {
                        try {
                            pluginLocations.add(jarPath.toUri().toURL());
                        } catch (MalformedURLException e) {
                            logger.warn("Could not convert path to URL: {}", jarPath, e);
                        }
                    });
            } catch (IOException e) {
                logger.error("Error while scanning for indicator JARs.", e);
            }
        }

        // 2. Add the 'classes' directory (for live-compiled indicators) to the classpath
        Optional<Path> classesDirOpt = AppDataManager.getClassesDirectory();
        if (classesDirOpt.isPresent() && Files.exists(classesDirOpt.get())) {
            try {
                pluginLocations.add(classesDirOpt.get().toUri().toURL());
                logger.info("Added compiled classes directory to plugin path: {}", classesDirOpt.get().toAbsolutePath());
            } catch (MalformedURLException e) {
                 logger.error("Could not add classes directory to plugin path", e);
            }
        }
        
        if (pluginLocations.isEmpty()) {
            logger.info("No plugin JARs or custom classes found.");
            // Ensure list is clear if nothing is found
            if (!loadedIndicators.isEmpty()) {
                loadedIndicators.clear();
                pcs.firePropertyChange("pluginListChanged", null, getLoadedIndicators());
            }
            return;
        }

        // Close the old classloader if it exists, to release resources
        if (pluginClassLoader != null) {
            try {
                pluginClassLoader.close();
                logger.debug("Closed old plugin ClassLoader.");
            } catch (IOException e) {
                logger.error("Error closing old plugin classloader.", e);
            }
        }
        
        // 3. Create ONE new class loader for ALL plugin locations.
        pluginClassLoader = new URLClassLoader(pluginLocations.toArray(new URL[0]), getClass().getClassLoader());

        try (ScanResult scanResult = new ClassGraph()
                .overrideClassLoaders(pluginClassLoader)
                .enableClassInfo()
                .scan()) {

            ClassInfoList controlClasses = scanResult.getClassesImplementing(CustomIndicator.class.getName()).getStandardClasses();
            
            loadedIndicators.clear(); // Clear previous results before scan
            controlClasses.loadClasses(CustomIndicator.class)
                    .forEach(implClass -> {
                        try {
                            CustomIndicator indicator = (CustomIndicator) implClass.getConstructor().newInstance();
                            loadedIndicators.add(indicator);
                            logger.info("Successfully loaded custom indicator: '{}' from {}", indicator.getName(), implClass.getSimpleName());
                        } catch (Exception e) {
                            logger.error("Failed to instantiate custom indicator plugin: {}", implClass.getName(), e);
                        }
                    });
        }
        // Notify UI that the list of available plugins has changed.
        pcs.firePropertyChange("pluginListChanged", null, getLoadedIndicators());
    }

    /**
     * Provides a public method to trigger a full rescan of all plugin sources.
     * This is called by the JavaEditorDialog after a successful compilation.
     */
    public void rescanPlugins() {
        logger.info("Manual plugin rescan triggered.");
        scanAndLoadPlugins();
    }

    public List<CustomIndicator> getLoadedIndicators() {
        return Collections.unmodifiableList(loadedIndicators);
    }
}