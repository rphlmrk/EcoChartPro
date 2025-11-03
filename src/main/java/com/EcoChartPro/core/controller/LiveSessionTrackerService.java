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
 * A singleton service that tracks and calculates performance statistics for the
 * current, active replay session only. It provides real-time data for live UI widgets.
 * It also handles persisting the live session state.
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
    private static volatile LiveSessionTrackerService instance;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final Timer saveTimer;

    // --- Inner class to hold state for a session type ---
    private static class TrackerContext {
        List<Trade> sessionTrades = new ArrayList<>();
        int sessionWinStreak = 0;
        int sessionLossStreak = 0;
        int sessionDisciplineScore = 100;
        BigDecimal sessionInitialBalance = BigDecimal.ZERO;
    }

    private final Map<SessionType, TrackerContext> contexts = new EnumMap<>(SessionType.class);
    private SessionType activeSessionType = SessionType.REPLAY; // Default to REPLAY


    private LiveSessionTrackerService() {
        contexts.put(SessionType.REPLAY, new TrackerContext());
        contexts.put(SessionType.LIVE, new TrackerContext());
        // Register to listen to the core services to receive events
        PaperTradingService.getInstance().addPropertyChangeListener(this);
        ReplaySessionManager.getInstance().addListener(this);
        
        // Save every 30 seconds
        this.saveTimer = new Timer(30000, e -> saveLiveSessionState());
        this.saveTimer.setRepeats(true);
    }

    /**
     * Gets the singleton instance of the service.
     * @return The singleton instance.
     */
    public static LiveSessionTrackerService getInstance() {
        if (instance == null) {
            synchronized (LiveSessionTrackerService.class) {
                if (instance == null) {
                    instance = new LiveSessionTrackerService();
                }
            }
        }
        return instance;
    }

    private TrackerContext getActiveContext() {
        return contexts.get(activeSessionType);
    }

    /**
     * Switches the active session type (LIVE or REPLAY) this tracker is monitoring.
     * @param type The session type to activate.
     */
    public void setActiveSessionType(SessionType type) {
        if (type != null && this.activeSessionType != type) {
            logger.info("LiveSessionTrackerService active session type switched to: " + type);
            this.activeSessionType = type;
            // Recalculate and notify with the new context's data to update UI
            recalculateAndNotify();
        }
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
        if (activeSessionType != SessionType.LIVE) {
            return; // Only save when the live session is active
        }
        
        PaperTradingService tradingService = PaperTradingService.getInstance();
        
        // Only save if there is something to save (open positions, etc.)
        if (tradingService.hasAnyTradesOrPositions()) {
            ReplaySessionState liveState = tradingService.getCurrentSessionState();
            try {
                SessionManager.getInstance().saveLiveSession(liveState);
                logger.debug("Live session state auto-saved.");
            } catch (IOException e) {
                logger.error("Failed to auto-save live session state.", e);
            }
        }
    }

    /**
     * Resets all session-specific statistics to their initial state for the ACTIVE context.
     */
    private void reset() {
        TrackerContext context = getActiveContext();
        context.sessionTrades.clear();
        context.sessionWinStreak = 0;
        context.sessionLossStreak = 0;
        context.sessionDisciplineScore = 100;
        context.sessionInitialBalance = PaperTradingService.getInstance().getAccountBalance();

        // Notify listeners that the session has reset
        SessionStats initialStats = new SessionStats(
            BigDecimal.ZERO, 0.0, 0, 0, BigDecimal.ZERO, Duration.ZERO,
            Collections.singletonList(new JournalAnalysisService.EquityPoint(Instant.now(), context.sessionInitialBalance))
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
        TrackerContext context = getActiveContext();
        if (context.sessionTrades.isEmpty()) {
            reset(); 
            return;
        }

        // --- Recalculate All Statistics ---
        int totalTrades = context.sessionTrades.size();
        List<Trade> winners = context.sessionTrades.stream().filter(t -> t.profitAndLoss().signum() > 0).collect(Collectors.toList());
        List<Trade> losers = context.sessionTrades.stream().filter(t -> t.profitAndLoss().signum() < 0).collect(Collectors.toList());

        BigDecimal realizedPnl = context.sessionTrades.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
        double winRate = totalTrades > 0 ? (double) winners.size() / totalTrades : 0.0;
        
        BigDecimal grossProfit = winners.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss = losers.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add).abs();
        
        BigDecimal avgWin = winners.isEmpty() ? BigDecimal.ZERO : grossProfit.divide(new BigDecimal(winners.size()), 2, RoundingMode.HALF_UP);
        BigDecimal avgLoss = losers.isEmpty() ? BigDecimal.ZERO : grossLoss.divide(new BigDecimal(losers.size()), 2, RoundingMode.HALF_UP);

        BigDecimal avgRiskReward = avgLoss.signum() == 0 ? BigDecimal.ZERO : avgWin.divide(avgLoss, 2, RoundingMode.HALF_UP);

        long totalSeconds = context.sessionTrades.stream().mapToLong(t -> Duration.between(t.entryTime(), t.exitTime()).getSeconds()).sum();
        Duration avgTradeTime = totalTrades > 0 ? Duration.ofSeconds(totalSeconds / totalTrades) : Duration.ZERO;
        
        List<JournalAnalysisService.EquityPoint> equityCurve = new ArrayList<>();
        equityCurve.add(new JournalAnalysisService.EquityPoint(context.sessionTrades.get(0).entryTime().minusSeconds(1), context.sessionInitialBalance));
        BigDecimal cumulativePnl = BigDecimal.ZERO;
        for (Trade trade : context.sessionTrades) {
            cumulativePnl = cumulativePnl.add(trade.profitAndLoss());
            equityCurve.add(new JournalAnalysisService.EquityPoint(trade.exitTime(), context.sessionInitialBalance.add(cumulativePnl)));
        }

        Trade lastTrade = context.sessionTrades.get(totalTrades - 1);
        if (lastTrade.profitAndLoss().signum() > 0) {
            context.sessionWinStreak++;
            context.sessionLossStreak = 0;
        } else if (lastTrade.profitAndLoss().signum() < 0) {
            context.sessionWinStreak = 0;
            context.sessionLossStreak++;
        }

        SessionStats newStats = new SessionStats(
            realizedPnl, winRate, winners.size(), losers.size(), avgRiskReward, avgTradeTime, equityCurve
        );
        pcs.firePropertyChange("sessionStatsUpdated", null, newStats);
        pcs.firePropertyChange("sessionStreakUpdated", null, context.sessionWinStreak);
        pcs.firePropertyChange("sessionLossStreakUpdated", null, context.sessionLossStreak);
    }

    @Override
    public void onReplaySessionStart() {
        if (activeSessionType == SessionType.REPLAY) {
            reset();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        TrackerContext context = getActiveContext();

        if ("tradeHistoryUpdated".equals(propertyName)) {
            List<Trade> fullHistory = PaperTradingService.getInstance().getTradeHistory();
            if (fullHistory.size() > context.sessionTrades.size()) {
                Trade newTrade = fullHistory.get(fullHistory.size() - 1);
                context.sessionTrades.add(newTrade);
                recalculateAndNotify();
            } else if (fullHistory.size() < context.sessionTrades.size()) {
                context.sessionTrades = new ArrayList<>(fullHistory); 
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
                context.sessionDisciplineScore += totalPenalty;
                context.sessionDisciplineScore = Math.max(0, context.sessionDisciplineScore);
                pcs.firePropertyChange("disciplineScoreUpdated", null, context.sessionDisciplineScore);
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