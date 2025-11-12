package com.EcoChartPro.core.controller;

import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.core.trading.SessionType;
import com.EcoChartPro.utils.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.utils.DataSourceManager.ChartDataSource;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A service that tracks and calculates performance statistics for a single session.
 * It provides real-time data for live UI widgets and handles persisting the live session state.
 */
public final class LiveSessionTrackerService implements ReplayStateListener, PropertyChangeListener {

    /**
     * A Data Transfer Object for broadcasting all key session statistics at once.
     */
    public record SessionStats(
        BigDecimal realizedPnl,
        double winRate,
        int winCount,
        int lossCount,
        BigDecimal avgRiskReward,
        Duration avgTradeTime,
        List<JournalAnalysisService.EquityPoint> equityCurve
    ) {}

    private static final Logger logger = LoggerFactory.getLogger(LiveSessionTrackerService.class);
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final Timer saveTimer;
    private final PaperTradingService paperTradingService;

    // --- State is now held per-instance, representing a single session context ---
    private List<Trade> sessionTrades = new ArrayList<>();
    private int sessionWinStreak = 0;
    private int sessionLossStreak = 0;
    private int sessionDisciplineScore = 100;
    private BigDecimal sessionInitialBalance = BigDecimal.ZERO;

    public LiveSessionTrackerService(PaperTradingService paperTradingService) {
        this.paperTradingService = paperTradingService;
        
        // Save every 30 seconds
        this.saveTimer = new Timer(30000, e -> saveLiveSessionState());
        this.saveTimer.setRepeats(true);
    }

    public void start() {
        if (!saveTimer.isRunning()) {
            logger.info("Starting live session tracker.");
            saveTimer.start();
        }
    }

    public void stop() {
        if (saveTimer.isRunning()) {
            logger.info("Stopping live session tracker.");
            saveTimer.stop();
            // Perform one final save on stop
            saveLiveSessionState();
        }
    }

    private void saveLiveSessionState() {
        // This service instance might be for a replay session. We only save if it's a live session.
        if (ReplaySessionManager.getInstance().getActiveSymbol() != null) {
            return; // It's a replay session, do not save.
        }
        
        ReplaySessionState liveState = paperTradingService.getCurrentSessionState();

        if (liveState != null && liveState.lastActiveSymbol() == null) {
            ChartDataSource source = LiveWindowManager.getInstance().getActiveDataSource();
            if (source != null) {
                liveState = new ReplaySessionState(
                    liveState.accountBalance(),
                    source.symbol(),
                    liveState.symbolStates()
                );
                logger.warn("Corrected a null lastActiveSymbol in the live session state before saving. The new symbol is '{}'.", source.symbol());
            }
        }

        if (liveState == null) {
            logger.error("Failed to get current session state from PaperTradingService. Live session auto-save skipped.");
            return;
        }

        try {
            SessionManager.getInstance().saveLiveSession(liveState);
            logger.debug("Live session state auto-saved.");
        } catch (IOException e) {
            logger.error("Failed to auto-save live session state.", e);
        }
    }

    /**
     * Resets all session-specific statistics to their initial state.
     */
    public void reset() {
        this.sessionTrades.clear();
        this.sessionWinStreak = 0;
        this.sessionLossStreak = 0;
        this.sessionDisciplineScore = 100;
        this.sessionInitialBalance = paperTradingService.getAccountBalance();

        // Notify listeners that the session has reset
        SessionStats initialStats = new SessionStats(
            BigDecimal.ZERO, 0.0, 0, 0, BigDecimal.ZERO, Duration.ZERO,
            Collections.singletonList(new JournalAnalysisService.EquityPoint(Instant.now(), this.sessionInitialBalance))
        );
        pcs.firePropertyChange("sessionStatsUpdated", null, initialStats);
        pcs.firePropertyChange("sessionStreakUpdated", null, 0);
        pcs.firePropertyChange("sessionLossStreakUpdated", null, 0);
        pcs.firePropertyChange("disciplineScoreUpdated", null, 100);
    }
    
