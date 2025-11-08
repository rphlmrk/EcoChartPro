package com.EcoChartPro.ui.chart.render;

import com.EcoChartPro.core.settings.SettingsService;
import com.EcoChartPro.core.settings.config.DisciplineCoachConfig;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import com.EcoChartPro.ui.chart.axis.ChartAxis;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A renderer responsible for drawing visual indicators on the chart
 * for the start and end of a user's peak performance windows, with multiple display styles.
 */
public class PeakHoursRenderer {

    private static final int INDICATOR_HEIGHT = 8;
    private static final int INDICATOR_WIDTH = 8;

    /** A private record to represent a contiguous block of hours. */
    private record TimeWindow(int startHour, int endHour) {}

    /**
     * Main drawing method. It reads the user's preferred style from settings
     * and calls the appropriate drawing helper method.
     */
    public void draw(Graphics2D g, ChartAxis axis, List<KLine> visibleKLines, Timeframe timeframe, List<Integer> peakHours) {
        if (visibleKLines == null || visibleKLines.isEmpty() || peakHours == null || peakHours.isEmpty() || !axis.isConfigured()) {
            return;
        }

        SettingsService settings = SettingsService.getInstance();
        DisciplineCoachConfig.PeakHoursDisplayStyle style = settings.getPeakHoursDisplayStyle();

        switch (style) {
            case SHADE_AREA:
                drawShadeArea(g, axis, visibleKLines, peakHours);
                break;
            case INDICATOR_LINES:
                drawIndicatorLines(g, axis, visibleKLines, timeframe, peakHours);
                break;
            case BOTTOM_BAR:
                drawBottomBar(g, axis, visibleKLines, timeframe, peakHours);
                break;
        }
    }

    private void drawShadeArea(Graphics2D g, ChartAxis axis, List<KLine> visibleKLines, List<Integer> peakHours) {
        Set<Integer> peakHoursSet = new HashSet<>(peakHours);
        g.setColor(SettingsService.getInstance().getPeakHoursColorShade());
        int barWidth = (int) Math.max(1.0, axis.getBarWidth());

        for (int i = 0; i < visibleKLines.size(); i++) {
            KLine kline = visibleKLines.get(i);
            int hour = kline.timestamp().atZone(ZoneOffset.UTC).getHour();
            if (peakHoursSet.contains(hour)) {
                int x = axis.slotToX(i);
                g.fillRect(x, 0, barWidth, g.getClipBounds().height);
            }
        }
    }

    private void drawBottomBar(Graphics2D g, ChartAxis axis, List<KLine> visibleKLines, Timeframe timeframe, List<Integer> peakHours) {
        List<TimeWindow> windows = parseHoursIntoWindows(peakHours);
        g.setColor(SettingsService.getInstance().getPeakHoursColorShade());
        LocalDate startDate = visibleKLines.get(0).timestamp().atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);
        LocalDate endDate = visibleKLines.get(visibleKLines.size() - 1).timestamp().atZone(ZoneOffset.UTC).toLocalDate().plusDays(1);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            for (TimeWindow window : windows) {
                ZonedDateTime windowStart = date.atStartOfDay(ZoneOffset.UTC).withHour(window.startHour());
                ZonedDateTime windowEnd = date.atStartOfDay(ZoneOffset.UTC).withHour(window.endHour()).plusHours(1);
                if (window.endHour() < window.startHour()) windowEnd = windowEnd.plusDays(1);

                int xStart = axis.timeToX(windowStart.toInstant(), visibleKLines, timeframe);
                int xEnd = axis.timeToX(windowEnd.toInstant(), visibleKLines, timeframe);
                
                // If the entire window is off-screen to the left, xEnd will be valid but xStart won't.
                if (xStart == -1 && xEnd > 0) xStart = 0;
                // If the entire window is off-screen to the right, xStart will be valid but xEnd won't.
                if (xEnd == -1 && xStart < g.getClipBounds().width) xEnd = g.getClipBounds().width;

                if (xStart != -1 && xEnd != -1) {
                    int barHeight = SettingsService.getInstance().getPeakHoursBottomBarHeight();
                    int y = g.getClipBounds().height - barHeight;
                    g.fillRect(xStart, y, xEnd - xStart, barHeight);
                }
            }
        }
    }

    private void drawIndicatorLines(Graphics2D g, ChartAxis axis, List<KLine> visibleKLines, Timeframe timeframe, List<Integer> peakHours) {
        List<TimeWindow> windows = parseHoursIntoWindows(peakHours);
        LocalDate startDate = visibleKLines.get(0).timestamp().atZone(ZoneOffset.UTC).toLocalDate().minusDays(1);
        LocalDate endDate = visibleKLines.get(visibleKLines.size() - 1).timestamp().atZone(ZoneOffset.UTC).toLocalDate().plusDays(1);

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            for (TimeWindow window : windows) {
                ZonedDateTime windowStart = date.atStartOfDay(ZoneOffset.UTC).withHour(window.startHour());
                ZonedDateTime windowEnd = date.atStartOfDay(ZoneOffset.UTC).withHour(window.endHour()).plusHours(1);
                if (window.endHour() < window.startHour()) windowEnd = windowEnd.plusDays(1);

                int xStart = axis.timeToX(windowStart.toInstant(), visibleKLines, timeframe);
                int xEnd = axis.timeToX(windowEnd.toInstant(), visibleKLines, timeframe);

                if (xStart != -1) drawIndicator(g, xStart, SettingsService.getInstance().getPeakHoursColorStart(), true);
                if (xEnd != -1) drawIndicator(g, xEnd, SettingsService.getInstance().getPeakHoursColorEnd(), false);
            }
        }
    }

    private void drawIndicator(Graphics2D g, int x, Color color, boolean isStart) {
        int chartHeight = g.getClipBounds().height;
        g.setColor(color);

        // Draw dashed vertical line
        Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{2}, 0);
        Stroke solid = g.getStroke();
        g.setStroke(dashed);
        g.drawLine(x, 0, x, chartHeight);
        g.setStroke(solid);

        // Draw triangle indicator at the bottom
        Polygon triangle = new Polygon();
        int yBase = chartHeight;
        int yTip = chartHeight - INDICATOR_HEIGHT;
        if (isStart) {
            triangle.addPoint(x, yTip);
            triangle.addPoint(x - (INDICATOR_WIDTH / 2), yBase);
            triangle.addPoint(x + (INDICATOR_WIDTH / 2), yBase);
        } else {
            triangle.addPoint(x, yBase);
            triangle.addPoint(x - (INDICATOR_WIDTH / 2), yTip);
            triangle.addPoint(x + (INDICATOR_WIDTH / 2), yTip);
        }
        g.fill(triangle);
    }
    
    private List<TimeWindow> parseHoursIntoWindows(List<Integer> hours) {
        if (hours == null || hours.isEmpty()) return Collections.emptyList();
        Set<Integer> hoursSet = new HashSet<>(hours);
        List<TimeWindow> windows = new ArrayList<>();
        Set<Integer> processedHours = new HashSet<>();
        List<Integer> sortedHours = hours.stream().sorted().collect(Collectors.toList());
        for (Integer startHour : sortedHours) {
            if (processedHours.contains(startHour)) continue;
            processedHours.add(startHour);
            int endHour = startHour;
            int nextHour = (startHour + 1) % 24;
            while (hoursSet.contains(nextHour) && !processedHours.contains(nextHour)) {
                endHour = nextHour;
                processedHours.add(endHour);
                nextHour = (endHour + 1) % 24;
            }
            windows.add(new TimeWindow(startHour, endHour));
        }
        return windows;
    }
}