package com.EcoChartPro.core.controller;

import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.service.PnlCalculationService;
import com.EcoChartPro.core.settings.SettingsManager;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.trading.Position;
import com.EcoChartPro.ui.NotificationService;
import com.EcoChartPro.ui.chart.ChartPanel;
import com.EcoChartPro.ui.dashboard.theme.UITheme;

import javax.swing.SwingUtilities;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ReplayController implements ReplayStateListener, PropertyChangeListener {

    private final java.beans.PropertyChangeSupport pcs = new java.beans.PropertyChangeSupport(this);
    private ChartPanel activeChartPanel;
    private KLine lastSeenBar;
    private BigDecimal initialBalance = BigDecimal.ZERO;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss");

    // --- Real-time Session Monitoring Fields ---
    private List<Trade> sessionTradesToday = new ArrayList<>();
    private LocalDate lastReplayDate = null;
    private int optimalTradeCount;
    private List<Integer> peakPerformanceHours;
    private boolean hasSentOvertrainingNudgeToday = false;
    private final Set<SettingsManager.TradingSession> activeSessions = new HashSet<>();
    private boolean hasSentFatigueNudgeToday = false;

    public ReplayController() {
        ReplaySessionManager.getInstance().addListener(this);
        SettingsManager.getInstance().addPropertyChangeListener(this);
        PaperTradingService.getInstance().addPropertyChangeListener(this);
        
        // Fetch initial discipline metrics
        GamificationService gService = GamificationService.getInstance();
        this.optimalTradeCount = gService.getOptimalTradeCount();
        this.peakPerformanceHours = gService.getPeakPerformanceHours();
    }
    
    public void setActiveChartPanel(ChartPanel panel) {
        this.activeChartPanel = panel;
        updateFullUIState();
    }
    
    public void togglePlayPause() {
        ReplaySessionManager.getInstance().togglePlayPause();
    }

    public void nextBar() {
        ReplaySessionManager.getInstance().nextBar();
    }
    
    public void setSpeed(int delay) {
        ReplaySessionManager.getInstance().setSpeed(delay);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("displayZoneId".equals(evt.getPropertyName())) {
            updateDateTimeLabel();
        } else if ("unrealizedPnlCalculated".equals(evt.getPropertyName()) || "openPositionsUpdated".equals(evt.getPropertyName()) || "tradeHistoryUpdated".equals(evt.getPropertyName())) {
            updateAccountBalanceDisplay();
        }
    }

    @Override
    public void onReplayTick(KLine newM1Bar) {
        // IMPORTANT: Process the bar first so that any new trades for this tick are registered.
        PaperTradingService.getInstance().onBarUpdate(newM1Bar);
        this.lastSeenBar = newM1Bar;
        checkSessionTransitions(newM1Bar);

        LocalDate currentDate = newM1Bar.timestamp().atZone(ZoneId.of("UTC")).toLocalDate();
        int currentHour = newM1Bar.timestamp().atZone(ZoneId.of("UTC")).getHour();

        if (lastReplayDate == null || !currentDate.equals(lastReplayDate)) {
            // New day has started in the replay
            lastReplayDate = currentDate;
            sessionTradesToday.clear();
            hasSentOvertrainingNudgeToday = false;
            hasSentFatigueNudgeToday = false;
            activeSessions.clear(); // Reset for the new day
        }
        
        // Update the list of today's trades
        this.sessionTradesToday = PaperTradingService.getInstance().getTradeHistory().stream()
            .filter(trade -> trade.entryTime().atZone(ZoneId.of("UTC")).toLocalDate().equals(currentDate))
            .collect(Collectors.toList());

        // --- Nudge Logic ---
        SettingsManager settings = SettingsManager.getInstance();
        if (settings.isOvertrainingNudgeEnabled() && optimalTradeCount > 0 && sessionTradesToday.size() > optimalTradeCount && !hasSentOvertrainingNudgeToday) {
            NotificationService.getInstance().showDisciplineNudge(
                "Discipline Hint",
                "You've taken more than your optimal " + optimalTradeCount + " trades for the day. Be aware of potential overtrading.",
                UITheme.Icons.REPORT
            );
            hasSentOvertrainingNudgeToday = true;
        }

        if (settings.isFatigueNudgeEnabled() && peakPerformanceHours != null && !peakPerformanceHours.isEmpty() && !peakPerformanceHours.contains(currentHour) && !hasSentFatigueNudgeToday) {
            NotificationService.getInstance().showDisciplineNudge(
                "Performance Hint",
                "You are now trading outside your historical peak hours. Ensure your setups are of the highest quality.",
                UITheme.Icons.CLOCK
            );
            hasSentFatigueNudgeToday = true;
        }

        updateDateTimeLabel();
        updateAccountBalanceDisplay();
    }

    @Override
    public void onReplaySessionStart() {
        this.lastSeenBar = ReplaySessionManager.getInstance().getCurrentBar();
        this.initialBalance = PaperTradingService.getInstance().getAccountBalance();
        this.activeSessions.clear();
        pcs.firePropertyChange("initialBalance", null, this.initialBalance);

        // Reset real-time monitoring state for the new session
        this.sessionTradesToday.clear();
        if (this.lastSeenBar != null) {
            this.lastReplayDate = this.lastSeenBar.timestamp().atZone(ZoneId.of("UTC")).toLocalDate();
        } else {
            this.lastReplayDate = null;
        }
        this.hasSentOvertrainingNudgeToday = false;
        this.hasSentFatigueNudgeToday = false;

        updateFullUIState();
    }

    @Override
    public void onReplayStateChanged() {
        pcs.firePropertyChange("replayStateChanged", null, null);
    }
    
    private void checkSessionTransitions(KLine bar) {
        SettingsManager settings = SettingsManager.getInstance();
        if (!settings.isSessionHighlightingEnabled()) {
            return; // Don't process if the feature is off
        }

        LocalTime currentTime = bar.timestamp().atZone(ZoneId.of("UTC")).toLocalTime();

        for (SettingsManager.TradingSession session : SettingsManager.TradingSession.values()) {
            if (!settings.getSessionEnabled().get(session)) {
                continue; // Skip disabled sessions
            }
            
            LocalTime startTime = settings.getSessionStartTimes().get(session);
            LocalTime endTime = settings.getSessionEndTimes().get(session);

            boolean isInSession;
            if (endTime.isBefore(startTime)) { // Overnight session
                isInSession = !currentTime.isBefore(startTime) || !currentTime.isAfter(endTime);
            } else { // Same-day session
                isInSession = !currentTime.isBefore(startTime) && currentTime.isBefore(endTime);
            }

            boolean wasActive = activeSessions.contains(session);

            if (isInSession && !wasActive) {
                // Session just started
                activeSessions.add(session);
                NotificationService.getInstance().showDisciplineNudge(session + " Session Started", "The " + session + " trading session is now open.", UITheme.Icons.CLOCK);
            } else if (!isInSession && wasActive) {
                // Session just ended
                activeSessions.remove(session);
                NotificationService.getInstance().showDisciplineNudge(session + " Session Ended", "The " + session + " trading session is now closed.", UITheme.Icons.CLOCK);
            }
        }
    }

    private void updateFullUIState() {
        updateDateTimeLabel();
        updateAccountBalanceDisplay();
        pcs.firePropertyChange("replayStateChanged", null, null);
    }

    private void updateDateTimeLabel() {
        KLine currentKLine = this.lastSeenBar;
        if (currentKLine == null && activeChartPanel != null) {
            currentKLine = activeChartPanel.getDataModel().getCurrentReplayKLine();
        }

        String displayString;
        if (currentKLine != null) {
            ZoneId displayZoneId = SettingsManager.getInstance().getDisplayZoneId();
            String formattedDateTime = currentKLine.timestamp().atZone(displayZoneId).format(DATE_TIME_FORMATTER);
            String zoneIdString = displayZoneId.getId();
            String displayZone = zoneIdString.contains("/") ? zoneIdString.substring(zoneIdString.lastIndexOf('/') + 1).replace('_', ' ') : zoneIdString;
            displayString = String.format("%s (%s)", formattedDateTime, displayZone);
        } else if (ReplaySessionManager.getInstance().isReplayFinished()) {
            displayString = "Replay Finished";
        } else {
            displayString = "Ready";
        }
        pcs.firePropertyChange("timeUpdated", null, displayString);
    }

    private void updateAccountBalanceDisplay() {
        PaperTradingService service = PaperTradingService.getInstance();
        BigDecimal realizedBalance = service.getAccountBalance();
        BigDecimal unrealizedPnl = BigDecimal.ZERO;
        KLine currentBar = (activeChartPanel != null) ? activeChartPanel.getDataModel().getCurrentReplayKLine() : this.lastSeenBar;

        if (currentBar != null && !service.getOpenPositions().isEmpty()) {
            Map<UUID, BigDecimal> pnlMap = PnlCalculationService.getInstance()
                    .calculateUnrealizedPnl(service.getOpenPositions(), currentBar);
            unrealizedPnl = pnlMap.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        BigDecimal displayBalance = realizedBalance.add(unrealizedPnl);
        pcs.firePropertyChange("balanceUpdated", null, displayBalance);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }
}