package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.Color;
import java.awt.Graphics2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SessionVolumeProfileRenderer {

    private static final Color PROFILE_COLOR = new Color(158, 158, 158, 50);
    private static final Color POC_COLOR = new Color(255, 255, 255, 70);
    private static final double PROFILE_WIDTH_RATIO = 0.8; // 80% of the session's width

    public void draw(Graphics2D g2d, ChartAxis axis, ChartDataModel dataModel) {
        List<KLine> visibleKlines = dataModel.getVisibleKLines();
        if (!axis.isConfigured() || visibleKlines.isEmpty()) {
            return;
        }

        ZoneId zoneId = SettingsManager.getInstance().getDisplayZoneId();
        Timeframe timeframe = dataModel.getCurrentDisplayTimeframe();

        // 1. Identify session boundaries within the visible range
        LocalDate currentDay = null;
        List<List<KLine>> sessions = new ArrayList<>();
        List<KLine> currentSessionKlines = new ArrayList<>();

        for (KLine kline : visibleKlines) {
            LocalDate klineDay = kline.timestamp().atZone(zoneId).toLocalDate();
            if (currentDay == null) {
                currentDay = klineDay;
            }

            if (!klineDay.equals(currentDay)) {
                if (!currentSessionKlines.isEmpty()) {
                    sessions.add(new ArrayList<>(currentSessionKlines));
                }
                currentSessionKlines.clear();
                currentDay = klineDay;
            }
            currentSessionKlines.add(kline);
        }
        if (!currentSessionKlines.isEmpty()) {
            sessions.add(currentSessionKlines);
        }

        // 2. For each session, calculate and draw its profile
        for (List<KLine> sessionKlines : sessions) {
            drawProfileForSession(g2d, axis, sessionKlines, timeframe, visibleKlines);
        }
    }

    private void drawProfileForSession(Graphics2D g2d, ChartAxis axis, List<KLine> sessionKlines, Timeframe timeframe, List<KLine> allVisibleKlines) {
        if (sessionKlines.isEmpty()) return;

        BigDecimal pricePerPixel = axis.getMaxPrice().subtract(axis.getMinPrice()).divide(BigDecimal.valueOf(g2d.getClipBounds().getHeight()), 8, RoundingMode.HALF_UP);
        if (pricePerPixel.compareTo(BigDecimal.ZERO) <= 0) return;

        // Build histogram for this session
        Map<BigDecimal, BigDecimal> volumeHistogram = new TreeMap<>();
        for (KLine kline : sessionKlines) {
            for (BigDecimal price = kline.low(); price.compareTo(kline.high()) <= 0; price = price.add(pricePerPixel)) {
                BigDecimal binPrice = price.divide(pricePerPixel, 0, RoundingMode.HALF_UP).multiply(pricePerPixel);
                volumeHistogram.merge(binPrice, kline.volume(), BigDecimal::add);
            }
        }

        // Find max volume and POC for this session
        BigDecimal maxVolume = BigDecimal.ZERO;
        BigDecimal pocPrice = BigDecimal.ZERO;
        for (Map.Entry<BigDecimal, BigDecimal> entry : volumeHistogram.entrySet()) {
            if (entry.getValue().compareTo(maxVolume) > 0) {
                maxVolume = entry.getValue();
                pocPrice = entry.getKey();
            }
        }
        if (maxVolume.compareTo(BigDecimal.ZERO) <= 0) return;

        // Determine drawing area for this session
        Instant sessionStart = sessionKlines.get(0).timestamp();
        Instant sessionEnd = sessionKlines.get(sessionKlines.size() - 1).timestamp();
        int startX = axis.timeToX(sessionStart, allVisibleKlines, timeframe);
        int endX = axis.timeToX(sessionEnd, allVisibleKlines, timeframe);
        int sessionWidth = endX - startX;
        if (sessionWidth <= 0) return;
        
        int maxBarWidth = (int) (sessionWidth * PROFILE_WIDTH_RATIO);

        // Render histogram bars for this session
        for (Map.Entry<BigDecimal, BigDecimal> entry : volumeHistogram.entrySet()) {
            BigDecimal price = entry.getKey();
            int barWidth = (int) (maxBarWidth * (entry.getValue().doubleValue() / maxVolume.doubleValue()));
            int y = axis.priceToY(price);

            g2d.setColor(price.equals(pocPrice) ? POC_COLOR : PROFILE_COLOR);
            g2d.fillRect(startX, y, barWidth, 1);
        }
    }
}