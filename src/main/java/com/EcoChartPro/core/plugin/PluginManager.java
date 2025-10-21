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

/**
 * A singleton service that discovers and loads custom indicator plugins.
 * It scans a designated 'indicators' directory for .java files, compiles them,
 * and loads the resulting .class files from a 'classes' directory.
 */
public final class PluginManager {

    private static final Logger logger = LoggerFactory.getLogger(PluginManager.class);
    private static volatile PluginManager instance;

    private final List<CustomIndicator> loadedIndicators = new CopyOnWriteArrayList<>();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private URLClassLoader pluginClassLoader;
    private final JavaIndicatorCompiler compiler = new JavaIndicatorCompiler();

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
     * Scans, compiles, and loads all plugins from the designated directories.
     */
    private synchronized void scanAndLoadPlugins() {
        // First, compile any new or modified .java files
        compiler.compileIndicators();

        List<URL> pluginLocations = new ArrayList<>();
        Optional<Path> classesDirOpt = AppDataManager.getClassesDirectory();

        if (classesDirOpt.isEmpty() || !Files.exists(classesDirOpt.get())) {
            logger.info("Plugin classes directory not found. No custom indicators to load.");
            if (!loadedIndicators.isEmpty()) {
                loadedIndicators.clear();
                pcs.firePropertyChange("pluginListChanged", null, getLoadedIndicators());
            }
            return;
        }

        try {
            pluginLocations.add(classesDirOpt.get().toUri().toURL());
            logger.info("Added compiled classes directory to plugin path: {}", classesDirOpt.get().toAbsolutePath());
        } catch (MalformedURLException e) {
             logger.error("Could not add classes directory to plugin path", e);
             return;
        }

        // Close the old classloader to release file locks and allow for reloading.
        if (pluginClassLoader != null) {
            try {
                pluginClassLoader.close();
                logger.debug("Closed old plugin ClassLoader.");
            } catch (IOException e) {
                logger.error("Error closing old plugin classloader.", e);
            }
        }

        // Create a new class loader for the 'classes' directory.
        pluginClassLoader = new URLClassLoader(pluginLocations.toArray(new URL[0]), getClass().getClassLoader());

        try (ScanResult scanResult = new ClassGraph()
                .overrideClassLoaders(pluginClassLoader)
                .enableClassInfo()
                .scan()) {

            ClassInfoList controlClasses = scanResult.getClassesImplementing(CustomIndicator.class.getName()).getStandardClasses();

            loadedIndicators.clear(); // Clear previous results before loading new ones.
            controlClasses.loadClasses(CustomIndicator.class)
                    .forEach(implClass -> {
                        try {
                            CustomIndicator indicator = implClass.getConstructor().newInstance();
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
     * This is called by the JavaEditorDialog after a successful compilation and by the Marketplace.
     */
    public synchronized void rescanPlugins() {
        logger.info("Manual plugin rescan triggered.");
        scanAndLoadPlugins();
    }

    public List<CustomIndicator> getLoadedIndicators() {
        return Collections.unmodifiableList(loadedIndicators);
    }
}