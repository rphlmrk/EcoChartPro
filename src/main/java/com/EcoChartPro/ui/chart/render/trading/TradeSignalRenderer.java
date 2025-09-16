package com.EcoChartPro.ui.chart.render.trading;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.TradeDirection;
import com.EcoChartPro.ui.chart.axis.ChartAxis;
import javax.swing.UIManager;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * Renders historical trade signals (entry and exit markers) on the chart.
 */
public class TradeSignalRenderer {

    private static final Stroke PNL_LINE_STROKE = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0);
    private static final int ARROW_SIZE = 8;

    public void draw(Graphics2D g, ChartAxis axis, List<Trade> trades, List<KLine> visibleKLines, Timeframe timeframe) {
        if (trades == null || trades.isEmpty() || !axis.isConfigured() || visibleKLines.isEmpty() || timeframe == null) {
            return;
        }

        for (Trade trade : trades) {
            // Convert trade times and prices to screen coordinates
            int xEntry = axis.timeToX(trade.entryTime(), visibleKLines, timeframe);
            int yEntry = axis.priceToY(trade.entryPrice());
            int xExit = axis.timeToX(trade.exitTime(), visibleKLines, timeframe);
            int yExit = axis.priceToY(trade.exitPrice());

            // Draw the line connecting entry and exit
            drawPnlLine(g, xEntry, yEntry, xExit, yExit, trade.profitAndLoss());

            // Draw entry and exit arrows
            drawEntryArrow(g, xEntry, yEntry, trade.direction());
            drawExitArrow(g, xExit, yExit, trade.direction());
        }
    }

    private void drawPnlLine(Graphics2D g, int x1, int y1, int x2, int y2, BigDecimal pnl) {
        g.setStroke(PNL_LINE_STROKE);
        if (pnl.compareTo(BigDecimal.ZERO) >= 0) {
            g.setColor(UIManager.getColor("app.trading.pnlProfit"));
        } else {
            g.setColor(UIManager.getColor("app.trading.pnlLoss"));
        }
        g.drawLine(x1, y1, x2, y2);
    }

    private void drawEntryArrow(Graphics2D g, int x, int y, TradeDirection direction) {
        g.setStroke(new BasicStroke(2f));
        if (direction == TradeDirection.LONG) {
            g.setColor(UIManager.getColor("app.trading.long"));
            // Upward pointing arrow
            int[] xPoints = {x - ARROW_SIZE, x, x + ARROW_SIZE};
            int[] yPoints = {y + ARROW_SIZE, y, y + ARROW_SIZE};
            g.fillPolygon(xPoints, yPoints, 3);
        } else { // SHORT
            g.setColor(UIManager.getColor("app.trading.short"));
            // Downward pointing arrow
            int[] xPoints = {x - ARROW_SIZE, x, x + ARROW_SIZE};
            int[] yPoints = {y - ARROW_SIZE, y, y - ARROW_SIZE};
            g.fillPolygon(xPoints, yPoints, 3);
        }
    }

    private void drawExitArrow(Graphics2D g, int x, int y, TradeDirection direction) {
        g.setStroke(new BasicStroke(2f));
        // Exit arrow is the reverse of the entry arrow
        if (direction == TradeDirection.LONG) { // Exit from a long is a sell
            g.setColor(UIManager.getColor("app.trading.short"));
            int[] xPoints = {x - ARROW_SIZE, x, x + ARROW_SIZE};
            int[] yPoints = {y - ARROW_SIZE, y, y - ARROW_SIZE};
            g.drawPolygon(xPoints, yPoints, 3);
        } else { // Exit from a short is a buy
            g.setColor(UIManager.getColor("app.trading.long"));
            int[] xPoints = {x - ARROW_SIZE, x, x + ARROW_SIZE};
            int[] yPoints = {y + ARROW_SIZE, y, y + ARROW_SIZE};
            g.drawPolygon(xPoints, yPoints, 3);
        }
    }
}