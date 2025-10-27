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
import com.EcoChartPro.ui.chart.axis.IChartAxis;

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

public class PriceAxisPanel extends JPanel implements PropertyChangeListener {

    private final ChartDataModel dataModel;
    private IChartAxis yAxis;
    private DrawingObjectPoint crosshairPoint;
    private final ChartInteractionManager interactionManager;
    private javax.swing.Timer repaintTimer;
    private boolean isPriceSelectionMode = false;
    private ChartPanel priceSelectionController;

    private record PriceLabel(String text, BigDecimal price, Color color, int y) {}

    public PriceAxisPanel(ChartDataModel dataModel, IChartAxis yAxis, ChartInteractionManager interactionManager) {
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
        sm.addPropertyChangeListener("priceAxisLabelPositionChanged", this);
        sm.addPropertyChangeListener("chartColorsChanged", this);
        sm.addPropertyChangeListener("livePriceLabelFontSizeChanged", this);
        sm.addPropertyChangeListener("crosshairLabelColorChanged", this);
        CrosshairManager.getInstance().addPropertyChangeListener("crosshairMoved", this);
        
        startRepaintTimer();
    }

    public void setChartAxis(IChartAxis axis) {
        this.yAxis = axis;
        repaint();
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
            // --- FIX: This condition correctly triggers repaints in LIVE mode for the countdown ---
            repaintTimer = new javax.swing.Timer(1000, e -> {
                if (dataModel != null && dataModel.getCurrentMode() == ChartDataModel.ChartMode.LIVE) {
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
            if (dataModel == null) return;
        
            KLine lastKline = dataModel.getCurrentReplayKLine();
            if (lastKline == null) {
                return;
            }
        
            BigDecimal lastClose = lastKline.close();
            int yPriceLabel = yAxis.priceToY(lastClose);
        
            SettingsManager settings = SettingsManager.getInstance();
            boolean isBullish = lastKline.close().compareTo(lastKline.open()) >= 0;
            
            Color backgroundColor = isBullish ? settings.getBullColor() : settings.getBearColor();
            Color textColor = isBullish ? settings.getLivePriceLabelBullTextColor() : settings.getLivePriceLabelBearTextColor();
            Font liveFont = LABEL_FONT.deriveFont((float) settings.getLivePriceLabelFontSize());
        
            String priceStr = lastClose.setScale(2, RoundingMode.HALF_UP).toPlainString();
            g2d.setFont(liveFont);
            FontMetrics fmPrice = g2d.getFontMetrics();
            int priceLabelHeight = fmPrice.getHeight() + 2;
            int priceRectWidth = getWidth();
            int priceRectHeight = priceLabelHeight;
            int yPriceRect = yPriceLabel - priceRectHeight / 2;
            int rectX = 0;
        
            g2d.setColor(backgroundColor);
            g2d.fillRect(rectX, yPriceRect, priceRectWidth, priceRectHeight);
            g2d.setColor(textColor);
            g2d.drawString(priceStr, rectX + PADDING_X, yPriceRect + fmPrice.getAscent() + 1);
        
            Timeframe timeframe = dataModel.getCurrentDisplayTimeframe();
            if (timeframe == null) {
                return;
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
        
            if (countdownText != null) {
                g2d.setFont(liveFont);
                FontMetrics fmCountdown = g2d.getFontMetrics();
                
                int yCountdownRect = yPriceRect + priceRectHeight;
                int countdownRectWidth = getWidth();
                int countdownRectHeight = fmCountdown.getHeight() + 2;
        
                g2d.setColor(backgroundColor);
                g2d.fillRect(rectX, yCountdownRect, countdownRectWidth, countdownRectHeight);
                
                g2d.setColor(textColor);
                g2d.drawString(countdownText, rectX + PADDING_X, yCountdownRect + fmCountdown.getAscent() + 1);
            }
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
            SettingsManager.PriceAxisLabelPosition position = SettingsManager.getInstance().getPriceAxisLabelPosition();
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(label.text);
            int labelHeight = fm.getHeight();
            int rectWidth = textWidth + PADDING_X * 2;
            int rectHeight = labelHeight + 4;
            int y = label.y - rectHeight / 2;

            int rectX, textX;
            int[] triangleXP, triangleYP;

            if (position == SettingsManager.PriceAxisLabelPosition.RIGHT) {
                rectX = 0;
                textX = PADDING_X;
                triangleXP = new int[]{rectWidth, rectWidth + TRIANGLE_WIDTH, rectWidth};
                triangleYP = new int[]{y, y + rectHeight / 2, y + rectHeight};
            } else { // LEFT
                int panelWidth = getWidth();
                rectX = panelWidth - rectWidth;
                textX = panelWidth - rectWidth + PADDING_X;
                triangleXP = new int[]{rectX, rectX - TRIANGLE_WIDTH, rectX};
                triangleYP = new int[]{y, y + rectHeight / 2, y + rectHeight};
            }

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