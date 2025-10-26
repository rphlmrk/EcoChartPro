package com.EcoChartPro.ui.chart;

import com.EcoChartPro.api.indicator.IndicatorType;
import com.EcoChartPro.api.indicator.drawing.DrawableObject;
import com.EcoChartPro.core.controller.ChartInteractionManager;
import com.EcoChartPro.core.controller.DrawingController;
import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.controller.ReplayStateListener;
import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.indicator.Indicator;
import com.EcoChartPro.core.manager.CrosshairManager;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.manager.PriceRange;
import com.EcoChartPro.core.manager.TimeRange;
import com.EcoChartPro.core.manager.listener.DrawingListener;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.tool.DrawingTool;
import com.EcoChartPro.core.tool.InfoTool;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.data.DataTransformer;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.chart.ChartType;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.model.trading.Order;
import com.EcoChartPro.model.trading.Position;
import com.EcoChartPro.ui.MainWindow;
import com.EcoChartPro.ui.chart.axis.ChartAxis;
import com.EcoChartPro.ui.chart.render.AxisRenderer;
import com.EcoChartPro.ui.chart.render.ChartRenderer;
import com.EcoChartPro.ui.chart.render.DaySeparatorRenderer;
import com.EcoChartPro.ui.chart.render.IndicatorDrawableRenderer;
import com.EcoChartPro.ui.chart.render.PeakHoursRenderer;
import com.EcoChartPro.ui.chart.render.VisibleRangeVolumeProfileRenderer;
import com.EcoChartPro.ui.chart.render.drawing.DrawingRenderer;
import com.EcoChartPro.ui.chart.render.trading.OrderRenderer;
import com.EcoChartPro.ui.chart.render.trading.TradeSignalRenderer;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.dialogs.TimeframeInputDialog;
import com.EcoChartPro.ui.toolbar.FloatingPropertiesToolbar;
import com.EcoChartPro.ui.trading.OrderPreview;
import com.EcoChartPro.utils.DataSourceManager;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ChartPanel extends JPanel implements PropertyChangeListener, DrawingListener, ReplayStateListener {

    private final ChartRenderer chartRenderer;
    private final AxisRenderer axisRenderer;
    private final DrawingRenderer drawingRenderer;
    private final OrderRenderer orderRenderer;
    private final TradeSignalRenderer tradeSignalRenderer;
    private final IndicatorDrawableRenderer indicatorDrawableRenderer;
    private final DaySeparatorRenderer daySeparatorRenderer;
    private final PeakHoursRenderer peakHoursRenderer;
    private final VisibleRangeVolumeProfileRenderer volumeProfileRenderer;
    private final DrawingController drawingController;
    private final ChartInteractionManager interactionManager;
    private final ChartAxis chartAxis;
    private final ChartDataModel dataModel;
    private final PriceAxisPanel priceAxisPanel;
    private final TimeAxisPanel timeAxisPanel;
    private final FloatingPropertiesToolbar propertiesToolbar;
    private final InfoPanel infoPanel;
    private static final Font SYMBOL_FONT = new Font("SansSerif", Font.BOLD, 16);
    private static final Border INACTIVE_BORDER = BorderFactory.createEmptyBorder(2, 2, 2, 2);
    private OrderRenderer.InteractiveZone dragPreview = null;
    private OrderPreview orderPreview;
    private boolean isPriceSelectionMode = false;
    private Consumer<BigDecimal> priceSelectionCallback;
    private final JButton jumpToLiveEdgeButton;
    private final JButton increaseMarginButton;
    private final JButton decreaseMarginButton;
    private boolean isReplayPlaying = false;

    private DrawingObjectPoint crosshairPoint;
    private DrawingObjectPoint previousCrosshairPoint; 

    private boolean isLoading = false;
    private String loadingMessage = "";

    private boolean showDrawings = true;
    private boolean showIndicators = true;
    private boolean showPositionsAndOrders = true;

    public ChartPanel(ChartDataModel dataModel, ChartInteractionManager interactionManager, ChartAxis chartAxis, PriceAxisPanel priceAxisPanel, TimeAxisPanel timeAxisPanel, Consumer<DrawingTool> onToolStateChange, FloatingPropertiesToolbar propertiesToolbar) {
        this.dataModel = dataModel;
        this.interactionManager = interactionManager;
        this.chartAxis = chartAxis;
        this.priceAxisPanel = priceAxisPanel;
        this.timeAxisPanel = timeAxisPanel;
        this.propertiesToolbar = propertiesToolbar;
        this.chartRenderer = new ChartRenderer();
        this.axisRenderer = new AxisRenderer();
        this.drawingRenderer = new DrawingRenderer();
        this.orderRenderer = new OrderRenderer();
        this.tradeSignalRenderer = new TradeSignalRenderer();
        this.indicatorDrawableRenderer = new IndicatorDrawableRenderer();
        this.daySeparatorRenderer = new DaySeparatorRenderer();
        this.peakHoursRenderer = new PeakHoursRenderer();
        this.volumeProfileRenderer = new VisibleRangeVolumeProfileRenderer();
        this.drawingController = new DrawingController(this, onToolStateChange);
        this.infoPanel = new InfoPanel();
        
        SettingsManager settings = SettingsManager.getInstance();
        setBackground(settings.getChartBackground());
        setBorder(INACTIVE_BORDER);
        setLayout(null);
        setFocusable(true);
        
        this.jumpToLiveEdgeButton = createOverlayButton(UITheme.Icons.FAST_FORWARD, "Jump to Live Edge");
        this.jumpToLiveEdgeButton.addActionListener(e -> interactionManager.jumpToLiveEdge());
        this.increaseMarginButton = createOverlayButton(UITheme.Icons.CHEVRON_DOUBLE_RIGHT, "Increase Right Margin");
        this.increaseMarginButton.addActionListener(e -> interactionManager.increaseRightMargin());
        this.decreaseMarginButton = createOverlayButton(UITheme.Icons.CHEVRON_DOUBLE_LEFT, "Decrease Right Margin");
        this.decreaseMarginButton.addActionListener(e -> interactionManager.decreaseRightMargin());
        add(jumpToLiveEdgeButton);
        add(increaseMarginButton);
        add(decreaseMarginButton);

        this.dataModel.addPropertyChangeListener(this);
        this.interactionManager.addPropertyChangeListener(this);
        this.dataModel.setView(this);
        DrawingManager.getInstance().addListener(this);
        DrawingManager.getInstance().addPropertyChangeListener("selectedDrawingChanged", this);
        settings.addPropertyChangeListener(this);
        CrosshairManager.getInstance().addPropertyChangeListener("crosshairMoved", this);
        if (dataModel.isInReplayMode()) {
            ReplaySessionManager.getInstance().addListener(this);
        }
        PaperTradingService.getInstance().addPropertyChangeListener(this);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                positionOverlayButtons();
            }
        });
    }
    
    public DrawingObjectPoint getSnappingPoint(MouseEvent e) {
        if (!getChartAxis().isConfigured()) return null;

        DrawingObjectPoint rawPoint = getChartAxis().screenToDataPoint(e.getX(), e.getY(), getDataModel().getVisibleKLines(), getDataModel().getCurrentDisplayTimeframe());
        if (rawPoint == null || !e.isControlDown()) {
            return rawPoint;
        }

        // Snap to OHLC of the nearest candle
        KLine targetKline = null;
        for (KLine kline : getDataModel().getVisibleKLines()) {
            if (kline.timestamp().equals(rawPoint.timestamp())) {
                targetKline = kline;
                break;
            }
        }
        if (targetKline == null) {
            return rawPoint;
        }

        Map<BigDecimal, Integer> priceLevelsY = new HashMap<>();
        priceLevelsY.put(targetKline.open(), getChartAxis().priceToY(targetKline.open()));
        priceLevelsY.put(targetKline.high(), getChartAxis().priceToY(targetKline.high()));
        priceLevelsY.put(targetKline.low(), getChartAxis().priceToY(targetKline.low()));
        priceLevelsY.put(targetKline.close(), getChartAxis().priceToY(targetKline.close()));

        int mouseY = e.getY();
        double minDistance = Double.MAX_VALUE;
        BigDecimal bestPrice = null;

        for (Map.Entry<BigDecimal, Integer> entry : priceLevelsY.entrySet()) {
            double distance = Math.abs(mouseY - entry.getValue());
            if (distance < minDistance) {
                minDistance = distance;
                bestPrice = entry.getKey();
            }
        }
        
        if (bestPrice != null && minDistance < SettingsManager.getInstance().getSnapRadius()) {
            return new DrawingObjectPoint(rawPoint.timestamp(), bestPrice);
        } else {
            return rawPoint;
        }
    }


    public void setLoading(boolean loading, String message) {
        this.isLoading = loading;
        this.loadingMessage = message != null ? message : "";
        repaint();
    }

    private JButton createOverlayButton(String iconPath, String toolTip) {
        Color normalColor = UIManager.getColor("Button.disabledText");
        Color hoverColor = UIManager.getColor("Button.foreground");

        Icon normalIcon = UITheme.getIcon(iconPath, 16, 16, normalColor);
        Icon hoverIcon = UITheme.getIcon(iconPath, 16, 16, hoverColor);

        JButton button = new JButton(normalIcon);
        button.setToolTipText(toolTip);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setIcon(hoverIcon);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                button.setIcon(normalIcon);
            }
        });
        return button;
    }
    
    private void positionOverlayButtons() {
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        int buttonWidth = 28;
        int buttonHeight = 28;
        int margin = 5;

        int bottomY = panelHeight - buttonHeight - margin;
        int jumpButtonX = (panelWidth - buttonWidth) / 2;
        jumpToLiveEdgeButton.setBounds(jumpButtonX, bottomY, buttonWidth, buttonHeight);

        int topY = 5;
        increaseMarginButton.setBounds(panelWidth - buttonWidth - margin, topY, buttonWidth, buttonHeight);
        decreaseMarginButton.setBounds(panelWidth - (buttonWidth * 2) - (margin * 2), topY, buttonWidth, buttonHeight);
    }
    private void updateOverlayButtonsVisibility() {
        boolean isAtLiveEdge = interactionManager.isViewingLiveEdge();
        jumpToLiveEdgeButton.setVisible(!isAtLiveEdge);
    
        boolean showMarginControls;
        if (dataModel.isInReplayMode()) {
            showMarginControls = isAtLiveEdge && isReplayPlaying;
        } else { // Live mode
            showMarginControls = isAtLiveEdge;
        }
        
        increaseMarginButton.setVisible(showMarginControls);
        decreaseMarginButton.setVisible(showMarginControls);
    }
    public void setOrderPreview(OrderPreview preview) {
        this.orderPreview = preview;
        repaint();
    }
    public void enterPriceSelectionMode(Consumer<BigDecimal> callback) {
        this.isPriceSelectionMode = true;
        this.priceSelectionCallback = callback;
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        if (priceAxisPanel != null) {
            priceAxisPanel.enterPriceSelectionMode(this);
        }
    }

    public void exitPriceSelectionMode() {
        this.isPriceSelectionMode = false;
        this.priceSelectionCallback = null;
        setCursor(Cursor.getDefaultCursor());
        if (priceAxisPanel != null) {
            priceAxisPanel.exitPriceSelectionMode();
        }
    }

    public boolean isPriceSelectionMode() {
        return isPriceSelectionMode;
    }

    public Consumer<BigDecimal> getPriceSelectionCallback() {
        return priceSelectionCallback;
    }

    public void cleanup() {
        DrawingManager.getInstance().removeListener(this);
        DrawingManager.getInstance().removePropertyChangeListener("selectedDrawingChanged", this);
        CrosshairManager.getInstance().removePropertyChangeListener("crosshairMoved", this);
        dataModel.removePropertyChangeListener(this);
        interactionManager.removePropertyChangeListener(this);
        SettingsManager.getInstance().removePropertyChangeListener(this);
        if (timeAxisPanel != null) {
            timeAxisPanel.cleanup();
        }
        if (dataModel.isInReplayMode()) {
            ReplaySessionManager.getInstance().removeListener(this);
        }
        PaperTradingService.getInstance().removePropertyChangeListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propName = evt.getPropertyName();

        if ("chartColorsChanged".equals(propName) || "chartTypeChanged".equals(propName) || "volumeProfileVisibilityChanged".equals(propName) || "peakHoursLinesVisibilityChanged".equals(propName) || "peakHoursOverrideChanged".equals(propName) || "peakHoursSettingsChanged".equals(propName)) {
            SettingsManager settings = SettingsManager.getInstance();
            setBackground(settings.getChartBackground());
            if (getBorder() != INACTIVE_BORDER) {
                setBorder(BorderFactory.createLineBorder(settings.getBullColor(), 2));
            }
            repaint();
        } else if ("dataUpdated".equals(propName) || "axisConfigChanged".equals(propName)) {
            updateOverlayButtonsVisibility();
            repaint();
        } else if ("daySeparatorsEnabledChanged".equals(propName) || "selectedDrawingChanged".equals(propName)) {
            repaint();
        } else if ("crosshairMoved".equals(propName) && evt.getNewValue() instanceof CrosshairManager.CrosshairUpdate) {
            CrosshairManager.CrosshairUpdate update = (CrosshairManager.CrosshairUpdate) evt.getNewValue();

            if (!CrosshairManager.getInstance().isSyncEnabled() && update.source() != this) {
                return;
            }

            if (previousCrosshairPoint != null) {
                repaintCrosshairRegion(previousCrosshairPoint);
            }

            this.crosshairPoint = update.point();
            this.previousCrosshairPoint = this.crosshairPoint;

            if (this.crosshairPoint != null) {
                repaintCrosshairRegion(this.crosshairPoint);
            }
        } else if ("pendingOrdersUpdated".equals(propName) || "openPositionsUpdated".equals(propName) || "tradeHistoryUpdated".equals(propName)) {
            repaint();
        }
    }

    private void repaintCrosshairRegion(DrawingObjectPoint point) {
        if (point == null || !chartAxis.isConfigured()) {
            return;
        }
        List<KLine> visibleKLines = dataModel.getVisibleKLines();
        int x = chartAxis.timeToX(point.timestamp(), visibleKLines, dataModel.getCurrentDisplayTimeframe());
        int y = chartAxis.priceToY(point.price());
        
        if (x >= 0) {
            repaint(x - 1, 0, 3, getHeight());
        }
        if (y >= 0) {
            repaint(0, y - 1, getWidth(), 3);
        }
    }

    @Override
    public void onDrawingAdded(DrawingObject drawingObject) {
        repaint();
    }
    @Override
    public void onDrawingUpdated(DrawingObject drawingObject) {
        repaint();
    }
    @Override
    public void onDrawingRemoved(UUID drawingObjectId) {
        repaint();
    }
    public void setActive(boolean isActive) {
        if (isActive) {
            setBorder(BorderFactory.createLineBorder(SettingsManager.getInstance().getBullColor(), 2));
        } else {
            setBorder(INACTIVE_BORDER);
        }
    }
    public ChartAxis getChartAxis() { return this.chartAxis; }
    public PriceAxisPanel getPriceAxisPanel() { return priceAxisPanel; }
    public TimeAxisPanel getTimeAxisPanel() { return timeAxisPanel; }
    public ChartDataModel getDataModel() { return this.dataModel; }
    public DrawingController getDrawingController() { return this.drawingController; }
    public OrderRenderer getOrderRenderer() { return this.orderRenderer; }
    public FloatingPropertiesToolbar getPropertiesToolbar() { return this.propertiesToolbar; }
    public void setDragPreview(OrderRenderer.InteractiveZone preview) {
        this.dragPreview = preview;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        SettingsManager settings = SettingsManager.getInstance();
        List<KLine> rawVisibleKLines = dataModel.getVisibleKLines();

        BigDecimal minP, maxP;
        if (interactionManager.isAutoScalingY()) {
            minP = dataModel.getMinPrice();
            maxP = dataModel.getMaxPrice();
        } else {
            minP = interactionManager.getManualMinPrice();
            maxP = interactionManager.getManualMaxPrice();
            if (minP == null || maxP == null) {
                minP = dataModel.getMinPrice();
                maxP = dataModel.getMaxPrice();
            }
        }

        chartAxis.configure(
            minP,
            maxP,
            interactionManager.getBarsPerScreen(),
            getSize(),
            interactionManager.isInvertedY()
        );

        DataSourceManager.ChartDataSource currentSource = dataModel.getCurrentSymbol();
        if (currentSource == null && dataModel.isInReplayMode()) {
             currentSource = ReplaySessionManager.getInstance().getCurrentSource();
        }
        Timeframe currentTimeframe = dataModel.getCurrentDisplayTimeframe();
        
        if (currentSource != null) {
            g2d.setFont(SYMBOL_FONT);
            g2d.setColor(settings.getAxisTextColor());
            // Use record accessor displayName()
            String text = currentSource.displayName() + " - " + (currentTimeframe != null ? currentTimeframe.displayName() : "");
            g2d.drawString(text, 20, 30);
        }

        if (rawVisibleKLines.isEmpty() && !isLoading) {
             g2d.setColor(UIManager.getColor("Label.disabledForeground"));
             g2d.drawString("No data available for the current view.", 20, 60);
             g2d.dispose();
             return;
        }

        // --- Data Transformation Step ---
        ChartType chartType = settings.getCurrentChartType();
        List<KLine> klinesToRender = rawVisibleKLines;
        if (chartType == ChartType.HEIKIN_ASHI) {
            klinesToRender = DataTransformer.transformToHeikinAshi(rawVisibleKLines);
        }

        axisRenderer.draw(g2d, chartAxis, klinesToRender, currentTimeframe);
        chartRenderer.draw(g2d, chartType, chartAxis, klinesToRender, interactionManager.getStartIndex());


        if (settings.isDaySeparatorsEnabled()) {
            daySeparatorRenderer.draw(g2d, chartAxis, klinesToRender, currentTimeframe);
        }

        if (settings.isShowPeakHoursLines() && dataModel.isInReplayMode()) {
            List<Integer> peakHours = settings.getPeakPerformanceHoursOverride();
            if (peakHours.isEmpty()) {
                peakHours = GamificationService.getInstance().getPeakPerformanceHours();
            }
            peakHoursRenderer.draw(g2d, chartAxis, klinesToRender, currentTimeframe, peakHours);
        }


        if (!klinesToRender.isEmpty()) {
            Instant startTime = klinesToRender.get(0).timestamp();
            Instant endTime = klinesToRender.get(klinesToRender.size() - 1).timestamp();
            TimeRange timeRange = new TimeRange(startTime, endTime);
            PriceRange priceRange = new PriceRange(dataModel.getMinPrice(), dataModel.getMaxPrice());

            if (settings.isVolumeProfileVisible()) {
                volumeProfileRenderer.draw(g2d, chartAxis, klinesToRender);
            }

            if (showIndicators) {
                List<DrawableObject> allIndicatorDrawables = new ArrayList<>();
                dataModel.getIndicatorManager().getIndicators().stream()
                    .filter(i -> i.getType() == IndicatorType.OVERLAY)
                    .forEach(indicator -> allIndicatorDrawables.addAll(indicator.getResults()));
                indicatorDrawableRenderer.draw(g2d, allIndicatorDrawables, chartAxis, klinesToRender, currentTimeframe);
            }

            if (showDrawings) {
                List<DrawingObject> visibleDrawings = DrawingManager.getInstance().getVisibleDrawings(timeRange, priceRange);
                drawingRenderer.draw(g2d, visibleDrawings, chartAxis, klinesToRender, currentTimeframe);

                DrawingTool activeTool = drawingController.getActiveTool();
                if (activeTool != null && activeTool.getPreviewObject() != null) {
                    drawingRenderer.draw(g2d, List.of(activeTool.getPreviewObject()), chartAxis, klinesToRender, currentTimeframe);
                }
            }

            if (showPositionsAndOrders) {
                PaperTradingService service = PaperTradingService.getInstance();
                List<Trade> allTrades = service.getTradeHistory();
                List<Trade> visibleTrades = filterVisibleTrades(allTrades, timeRange);
                tradeSignalRenderer.draw(g2d, chartAxis, visibleTrades, klinesToRender, currentTimeframe);
                List<Position> positions = service.getOpenPositions();
                List<Order> orders = service.getPendingOrders();
                orderRenderer.draw(g2d, chartAxis, positions, orders, dragPreview);
            }
        }

        if (orderPreview != null) {
            drawOrderPreview(g2d, orderPreview);
        }
        
        drawCrosshair(g2d, klinesToRender);

        drawInfoPanel(g2d, rawVisibleKLines); // Info panel should always show raw data

        if (interactionManager.isViewingLiveEdge()) {
            KLine lastKline = dataModel.getCurrentReplayKLine();
            if (lastKline != null) {
                BigDecimal lastClose = lastKline.close();
                int y = chartAxis.priceToY(lastClose);
                boolean isBullish = lastKline.close().compareTo(lastKline.open()) >= 0;
                Color backgroundColor = isBullish ? settings.getBullColor() : settings.getBearColor();
                
                int lastGlobalIndex;
                if (dataModel.getCurrentMode() == ChartDataModel.ChartMode.LIVE) {
                    // For live, the forming candle is at the next logical index
                    lastGlobalIndex = dataModel.getTotalCandleCount();
                } else {
                    // For replay, it's the last available index
                    lastGlobalIndex = dataModel.getTotalCandleCount() - 1;
                }
                
                int slot = lastGlobalIndex - interactionManager.getStartIndex();
                int startX = chartAxis.slotToX(slot);
                Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0);
                g2d.setStroke(dashed);
                g2d.setColor(backgroundColor);
                g2d.drawLine(startX, y, getWidth(), y);
            }
        }
        
        if (isLoading) {
            drawLoadingOverlay(g2d);
        }

        g2d.dispose();

        priceAxisPanel.repaint();
        timeAxisPanel.repaint();
    }
    
    private void drawInfoPanel(Graphics2D g, List<KLine> visibleKLines) {
        DrawingTool activeTool = drawingController.getActiveTool();
        if (!(activeTool instanceof InfoTool infoTool)) {
            return;
        }

        DrawingObjectPoint dataPoint = infoTool.getCurrentPoint();
        if (dataPoint == null || dataPoint.timestamp() == null) {
            infoPanel.updateData(null, null, null);
            return;
        }

        KLine targetKline = null;
        int slotIndex = chartAxis.timeToSlotIndex(dataPoint.timestamp(), visibleKLines, dataModel.getCurrentDisplayTimeframe());
        if (slotIndex >= 0 && slotIndex < visibleKLines.size()) {
            targetKline = visibleKLines.get(slotIndex);
        }

        infoPanel.updateData(targetKline, dataModel.getIndicatorManager().getIndicators(), SettingsManager.getInstance().getDisplayZoneId());

        Point screenPoint = infoTool.getScreenPoint();
        if (screenPoint == null) {
            return;
        }

        int panelWidth = infoPanel.getWidth();
        int panelHeight = infoPanel.getHeight();
        int padding = 20;

        int x = screenPoint.x + padding;
        int y = screenPoint.y + padding;

        if (x + panelWidth > getWidth()) {
            x = screenPoint.x - panelWidth - padding;
        }
        if (y + panelHeight > getHeight()) {
            y = screenPoint.y - panelHeight - padding;
        }
        
        x = Math.max(5, x);
        y = Math.max(5, y);

        SwingUtilities.paintComponent(g, infoPanel, this, x, y, panelWidth, panelHeight);
    }
    
    private void drawCrosshair(Graphics2D g2d, List<KLine> visibleKLines) {
        if (crosshairPoint == null || !chartAxis.isConfigured()) return;
        
        int x = chartAxis.timeToX(crosshairPoint.timestamp(), visibleKLines, dataModel.getCurrentDisplayTimeframe());
        int y = chartAxis.priceToY(crosshairPoint.price());

        Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{2}, 0);
        g2d.setStroke(dashed);
        g2d.setColor(SettingsManager.getInstance().getCrosshairColor());

        g2d.drawLine(0, y, getWidth(), y);
        if (x >= 0) {
            g2d.drawLine(x, 0, x, getHeight());
        }
    }

    private void drawLoadingOverlay(Graphics2D g2d) {
        Color bgColor = UIManager.getColor("Panel.background");
        g2d.setColor(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 180));
        g2d.fillRect(0, 0, getWidth(), getHeight());

        g2d.setColor(UIManager.getColor("Label.foreground"));
        g2d.setFont(getFont().deriveFont(Font.BOLD, 16f));
        FontMetrics fm = g2d.getFontMetrics();
        int stringWidth = fm.stringWidth(loadingMessage);
        int x = (getWidth() - stringWidth) / 2;
        int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g2d.drawString(loadingMessage, x, y);
    }

    private void drawOrderPreview(Graphics2D g, OrderPreview preview) {
        if (!chartAxis.isConfigured()) return;
        final Stroke previewStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4}, 0);
        g.setStroke(previewStroke);

        if (preview.entryPrice() != null) {
            g.setColor(new Color(255, 193, 7, 180));
            int y = chartAxis.priceToY(preview.entryPrice());
            g.drawLine(0, y, getWidth(), y);
        }
        if (preview.stopLoss() != null) {
            g.setColor(new Color(244, 67, 54, 180));
            int y = chartAxis.priceToY(preview.stopLoss());
            g.drawLine(0, y, getWidth(), y);
        }
        if (preview.takeProfit() != null) {
            g.setColor(new Color(76, 175, 80, 180));
            int y = chartAxis.priceToY(preview.takeProfit());
            g.drawLine(0, y, getWidth(), y);
        }
    }

    private List<Trade> filterVisibleTrades(List<Trade> allTrades, TimeRange visibleTimeRange) {
        return allTrades.stream()
                .filter(trade -> {
                    boolean entryVisible = visibleTimeRange.contains(trade.entryTime());
                    boolean exitVisible = visibleTimeRange.contains(trade.exitTime());
                    boolean spansAcross = trade.entryTime().isBefore(visibleTimeRange.start()) && trade.exitTime().isAfter(visibleTimeRange.end());
                    return entryVisible || exitVisible || spansAcross;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void onReplayTick(KLine newM1Bar) { }

    @Override
    public void onReplaySessionStart() {
        this.isReplayPlaying = false;
        SwingUtilities.invokeLater(this::updateOverlayButtonsVisibility);
    }

    @Override
    public void onReplayStateChanged() {
        this.isReplayPlaying = ReplaySessionManager.getInstance().isPlaying();
        SwingUtilities.invokeLater(this::updateOverlayButtonsVisibility);
    }

    public boolean getShowDrawings() {
        return showDrawings;
    }

    public void setShowDrawings(boolean showDrawings) {
        if (this.showDrawings != showDrawings) {
            this.showDrawings = showDrawings;
            repaint();
        }
    }

    public boolean getShowIndicators() {
        return showIndicators;
    }

    public void setShowIndicators(boolean showIndicators) {
        if (this.showIndicators != showIndicators) {
            this.showIndicators = showIndicators;
            repaint();
        }
    }

    public boolean getShowPositionsAndOrders() {
        return showPositionsAndOrders;
    }

    public void setShowPositionsAndOrders(boolean showPositionsAndOrders) {
        if (this.showPositionsAndOrders != showPositionsAndOrders) {
            this.showPositionsAndOrders = showPositionsAndOrders;
            repaint();
        }
    }
}