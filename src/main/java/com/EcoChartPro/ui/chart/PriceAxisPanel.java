package com.EcoChartPro.ui.chart;

import com.EcoChartPro.core.controller.ChartInteractionManager;
import com.EcoChartPro.core.controller.ReplaySessionManager;
import com.EcoChartPro.core.manager.CrosshairManager;
import com.EcoChartPro.core.manager.DrawingManager;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.model.chart.ChartType;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.drawing.DrawingObjectPoint;
import com.EcoChartPro.model.drawing.FibonacciExtensionObject;
import com.EcoChartPro.model.drawing.FibonacciRetracementObject;
import com.EcoChartPro.model.drawing.HorizontalLineObject;
import com.EcoChartPro.model.drawing.HorizontalRayObject;
import com.EcoChartPro.model.drawing.RectangleObject;
import com.EcoChartPro.model.drawing.Trendline;
import com.EcoChartPro.model.trading.Order;
import com.EcoChartPro.model.trading.Position;
import com.EcoChartPro.ui.MainWindow;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Renders the vertical price axis for a chart, including the price scale and dynamic labels
 * for orders, positions, and drawings.
 */
public class PriceAxisPanel extends JPanel implements PropertyChangeListener {

    private final ChartDataModel dataModel;
    private final ChartAxis yAxis;
    private DrawingObjectPoint crosshairPoint;
    private final ChartInteractionManager interactionManager;
    private javax.swing.Timer repaintTimer;
    private boolean isPriceSelectionMode = false;
    private ChartPanel priceSelectionController;

    /**
     * A record to hold all necessary information for rendering a price label.
     */
    private record PriceLabel(String text, BigDecimal price, Color color, int y) {}

    public PriceAxisPanel(ChartDataModel dataModel, ChartAxis yAxis, ChartInteractionManager interactionManager) {
        this.dataModel = dataModel;
        this.yAxis = yAxis;
        this.interactionManager = interactionManager;
        setPreferredSize(new Dimension(80, 0));
        setLayout(new BorderLayout());

        PriceScaleDrawer drawer = new PriceScaleDrawer();
        add(drawer, BorderLayout.CENTER);

        SettingsManager sm = SettingsManager.getInstance();
        sm.addPropertyChangeListener("priceAxisLabelsEnabledChanged", this);
        sm.addPropertyChangeListener("priceAxisLabelsVisibilityChanged", this);
        sm.addPropertyChangeListener("chartColorsChanged", this);
        sm.addPropertyChangeListener("livePriceLabelFontSizeChanged", this);
        sm.addPropertyChangeListener("crosshairLabelColorChanged", this);
        sm.addPropertyChangeListener("chartTypeChanged", this);
        CrosshairManager.getInstance().addPropertyChangeListener("crosshairMoved", this);
        
        startRepaintTimer();
    }

    public void enterPriceSelectionMode(ChartPanel controller) {
        this.isPriceSelectionMode = true;
        this.priceSelectionController = controller;
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    }

    public void exitPriceSelectionMode() {
        this.isPriceSelectionMode = false;
        this.priceSelectionController = null;
        setCursor(Cursor.getDefaultCursor());
    }
    
    private void startRepaintTimer() {
        if (repaintTimer == null) {
            repaintTimer = new javax.swing.Timer(1000, e -> {
                if (dataModel != null && dataModel.getCurrentReplayKLine() != null) {
                    repaint();
                }
            });
            repaintTimer.setRepeats(true);
            repaintTimer.start();
        }
    }