    /**
     * Recalculates all session statistics and broadcasts the new data.
     */
    private void recalculateAndNotify() {
        if (this.sessionTrades.isEmpty()) {
            reset(); 
            return;
        }

        // --- Recalculate All Statistics ---
        int totalTrades = this.sessionTrades.size();
        List<Trade> winners = this.sessionTrades.stream().filter(t -> t.profitAndLoss().signum() > 0).collect(Collectors.toList());
        List<Trade> losers = this.sessionTrades.stream().filter(t -> t.profitAndLoss().signum() < 0).collect(Collectors.toList());

        BigDecimal realizedPnl = this.sessionTrades.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
        double winRate = totalTrades > 0 ? (double) winners.size() / totalTrades : 0.0;
        
        BigDecimal grossProfit = winners.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss = losers.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add).abs();
        
        BigDecimal avgWin = winners.isEmpty() ? BigDecimal.ZERO : grossProfit.divide(new BigDecimal(winners.size()), 2, RoundingMode.HALF_UP);
        BigDecimal avgLoss = losers.isEmpty() ? BigDecimal.ZERO : grossLoss.divide(new BigDecimal(losers.size()), 2, RoundingMode.HALF_UP);

        BigDecimal avgRiskReward = avgLoss.signum() == 0 ? BigDecimal.ZERO : avgWin.divide(avgLoss, 2, RoundingMode.HALF_UP);

        long totalSeconds = this.sessionTrades.stream().mapToLong(t -> Duration.between(t.entryTime(), t.exitTime()).getSeconds()).sum();
        Duration avgTradeTime = totalTrades > 0 ? Duration.ofSeconds(totalSeconds / totalTrades) : Duration.ZERO;
        
        List<JournalAnalysisService.EquityPoint> equityCurve = new ArrayList<>();
        equityCurve.add(new JournalAnalysisService.EquityPoint(this.sessionTrades.get(0).entryTime().minusSeconds(1), this.sessionInitialBalance));
        BigDecimal cumulativePnl = BigDecimal.ZERO;
        for (Trade trade : this.sessionTrades) {
            cumulativePnl = cumulativePnl.add(trade.profitAndLoss());
            equityCurve.add(new JournalAnalysisService.EquityPoint(trade.exitTime(), this.sessionInitialBalance.add(cumulativePnl)));
        }

        Trade lastTrade = this.sessionTrades.get(totalTrades - 1);
        if (lastTrade.profitAndLoss().signum() > 0) {
            this.sessionWinStreak++;
            this.sessionLossStreak = 0;
        } else if (lastTrade.profitAndLoss().signum() < 0) {
            this.sessionWinStreak = 0;
            this.sessionLossStreak++;
        }

        SessionStats newStats = new SessionStats(
            realizedPnl, winRate, winners.size(), losers.size(), avgRiskReward, avgTradeTime, equityCurve
        );
        pcs.firePropertyChange("sessionStatsUpdated", null, newStats);
        pcs.firePropertyChange("sessionStreakUpdated", null, this.sessionWinStreak);
        pcs.firePropertyChange("sessionLossStreakUpdated", null, this.sessionLossStreak);
    }

    @Override
    public void onReplaySessionStart() {
        reset();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();

        if ("tradeHistoryUpdated".equals(propertyName)) {
            List<Trade> fullHistory = paperTradingService.getTradeHistory();
            if (fullHistory.size() > this.sessionTrades.size()) {
                Trade newTrade = fullHistory.get(fullHistory.size() - 1);
                this.sessionTrades.add(newTrade);
                recalculateAndNotify();
            } else if (fullHistory.size() < this.sessionTrades.size()) {
                this.sessionTrades = new ArrayList<>(fullHistory); 
                recalculateAndNotify();
            }
        }
        
        if ("mistakeLogged".equals(propertyName) && evt.getNewValue() instanceof Trade) {
            Trade tradeWithMistakes = (Trade) evt.getNewValue();
            int totalPenalty = 0;
            if (tradeWithMistakes.identifiedMistakes() != null) {
                for (String mistake : tradeWithMistakes.identifiedMistakes()) {
                    totalPenalty += GamificationService.XP_SCORES.getOrDefault(mistake, 0);
                }
            }
            if (totalPenalty < 0) {
                this.sessionDisciplineScore += totalPenalty;
                this.sessionDisciplineScore = Math.max(0, this.sessionDisciplineScore);
                pcs.firePropertyChange("disciplineScoreUpdated", null, this.sessionDisciplineScore);
            }
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }
    
    @Override
    public void onReplayTick(KLine newM1Bar) {}
    @Override
    public void onReplayStateChanged() {}
}