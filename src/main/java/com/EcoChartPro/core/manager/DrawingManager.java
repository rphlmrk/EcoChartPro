package com.EcoChartPro.core.manager;

import com.EcoChartPro.core.commands.AddDrawingCommand;
import com.EcoChartPro.core.commands.RemoveDrawingCommand;
import com.EcoChartPro.core.commands.UndoableCommand;
import com.EcoChartPro.core.commands.UpdateDrawingCommand;
import com.EcoChartPro.core.manager.listener.DrawingListener;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.ui.chart.axis.IChartAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A singleton manager that serves as the "source of truth" for all drawing objects.
 * It holds a master list of drawings for each symbol and notifies listeners of any changes.
 * All modification operations are routed through the UndoManager.
 * This class is thread-safe.
 */
public final class DrawingManager {

    private static final Logger logger = LoggerFactory.getLogger(DrawingManager.class);
    private static volatile DrawingManager instance;

    // MODIFICATION: Changed from a single map to a map of maps, keyed by symbol.
    private final Map<String, Map<UUID, DrawingObject>> drawingsBySymbol = new ConcurrentHashMap<>();
    private volatile String activeSymbol;

    private final CopyOnWriteArrayList<DrawingListener> listeners = new CopyOnWriteArrayList<>();

    private volatile UUID selectedDrawingId;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private DrawingManager() {
        // Private constructor to enforce singleton pattern.
    }

    public static DrawingManager getInstance() {
        if (instance == null) {
            synchronized (DrawingManager.class) {
                if (instance == null) {
                    instance = new DrawingManager();
                }
            }
        }
        return instance;
    }

