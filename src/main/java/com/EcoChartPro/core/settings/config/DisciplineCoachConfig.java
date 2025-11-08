package com.EcoChartPro.core.settings.config;

import java.awt.*;
import java.io.Serializable;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DisciplineCoachConfig implements Serializable {

    public enum PeakHoursDisplayStyle {
        SHADE_AREA("Shade Background Area"),
        INDICATOR_LINES("Start/End Indicator Lines"),
        BOTTOM_BAR("Bar Along Time Axis");
        private final String displayName; PeakHoursDisplayStyle(String s) { this.displayName = s; } @Override public String toString() { return displayName; }
    }

    private boolean disciplineCoachEnabled = true;
    private int optimalTradeCountOverride = -1;
    private boolean overtrainingNudgeEnabled = true;
    private boolean fatigueNudgeEnabled = true;
    private boolean winStreakNudgeEnabled = true;
    private boolean lossStreakNudgeEnabled = true;
    private LocalTime fastForwardTime = LocalTime.of(0, 0);
    private boolean showPeakHoursLines = true;
    private PeakHoursDisplayStyle peakHoursDisplayStyle = PeakHoursDisplayStyle.INDICATOR_LINES;
    private Color peakHoursColorShade = new Color(76, 175, 80, 20);
    private Color peakHoursColorStart = new Color(76, 175, 80, 150);
    private Color peakHoursColorEnd = new Color(0, 150, 136, 150);
    private int peakHoursBottomBarHeight = 4;
    private List<Integer> peakPerformanceHoursOverride = new ArrayList<>();
    private List<TradingConfig.TradingSession> preferredTradingSessions = new ArrayList<>(Arrays.asList(TradingConfig.TradingSession.LONDON, TradingConfig.TradingSession.NEW_YORK));

    // --- Getters and Setters ---

    public boolean isDisciplineCoachEnabled() { return disciplineCoachEnabled; }
    public void setDisciplineCoachEnabled(boolean disciplineCoachEnabled) { this.disciplineCoachEnabled = disciplineCoachEnabled; }

    public int getOptimalTradeCountOverride() { return optimalTradeCountOverride; }
    public void setOptimalTradeCountOverride(int optimalTradeCountOverride) { this.optimalTradeCountOverride = optimalTradeCountOverride; }

    public boolean isOvertrainingNudgeEnabled() { return overtrainingNudgeEnabled; }
    public void setOvertrainingNudgeEnabled(boolean overtrainingNudgeEnabled) { this.overtrainingNudgeEnabled = overtrainingNudgeEnabled; }

    public boolean isFatigueNudgeEnabled() { return fatigueNudgeEnabled; }
    public void setFatigueNudgeEnabled(boolean fatigueNudgeEnabled) { this.fatigueNudgeEnabled = fatigueNudgeEnabled; }

    public boolean isWinStreakNudgeEnabled() { return winStreakNudgeEnabled; }
    public void setWinStreakNudgeEnabled(boolean winStreakNudgeEnabled) { this.winStreakNudgeEnabled = winStreakNudgeEnabled; }

    public boolean isLossStreakNudgeEnabled() { return lossStreakNudgeEnabled; }
    public void setLossStreakNudgeEnabled(boolean lossStreakNudgeEnabled) { this.lossStreakNudgeEnabled = lossStreakNudgeEnabled; }

    public LocalTime getFastForwardTime() { return fastForwardTime; }
    public void setFastForwardTime(LocalTime fastForwardTime) { this.fastForwardTime = fastForwardTime; }

    public boolean isShowPeakHoursLines() { return showPeakHoursLines; }
    public void setShowPeakHoursLines(boolean showPeakHoursLines) { this.showPeakHoursLines = showPeakHoursLines; }

    public PeakHoursDisplayStyle getPeakHoursDisplayStyle() { return peakHoursDisplayStyle; }
    public void setPeakHoursDisplayStyle(PeakHoursDisplayStyle peakHoursDisplayStyle) { this.peakHoursDisplayStyle = peakHoursDisplayStyle; }

    public Color getPeakHoursColorShade() { return peakHoursColorShade; }
    public void setPeakHoursColorShade(Color peakHoursColorShade) { this.peakHoursColorShade = peakHoursColorShade; }

    public Color getPeakHoursColorStart() { return peakHoursColorStart; }
    public void setPeakHoursColorStart(Color peakHoursColorStart) { this.peakHoursColorStart = peakHoursColorStart; }

    public Color getPeakHoursColorEnd() { return peakHoursColorEnd; }
    public void setPeakHoursColorEnd(Color peakHoursColorEnd) { this.peakHoursColorEnd = peakHoursColorEnd; }

    public int getPeakHoursBottomBarHeight() { return peakHoursBottomBarHeight; }
    public void setPeakHoursBottomBarHeight(int peakHoursBottomBarHeight) { this.peakHoursBottomBarHeight = peakHoursBottomBarHeight; }

    public List<Integer> getPeakPerformanceHoursOverride() { return peakPerformanceHoursOverride; }
    public void setPeakPerformanceHoursOverride(List<Integer> hours) {
        this.peakPerformanceHoursOverride = hours.stream().sorted().collect(Collectors.toList());
    }

    public List<TradingConfig.TradingSession> getPreferredTradingSessions() { return preferredTradingSessions; }
    public void setPreferredTradingSessions(List<TradingConfig.TradingSession> preferredTradingSessions) { this.preferredTradingSessions = preferredTradingSessions; }
}