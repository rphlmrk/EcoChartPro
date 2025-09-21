package com.EcoChartPro.ui.chart;

import com.EcoChartPro.api.indicator.IndicatorType;
import com.EcoChartPro.api.indicator.drawing.DrawableObject;
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
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.model.trading.Order;
import com.EcoChartPro.model.trading.Position;
import com.EcoChartPro.ui.MainWindow;
import com.EcoChartPro.ui.chart.axis.ChartAxis;
import com.EcoChartPro.ui.chart.render.ChartRenderer;
import com.EcoChartPro.ui.chart.render.DaySeparatorRenderer;
import com.EcoChartPro.ui.chart.render.IndicatorDrawableRenderer;
import com.EcoChartPro.ui.chart.render.PeakHoursRenderer;
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
    private final DrawingRenderer drawingRenderer;
    private final OrderRenderer orderRenderer;
    private final TradeSignalRenderer tradeSignalRenderer;
    private final IndicatorDrawableRenderer indicatorDrawableRenderer;
    private final DaySeparatorRenderer daySeparatorRenderer;
    private final PeakHoursRenderer peakHoursRenderer;
    private final DrawingController drawingController;
    private final ChartAxis chartAxis;
    private final ChartDataModel dataModel;
    private final PriceAxisPanel priceAxisPanel;
    private final TimeAxisPanel timeAxisPanel;
    private final FloatingPropertiesToolbar propertiesToolbar; // New field
    private List<KLine> localVisibleKLines;
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

    // Fields for timeframe input ---
    private final TimeframeInputDialog timeframeInputDialog;
    private final StringBuilder timeframeInputBuffer = new StringBuilder();
    private final Timer timeframeInputTimer;

    public ChartPanel(ChartDataModel dataModel, ChartAxis chartAxis, PriceAxisPanel priceAxisPanel, TimeAxisPanel timeAxisPanel, Consumer<Boolean> onToolStateChange, FloatingPropertiesToolbar propertiesToolbar) {
        this.dataModel = dataModel;
        this.chartAxis = chartAxis;
        this.priceAxisPanel = priceAxisPanel;
        this.timeAxisPanel = timeAxisPanel;
        this.propertiesToolbar = propertiesToolbar; // Assign new parameter
        this.chartRenderer = new ChartRenderer();
        this.drawingRenderer = new DrawingRenderer();
        this.orderRenderer = new OrderRenderer();
        this.tradeSignalRenderer = new TradeSignalRenderer();
        this.indicatorDrawableRenderer = new IndicatorDrawableRenderer();
        this.daySeparatorRenderer = new DaySeparatorRenderer();
        this.peakHoursRenderer = new PeakHoursRenderer();
        this.drawingController = new DrawingController(this, onToolStateChange);
        this.localVisibleKLines = new ArrayList<>();
        
        SettingsManager settings = SettingsManager.getInstance();
        setBackground(settings.getChartBackground());
        setBorder(INACTIVE_BORDER);
        setLayout(null);
        // Make chart focusable for keyboard input ---
        setFocusable(true);
        
        this.jumpToLiveEdgeButton = createOverlayButton(UITheme.Icons.FAST_FORWARD, "Jump to Live Edge");
        this.jumpToLiveEdgeButton.addActionListener(e -> dataModel.jumpToLiveEdge());
        this.increaseMarginButton = createOverlayButton(UITheme.Icons.CHEVRON_DOUBLE_RIGHT, "Increase Right Margin");
        this.increaseMarginButton.addActionListener(e -> dataModel.increaseRightMargin());
        this.decreaseMarginButton = createOverlayButton(UITheme.Icons.CHEVRON_DOUBLE_LEFT, "Decrease Right Margin");
        this.decreaseMarginButton.addActionListener(e -> dataModel.decreaseRightMargin());
        add(jumpToLiveEdgeButton);
        add(increaseMarginButton);
        add(decreaseMarginButton);

        this.dataModel.addPropertyChangeListener(this);
        this.dataModel.setView(this);
        DrawingManager.getInstance().addListener(this);
        DrawingManager.getInstance().addPropertyChangeListener("selectedDrawingChanged", this);
        settings.addPropertyChangeListener(this);
        CrosshairManager.getInstance().addPropertyChangeListener("crosshairMoved", this);
        if (dataModel.isInReplayMode()) {
            ReplaySessionManager.getInstance().addListener(this);
            PaperTradingService.getInstance().addPropertyChangeListener(this);
        }

        // Initialize timeframe input components ---
        this.timeframeInputDialog = new TimeframeInputDialog((Frame) SwingUtilities.getWindowAncestor(this));
        this.timeframeInputTimer = new Timer(3000, e -> clearTimeframeInput());
        this.timeframeInputTimer.setRepeats(false);
        addKeyListener(new TimeframeInputListener());
        // listener for the Escape key to cancel price selection.
        addKeyListener(new EscapeKeyListener());


        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                positionOverlayButtons();
            }
        });
    }
    
    /**
     * MODIFICATION: New public method to get a raw or snapped data point.
     * Centralizes the magnet mode logic for use by all controllers.
     * @param e The mouse event containing the screen coordinates.
     * @return A DrawingObjectPoint, snapped to OHLC if CTRL is pressed.
     */
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
        if (!dataModel.isInReplayMode()) {
            jumpToLiveEdgeButton.setVisible(false);
            increaseMarginButton.setVisible(false);
            decreaseMarginButton.setVisible(false);
            return;
        }

        boolean isAtLiveEdge = dataModel.isViewingLiveEdge();

        jumpToLiveEdgeButton.setVisible(!isAtLiveEdge);

        boolean showMarginControls = isAtLiveEdge && isReplayPlaying;
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
    }
    public void exitPriceSelectionMode() {
        this.isPriceSelectionMode = false;
        this.priceSelectionCallback = null;
        setCursor(Cursor.getDefaultCursor());
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
        SettingsManager.getInstance().removePropertyChangeListener(this);
        if (timeAxisPanel != null) {
            timeAxisPanel.cleanup();
        }
        if (dataModel.isInReplayMode()) {
            ReplaySessionManager.getInstance().removeListener(this);
            PaperTradingService.getInstance().removePropertyChangeListener(this);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propName = evt.getPropertyName();

        if ("chartColorsChanged".equals(propName) || "peakHoursLinesVisibilityChanged".equals(propName) || "peakHoursOverrideChanged".equals(propName) || "peakHoursSettingsChanged".equals(propName)) {
            SettingsManager settings = SettingsManager.getInstance();
            setBackground(settings.getChartBackground());
            if (getBorder() != INACTIVE_BORDER) {
                setBorder(BorderFactory.createLineBorder(settings.getBullColor(), 2));
            }
            repaint();
        } else if ("dataUpdated".equals(propName)) {
            this.localVisibleKLines = new ArrayList<>(dataModel.getVisibleKLines());
            SwingUtilities.invokeLater(() -> {
                updateOverlayButtonsVisibility();
                repaint();
            });
        } else if ("daySeparatorsEnabledChanged".equals(propName) || "selectedDrawingChanged".equals(propName)) {
            repaint();
        } else if ("crosshairMoved".equals(propName) && evt.getNewValue() instanceof CrosshairManager.CrosshairUpdate) {
            // Full implementation of sync logic and repaint handling ---
            CrosshairManager.CrosshairUpdate update = (CrosshairManager.CrosshairUpdate) evt.getNewValue();

            // Core sync logic: if sync is off, only process events from this panel
            if (!CrosshairManager.getInstance().isSyncEnabled() && update.source() != this) {
                return;
            }

            // Repaint the area of the old crosshair to erase it
            if (previousCrosshairPoint != null) {
                repaintCrosshairRegion(previousCrosshairPoint);
            }

            this.crosshairPoint = update.point();
            this.previousCrosshairPoint = this.crosshairPoint; // Store for the next erase cycle

            // Repaint the area of the new crosshair to draw it
            if (this.crosshairPoint != null) {
                repaintCrosshairRegion(this.crosshairPoint);
            }
        } else if ("pendingOrdersUpdated".equals(propName) || "openPositionsUpdated".equals(propName) || "tradeHistoryUpdated".equals(propName)) {
            // A trading-related object was added, removed, or modified.
            // The chart needs to be redrawn to reflect this change.
            repaint();
        }
    }

    private void repaintCrosshairRegion(DrawingObjectPoint point) {
        if (point == null || !chartAxis.isConfigured()) {
            return;
        }
        int x = chartAxis.timeToX(point.timestamp(), localVisibleKLines, dataModel.getCurrentDisplayTimeframe());
        int y = chartAxis.priceToY(point.price());
        
        // Repaint a thin strip for the vertical and horizontal lines.
        // The +3/-1 ensures we cover the line and its anti-aliasing.
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
    public FloatingPropertiesToolbar getPropertiesToolbar() { return this.propertiesToolbar; } // New Getter
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

        chartAxis.configure(
            dataModel.getMinPrice(),
            dataModel.getMaxPrice(),
            dataModel.getBarsPerScreen(),
            getSize()
        );

        DataSourceManager.ChartDataSource currentSource = dataModel.getCurrentSymbol();
        if (currentSource == null) {
             currentSource = ReplaySessionManager.getInstance().getCurrentSource();
        }
        Timeframe currentTimeframe = dataModel.getCurrentDisplayTimeframe();

        // The check for `isFullRepaint` was causing content to disappear during partial repaints (like crosshair moves).
        // By removing the conditional block, we ensure all chart content is redrawn every time, fixing the bug.
        
        if (currentSource != null) {
            g2d.setFont(SYMBOL_FONT);
            g2d.setColor(settings.getAxisTextColor());
            String text = currentSource.displayName() + " - " + (currentTimeframe != null ? currentTimeframe.getDisplayName() : "");
            g2d.drawString(text, 20, 30);
        }

        if (localVisibleKLines.isEmpty() && dataModel.getTotalCandleCount() == 0 && !isLoading) {
             g2d.setColor(UIManager.getColor("Label.disabledForeground"));
             g2d.drawString("No data to display. Please launch a chart.", 20, 30);
             g2d.dispose();
             return;
        }

        chartRenderer.draw(g2d, chartAxis, localVisibleKLines, dataModel.getStartIndex(), currentTimeframe);

        if (settings.isDaySeparatorsEnabled()) {
            daySeparatorRenderer.draw(g2d, chartAxis, localVisibleKLines, currentTimeframe);
        }

        if (settings.isShowPeakHoursLines() && dataModel.isInReplayMode()) {
            List<Integer> peakHours = settings.getPeakPerformanceHoursOverride();
            // If override is empty, use the auto-detected hours
            if (peakHours.isEmpty()) {
                peakHours = GamificationService.getInstance().getPeakPerformanceHours();
            }
            // FIX: Pass the currentTimeframe to the renderer
            peakHoursRenderer.draw(g2d, chartAxis, localVisibleKLines, currentTimeframe, peakHours);
        }


        if (!localVisibleKLines.isEmpty()) {
            Instant startTime = localVisibleKLines.get(0).timestamp();
            Instant endTime = localVisibleKLines.get(localVisibleKLines.size() - 1).timestamp();
            TimeRange timeRange = new TimeRange(startTime, endTime);
            PriceRange priceRange = new PriceRange(dataModel.getMinPrice(), dataModel.getMaxPrice());

            if (showIndicators) {
                List<DrawableObject> allIndicatorDrawables = new ArrayList<>();
                dataModel.getIndicatorManager().getIndicators().stream()
                    .filter(i -> i.getType() == IndicatorType.OVERLAY)
                    .forEach(indicator -> allIndicatorDrawables.addAll(indicator.getResults()));
                indicatorDrawableRenderer.draw(g2d, allIndicatorDrawables, chartAxis, localVisibleKLines, currentTimeframe);
            }

            if (showDrawings) {
                List<DrawingObject> visibleDrawings = DrawingManager.getInstance().getVisibleDrawings(timeRange, priceRange);
                drawingRenderer.draw(g2d, visibleDrawings, chartAxis, localVisibleKLines, currentTimeframe);

                DrawingTool activeTool = drawingController.getActiveTool();
                if (activeTool != null && activeTool.getPreviewObject() != null) {
                    drawingRenderer.draw(g2d, List.of(activeTool.getPreviewObject()), chartAxis, localVisibleKLines, currentTimeframe);
                }
            }

            if (showPositionsAndOrders && dataModel.isInReplayMode()) {
                PaperTradingService service = PaperTradingService.getInstance();
                List<Trade> allTrades = service.getTradeHistory();
                List<Trade> visibleTrades = filterVisibleTrades(allTrades, timeRange);
                tradeSignalRenderer.draw(g2d, chartAxis, visibleTrades, localVisibleKLines, currentTimeframe);
                List<Position> positions = service.getOpenPositions();
                List<Order> orders = service.getPendingOrders();
                orderRenderer.draw(g2d, chartAxis, positions, orders, dragPreview);
            }
        }

        if (orderPreview != null) {
            drawOrderPreview(g2d, orderPreview);
        }
        
        drawCrosshair(g2d);

        if (dataModel.isViewingLiveEdge()) {
            KLine lastKline = dataModel.getCurrentReplayKLine();
            if (lastKline != null) {
                BigDecimal lastClose = lastKline.close();
                int y = chartAxis.priceToY(lastClose);
                boolean isBullish = lastKline.close().compareTo(lastKline.open()) >= 0;
                Color backgroundColor = isBullish ? settings.getBullColor() : settings.getBearColor();
                Color textColor = getHighContrastColor(backgroundColor);
                
                int lastGlobalIndex = dataModel.getTotalCandleCount() - 1;
                int slot = lastGlobalIndex - dataModel.getStartIndex();
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
    
    private Color getHighContrastColor(Color background) {
        double luminance = (0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue()) / 255;
        return (luminance > 0.5) ? Color.BLACK : Color.WHITE;
    }
    
    private void drawCrosshair(Graphics2D g2d) {
        if (crosshairPoint == null || !chartAxis.isConfigured()) return;
        
        int x = chartAxis.timeToX(crosshairPoint.timestamp(), localVisibleKLines, dataModel.getCurrentDisplayTimeframe());
        int y = chartAxis.priceToY(crosshairPoint.price());

        // This prevented the horizontal line from being drawn if the vertical line was off-screen,
        // causing the crosshair to disappear on other synced charts with different visible time ranges.

        Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{2}, 0);
        g2d.setStroke(dashed);
        g2d.setColor(SettingsManager.getInstance().getCrosshairColor());

        // Always draw the horizontal price line, as the price is always synced.
        g2d.drawLine(0, y, getWidth(), y);
        // Only draw the vertical time line if the timestamp is visible on this chart.
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
    
    // New methods and inner classes for timeframe input ---

    private void clearTimeframeInput() {
        timeframeInputBuffer.setLength(0);
        timeframeInputDialog.setVisible(false);
        timeframeInputTimer.stop();
    }

    private class TimeframeInputListener extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                String input = timeframeInputBuffer.toString().toLowerCase();
                Timeframe newTf = Timeframe.fromString(input);
                if (newTf != null) {
                    dataModel.setDisplayTimeframe(newTf);
                    // Also update the toolbar button text
                    MainWindow mw = (MainWindow) SwingUtilities.getWindowAncestor(ChartPanel.this);
                    if (mw != null) {
                        mw.getTopToolbarPanel().selectTimeframe(newTf.getDisplayName());
                    }
                }
                clearTimeframeInput();
                e.consume();
            } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                clearTimeframeInput();
                e.consume();
            } else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                if (!timeframeInputBuffer.isEmpty()) {
                    timeframeInputBuffer.setLength(timeframeInputBuffer.length() - 1);
                    timeframeInputDialog.updateInputText(timeframeInputBuffer.toString());
                    timeframeInputTimer.restart();
                }
                e.consume();
            }
        }

        @Override
        public void keyTyped(KeyEvent e) {
            char c = e.getKeyChar();
            if (Character.isLetterOrDigit(c)) {
                if (timeframeInputBuffer.isEmpty() && !Character.isDigit(c)) {
                    return; // Must start with a number
                }
                timeframeInputBuffer.append(c);
                if (!timeframeInputDialog.isVisible()) {
                    timeframeInputDialog.showDialog(ChartPanel.this, timeframeInputBuffer.toString());
                } else {
                    timeframeInputDialog.updateInputText(timeframeInputBuffer.toString());
                }
                timeframeInputTimer.restart();
                e.consume();
            }
        }
    }

    /**
     * A dedicated key listener to handle canceling operations like price selection.
     */
    private class EscapeKeyListener extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                if (isPriceSelectionMode) {
                    // Signal cancellation to the callback by passing null
                    if (priceSelectionCallback != null) {
                        priceSelectionCallback.accept(null);
                    }
                    exitPriceSelectionMode();
                    e.consume(); // Prevent other components from processing this Escape press
                }
            }
        }
    }
}