    /**
     * [NEW] Sets the currently active symbol. This determines which set of drawings are manipulated and displayed.
     * @param symbol The symbol identifier (e.g., "btcusdt").
     */
    public void setActiveSymbol(String symbol) {
        if (symbol != null && !symbol.equals(this.activeSymbol)) {
            String oldSymbol = this.activeSymbol;
            logger.debug("Active symbol switched from {} to {}", oldSymbol, symbol);
            this.activeSymbol = symbol;
            // Ensure a map exists for the new symbol.
            this.drawingsBySymbol.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>());
            setSelectedDrawingId(null); // Deselect when switching symbols.
            pcs.firePropertyChange("activeSymbolChanged", oldSymbol, symbol);
        }
    }

    /**
     * [NEW] Helper method to get the map of drawings for the currently active symbol.
     * @return The map of drawings, or null if no symbol is active.
     */
    private Map<UUID, DrawingObject> getActiveDrawingsMap() {
        if (activeSymbol == null) {
            return null;
        }
        return drawingsBySymbol.get(activeSymbol);
    }

    public DrawingObject getDrawingById(UUID id) {
        if (id == null) return null;
        Map<UUID, DrawingObject> activeDrawings = getActiveDrawingsMap();
        return (activeDrawings != null) ? activeDrawings.get(id) : null;
    }

    public UUID getSelectedDrawingId() {
        return selectedDrawingId;
    }

    public DrawingObject getSelectedDrawing() {
        if (selectedDrawingId == null) return null;
        Map<UUID, DrawingObject> activeDrawings = getActiveDrawingsMap();
        return (activeDrawings != null) ? activeDrawings.get(selectedDrawingId) : null;
    }

    public void setSelectedDrawingId(UUID id) {
        UUID oldId = this.selectedDrawingId;
        this.selectedDrawingId = id;
        pcs.firePropertyChange("selectedDrawingChanged", oldId, this.selectedDrawingId);
    }

    public DrawingObject findDrawingAt(Point screenPoint, IChartAxis axis, List<? extends AbstractChartData> data, Timeframe timeframe) {
        if (!axis.isConfigured() || getActiveDrawingsMap() == null) {
            return null;
        }

        List<DrawingObject> drawingList = new ArrayList<>(getActiveDrawingsMap().values());
        Collections.reverse(drawingList);

        for (DrawingObject drawing : drawingList) {
            if (drawing.isHit(screenPoint, axis, data, timeframe)) {
                return drawing;
            }
        }
        return null;
    }

    /**
     * [MODIFIED] Clears drawings only for the currently active symbol.
     */
    public void clearAllDrawings() {
        Map<UUID, DrawingObject> activeDrawings = getActiveDrawingsMap();
        if (activeDrawings != null) {
            List<UUID> idsToRemove = new ArrayList<>(activeDrawings.keySet());
            idsToRemove.forEach(this::performRemove); // Should use active symbol's remove
            logger.debug("All drawings for symbol {} cleared.", activeSymbol);
        }
    }
    
    /**
     * [NEW] Clears all drawings across all symbols. Used for full resets.
     */
    public void clearAllDrawingsForAllSymbols() {
        drawingsBySymbol.clear();
        selectedDrawingId = null;
        logger.debug("All drawings for all symbols have been cleared.");
        pcs.firePropertyChange("activeSymbolChanged", null, null);
    }

    /**
     * [MODIFIED] Restores drawings for a specific symbol, typically from a saved session.
     * @param symbol The symbol to restore drawings for.
     * @param drawingsToRestore The list of drawings.
     */
    public void restoreDrawingsForSymbol(String symbol, List<DrawingObject> drawingsToRestore) {
        if (symbol == null) return;
        Map<UUID, DrawingObject> symbolDrawings = drawingsBySymbol.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>());
        symbolDrawings.clear();

        if (drawingsToRestore != null) {
            for (DrawingObject drawing : drawingsToRestore) {
                symbolDrawings.put(drawing.id(), drawing);
            }
        }
        logger.info("Restored {} drawings for symbol {}.", drawingsToRestore != null ? drawingsToRestore.size() : 0, symbol);
        if (symbol.equals(activeSymbol)) {
             pcs.firePropertyChange("activeSymbolChanged", null, symbol);
        }
    }

    public void addDrawing(DrawingObject drawingObject) {
        if (drawingObject == null || activeSymbol == null) {
            logger.warn("Attempted to add a null drawing object or no active symbol is set.");
            return;
        }
        // NOTE: Assumes command objects are updated to handle the symbol context implicitly via performAdd.
        UndoableCommand command = new AddDrawingCommand(drawingObject);
        UndoManager.getInstance().executeCommand(command);
    }

    public void updateDrawing(DrawingObject drawingObject) {
        if (drawingObject == null || activeSymbol == null) {
            logger.warn("Attempted to update a null drawing object or no active symbol is set.");
            return;
        }
        Map<UUID, DrawingObject> activeDrawings = getActiveDrawingsMap();
        if (activeDrawings == null) return;
        
        DrawingObject stateBefore = activeDrawings.get(drawingObject.id());
        if (stateBefore == null) {
            logger.error("Attempted to update a drawing that does not exist in the manager: {}", drawingObject.id());
            return;
        }
        if (stateBefore.equals(drawingObject)) {
            return;
        }
        UndoableCommand command = new UpdateDrawingCommand(stateBefore, drawingObject);
        UndoManager.getInstance().executeCommand(command);
    }

    public void updateDrawingPreview(DrawingObject drawingObject) {
        if (drawingObject == null || activeSymbol == null) {
            logger.warn("Attempted to update a null drawing preview or no active symbol is set.");
            return;
        }
        performUpdate(drawingObject);
    }

    public void removeDrawing(UUID drawingObjectId) {
        if (drawingObjectId == null || activeSymbol == null) return;
        Map<UUID, DrawingObject> activeDrawings = getActiveDrawingsMap();
        if (activeDrawings == null) return;

        DrawingObject objectToRemove = activeDrawings.get(drawingObjectId);
        if (objectToRemove != null) {
            UndoableCommand command = new RemoveDrawingCommand(objectToRemove);
            UndoManager.getInstance().executeCommand(command);
        } else {
            logger.warn("Attempted to remove a non-existent drawing with ID: {}", drawingObjectId);
        }
    }

    // --- Internal Command Methods ---
    // These now operate on the active symbol's drawing map.

    public void performAdd(DrawingObject drawingObject) {
        Map<UUID, DrawingObject> activeDrawings = getActiveDrawingsMap();
        if (activeDrawings != null) {
            activeDrawings.put(drawingObject.id(), drawingObject);
            logger.debug("Performed add for drawing: {} on symbol {}", drawingObject.id(), activeSymbol);
            notifyDrawingAdded(drawingObject);
        }
    }

    public void performUpdate(DrawingObject drawingObject) {
        Map<UUID, DrawingObject> activeDrawings = getActiveDrawingsMap();
        if (activeDrawings != null) {
            activeDrawings.put(drawingObject.id(), drawingObject);
            logger.debug("Performed update for drawing: {} on symbol {}", drawingObject.id(), activeSymbol);
            notifyDrawingUpdated(drawingObject);
        }
    }

    public void performRemove(UUID drawingObjectId) {
        Map<UUID, DrawingObject> activeDrawings = getActiveDrawingsMap();
        if (activeDrawings != null) {
            DrawingObject removedObject = activeDrawings.remove(drawingObjectId);
            if (removedObject != null) {
                logger.debug("Performed remove for drawing ID: {} on symbol {}", drawingObjectId, activeSymbol);
                notifyDrawingRemoved(drawingObjectId);
            }
        }
    }

    public List<DrawingObject> getVisibleDrawings(TimeRange timeRange, PriceRange priceRange) {
        List<DrawingObject> visibleDrawings = new ArrayList<>();
        Map<UUID, DrawingObject> activeDrawings = getActiveDrawingsMap();
        if (timeRange == null || priceRange == null || activeDrawings == null) {
            return visibleDrawings;
        }
        for (DrawingObject drawing : activeDrawings.values()) {
            if (isDrawingVisible(drawing, timeRange, priceRange)) {
                visibleDrawings.add(drawing);
            }
        }
        return visibleDrawings;
    }

    private boolean isDrawingVisible(DrawingObject drawing, TimeRange timeRange, PriceRange priceRange) {
        return drawing.isVisible(timeRange, priceRange);
    }

    public List<DrawingObject> getAllDrawings() {
        Map<UUID, DrawingObject> activeDrawings = getActiveDrawingsMap();
        if (activeDrawings == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(activeDrawings.values());
    }

    /**
     * [NEW] Gets all drawings for a specific symbol, regardless of whether it's active.
     * @param symbol The symbol identifier.
     * @return A list of all drawings for that symbol.
     */
    public List<DrawingObject> getAllDrawingsForSymbol(String symbol) {
        if (symbol == null) {
            return Collections.emptyList();
        }
        Map<UUID, DrawingObject> symbolDrawings = drawingsBySymbol.get(symbol);
        if (symbolDrawings == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(symbolDrawings.values());
    }
    
    /**
     * [NEW] Gets a set of all symbol identifiers for which drawings have been stored.
     * @return A set of known symbol strings.
     */
    public Set<String> getAllKnownSymbols() {
        return drawingsBySymbol.keySet();
    }
    
    // Deprecated methods that are replaced by symbol-aware versions.
    @Deprecated
    public void restoreDrawings(List<DrawingObject> drawingsToRestore) {
        logger.warn("Deprecated method restoreDrawings called. Use restoreDrawingsForSymbol instead.");
        if (activeSymbol != null) {
            restoreDrawingsForSymbol(activeSymbol, drawingsToRestore);
        }
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }

    public void addListener(DrawingListener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(DrawingListener listener) {
        listeners.remove(listener);
    }

    private void notifyDrawingAdded(DrawingObject drawingObject) {
        for (DrawingListener listener : listeners) {
            listener.onDrawingAdded(drawingObject);
        }
    }

    private void notifyDrawingUpdated(DrawingObject drawingObject) {
        for (DrawingListener listener : listeners) {
            listener.onDrawingUpdated(drawingObject);
        }
    }

    private void notifyDrawingRemoved(UUID drawingObjectId) {
        for (DrawingListener listener : listeners) {
            listener.onDrawingRemoved(drawingObjectId);
        }
    }
}