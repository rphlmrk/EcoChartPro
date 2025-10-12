package com.EcoChartPro.core.controller;

import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.model.ChartDataModel.ChartMode;
import com.EcoChartPro.model.KLine;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class ChartInteractionManager implements ReplayStateListener {

    private final ChartDataModel model;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // View state fields
    private int startIndex = 0;
    private int barsPerScreen = 200;
    private boolean viewingLiveEdge = true;
    private double rightMarginRatio = 0.20;

    // Y-Axis state fields (for Phase 2)
    private boolean isAutoScalingY = true;
    private boolean isInvertedY = false;
    private BigDecimal manualMinPrice;
    private BigDecimal manualMaxPrice;

    public ChartInteractionManager(ChartDataModel model) {
        this.model = model;
    }

    public void pan(int barDelta) {
        if (model == null) return;

        // In live mode, prevent panning into the future
        if (model.getCurrentMode() == ChartMode.LIVE && barDelta < 0) {
            if (startIndex + barDelta + barsPerScreen > model.getTotalCandleCount() + (int)(barsPerScreen * rightMarginRatio)) {
                return;
            }
        }

        viewingLiveEdge = false;
        int newStartIndex = Math.max(0, this.startIndex + barDelta);
        // Use rightMarginRatio to prevent panning too far right
        int maxStartIndex = Math.max(0, model.getTotalCandleCount() - (int)(barsPerScreen * (1.0 - rightMarginRatio)));
        newStartIndex = Math.min(newStartIndex, maxStartIndex);

        if (this.startIndex == newStartIndex) return;

        this.startIndex = newStartIndex;
        fireViewStateChanged();
    }

    public void zoom(double zoomFactor, double cursorXRatio) {
        if (model == null) return;

        viewingLiveEdge = false;
        int totalSize = model.getTotalCandleCount();

        // 1. Determine the absolute index of the bar under the cursor before zooming.
        int barsBeforeCursor = (int) (this.barsPerScreen * cursorXRatio);
        int cursorIndex = this.startIndex + barsBeforeCursor;

        // 2. Calculate the new number of bars to display.
        int newBarsPerScreen = Math.max(20, Math.min((int)(this.barsPerScreen / zoomFactor), 1000));
        
        // 3. Calculate the new start index to keep the cursor at the same screen ratio.
        int newBarsBeforeCursor = (int) (newBarsPerScreen * cursorXRatio);
        int newStartIndex = Math.max(0, cursorIndex - newBarsBeforeCursor);
        
        // 4. Ensure the new start index doesn't scroll past the end of the data.
        newStartIndex = Math.min(newStartIndex, totalSize - newBarsPerScreen);
        
        if (this.barsPerScreen == newBarsPerScreen && this.startIndex == newStartIndex) return;

        this.barsPerScreen = newBarsPerScreen;
        this.startIndex = newStartIndex;
        fireViewStateChanged();
    }

    public void jumpToLiveEdge() {
        this.viewingLiveEdge = true;
        int dataBarsOnScreen = (int) (barsPerScreen * (1.0 - rightMarginRatio));
        int liveEdgeStartIndex = Math.max(0, model.getTotalCandleCount() - dataBarsOnScreen);

        if (this.startIndex != liveEdgeStartIndex) {
            this.startIndex = liveEdgeStartIndex;
            fireViewStateChanged();
        }
    }

    public void increaseRightMargin() {
        this.rightMarginRatio = Math.min(0.9, this.rightMarginRatio + 0.05);
        if (viewingLiveEdge) {
            jumpToLiveEdge();
        } else {
            fireViewStateChanged(); // To redraw with new margin
        }
    }

    public void decreaseRightMargin() {
        this.rightMarginRatio = Math.max(0.05, this.rightMarginRatio - 0.05);
        if (viewingLiveEdge) {
            jumpToLiveEdge();
        } else {
            fireViewStateChanged();
        }
    }

    public void setStartIndex(int newStartIndex) {
        if (model == null) return;

        this.viewingLiveEdge = false;
        int validatedIndex = Math.max(0, newStartIndex);
        int maxStartIndex = model.getTotalCandleCount() - (int)(barsPerScreen * (1.0 - rightMarginRatio));
        validatedIndex = Math.min(validatedIndex, maxStartIndex);

        if (this.startIndex != validatedIndex) {
            this.startIndex = validatedIndex;
            fireViewStateChanged();
        }
    }

    public void setAutoScalingY(boolean autoScaling) {
        if (this.isAutoScalingY == autoScaling) return;

        this.isAutoScalingY = autoScaling;
        if (!isAutoScalingY) {
            // Capture current auto-scaled prices for manual mode
            this.manualMinPrice = model.getMinPrice();
            this.manualMaxPrice = model.getMaxPrice();
        }
        pcs.firePropertyChange("axisConfigChanged", !autoScaling, autoScaling);
    }

    public void setInvertedY(boolean inverted) {
        if (this.isInvertedY == inverted) return;
        this.isInvertedY = inverted;
        pcs.firePropertyChange("axisConfigChanged", !inverted, inverted);
    }

    public void toggleInvertY() {
        setInvertedY(!this.isInvertedY);
    }

    public void setManualPriceRange(BigDecimal min, BigDecimal max) {
        if (min == null || max == null || min.compareTo(max) >= 0) return;
        
        // Ensure we are in manual mode when this is called
        if (isAutoScalingY) {
            isAutoScalingY = false;
        }
        
        this.manualMinPrice = min;
        this.manualMaxPrice = max;
        pcs.firePropertyChange("axisConfigChanged", null, null);
    }

    public void scalePriceAxis(double scaleFactor, BigDecimal anchorPrice) {
        if (anchorPrice == null) return;
    
        setAutoScalingY(false); // This action implies manual mode
    
        BigDecimal min = getManualMinPrice();
        BigDecimal max = getManualMaxPrice();
        if (min == null || max == null) {
            min = model.getMinPrice(); // Fallback to current auto-scale
            max = model.getMaxPrice();
        }
    
        BigDecimal priceRange = max.subtract(min);
        if (priceRange.signum() <= 0) return;
    
        // New range is based on the scale factor
        BigDecimal newPriceRange = priceRange.divide(BigDecimal.valueOf(scaleFactor), 8, RoundingMode.HALF_UP);
    
        // Calculate the ratio of the anchor price within the old range
        BigDecimal ratio = anchorPrice.subtract(min).divide(priceRange, 8, RoundingMode.HALF_UP);
        
        // Calculate new min/max to keep the anchor price at the same ratio
        BigDecimal newMin = anchorPrice.subtract(newPriceRange.multiply(ratio));
        BigDecimal newMax = newMin.add(newPriceRange);
    
        setManualPriceRange(newMin, newMax);
    }

    private void fireViewStateChanged() {
        pcs.firePropertyChange("viewStateChanged", null, null);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
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

    // --- Getters ---
    public int getStartIndex() { return startIndex; }
    public int getBarsPerScreen() { return barsPerScreen; }
    public boolean isViewingLiveEdge() { return viewingLiveEdge; }
    public double getRightMarginRatio() { return rightMarginRatio; }
    public boolean isAutoScalingY() { return isAutoScalingY; }
    public boolean isInvertedY() { return isInvertedY; }
    public BigDecimal getManualMinPrice() { return manualMinPrice; }
    public BigDecimal getManualMaxPrice() { return manualMaxPrice; }

    // --- ReplayStateListener Implementation ---

    @Override
    public void onReplayTick(KLine newBar) {
        // This is now called for both Replay and Live ticks.
        // In Live mode, if we are viewing the latest candles, we want to auto-scroll.
        boolean shouldJump = (model.getCurrentMode() == ChartMode.REPLAY && viewingLiveEdge) ||
                             (model.getCurrentMode() == ChartMode.LIVE && viewingLiveEdge);
        if (shouldJump) {
            jumpToLiveEdge();
        }
    }

    @Override
    public void onReplaySessionStart() {
        this.viewingLiveEdge = true;
        this.startIndex = 0; // Reset on new session
        jumpToLiveEdge();
    }

    @Override
    public void onReplayStateChanged() {
        if (ReplaySessionManager.getInstance().isPlaying()) {
            jumpToLiveEdge();
        }
    }
}