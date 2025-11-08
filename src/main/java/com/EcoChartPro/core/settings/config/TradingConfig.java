package com.EcoChartPro.core.settings.config;

import java.awt.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class TradingConfig implements Serializable {

    public enum TradingSession { ASIA, SYDNEY, LONDON, NEW_YORK }

    private BigDecimal commissionPerTrade = BigDecimal.ZERO;
    private BigDecimal simulatedSpreadPoints = BigDecimal.ZERO;
    private boolean autoJournalOnTradeClose = true;
    private boolean sessionHighlightingEnabled = true;
    private int tradeCandleRetentionMonths = 12;
    private int autoSaveInterval = 100;
    private List<String> tradeReplayAvailableTimeframes = new ArrayList<>(Arrays.asList("1m", "5m", "15m"));
    private List<String> favoriteSymbols = new ArrayList<>();
    private Map<TradingSession, Boolean> sessionEnabled = new EnumMap<>(TradingSession.class);
    private Map<TradingSession, LocalTime> sessionStartTimes = new EnumMap<>(TradingSession.class);
    private Map<TradingSession, LocalTime> sessionEndTimes = new EnumMap<>(TradingSession.class);
    private Map<TradingSession, Color> sessionColors = new EnumMap<>(TradingSession.class);

    public TradingConfig() {
        initializeDefaultSessionValues();
    }

    private void initializeDefaultSessionValues() {
        sessionStartTimes.put(TradingSession.ASIA, LocalTime.of(0, 0));
        sessionEndTimes.put(TradingSession.ASIA, LocalTime.of(9, 0));
        sessionColors.put(TradingSession.ASIA, new Color(0, 150, 136, 40));
        sessionEnabled.put(TradingSession.ASIA, true);

        sessionStartTimes.put(TradingSession.SYDNEY, LocalTime.of(22, 0));
        sessionEndTimes.put(TradingSession.SYDNEY, LocalTime.of(7, 0));
        sessionColors.put(TradingSession.SYDNEY, new Color(255, 82, 82, 40));
        sessionEnabled.put(TradingSession.SYDNEY, true);

        sessionStartTimes.put(TradingSession.LONDON, LocalTime.of(8, 0));
        sessionEndTimes.put(TradingSession.LONDON, LocalTime.of(17, 0));
        sessionColors.put(TradingSession.LONDON, new Color(33, 150, 243, 40));
        sessionEnabled.put(TradingSession.LONDON, true);

        sessionStartTimes.put(TradingSession.NEW_YORK, LocalTime.of(13, 0));
        sessionEndTimes.put(TradingSession.NEW_YORK, LocalTime.of(22, 0));
        sessionColors.put(TradingSession.NEW_YORK, new Color(255, 193, 7, 40));
        sessionEnabled.put(TradingSession.NEW_YORK, true);
    }
    
    public boolean isFavoriteSymbol(String symbol) {
        return favoriteSymbols.contains(symbol);
    }

    public boolean addFavoriteSymbol(String symbol) {
        if (!favoriteSymbols.contains(symbol)) {
            return favoriteSymbols.add(symbol);
        }
        return false;
    }

    public boolean removeFavoriteSymbol(String symbol) {
        return favoriteSymbols.remove(symbol);
    }

    // --- Getters and Setters ---

    public BigDecimal getCommissionPerTrade() { return commissionPerTrade; }
    public void setCommissionPerTrade(BigDecimal commissionPerTrade) { this.commissionPerTrade = commissionPerTrade; }

    public BigDecimal getSimulatedSpreadPoints() { return simulatedSpreadPoints; }
    public void setSimulatedSpreadPoints(BigDecimal simulatedSpreadPoints) { this.simulatedSpreadPoints = simulatedSpreadPoints; }

    public boolean isAutoJournalOnTradeClose() { return autoJournalOnTradeClose; }
    public void setAutoJournalOnTradeClose(boolean autoJournalOnTradeClose) { this.autoJournalOnTradeClose = autoJournalOnTradeClose; }

    public boolean isSessionHighlightingEnabled() { return sessionHighlightingEnabled; }
    public void setSessionHighlightingEnabled(boolean sessionHighlightingEnabled) { this.sessionHighlightingEnabled = sessionHighlightingEnabled; }

    public int getTradeCandleRetentionMonths() { return tradeCandleRetentionMonths; }
    public void setTradeCandleRetentionMonths(int tradeCandleRetentionMonths) { this.tradeCandleRetentionMonths = tradeCandleRetentionMonths; }

    public int getAutoSaveInterval() { return autoSaveInterval; }
    public void setAutoSaveInterval(int autoSaveInterval) { this.autoSaveInterval = autoSaveInterval; }

    public List<String> getTradeReplayAvailableTimeframes() { return tradeReplayAvailableTimeframes; }
    public void setTradeReplayAvailableTimeframes(List<String> tradeReplayAvailableTimeframes) { this.tradeReplayAvailableTimeframes = tradeReplayAvailableTimeframes; }

    public List<String> getFavoriteSymbols() { return favoriteSymbols; }
    public void setFavoriteSymbols(List<String> favoriteSymbols) { this.favoriteSymbols = favoriteSymbols; }

    public Map<TradingSession, Boolean> getSessionEnabled() { return sessionEnabled; }
    public void setSessionEnabled(Map<TradingSession, Boolean> sessionEnabled) { this.sessionEnabled = sessionEnabled; }

    public Map<TradingSession, LocalTime> getSessionStartTimes() { return sessionStartTimes; }
    public void setSessionStartTimes(Map<TradingSession, LocalTime> sessionStartTimes) { this.sessionStartTimes = sessionStartTimes; }

    public Map<TradingSession, LocalTime> getSessionEndTimes() { return sessionEndTimes; }
    public void setSessionEndTimes(Map<TradingSession, LocalTime> sessionEndTimes) { this.sessionEndTimes = sessionEndTimes; }

    public Map<TradingSession, Color> getSessionColors() { return sessionColors; }
    public void setSessionColors(Map<TradingSession, Color> sessionColors) { this.sessionColors = sessionColors; }
}