    public void cleanup() {
        SettingsManager.getInstance().removePropertyChangeListener(this);
        CrosshairManager.getInstance().removePropertyChangeListener("crosshairMoved", this);
        if (repaintTimer != null) {
            repaintTimer.stop();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("crosshairMoved".equals(evt.getPropertyName())) {
            if (evt.getNewValue() instanceof CrosshairManager.CrosshairUpdate) {
                CrosshairManager.CrosshairUpdate update = (CrosshairManager.CrosshairUpdate) evt.getNewValue();
                this.crosshairPoint = update.point();
            } else {
                this.crosshairPoint = null;
            }
        }
        repaint();
    }
    
    /**
     * Inner class responsible for all custom drawing on the price axis.
     */
    private class PriceScaleDrawer extends JComponent {
        private static final int TICK_LENGTH = 5;
        private static final int PADDING_X = 5;
        private static final int TRIANGLE_WIDTH = 6;
        private static final Font LABEL_FONT = new Font("SansSerif", Font.BOLD, 10);
        private static final Color LONG_COLOR = new Color(0, 150, 136);
        private static final Color SHORT_COLOR = new Color(211, 47, 47);
        private Point lastMousePoint;

        PriceScaleDrawer() {
            addMouseListeners();
        }

        private void addMouseListeners() {
            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (isPriceSelectionMode) {
                        BigDecimal price = yAxis.yToPrice(e.getY());
                        Consumer<BigDecimal> callback = priceSelectionController.getPriceSelectionCallback();
                        if (price != null && callback != null) {
                            callback.accept(price);
                        }
                        priceSelectionController.exitPriceSelectionMode();
                        e.consume();
                        return;
                    }

                    if (interactionManager != null && SwingUtilities.isLeftMouseButton(e)) {
                        interactionManager.setAutoScalingY(false);
                        lastMousePoint = e.getPoint();
                        setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    lastMousePoint = null;
                    setCursor(Cursor.getDefaultCursor());
                }
                
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e) && interactionManager != null) {
                        JPopupMenu popupMenu = new JPopupMenu();
                        JMenuItem invertItem = new JMenuItem("Invert Scale");
                        invertItem.addActionListener(evt -> interactionManager.toggleInvertY());
                        popupMenu.add(invertItem);

                        popupMenu.addSeparator();

                        JMenuItem calculatorItem = new JMenuItem("Position Size Calculator...");
                        calculatorItem.addActionListener(evt -> {
                            Frame owner = (Frame) SwingUtilities.getWindowAncestor(PriceScaleDrawer.this);
                            if (owner instanceof MainWindow mainWindow) {
                                BigDecimal clickedPrice = yAxis.yToPrice(e.getY());
                                mainWindow.getUiManager().openPositionSizeCalculator(clickedPrice);
                            }
                        });
                        popupMenu.add(calculatorItem);

                        popupMenu.show(PriceScaleDrawer.this, e.getX(), e.getY());
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (lastMousePoint == null || interactionManager == null || !yAxis.isConfigured()) return;

                    int dy = e.getY() - lastMousePoint.y;
                    // Exponential scaling feels smoother and more controlled than linear
                    double scaleFactor = Math.pow(1.005, -dy);
                    
                    BigDecimal anchorPrice = yAxis.yToPrice(e.getY());
                    interactionManager.scalePriceAxis(scaleFactor, anchorPrice);
                    
                    lastMousePoint = e.getPoint();
                }
            };

            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);

            addMouseWheelListener((MouseWheelEvent e) -> {
                if (interactionManager == null || !yAxis.isConfigured()) return;
                
                double zoomFactor = e.getWheelRotation() < 0 ? 1.1 : 0.9;
                BigDecimal anchorPrice = yAxis.yToPrice(e.getY());
                interactionManager.scalePriceAxis(zoomFactor, anchorPrice);
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2d.setColor(getBackground());
            g2d.fillRect(0, 0, getWidth(), getHeight());

            drawPriceScale(g2d);

            if (SettingsManager.getInstance().isPriceAxisLabelsEnabled()) {
                drawDynamicPriceLabels(g2d);
            }

            drawCrosshairPrice(g2d);
            drawLiveInfo(g2d);
            
            g2d.dispose();
        }

        private void drawCrosshairPrice(Graphics2D g2d) {
            if (crosshairPoint == null || !yAxis.isConfigured()) {
                return;
            }

            BigDecimal price = crosshairPoint.price();
            int y = yAxis.priceToY(price);
            
            int scale = 2;
            if (dataModel != null && yAxis.getMaxPrice() != null && yAxis.getMinPrice() != null) {
                BigDecimal priceRange = yAxis.getMaxPrice().subtract(yAxis.getMinPrice());
                if (priceRange.doubleValue() < 1.0) {
                    scale = 8;
                }
            }
            String priceStr = price.setScale(scale, RoundingMode.HALF_UP).toPlainString();

            g2d.setFont(LABEL_FONT);
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(priceStr);
            int labelHeight = fm.getHeight() + 2;
            int rectWidth = textWidth + PADDING_X * 2;
            
            int rectX = 0;
            int textX = rectX + PADDING_X;
            int yRect = y - labelHeight / 2;

            SettingsManager settings = SettingsManager.getInstance();
            g2d.setColor(settings.getCrosshairLabelBackgroundColor());
            g2d.fillRect(rectX, yRect, rectWidth, labelHeight);
            g2d.setColor(settings.getCrosshairLabelForegroundColor());
            g2d.drawString(priceStr, textX, yRect + fm.getAscent() + 1);
        }

        private void drawLiveInfo(Graphics2D g2d) {
            if (dataModel == null) {
                return;
            }
        
            KLine lastKline = dataModel.getCurrentReplayKLine();
            if (lastKline == null) {
                return;
            }
        
            SettingsManager settings = SettingsManager.getInstance();
            boolean isHeikinAshiMode = settings.getCurrentChartType() == ChartType.HEIKIN_ASHI;
        
            // --- 1. Get Data & Countdown ---
            BigDecimal lastClose = lastKline.close();
            KLine lastHaKline = null;
            if (isHeikinAshiMode) {
                List<KLine> haCandles = dataModel.getHeikinAshiCandles();
                if (haCandles != null && !haCandles.isEmpty()) {
                    lastHaKline = haCandles.get(haCandles.size() - 1);
                }
            }
            String countdownText = getCountdownText(lastKline, dataModel.getCurrentDisplayTimeframe());
        
            // --- 2. Setup Rendering Variables ---
            boolean isRawBullish = lastKline.close().compareTo(lastKline.open()) >= 0;
            Color realPriceBackgroundColor = isRawBullish ? settings.getBullColor() : settings.getBearColor();
            Color textColor = isRawBullish ? settings.getLivePriceLabelBullTextColor() : settings.getLivePriceLabelBearTextColor();
            Font liveFont = LABEL_FONT.deriveFont((float) settings.getLivePriceLabelFontSize());
            g2d.setFont(liveFont);
            FontMetrics fm = g2d.getFontMetrics();
            
            int priceLabelHeight = fm.getHeight() + 2;
            int rectX = 0;
            int priceRectWidth = getWidth();
            int separatorHeight = 1;
        
            // --- 3. Calculate Independent Positions and Handle Overlaps ---
            int yRealPriceCenter = yAxis.priceToY(lastClose);
            int yRealPriceRectTop = yRealPriceCenter - priceLabelHeight / 2;
        
            int yHaPriceRectTop = -1;
            if (isHeikinAshiMode && lastHaKline != null) {
                int yHaPriceCenter = yAxis.priceToY(lastHaKline.close());
                yHaPriceRectTop = yHaPriceCenter - priceLabelHeight / 2;
        
                // Handle overlap by stacking
                if (Math.abs(yRealPriceCenter - yHaPriceCenter) < priceLabelHeight) {
                    if (yRealPriceCenter < yHaPriceCenter) { // Real price is higher on chart
                        yHaPriceRectTop = yRealPriceRectTop + priceLabelHeight + separatorHeight;
                    } else { // HA price is higher on chart
                        yRealPriceRectTop = yHaPriceRectTop + priceLabelHeight + separatorHeight;
                    }
                }
            }
        
            // --- 4. Draw Labels Sequentially ---
        
            // A. Draw Real Price Label
            String realPriceStr = lastClose.setScale(2, RoundingMode.HALF_UP).toPlainString();
            g2d.setColor(realPriceBackgroundColor);
            g2d.fillRect(rectX, yRealPriceRectTop, priceRectWidth, priceLabelHeight);
            g2d.setColor(textColor);
            g2d.drawString(realPriceStr, rectX + PADDING_X, yRealPriceRectTop + fm.getAscent() + 1);
        
            int lowestY = yRealPriceRectTop + priceLabelHeight;
        
            // B. Draw HA Price Label
            if (isHeikinAshiMode && lastHaKline != null) {
                BigDecimal haClose = lastHaKline.close();
                boolean isHaBullish = haClose.compareTo(lastHaKline.open()) >= 0;
                String haPriceStr = haClose.setScale(2, RoundingMode.HALF_UP).toPlainString();
                Color haBackgroundColor = isHaBullish ? settings.getBullColor().darker() : settings.getBearColor().darker();
        
                g2d.setColor(haBackgroundColor);
                g2d.fillRect(rectX, yHaPriceRectTop, priceRectWidth, priceLabelHeight);
                g2d.setColor(textColor);
                g2d.drawString(haPriceStr, rectX + PADDING_X, yHaPriceRectTop + fm.getAscent() + 1);
                
                lowestY = Math.max(lowestY, yHaPriceRectTop + priceLabelHeight);
            }
            
            // C. Draw Countdown Label
            if (countdownText != null) {
                int yCountdownRect = lowestY + separatorHeight;
        
                g2d.setColor(realPriceBackgroundColor); // Use real price background for countdown
                g2d.fillRect(rectX, yCountdownRect, priceRectWidth, priceLabelHeight);
                g2d.setColor(textColor);
                g2d.drawString(countdownText, rectX + PADDING_X, yCountdownRect + fm.getAscent() + 1);
            }
        }
        
        private String getCountdownText(KLine lastKline, Timeframe timeframe) {
             if (timeframe == null) {
                return null;
            }
        
            String countdownText = null;
            if (dataModel.getCurrentMode() == ChartDataModel.ChartMode.LIVE) {
                long durationMillis = timeframe.duration().toMillis();
                if (durationMillis > 0) {
                    long intervalStartMillis = getIntervalStart(lastKline.timestamp(), timeframe).toEpochMilli();
                    long nextIntervalStartMillis = intervalStartMillis + durationMillis;
                    long currentSystemMillis = Instant.now().toEpochMilli();
                    long millisRemaining = nextIntervalStartMillis - currentSystemMillis;
        
                    if (millisRemaining > 0 && millisRemaining <= durationMillis) {
                        long totalSecondsRemaining = TimeUnit.MILLISECONDS.toSeconds(millisRemaining);
                        long hours = TimeUnit.SECONDS.toHours(totalSecondsRemaining);
                        long minutes = TimeUnit.SECONDS.toMinutes(totalSecondsRemaining) % 60;
                        long seconds = totalSecondsRemaining % 60;
                        
                        if (hours > 0) {
                            countdownText = String.format("-%d:%02d:%02d", hours, minutes, seconds);
                        } else {
                            countdownText = String.format("-%02d:%02d", minutes, seconds);
                        }
                    }
                }
            } else { // REPLAY Mode
                ReplaySessionManager manager = ReplaySessionManager.getInstance();
                if (manager.isPlaying() && timeframe != Timeframe.M1) {
                    KLine currentM1Bar = manager.getCurrentBar();
                    if (currentM1Bar != null) {
                        Instant intervalStart = getIntervalStart(currentM1Bar.timestamp(), timeframe);
                        long minutesPassed = Duration.between(intervalStart, currentM1Bar.timestamp()).toMinutes();
                        long totalMinutesInBar = timeframe.duration().toMinutes();
                        long minutesRemaining = totalMinutesInBar - minutesPassed - 1;

                        if (minutesRemaining >= 0) {
                            long hours = minutesRemaining / 60;
                            long mins = minutesRemaining % 60;
                            if (hours > 0) {
                                countdownText = String.format("-%d:%02d", hours, mins);
                            } else {
                                countdownText = String.format("-%dm", minutesRemaining);
                            }
                        }
                    }
                }
            }
            return countdownText;
        }

        private void drawPriceScale(Graphics2D g2d) {
            if (!yAxis.isConfigured()) return;

            g2d.setFont(g2d.getFont().deriveFont(10f));
            g2d.setColor(SettingsManager.getInstance().getAxisTextColor());
            FontMetrics fm = g2d.getFontMetrics();

            int numTicks = getHeight() / (fm.getHeight() * 3);
            BigDecimal priceRange = yAxis.getMaxPrice().subtract(yAxis.getMinPrice());
            if (priceRange.compareTo(BigDecimal.ZERO) <= 0) return;

            BigDecimal step = priceRange.divide(BigDecimal.valueOf(numTicks), 8, RoundingMode.HALF_UP);
            int scale = 2;
            if (priceRange.doubleValue() < 1.0) {
                scale = 8;
            }

            for (int i = 0; i <= numTicks; i++) {
                BigDecimal price = yAxis.getMinPrice().add(step.multiply(BigDecimal.valueOf(i)));
                int y = yAxis.priceToY(price);
                String priceStr = price.setScale(scale, RoundingMode.HALF_UP).toPlainString();
                g2d.drawLine(0, y, TICK_LENGTH, y);
                g2d.drawString(priceStr, TICK_LENGTH + 2, y + (fm.getAscent() / 2) - 2);
            }
        }
        
        private void drawDynamicPriceLabels(Graphics2D g2d) {
            List<PriceLabel> labelsToDraw = new ArrayList<>();
            collectPositionAndOrderLabels(labelsToDraw);
            collectDrawingLabels(labelsToDraw);
            layoutAndDrawLabels(g2d, labelsToDraw);
        }
        
        private void layoutAndDrawLabels(Graphics2D g2d, List<PriceLabel> labels) {
            if (labels.isEmpty()) return;

            g2d.setFont(LABEL_FONT);
            FontMetrics fm = g2d.getFontMetrics();
            int labelHeight = fm.getHeight() + 4;

            List<PriceLabel> sortedLabels = labels.stream()
                .map(label -> new PriceLabel(label.text, label.price, label.color, yAxis.priceToY(label.price)))
                .sorted(Comparator.comparingInt(PriceLabel::y))
                .collect(Collectors.toList());

            for (int i = 1; i < sortedLabels.size(); i++) {
                PriceLabel prev = sortedLabels.get(i - 1);
                PriceLabel current = sortedLabels.get(i);
                if (current.y < prev.y + labelHeight) {
                    sortedLabels.set(i, new PriceLabel(current.text, current.price, current.color, prev.y + labelHeight));
                }
            }

            for (PriceLabel label : sortedLabels) {
                drawPriceLabel(g2d, label);
            }
        }

        private void collectPositionAndOrderLabels(List<PriceLabel> labelsToDraw) {
            if (dataModel == null || !SettingsManager.getInstance().isPriceAxisLabelsShowOrders()) return;
        
            PaperTradingService service = PaperTradingService.getInstance();
            for (Position pos : service.getOpenPositions()) {
                Color color = pos.direction() == TradeDirection.LONG ? LONG_COLOR : SHORT_COLOR;
                labelsToDraw.add(new PriceLabel(pos.size().toPlainString(), pos.entryPrice(), color, 0));
                if (pos.stopLoss() != null) labelsToDraw.add(new PriceLabel("SL", pos.stopLoss(), SHORT_COLOR.darker(), 0));
                if (pos.takeProfit() != null) labelsToDraw.add(new PriceLabel("TP", pos.takeProfit(), LONG_COLOR.darker(), 0));
            }
        
            for (Order order : service.getPendingOrders()) {
                Color color = order.direction() == TradeDirection.LONG ? LONG_COLOR.brighter() : SHORT_COLOR.brighter();
                labelsToDraw.add(new PriceLabel(order.type().toString(), order.limitPrice(), color, 0));
            }
        }

        private void collectDrawingLabels(List<PriceLabel> labelsToDraw) {
            if (dataModel == null) return;
            List<DrawingObject> allDrawings = DrawingManager.getInstance().getAllDrawings();

            boolean showDrawings = SettingsManager.getInstance().isPriceAxisLabelsShowDrawings();
            boolean showFibonaccis = SettingsManager.getInstance().isPriceAxisLabelsShowFibonaccis();

            for (DrawingObject drawing : allDrawings) {
                if (!drawing.showPriceLabel()) {
                    continue;
                }

                if (drawing instanceof HorizontalLineObject hl) {
                    if (showDrawings) {
                        labelsToDraw.add(new PriceLabel("H-Line", hl.anchor().price(), getLabelColorForDrawing(drawing), 0));
                    }
                } else if (drawing instanceof HorizontalRayObject hr) {
                    if (showDrawings) {
                        labelsToDraw.add(new PriceLabel("H-Ray", hr.anchor().price(), getLabelColorForDrawing(drawing), 0));
                    }
                } else if (drawing instanceof RectangleObject rect) {
                    if (showDrawings) {
                        BigDecimal topPrice = rect.corner1().price().max(rect.corner2().price());
                        BigDecimal bottomPrice = rect.corner1().price().min(rect.corner2().price());
                        labelsToDraw.add(new PriceLabel("Rect", topPrice, getLabelColorForDrawing(drawing), 0));
                        labelsToDraw.add(new PriceLabel("Rect", bottomPrice, getLabelColorForDrawing(drawing), 0));
                    }
                } else if (drawing instanceof Trendline tl) {
                    if (showDrawings) {
                        labelsToDraw.add(new PriceLabel("TL", tl.start().price(), getLabelColorForDrawing(drawing), 0));
                        labelsToDraw.add(new PriceLabel("TL", tl.end().price(), getLabelColorForDrawing(drawing), 0));
                    }
                } else if (drawing instanceof FibonacciRetracementObject fib) {
                    if (showFibonaccis) {
                        BigDecimal priceRange = fib.p2().price().subtract(fib.p1().price());
                        for (Map.Entry<Double, FibonacciRetracementObject.FibLevelProperties> entry : fib.fibLevels().entrySet()) {
                            if (entry.getValue().enabled()) {
                                double level = entry.getKey();
                                BigDecimal levelPrice = fib.p1().price().add(priceRange.multiply(BigDecimal.valueOf(level)));
                                String labelText = String.format("%.3f", level);
                                labelsToDraw.add(new PriceLabel(labelText, levelPrice, entry.getValue().color(), 0));
                            }
                        }
                    }
                } else if (drawing instanceof FibonacciExtensionObject fibEx) {
                    if (showFibonaccis) {
                        BigDecimal priceRange = fibEx.p1().price().subtract(fibEx.p0().price());
                        for (Map.Entry<Double, FibonacciRetracementObject.FibLevelProperties> entry : fibEx.fibLevels().entrySet()) {
                           if (entry.getValue().enabled()) {
                               double level = entry.getKey();
                               BigDecimal levelPrice = fibEx.p2().price().add(priceRange.multiply(BigDecimal.valueOf(level)));
                               String labelText = String.format("%.3f", level);
                               labelsToDraw.add(new PriceLabel(labelText, levelPrice, entry.getValue().color(), 0));
                           }
                       }
                    }
                }
            }
        }

        private Color getLabelColorForDrawing(DrawingObject drawing) {
            return drawing.color().darker();
        }

        private void drawPriceLabel(Graphics2D g2d, PriceLabel label) {
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(label.text);
            int labelHeight = fm.getHeight();
            int rectWidth = textWidth + PADDING_X * 2;
            int rectHeight = labelHeight + 4;
            int y = label.y - rectHeight / 2;

            // --- [MODIFIED] Always draw on the right edge of the axis panel ---
            int rectX = 0;
            int textX = PADDING_X;
            int[] triangleXP = new int[]{rectWidth, rectWidth + TRIANGLE_WIDTH, rectWidth};
            int[] triangleYP = new int[]{y, y + rectHeight / 2, y + rectHeight};
            // --- End Modification ---

            g2d.setColor(label.color);
            g2d.fillRect(rectX, y, rectWidth, rectHeight);
            g2d.fillPolygon(triangleXP, triangleYP, 3);
            g2d.setColor(Color.WHITE);
            g2d.drawString(label.text, textX, y + fm.getAscent() + 2);
        }
         private Instant getIntervalStart(Instant timestamp, Timeframe timeframe) {
            long durationMillis = timeframe.duration().toMillis();
            if (durationMillis == 0) return timestamp;
            long epochMillis = timestamp.toEpochMilli();
            return Instant.ofEpochMilli(epochMillis - (epochMillis % durationMillis));
        }
    }
}