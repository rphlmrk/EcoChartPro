package com.EcoChartPro.core.controller;

import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.core.trading.SessionType;
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

    private static volatile LiveSessionTrackerService instance;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    // --- NEW: Inner class to hold state for a session type ---
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
     * [NEW] Switches the active session type (LIVE or REPLAY) this tracker is monitoring.
     * @param type The session type to activate.
     */
    public void setActiveSessionType(SessionType type) {
        if (type != null && this.activeSessionType != type) {
            System.out.println("LiveSessionTrackerService active session type switched to: " + type);
            this.activeSessionType = type;
            // Recalculate and notify with the new context's data to update UI
            recalculateAndNotify();
        }
    }

    /**
     * Resets all session-specific statistics to their initial state for the ACTIVE context.
     * This is called automatically when a new replay session starts.
     */
    private void reset() {
        TrackerContext context = getActiveContext();
        context.sessionTrades.clear();
        context.sessionWinStreak = 0;
        context.sessionLossStreak = 0;
        context.sessionDisciplineScore = 100;
        context.sessionInitialBalance = PaperTradingService.getInstance().getAccountBalance();

        // Notify listeners that the session has reset to its initial "zeroed-out" state
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
     * The core engine of the service. It recalculates all session statistics based on the
     * current list of session trades and then broadcasts the new data to any UI listeners.
     */
    private void recalculateAndNotify() {
        TrackerContext context = getActiveContext();
        if (context.sessionTrades.isEmpty()) {
            reset(); // Ensure a clean state if trades are removed or the list becomes empty
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
        
        // --- Generate Session Equity Curve ---
        List<JournalAnalysisService.EquityPoint> equityCurve = new ArrayList<>();
        equityCurve.add(new JournalAnalysisService.EquityPoint(context.sessionTrades.get(0).entryTime().minusSeconds(1), context.sessionInitialBalance));
        BigDecimal cumulativePnl = BigDecimal.ZERO;
        for (Trade trade : context.sessionTrades) {
            cumulativePnl = cumulativePnl.add(trade.profitAndLoss());
            equityCurve.add(new JournalAnalysisService.EquityPoint(trade.exitTime(), context.sessionInitialBalance.add(cumulativePnl)));
        }

        // --- Update Session Win/Loss Streaks ---
        Trade lastTrade = context.sessionTrades.get(totalTrades - 1);
        if (lastTrade.profitAndLoss().signum() > 0) {
            context.sessionWinStreak++;
            context.sessionLossStreak = 0;
        } else if (lastTrade.profitAndLoss().signum() < 0) {
            context.sessionWinStreak = 0;
            context.sessionLossStreak++;
        }
        // (No change for break-even trades)

        // --- Broadcast Updated Data ---
        SessionStats newStats = new SessionStats(
            realizedPnl, winRate, winners.size(), losers.size(), avgRiskReward, avgTradeTime, equityCurve
        );
        pcs.firePropertyChange("sessionStatsUpdated", null, newStats);
        pcs.firePropertyChange("sessionStreakUpdated", null, context.sessionWinStreak);
        pcs.firePropertyChange("sessionLossStreakUpdated", null, context.sessionLossStreak);
    }


    // --- Listener Implementations ---

    /**
     * Called by {@link ReplaySessionManager} when a new replay session is started.
     */
    @Override
    public void onReplaySessionStart() {
        // This is called by ReplaySessionManager, so we assume the active context
        // should be REPLAY. The SessionController is responsible for setting this.
        reset();
    }

    /**
     * Listens for events from {@link PaperTradingService}.
     * @param evt The event object.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        TrackerContext context = getActiveContext();

        // A new trade has been closed and added to the history
        if ("tradeHistoryUpdated".equals(propertyName)) {
            List<Trade> fullHistory = PaperTradingService.getInstance().getTradeHistory();
            if (fullHistory.size() > context.sessionTrades.size()) {
                Trade newTrade = fullHistory.get(fullHistory.size() - 1);
                context.sessionTrades.add(newTrade);
                recalculateAndNotify();
            } else if (fullHistory.size() < context.sessionTrades.size()) {
                // Handle potential future cases like trade deletion by re-syncing
                context.sessionTrades = new ArrayList<>(fullHistory); 
                recalculateAndNotify();
            }
        }
        
        // The user has logged mistakes in their journal for a completed trade
        if ("mistakeLogged".equals(propertyName) && evt.getNewValue() instanceof Trade) {
            Trade tradeWithMistakes = (Trade) evt.getNewValue();
            int totalPenalty = 0;
            if (tradeWithMistakes.identifiedMistakes() != null) {
                for (String mistake : tradeWithMistakes.identifiedMistakes()) {
                    // Get the penalty from GamificationService's public map.
                    // The scores are negative, so we add them to decrease the discipline score.
                    totalPenalty += GamificationService.XP_SCORES.getOrDefault(mistake, 0);
                }
            }
            if (totalPenalty < 0) {
                context.sessionDisciplineScore += totalPenalty;
                context.sessionDisciplineScore = Math.max(0, context.sessionDisciplineScore); // Clamp at zero
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
    
    // --- Unused listener methods from ReplayStateListener ---
    @Override
    public void onReplayTick(KLine newM1Bar) { /* No action needed on every bar tick */ }
    @Override
    public void onReplayStateChanged() { /* No action needed for play/pause state changes */ }
}