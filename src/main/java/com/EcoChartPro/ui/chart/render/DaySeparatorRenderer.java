package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DaySeparatorRenderer {

    private enum Granularity { DAY, WEEK, MONTH, ADAPTIVE }

    private static final ZoneId TRADING_DAY_ZONE = ZoneId.of("America/New_York");
    private static final Font LABEL_FONT = new Font("SansSerif", Font.PLAIN, 12);

    public void draw(Graphics2D g, ChartAxis axis, List<KLine> visibleKLines, Timeframe chartTimeframe) {
        if (visibleKLines == null || visibleKLines.size() < 2 || chartTimeframe == null) {
            return;
        }

        Granularity granularity = resolveGranularity(Granularity.ADAPTIVE, chartTimeframe);

        List<List<KLine>> periods = groupCandlesByPeriod(visibleKLines, granularity, chartTimeframe);

        boolean isFirstPeriodInView = true;

        for (List<KLine> periodCandles : periods) {
            if (periodCandles.isEmpty()) continue;

            KLine firstCandle = periodCandles.get(0);
            KLine lastCandle = periodCandles.get(periodCandles.size() - 1);

            // Draw the vertical separator line
            if (!isFirstPeriodInView) {
                int x = axis.timeToX(firstCandle.timestamp(), visibleKLines, chartTimeframe);
                if (x != -1) {
                    g.setColor(UIManager.getColor("app.chart.separator"));
                    g.setStroke(new java.awt.BasicStroke(1.0f));
                    g.drawLine(x, 0, x, g.getClipBounds().height);
                }
            }

            // Draw the period label
            drawPeriodLabel(g, axis, visibleKLines, firstCandle.timestamp(), lastCandle.timestamp(), granularity, chartTimeframe);

            isFirstPeriodInView = false;
        }
    }

    private void drawPeriodLabel(Graphics2D g, ChartAxis axis, List<KLine> visibleKLines, Instant periodStart, Instant periodEnd, Granularity granularity, Timeframe timeframe) {
        long startSeconds = periodStart.getEpochSecond();
        long endSeconds = periodEnd.getEpochSecond();
        long midSeconds = startSeconds + (endSeconds - startSeconds) / 2;
        Instant midpointTime = Instant.ofEpochSecond(midSeconds);

        String label = getPeriodLabel(midpointTime, granularity, timeframe);
        if (label == null) return;

        int x = axis.timeToX(midpointTime, visibleKLines, timeframe);
        if (x == -1) return;

        g.setFont(LABEL_FONT);
        g.setColor(UIManager.getColor("app.chart.separatorLabel"));
        java.awt.FontMetrics fm = g.getFontMetrics();
        int labelWidth = fm.stringWidth(label);
        
        // Use the axis padding constant for correct vertical placement
        int y = axis.priceToY(ChartAxis.ANCHOR_TOP) + fm.getAscent() + 2;

        g.drawString(label, x - labelWidth / 2, y);
    }

    private List<List<KLine>> groupCandlesByPeriod(List<KLine> data, Granularity granularity, Timeframe timeframe) {
        List<List<KLine>> periods = new ArrayList<>();
        if (data.isEmpty()) return periods;

        List<KLine> currentPeriod = new ArrayList<>();
        int lastPeriodId = -1;

        for (KLine kline : data) {
            int currentPeriodId = getPeriodId(kline.timestamp(), granularity, timeframe);
            if (lastPeriodId != -1 && currentPeriodId != lastPeriodId) {
                periods.add(new ArrayList<>(currentPeriod));
                currentPeriod.clear();
            }
            currentPeriod.add(kline);
            lastPeriodId = currentPeriodId;
        }

        if (!currentPeriod.isEmpty()) {
            periods.add(currentPeriod);
        }
        return periods;
    }

    private int getPeriodId(Instant timestamp, Granularity granularity, Timeframe timeframe) {
        ZonedDateTime zdt = timestamp.atZone(TRADING_DAY_ZONE);
        Granularity resolvedGranularity = resolveGranularity(granularity, timeframe);
        
        switch (resolvedGranularity) {
            case WEEK: return zdt.get(WeekFields.of(Locale.US).weekOfYear());
            case MONTH: return zdt.getYear() * 100 + zdt.getMonthValue();
            case DAY:
            default: return zdt.getDayOfYear();
        }
    }

    private String getPeriodLabel(Instant timestamp, Granularity granularity, Timeframe timeframe) {
        ZonedDateTime zdt = timestamp.atZone(TRADING_DAY_ZONE);
        Granularity resolvedGranularity = resolveGranularity(granularity, timeframe);

        switch (resolvedGranularity) {
            case WEEK: return "W" + zdt.get(WeekFields.of(Locale.US).weekOfYear());
            case MONTH: return zdt.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
            case DAY:
            default: return zdt.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
        }
    }

    private Granularity resolveGranularity(Granularity granularity, Timeframe timeframe) {
        if (granularity != Granularity.ADAPTIVE) {
            return granularity;
        }
        if (timeframe == Timeframe.D1) return Granularity.MONTH;
        if (timeframe == Timeframe.H4) return Granularity.WEEK;
        return Granularity.DAY;
    }
}