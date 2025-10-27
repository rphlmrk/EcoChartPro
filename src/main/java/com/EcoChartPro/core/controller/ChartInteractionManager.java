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

    // Y-Axis state fields
    private boolean isAutoScalingY = true;
    private boolean isInvertedY = false;
    private BigDecimal manualMinPrice;
    private BigDecimal manualMaxPrice;

    public ChartInteractionManager(ChartDataModel model) {
        this.model = model;
    }

    public void pan(int barDelta) {
        if (model == null) return;

        if (model.getCurrentMode() == ChartMode.LIVE && barDelta < 0) {
            if (startIndex + barDelta + barsPerScreen > model.getTotalCandleCount() + (int)(barsPerScreen * rightMarginRatio)) {
                return;
            }
        }

        viewingLiveEdge = false;
        int newStartIndex = Math.max(0, this.startIndex + barDelta);
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
        if (totalSize <= 0) return;

        int barsBeforeCursor = (int) (this.barsPerScreen * cursorXRatio);
        int cursorIndex = this.startIndex + barsBeforeCursor;

        int newBarsPerScreen = Math.max(20, Math.min((int)(this.barsPerScreen / zoomFactor), 1000));
        
        int newBarsBeforeCursor = (int) (newBarsPerScreen * cursorXRatio);
        int newStartIndex = Math.max(0, cursorIndex - newBarsBeforeCursor);
        
        newStartIndex = Math.min(newStartIndex, Math.max(0, totalSize - newBarsPerScreen));
        
        if (this.barsPerScreen == newBarsPerScreen && this.startIndex == newStartIndex) return;

        this.barsPerScreen = newBarsPerScreen;
        this.startIndex = newStartIndex;
        fireViewStateChanged();
    }

    public void jumpToLiveEdge() {
        boolean needsUpdate = !this.viewingLiveEdge;
        this.viewingLiveEdge = true;

        // [FIX] Use getTotalCandleCount() which is now correctly updated for all chart types.
        int dataBarsOnScreen = (int) (barsPerScreen * (1.0 - rightMarginRatio));
        int liveEdgeStartIndex = Math.max(0, model.getTotalCandleCount() - dataBarsOnScreen);

        if (this.startIndex != liveEdgeStartIndex) {
            this.startIndex = liveEdgeStartIndex;
            needsUpdate = true;
        }

        if (needsUpdate) {
            fireViewStateChanged();
        }
    }

    public void increaseRightMargin() {
        this.rightMarginRatio = Math.min(0.9, this.rightMarginRatio + 0.05);
        if (viewingLiveEdge) {
            jumpToLiveEdge();
        } else {
            fireViewStateChanged();
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
        int maxStartIndex = Math.max(0, model.getTotalCandleCount() - (int)(barsPerScreen * (1.0 - rightMarginRatio)));
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

    public void toggleInvertY() { setInvertedY(!this.isInvertedY); }

    public void setManualPriceRange(BigDecimal min, BigDecimal max) {
        if (min == null || max == null || min.compareTo(max) >= 0) return;
        if (isAutoScalingY) isAutoScalingY = false;
        this.manualMinPrice = min;
        this.manualMaxPrice = max;
        pcs.firePropertyChange("axisConfigChanged", null, null);
    }

    public void scalePriceAxis(double scaleFactor, BigDecimal anchorPrice) {
        if (anchorPrice == null) return;
        setAutoScalingY(false);
        BigDecimal min = getManualMinPrice() != null ? getManualMinPrice() : model.getMinPrice();
        BigDecimal max = getManualMaxPrice() != null ? getManualMaxPrice() : model.getMaxPrice();
        if (min == null || max == null) return;
        BigDecimal priceRange = max.subtract(min);
        if (priceRange.signum() <= 0) return;
        BigDecimal newPriceRange = priceRange.divide(BigDecimal.valueOf(scaleFactor), 8, RoundingMode.HALF_UP);
        BigDecimal ratio = anchorPrice.subtract(min).divide(priceRange, 8, RoundingMode.HALF_UP);
        BigDecimal newMin = anchorPrice.subtract(newPriceRange.multiply(ratio));
        BigDecimal newMax = newMin.add(newPriceRange);
        setManualPriceRange(newMin, newMax);
    }

    private void fireViewStateChanged() { pcs.firePropertyChange("viewStateChanged", null, null); }
    public void addPropertyChangeListener(PropertyChangeListener listener) { pcs.addPropertyChangeListener(listener); }
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) { pcs.addPropertyChangeListener(propertyName, listener); }
    public void removePropertyChangeListener(PropertyChangeListener listener) { pcs.removePropertyChangeListener(listener); }
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) { pcs.removePropertyChangeListener(propertyName, listener); }

    public int getStartIndex() { return startIndex; }
    public int getBarsPerScreen() { return barsPerScreen; }
    public boolean isViewingLiveEdge() { return viewingLiveEdge; }
    public double getRightMarginRatio() { return rightMarginRatio; }
    public boolean isAutoScalingY() { return isAutoScalingY; }
    public boolean isInvertedY() { return isInvertedY; }
    public BigDecimal getManualMinPrice() { return manualMinPrice; }
    public BigDecimal getManualMaxPrice() { return manualMaxPrice; }

    @Override
    public void onReplayTick(KLine newBar) {
        boolean shouldJump = (model.getCurrentMode() == ChartMode.REPLAY && viewingLiveEdge) || (model.getCurrentMode() == ChartMode.LIVE && viewingLiveEdge);
        if (shouldJump) {
            jumpToLiveEdge();
        }
    }

    @Override
    public void onReplaySessionStart() {
        this.viewingLiveEdge = true;
        this.startIndex = 0;
        jumpToLiveEdge();
    }

    @Override
    public void onReplayStateChanged() {
        // This is handled by the ChartDataModel now to trigger a full rebuild
    }
}