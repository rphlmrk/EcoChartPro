package com.EcoChartPro.core.controller;

import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.trading.PaperTradingService;
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
import java.util.List;
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

    // --- Session-specific state variables ---
    private List<Trade> sessionTrades = new ArrayList<>();
    private int sessionWinStreak = 0;
    private int sessionLossStreak = 0;
    private int sessionDisciplineScore = 100;
    private BigDecimal sessionInitialBalance = BigDecimal.ZERO;


    private LiveSessionTrackerService() {
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

    /**
     * Resets all session-specific statistics to their initial state.
     * This is called automatically when a new replay session starts.
     */
    private void reset() {
        this.sessionTrades.clear();
        this.sessionWinStreak = 0;
        this.sessionLossStreak = 0;
        this.sessionDisciplineScore = 100;
        this.sessionInitialBalance = PaperTradingService.getInstance().getAccountBalance();

        // Notify listeners that the session has reset to its initial "zeroed-out" state
        SessionStats initialStats = new SessionStats(
            BigDecimal.ZERO, 0.0, 0, 0, BigDecimal.ZERO, Duration.ZERO,
            Collections.singletonList(new JournalAnalysisService.EquityPoint(Instant.now(), sessionInitialBalance))
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
        if (sessionTrades.isEmpty()) {
            reset(); // Ensure a clean state if trades are removed or the list becomes empty
            return;
        }

        // --- Recalculate All Statistics ---
        int totalTrades = sessionTrades.size();
        List<Trade> winners = sessionTrades.stream().filter(t -> t.profitAndLoss().signum() > 0).collect(Collectors.toList());
        List<Trade> losers = sessionTrades.stream().filter(t -> t.profitAndLoss().signum() < 0).collect(Collectors.toList());

        BigDecimal realizedPnl = sessionTrades.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
        double winRate = totalTrades > 0 ? (double) winners.size() / totalTrades : 0.0;
        
        BigDecimal grossProfit = winners.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss = losers.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add).abs();
        
        BigDecimal avgWin = winners.isEmpty() ? BigDecimal.ZERO : grossProfit.divide(new BigDecimal(winners.size()), 2, RoundingMode.HALF_UP);
        BigDecimal avgLoss = losers.isEmpty() ? BigDecimal.ZERO : grossLoss.divide(new BigDecimal(losers.size()), 2, RoundingMode.HALF_UP);

        BigDecimal avgRiskReward = avgLoss.signum() == 0 ? BigDecimal.ZERO : avgWin.divide(avgLoss, 2, RoundingMode.HALF_UP);

        long totalSeconds = sessionTrades.stream().mapToLong(t -> Duration.between(t.entryTime(), t.exitTime()).getSeconds()).sum();
        Duration avgTradeTime = totalTrades > 0 ? Duration.ofSeconds(totalSeconds / totalTrades) : Duration.ZERO;
        
        // --- Generate Session Equity Curve ---
        List<JournalAnalysisService.EquityPoint> equityCurve = new ArrayList<>();
        equityCurve.add(new JournalAnalysisService.EquityPoint(sessionTrades.get(0).entryTime().minusSeconds(1), sessionInitialBalance));
        BigDecimal cumulativePnl = BigDecimal.ZERO;
        for (Trade trade : sessionTrades) {
            cumulativePnl = cumulativePnl.add(trade.profitAndLoss());
            equityCurve.add(new JournalAnalysisService.EquityPoint(trade.exitTime(), sessionInitialBalance.add(cumulativePnl)));
        }

        // --- Update Session Win/Loss Streaks ---
        Trade lastTrade = sessionTrades.get(totalTrades - 1);
        if (lastTrade.profitAndLoss().signum() > 0) {
            sessionWinStreak++;
            sessionLossStreak = 0;
        } else if (lastTrade.profitAndLoss().signum() < 0) {
            sessionWinStreak = 0;
            sessionLossStreak++;
        }
        // (No change for break-even trades)

        // --- Broadcast Updated Data ---
        SessionStats newStats = new SessionStats(
            realizedPnl, winRate, winners.size(), losers.size(), avgRiskReward, avgTradeTime, equityCurve
        );
        pcs.firePropertyChange("sessionStatsUpdated", null, newStats);
        pcs.firePropertyChange("sessionStreakUpdated", null, sessionWinStreak);
        pcs.firePropertyChange("sessionLossStreakUpdated", null, sessionLossStreak);
    }


    // --- Listener Implementations ---

    /**
     * Called by {@link ReplaySessionManager} when a new replay session is started.
     */
    @Override
    public void onReplaySessionStart() {
        reset();
    }

    /**
     * Listens for events from {@link PaperTradingService}.
     * @param evt The event object.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();

        // A new trade has been closed and added to the history
        if ("tradeHistoryUpdated".equals(propertyName)) {
            List<Trade> fullHistory = PaperTradingService.getInstance().getTradeHistory();
            if (fullHistory.size() > this.sessionTrades.size()) {
                Trade newTrade = fullHistory.get(fullHistory.size() - 1);
                this.sessionTrades.add(newTrade);
                recalculateAndNotify();
            } else if (fullHistory.size() < this.sessionTrades.size()) {
                // Handle potential future cases like trade deletion by re-syncing
                this.sessionTrades = new ArrayList<>(fullHistory); 
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
                this.sessionDisciplineScore += totalPenalty;
                this.sessionDisciplineScore = Math.max(0, this.sessionDisciplineScore); // Clamp at zero
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
    
    // --- Unused listener methods from ReplayStateListener ---
    @Override
    public void onReplayTick(KLine newM1Bar) { /* No action needed on every bar tick */ }
    @Override
    public void onReplayStateChanged() { /* No action needed for play/pause state changes */ }
}