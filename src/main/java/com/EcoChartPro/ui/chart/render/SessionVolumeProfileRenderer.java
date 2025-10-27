package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.BasicStroke;
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

    private record VolumeBar(BigDecimal upVolume, BigDecimal downVolume) {
        BigDecimal totalVolume() {
            return upVolume.add(downVolume);
        }
    }

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

        SettingsManager settings = SettingsManager.getInstance();
        int rowHeight = settings.getVrvpRowHeight();

        BigDecimal pricePerPixel = axis.getMaxPrice().subtract(axis.getMinPrice()).divide(BigDecimal.valueOf(g2d.getClipBounds().getHeight()), 8, RoundingMode.HALF_UP);
        if (pricePerPixel.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal priceStep = pricePerPixel.multiply(BigDecimal.valueOf(rowHeight));
        if (priceStep.compareTo(BigDecimal.ZERO) <= 0) return;

        // Build histogram with up/down separation
        Map<BigDecimal, VolumeBar> volumeHistogram = new TreeMap<>();
        for (KLine kline : sessionKlines) {
            boolean isUpBar = kline.close().compareTo(kline.open()) >= 0;
            long priceLevelsInBar = kline.high().subtract(kline.low()).divide(priceStep, 0, RoundingMode.UP).longValue();
            if (priceLevelsInBar == 0) priceLevelsInBar = 1;

            BigDecimal volumePerLevel = kline.volume().divide(BigDecimal.valueOf(priceLevelsInBar), 8, RoundingMode.HALF_UP);

            for (BigDecimal price = kline.low(); price.compareTo(kline.high()) <= 0; price = price.add(priceStep)) {
                BigDecimal binPrice = price.divide(priceStep, 0, RoundingMode.FLOOR).multiply(priceStep);
                volumeHistogram.compute(binPrice, (p, bar) -> {
                    if (bar == null) {
                        return isUpBar ? new VolumeBar(volumePerLevel, BigDecimal.ZERO) : new VolumeBar(BigDecimal.ZERO, volumePerLevel);
                    } else {
                        return isUpBar ? new VolumeBar(bar.upVolume.add(volumePerLevel), bar.downVolume) : new VolumeBar(bar.upVolume, bar.downVolume.add(volumePerLevel));
                    }
                });
            }
        }

        // Find max volume and POC for this session
        BigDecimal maxVolume = BigDecimal.ZERO;
        BigDecimal pocPrice = BigDecimal.ZERO;
        for (Map.Entry<BigDecimal, VolumeBar> entry : volumeHistogram.entrySet()) {
            if (entry.getValue().totalVolume().compareTo(maxVolume) > 0) {
                maxVolume = entry.getValue().totalVolume();
                pocPrice = entry.getKey();
            }
        }
        if (maxVolume.compareTo(BigDecimal.ZERO) <= 0) return;

        // Determine drawing area for this session
        Instant sessionStart = sessionKlines.get(0).timestamp();
        int startX = axis.timeToX(sessionStart, allVisibleKlines, timeframe);

        // Calculate session width based on number of bars in the session
        long barsInSession = sessionKlines.size();
        double barWidthPx = axis.getBarWidth();
        int sessionWidth = (int) (barsInSession * barWidthPx);
        
        int maxBarWidth = (int) (sessionWidth * PROFILE_WIDTH_RATIO);

        // Render histogram bars for this session
        for (Map.Entry<BigDecimal, VolumeBar> entry : volumeHistogram.entrySet()) {
            BigDecimal price = entry.getKey();
            VolumeBar bar = entry.getValue();
            BigDecimal totalVolume = bar.totalVolume();

            int y = axis.priceToY(price);
            
            int totalBarWidth = (int) (maxBarWidth * (totalVolume.doubleValue() / maxVolume.doubleValue()));
            int upVolumeWidth = (int) (totalBarWidth * (bar.upVolume.doubleValue() / totalVolume.doubleValue()));
            
            // Draw Up Volume part
            g2d.setColor(settings.getVrvpUpVolumeColor());
            g2d.fillRect(startX, y, upVolumeWidth, rowHeight);
            
            // Draw Down Volume part
            g2d.setColor(settings.getVrvpDownVolumeColor());
            g2d.fillRect(startX + upVolumeWidth, y, totalBarWidth - upVolumeWidth, rowHeight);

            // Highlight POC row
            if (price.equals(pocPrice)) {
                g2d.setStroke(settings.getVrvpPocLineStroke());
                g2d.setColor(settings.getVrvpPocColor());
                g2d.drawRect(startX, y, totalBarWidth, rowHeight);
            }
        }
    }
}