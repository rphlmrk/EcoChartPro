package com.EcoChartPro.core.manager;

import com.EcoChartPro.core.commands.AddDrawingCommand;
import com.EcoChartPro.core.commands.RemoveDrawingCommand;
import com.EcoChartPro.core.commands.UndoableCommand;
import com.EcoChartPro.core.commands.UpdateDrawingCommand;
import com.EcoChartPro.core.manager.listener.DrawingListener;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.ui.chart.axis.ChartAxis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A singleton manager that serves as the "source of truth" for all drawing objects.
 * It holds the master list of drawings and notifies listeners of any changes.
 * All modification operations are now routed through the UndoManager.
 * This class is thread-safe.
 */
public final class DrawingManager {

    private static final Logger logger = LoggerFactory.getLogger(DrawingManager.class);
    private static volatile DrawingManager instance;

    private final Map<UUID, DrawingObject> drawings = new ConcurrentHashMap<>();
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

    public DrawingObject getDrawingById(UUID id) {
        if (id == null) return null;
        return drawings.get(id);
    }

    public UUID getSelectedDrawingId() {
        return selectedDrawingId;
    }

    public void setSelectedDrawingId(UUID id) {
        UUID oldId = this.selectedDrawingId;
        this.selectedDrawingId = id;
        pcs.firePropertyChange("selectedDrawingChanged", oldId, this.selectedDrawingId);
    }

    public DrawingObject findDrawingAt(Point screenPoint, ChartAxis axis, List<KLine> klines, Timeframe timeframe) {
        if (!axis.isConfigured()) {
            return null;
        }

        List<DrawingObject> drawingList = new ArrayList<>(drawings.values());
        Collections.reverse(drawingList);

        for (DrawingObject drawing : drawingList) {
            if (drawing.isHit(screenPoint, axis, klines, timeframe)) {
                return drawing;
            }
        }
        return null;
    }

    public void clearAllDrawings() {
        List<UUID> idsToRemove = new ArrayList<>(drawings.keySet());
        idsToRemove.forEach(this::performRemove);
        logger.debug("All drawings cleared (non-undoable operation).");
    }

    public void restoreDrawings(List<DrawingObject> drawingsToRestore) {
        clearAllDrawings();
        if (drawingsToRestore == null) return;
        for (DrawingObject drawing : drawingsToRestore) {
            this.drawings.put(drawing.id(), drawing);
        }
        logger.info("Restored {} drawings from session state.", drawingsToRestore.size());
    }

    public void addDrawing(DrawingObject drawingObject) {
        if (drawingObject == null) {
            logger.warn("Attempted to add a null drawing object.");
            return;
        }
        UndoableCommand command = new AddDrawingCommand(drawingObject);
        UndoManager.getInstance().executeCommand(command);
    }

    public void updateDrawing(DrawingObject drawingObject) {
        if (drawingObject == null) {
            logger.warn("Attempted to update a null drawing object.");
            return;
        }
        DrawingObject stateBefore = drawings.get(drawingObject.id());
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

    /**
     * MODIFICATION: New method to update a drawing for live feedback (e.g., dragging)
     * without creating an undo/redo command. This directly calls the internal performUpdate.
     * @param drawingObject The temporary state of the drawing object.
     */
    public void updateDrawingPreview(DrawingObject drawingObject) {
        if (drawingObject == null) {
            logger.warn("Attempted to update a null drawing preview.");
            return;
        }
        performUpdate(drawingObject);
    }

    public void removeDrawing(UUID drawingObjectId) {
        if (drawingObjectId == null) return;
        DrawingObject objectToRemove = drawings.get(drawingObjectId);
        if (objectToRemove != null) {
            UndoableCommand command = new RemoveDrawingCommand(objectToRemove);
            UndoManager.getInstance().executeCommand(command);
        } else {
            logger.warn("Attempted to remove a non-existent drawing with ID: {}", drawingObjectId);
        }
    }


    // --- Internal Command Methods ---

    public void performAdd(DrawingObject drawingObject) {
        drawings.put(drawingObject.id(), drawingObject);
        logger.debug("Performed add for drawing: {}", drawingObject.id());
        notifyDrawingAdded(drawingObject);
    }

    public void performUpdate(DrawingObject drawingObject) {
        drawings.put(drawingObject.id(), drawingObject);
        logger.debug("Performed update for drawing: {}", drawingObject.id());
        notifyDrawingUpdated(drawingObject);
    }

    public void performRemove(UUID drawingObjectId) {
        DrawingObject removedObject = drawings.remove(drawingObjectId);
        if (removedObject != null) {
            logger.debug("Performed remove for drawing ID: {}", drawingObjectId);
            notifyDrawingRemoved(drawingObjectId);
        }
    }


    public List<DrawingObject> getVisibleDrawings(TimeRange timeRange, PriceRange priceRange) {
        List<DrawingObject> visibleDrawings = new ArrayList<>();
        if (timeRange == null || priceRange == null) {
            return visibleDrawings;
        }
        for (DrawingObject drawing : drawings.values()) {
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
        return new ArrayList<>(drawings.values());
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