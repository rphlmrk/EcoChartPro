package com.EcoChartPro.model.drawing;

import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.PriceRange;
import com.EcoChartPro.core.manager.TimeRange;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.chart.AbstractChartData;
import com.EcoChartPro.ui.chart.axis.IChartAxis;
import com.EcoChartPro.ui.dialogs.DrawingToolSettingsDialog;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Trendline.class, name = "trendline"),
    @JsonSubTypes.Type(value = RectangleObject.class, name = "rectangle"),
    @JsonSubTypes.Type(value = HorizontalLineObject.class, name = "horizontal_line"),
    @JsonSubTypes.Type(value = VerticalLineObject.class, name = "vertical_line"),
    @JsonSubTypes.Type(value = RayObject.class, name = "ray"),
    @JsonSubTypes.Type(value = HorizontalRayObject.class, name = "horizontal_ray"),
    @JsonSubTypes.Type(value = MeasureToolObject.class, name = "measure_tool"),
    @JsonSubTypes.Type(value = TextObject.class, name = "text"),
    @JsonSubTypes.Type(value = ProtectedLevelPatternObject.class, name = "protected_level_pattern"),
    @JsonSubTypes.Type(value = FibonacciRetracementObject.class, name = "fib_retracement"),
    @JsonSubTypes.Type(value = FibonacciExtensionObject.class, name = "fib_extension")
})
public interface DrawingObject {
    UUID id();
    Map<Timeframe, Boolean> visibility();
    DrawingObject withVisibility(Map<Timeframe, Boolean> newVisibility);
    void render(Graphics2D g, IChartAxis axis, List<? extends AbstractChartData> data, Timeframe tf); // MODIFIED
    boolean isHit(Point screenPoint, IChartAxis axis, List<? extends AbstractChartData> data, Timeframe tf); // MODIFIED
    boolean isVisible(TimeRange timeRange, PriceRange priceRange);
    List<DrawingHandle> getHandles(IChartAxis axis, List<? extends AbstractChartData> data, Timeframe tf); // MODIFIED
    DrawingObject withPoint(DrawingHandle.HandleType handleType, DrawingObjectPoint newPoint);
    DrawingObject move(long timeDelta, java.math.BigDecimal priceDelta);
    Color color();
    BasicStroke stroke();
    DrawingObject withColor(Color newColor);
    DrawingObject withStroke(BasicStroke newStroke);
    boolean isLocked();
    DrawingObject withLocked(boolean locked);
    boolean showPriceLabel();
    DrawingObject withShowPriceLabel(boolean show);

    default void showSettingsDialog(Frame owner, DrawingManager dm) {
        new DrawingToolSettingsDialog(owner, this, dm).setVisible(true);
    }
}