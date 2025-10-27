package com.EcoChartPro.ui.chart;

import com.EcoChartPro.api.indicator.drawing.DataPoint;
import com.EcoChartPro.api.indicator.drawing.DrawableBox;
import com.EcoChartPro.api.indicator.drawing.DrawableLine;
import com.EcoChartPro.api.indicator.drawing.DrawableObject;
import com.EcoChartPro.api.indicator.drawing.DrawablePolygon;
import com.EcoChartPro.api.indicator.drawing.DrawablePolyline;
import com.EcoChartPro.core.indicator.Indicator;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.ui.chart.axis.IChartAxis;
import com.EcoChartPro.ui.chart.axis.IndexBasedAxis;
import com.EcoChartPro.ui.chart.axis.TimeBasedAxis;
import com.EcoChartPro.ui.chart.render.AxisRenderer;
import com.EcoChartPro.ui.chart.render.IndicatorDrawableRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.util.List;

/**
 * A dedicated drawing surface for a single pane-based indicator.
 * It manages its own Y-axis but synchronizes its X-axis with a main chart.
 */
public class IndicatorPanel extends JPanel implements PropertyChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(IndicatorPanel.class);

    private final ChartDataModel mainDataModel;
    private final IChartAxis mainXAxis;
    private final Indicator indicator;

    private final IChartAxis localYAxis;
    private final IndicatorDrawableRenderer indicatorDrawableRenderer;
    private final AxisRenderer axisRenderer;

    public IndicatorPanel(ChartDataModel mainDataModel, IChartAxis mainXAxis, Indicator indicator) {
        this.mainDataModel = mainDataModel;
        this.mainXAxis = mainXAxis;
        this.indicator = indicator;
        this.localYAxis = new TimeBasedAxis(); // Y-axis is always price/value based
        this.indicatorDrawableRenderer = new IndicatorDrawableRenderer();
        this.axisRenderer = new AxisRenderer();

        setOpaque(true);
        // Set background and border from theme-aware sources.
        setBackground(SettingsManager.getInstance().getChartBackground());
        setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));

        // Listen for data updates and theme changes to trigger repaints.
        this.mainDataModel.addPropertyChangeListener(this);
        SettingsManager.getInstance().addPropertyChangeListener(this);
    }
    
    /**
     * New cleanup method to remove listeners.
     */
    public void cleanup() {
        this.mainDataModel.removePropertyChangeListener(this);
        SettingsManager.getInstance().removePropertyChangeListener(this);
    }


    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // Added listener for theme/color changes.
        if ("dataUpdated".equals(evt.getPropertyName()) || "chartColorsChanged".equals(evt.getPropertyName())) {
            if ("chartColorsChanged".equals(evt.getPropertyName())) {
                setBackground(SettingsManager.getInstance().getChartBackground());
                setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));
            }
            repaint();
        }
    }

    public IChartAxis getLocalYAxis() {
        return localYAxis;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();

        if (!mainXAxis.isConfigured()) return;

        List<? extends AbstractChartData> visibleData = mainDataModel.getVisibleKLines();
        if (visibleData.isEmpty()) return;

        List<DrawableObject> drawables = indicator.getResults();
        if (drawables.isEmpty()) return;

        // --- Calculate Y-axis range from all drawables ---
        BigDecimal min = null;
        BigDecimal max = null;

        for (DrawableObject obj : drawables) {
             if (obj instanceof DrawablePolyline polyline) {
                for (DataPoint point : polyline.getPoints()) {
                    if (min == null || point.price().compareTo(min) < 0) min = point.price();
                    if (max == null || point.price().compareTo(max) > 0) max = point.price();
                }
            } else if (obj instanceof DrawablePolygon polygon) {
                for (DataPoint point : polygon.vertices()) {
                    if (min == null || point.price().compareTo(min) < 0) min = point.price();
                    if (max == null || point.price().compareTo(max) > 0) max = point.price();
                }
            } else if (obj instanceof DrawableBox box) {
                BigDecimal p1 = box.corner1().price();
                BigDecimal p2 = box.corner2().price();
                if (min == null || p1.compareTo(min) < 0) min = p1;
                if (min == null || p2.compareTo(min) < 0) min = p2;
                if (max == null || p1.compareTo(max) > 0) max = p1;
                if (max == null || p2.compareTo(max) > 0) max = p2;
            } else if (obj instanceof DrawableLine line) {
                BigDecimal p1 = line.start().price();
                BigDecimal p2 = line.end().price();
                if (min == null || p1.compareTo(min) < 0) min = p1;
                if (min == null || p2.compareTo(min) < 0) min = p2;
                if (max == null || p1.compareTo(max) > 0) max = p1;
                if (max == null || p2.compareTo(max) > 0) max = p2;
            } else if (obj instanceof DataPoint point) {
                if (min == null || point.price().compareTo(min) < 0) min = point.price();
                if (max == null || point.price().compareTo(max) > 0) max = point.price();
            }
        }
        
        if (min == null || max == null) {
            return;
        }
        
        if (min.compareTo(max) == 0) {
            max = min.add(BigDecimal.ONE);
            min = min.subtract(BigDecimal.ONE);
        }

        localYAxis.configure(min, max, 1, this.getSize(), false);

        IChartAxis renderAxis = (mainXAxis instanceof TimeBasedAxis) ? new TimeBasedAxis() : new IndexBasedAxis();
        renderAxis.configureForRendering(mainXAxis, localYAxis);

        Timeframe timeframe = mainDataModel.getCurrentDisplayTimeframe();

        axisRenderer.draw(g2d, renderAxis, visibleData, timeframe);

        indicatorDrawableRenderer.draw(g2d, drawables, renderAxis, visibleData, timeframe);

        g2d.dispose();
    }
}