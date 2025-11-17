package com.EcoChartPro.ui.dashboard;

import com.EcoChartPro.core.coaching.Challenge;
import com.EcoChartPro.core.coaching.CoachingInsight;
import com.EcoChartPro.core.coaching.CoachingService;
import com.EcoChartPro.core.coaching.InsightSeverity;
import com.EcoChartPro.core.controller.LiveSessionTrackerService;
import com.EcoChartPro.core.gamification.Achievement;
import com.EcoChartPro.core.gamification.AchievementService;
import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.gamification.ProgressCardViewModel;
import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.journal.JournalAnalysisService.OverallStats;
import com.EcoChartPro.core.service.ReviewReminderService;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.state.SymbolSessionState;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.dashboard.theme.UITheme;
import com.EcoChartPro.ui.dialogs.AchievementsDialog;
import com.EcoChartPro.ui.dialogs.InsightsDialog;
import com.EcoChartPro.ui.dashboard.widgets.*;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.SessionManager;
import com.EcoChartPro.utils.report.HtmlReportGenerator;
import com.EcoChartPro.utils.report.PdfReportGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ComprehensiveReportPanel extends JPanel implements Scrollable, PropertyChangeListener {
    private static final Logger logger = LoggerFactory.getLogger(ComprehensiveReportPanel.class);
    private static final int WIDGET_VIEW_ROTATION_MS = 8000;

    // --- UI Components ---
    private final StatWidget realizedPnlWidget, winRateWidget, avgRrWidget, tradeEfficiencyWidget;
    private final JLabel profitFactorValueLabel, expectedValueValueLabel, avgTradeTimeValueLabel;
    private final ProgressCardPanel streakProgressCard;
    private final AreaChartWidget finishedTradesPnlWidget;
    private final CoachingCardPanel coachingCardPanel;
    private final DailyDisciplineWidget dailyDisciplineWidget;
    private ReplaySessionState currentSessionState;
    private final Timer cosmeticRotationTimer;
    private final Timer liveViewRotationTimer;

    // [NEW] Labels for the external chart footer
    private final JLabel maxDrawdownValue;
    private final JLabel maxRunupValue;

    // --- View Model "Playlists" for Rotation ---
    private final List<ProgressCardViewModel> coachingViewModels = new ArrayList<>();
    private final List<ProgressCardViewModel> streakViewModels = new ArrayList<>();
    private int currentCoachingIndex = 0;
    private int currentStreakIndex = 0;
    
    // --- Live Mode ---
    private LiveSessionTrackerService liveSessionTracker;
    private boolean isLiveMode = false;
    private boolean isShowingLiveView = false;
    
    private static final List<String> quotes = List.of(
        "\"The secret of getting ahead is getting started.\" - Mark Twain",
        "\"Success is the sum of small efforts, repeated day-in and day-out.\" - Robert Collier",
        "\"Discipline is the bridge between goals and accomplishment.\" - Jim Rohn"
    );
    private final Random random = new Random();

    public ComprehensiveReportPanel() {
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setLayout(new BorderLayout(0, 15));

        realizedPnlWidget = new StatWidget("Realized PNL", "Total profit or loss from all finished trades, excluding fees.");
        winRateWidget = new StatWidget("Win Rate", "The percentage of winning trades out of all trades taken.");
        tradeEfficiencyWidget = new StatWidget("Win Efficiency", "The percentage of potential profit captured on winning trades (Actual PNL / Max Favorable Excursion).");
        avgRrWidget = new StatWidget("Average RR", "Average Risk-to-Reward Ratio: The average return for every dollar risked.");
        finishedTradesPnlWidget = new AreaChartWidget();
        
        coachingCardPanel = new CoachingCardPanel();
        streakProgressCard = new ProgressCardPanel();
        dailyDisciplineWidget = new DailyDisciplineWidget();

        // [NEW] Instantiate the external footer labels
        maxDrawdownValue = new JLabel("-");
        maxRunupValue = new JLabel("-");

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        
        // --- Button Bar for Refresh and Export ---
        JButton refreshButton = new JButton(UITheme.getIcon(UITheme.Icons.REFRESH, 18, 18));
        refreshButton.setToolTipText("Reload and recalculate all stats from the last session file");
        refreshButton.setOpaque(false);
        refreshButton.setContentAreaFilled(false);
        refreshButton.setBorderPainted(false);
        refreshButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        refreshButton.addActionListener(e -> refreshStats());

        JButton exportButton = new JButton(UITheme.getIcon(UITheme.Icons.EXPORT, 18, 18));
        exportButton.setToolTipText("Export report to a file");
        exportButton.setOpaque(false);
        exportButton.setContentAreaFilled(false);
        exportButton.setBorderPainted(false);
        exportButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        JPopupMenu exportMenu = new JPopupMenu();
        JMenuItem exportHtmlItem = new JMenuItem("Export to HTML...");
        exportHtmlItem.addActionListener(e -> exportReportToHtml());
        JMenuItem exportPdfItem = new JMenuItem("Export to PDF...");
        exportPdfItem.addActionListener(e -> exportReportToPdf());
        exportMenu.add(exportHtmlItem);
        exportMenu.add(exportPdfItem);
        
        exportButton.addActionListener(e -> exportMenu.show(exportButton, 0, exportButton.getHeight()));

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonBar.setOpaque(false);
        buttonBar.add(exportButton);
        buttonBar.add(refreshButton);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(buttonBar);
        headerPanel.add(actionPanel, BorderLayout.NORTH);
        
        JPanel statsContainer = new JPanel(new BorderLayout(0, 15));
        statsContainer.setOpaque(false);
        
        JPanel primaryStatsPanel = new JPanel(new GridLayout(1, 0, 15, 15));
        primaryStatsPanel.setOpaque(false);
        primaryStatsPanel.add(realizedPnlWidget);
        primaryStatsPanel.add(winRateWidget);
        primaryStatsPanel.add(tradeEfficiencyWidget);
        primaryStatsPanel.add(avgRrWidget);
        
        JPanel secondaryStatsPanel = new JPanel(new GridLayout(1, 0, 15, 15));
        secondaryStatsPanel.setOpaque(false);
        profitFactorValueLabel = createStatCard(secondaryStatsPanel, "Profit Factor", "Gross profits divided by gross losses. A value > 1 indicates a profitable system.");
        expectedValueValueLabel = createStatCard(secondaryStatsPanel, "Expected Value", "The average amount you can expect to win or lose per trade.");
        avgTradeTimeValueLabel = createStatCard(secondaryStatsPanel, "Average Trade Time", "The average duration of a single trade from entry to exit.");
        
        statsContainer.add(primaryStatsPanel, BorderLayout.NORTH);
        statsContainer.add(secondaryStatsPanel, BorderLayout.CENTER);
        headerPanel.add(statsContainer, BorderLayout.CENTER);


        // --- [MODIFIED] Rebuild the bottom section of the panel ---
        JPanel bottomContainer = new JPanel(new GridBagLayout());
        bottomContainer.setOpaque(false);

        // -- Left Column (Chart and Footer) --
        JPanel leftColumn = new JPanel(new BorderLayout());
        leftColumn.setOpaque(false);
        leftColumn.add(finishedTradesPnlWidget, BorderLayout.CENTER);
        leftColumn.add(createChartFooterPanel(), BorderLayout.SOUTH);

        // -- Right Column (Stacked Widgets) --
        JPanel rightColumn = new JPanel();
        rightColumn.setOpaque(false);
        rightColumn.setLayout(new BoxLayout(rightColumn, BoxLayout.Y_AXIS));
        rightColumn.add(coachingCardPanel);
        rightColumn.add(Box.createVerticalStrut(15));
        rightColumn.add(streakProgressCard);
        rightColumn.add(Box.createVerticalStrut(15));
        rightColumn.add(dailyDisciplineWidget);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(0, 8, 0, 8);
        
        gbc.gridx = 0; gbc.weightx = 0.5;
        bottomContainer.add(leftColumn, gbc);

        gbc.gridx = 1; gbc.weightx = 0.5;
        bottomContainer.add(rightColumn, gbc);
        
        add(headerPanel, BorderLayout.NORTH);
        add(bottomContainer, BorderLayout.CENTER);


        coachingCardPanel.addInsightsButtonListener(e -> {
            Window owner = SwingUtilities.getWindowAncestor(this);
            if (owner instanceof InsightsDialog) {
                owner.toFront();
                return;
            }
            InsightsDialog insightsDialog = new InsightsDialog((Frame) owner);
            
            if (this.currentSessionState != null) {
                insightsDialog.loadSessionData(this.currentSessionState);
            }
            insightsDialog.setVisible(true);
        });
        
        streakProgressCard.addInsightsButtonListener(e -> {
            Window owner = SwingUtilities.getWindowAncestor(this);
            new AchievementsDialog((Frame) owner).setVisible(true);
        });
        
        this.cosmeticRotationTimer = new Timer(WIDGET_VIEW_ROTATION_MS, e -> rotateCosmeticDisplay());
        this.cosmeticRotationTimer.setInitialDelay(WIDGET_VIEW_ROTATION_MS);
        
        this.liveViewRotationTimer = new Timer(WIDGET_VIEW_ROTATION_MS, e -> rotateLiveDisplay());

        ReviewReminderService.getInstance().addPropertyChangeListener(this);
    }

    // [NEW] Helper method to create the chart's external footer
    private JPanel createChartFooterPanel() {
        JPanel footer = new JPanel(new GridLayout(1, 2, 20, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(10, 15, 0, 15)); // Match chart's horizontal padding
        footer.add(createFooterStat("Max Drawdown", maxDrawdownValue));
        footer.add(createFooterStat("Max Runup", maxRunupValue));
        return footer;
    }

    // [NEW] Helper method to create a single stat for the chart footer
    private JPanel createFooterStat(String title, JLabel valueLabel) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UIManager.getFont("app.font.widget_content").deriveFont(12f));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        valueLabel.setFont(UIManager.getFont("app.font.widget_title").deriveFont(Font.BOLD, 14f));
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(valueLabel, BorderLayout.SOUTH);
        return panel;
    }
    
    private void exportReportToHtml() {
        if (this.currentSessionState == null || this.currentSessionState.symbolStates() == null || this.currentSessionState.symbolStates().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No session data available to export.", "Export Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save HTML Report");
        fileChooser.setFileFilter(new FileNameExtensionFilter("HTML Files (*.html)", "html"));

        String defaultFilename = String.format("EcoChartPro_Report_%s_%s.html",
                this.currentSessionState.lastActiveSymbol(),
                LocalDate.now().toString());
        fileChooser.setSelectedFile(new File(defaultFilename));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".html")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".html");
            }
            final File finalFile = fileToSave;
            
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    HtmlReportGenerator.generate(currentSessionState, finalFile);
                    return null;
                }
                @Override protected void done() {
                    handleExportCompletion(this, finalFile);
                }
            }.execute();
        }
    }
    
    private void exportReportToPdf() {
        if (this.currentSessionState == null || this.currentSessionState.symbolStates() == null || this.currentSessionState.symbolStates().isEmpty()) {
            JOptionPane.showMessageDialog(this, "No session data available to export.", "Export Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save PDF Report");
        fileChooser.setFileFilter(new FileNameExtensionFilter("PDF Documents (*.pdf)", "pdf"));

        String defaultFilename = String.format("EcoChartPro_Report_%s_%s.pdf",
                this.currentSessionState.lastActiveSymbol(),
                LocalDate.now().toString());
        fileChooser.setSelectedFile(new File(defaultFilename));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".pdf")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".pdf");
            }
            final File finalFile = fileToSave;

            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    PdfReportGenerator.generate(currentSessionState, finalFile);
                    return null;
                }
                @Override protected void done() {
                    handleExportCompletion(this, finalFile);
                }
            }.execute();
        }
    }
    
    private void handleExportCompletion(SwingWorker<Void, Void> worker, File outputFile) {
        try {
            worker.get(); // check for exceptions
            int choice = JOptionPane.showConfirmDialog(this,
                    "Report successfully exported to:\n" + outputFile.getAbsolutePath() + "\n\nDo you want to open it now?",
                    "Export Successful",
                    JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION && Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(outputFile);
            }
        } catch (Exception ex) {
            logger.error("Failed to export report to {}", outputFile.getName(), ex);
            JOptionPane.showMessageDialog(this,
                    "An error occurred while exporting the report:\n" + ex.getMessage(),
                    "Export Failed", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void rotateCosmeticDisplay() {
        if (!coachingViewModels.isEmpty()) {
            currentCoachingIndex = (currentCoachingIndex + 1) % coachingViewModels.size();
            coachingCardPanel.updateViewModel(coachingViewModels.get(currentCoachingIndex));
        }

        if (!streakViewModels.isEmpty()) {
            currentStreakIndex = (currentStreakIndex + 1) % streakViewModels.size();
            streakProgressCard.updateViewModel(streakViewModels.get(currentStreakIndex));
        }
    }
    
    private void rotateLiveDisplay() {
        isShowingLiveView = !isShowingLiveView;
        realizedPnlWidget.toggleView(isShowingLiveView);
        winRateWidget.toggleView(isShowingLiveView);
        avgRrWidget.toggleView(isShowingLiveView);
        finishedTradesPnlWidget.setTitle(isShowingLiveView ? "Session Equity Curve" : "Finished Trades PNL");
        dailyDisciplineWidget.toggleView(isShowingLiveView);
    }

    private void refreshStats() {
        // Refresh logic...
    }
    
    public void updateData(ReplaySessionState state) {
        if (state == null || state.symbolStates() == null || state.lastActiveSymbol() == null) {
            return;
        }

        SymbolSessionState activeSymbolState = state.symbolStates().get(state.lastActiveSymbol());
        if (activeSymbolState == null || activeSymbolState.tradeHistory() == null) {
            return; // No trade data for the last active symbol
        }

        this.currentSessionState = state;
        JournalAnalysisService service = new JournalAnalysisService();
        
        List<Trade> allTradesInSession = state.symbolStates().values().stream()
                .flatMap(s -> s.tradeHistory().stream())
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        BigDecimal totalPnl = allTradesInSession.stream()
            .map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal initialBalance = state.accountBalance().subtract(totalPnl);
        
        OverallStats stats = service.analyzeOverallPerformance(allTradesInSession, initialBalance);
        updateData(stats, service, state);
    }
    
    public void updateData(OverallStats stats, JournalAnalysisService service, ReplaySessionState state) {
        cosmeticRotationTimer.stop();
        
        if (stats == null || service == null || state == null) {
            return;
        }
        this.currentSessionState = state;
        
        coachingCardPanel.setLoading(true);

        // --- Update Overall (Static) Widget Data ---
        DecimalFormat pnlFormat = new DecimalFormat("+$#,##0.00;-$#,##0.00");
        DecimalFormat percentFormat = new DecimalFormat("0.0'%'");
        DecimalFormat decimalFormat = new DecimalFormat("0.00");

        realizedPnlWidget.setOverallValue(pnlFormat.format(stats.totalPnl()), stats.totalPnl().signum() >= 0 ? UIManager.getColor("app.color.accent") : UIManager.getColor("app.color.negative"));
        GaugeChart winRateGauge = new GaugeChart(GaugeChart.GaugeType.FULL_CIRCLE);
        winRateGauge.setData(stats.winRate());
        winRateWidget.setOverallValue(percentFormat.format(stats.winRate() * 100), UIManager.getColor("Label.foreground"));
        winRateWidget.setOverallGraphic(winRateGauge);
        avgRrWidget.setOverallValue(decimalFormat.format(stats.avgRiskReward()), stats.avgRiskReward() >= 1.0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));
        
        profitFactorValueLabel.setText(decimalFormat.format(stats.profitFactor()));
        expectedValueValueLabel.setText(pnlFormat.format(stats.expectancy()));
        avgTradeTimeValueLabel.setText(formatDuration(stats.avgTradeDuration()));
        
        finishedTradesPnlWidget.updateData(stats.equityCurve());
        maxDrawdownValue.setText(pnlFormat.format(stats.maxDrawdown()));
        maxDrawdownValue.setForeground(UIManager.getColor("app.color.negative"));
        maxRunupValue.setText(pnlFormat.format(stats.maxRunup()));
        maxRunupValue.setForeground(UIManager.getColor("app.color.accent"));

        Optional<DataSourceManager.ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.symbol().equalsIgnoreCase(state.lastActiveSymbol()))
                .findFirst();

        if (sourceOpt.isPresent()) {
            List<JournalAnalysisService.TradeMfeMae> mfeMaeData = service.calculateMfeMaeForAllTrades(stats.trades(), sourceOpt.get());
            List<JournalAnalysisService.TradeMfeMae> winners = mfeMaeData.stream().filter(d -> d.pnl().signum() > 0).collect(Collectors.toList());
            
            BigDecimal efficiency = BigDecimal.ZERO;
            if (!winners.isEmpty()) {
                BigDecimal totalWinnerPnl = winners.stream().map(JournalAnalysisService.TradeMfeMae::pnl).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalWinnerMfe = winners.stream().map(JournalAnalysisService.TradeMfeMae::mfe).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (totalWinnerMfe.signum() > 0) {
                    efficiency = totalWinnerPnl.divide(totalWinnerMfe, 4, RoundingMode.HALF_UP);
                }
            }

            GaugeChart efficiencyGauge = new GaugeChart(GaugeChart.GaugeType.FULL_CIRCLE);
            efficiencyGauge.setData(efficiency.doubleValue());
            tradeEfficiencyWidget.setOverallValue(percentFormat.format(efficiency.multiply(BigDecimal.valueOf(100))), UIManager.getColor("Label.foreground"));
            tradeEfficiencyWidget.setOverallGraphic(efficiencyGauge);
        } else {
            tradeEfficiencyWidget.setOverallValue("-", UIManager.getColor("Label.foreground"));
            tradeEfficiencyWidget.setOverallGraphic(null);
        }

        // Update gamification services which drive the cosmetic rotation content
        GamificationService.getInstance().updateProgression(stats.trades());
        populateStreakViewModels();
        updateOverallDisciplineWidget(stats.trades());
        
        new SwingWorker<List<ProgressCardViewModel>, Void>() {
            @Override
            protected List<ProgressCardViewModel> doInBackground() throws Exception {
                return populateCoachingViewModels(stats.trades(), sourceOpt);
            }

            @Override
            protected void done() {
                try {
                    List<ProgressCardViewModel> models = get();
                    coachingViewModels.clear();
                    coachingViewModels.addAll(models);

                    if (!streakViewModels.isEmpty()) {
                        currentStreakIndex = 0;
                        streakProgressCard.updateViewModel(streakViewModels.get(0));
                    }
                    if (!coachingViewModels.isEmpty()) {
                        currentCoachingIndex = 0;
                        coachingCardPanel.updateViewModel(coachingViewModels.get(0));
                    }
                    coachingCardPanel.setLoading(false);
                    cosmeticRotationTimer.start();
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Error populating coaching view models", e);
                    coachingCardPanel.setLoading(false);
                }
            }
        }.execute();

        boolean isReviewDue = ReviewReminderService.getInstance().isReviewDue(stats.trades());
        coachingCardPanel.setReviewDue(isReviewDue);
    }
    
    // --- Live Mode Methods ---

    public void activateLiveMode(LiveSessionTrackerService tracker) {
        this.isLiveMode = true;
        this.isShowingLiveView = true;
        this.liveSessionTracker = tracker;
        this.liveSessionTracker.addPropertyChangeListener(this);
        liveViewRotationTimer.start();
        cosmeticRotationTimer.start();
        clearAllWidgetsForLiveMode();
        logger.info("Dashboard report panel switched to LIVE mode.");
    }
    
    public void deactivateLiveMode() {
        if (this.liveSessionTracker != null) {
            this.liveSessionTracker.removePropertyChangeListener(this);
        }
        this.isLiveMode = false;
        this.liveSessionTracker = null;
        liveViewRotationTimer.stop();
        cosmeticRotationTimer.start();
        logger.info("Dashboard report panel switched to STATIC mode.");

        // Explicitly switch widgets back to showing the overall view
        isShowingLiveView = false;
        realizedPnlWidget.toggleView(false);
        winRateWidget.toggleView(false);
        avgRrWidget.toggleView(false);
        tradeEfficiencyWidget.toggleView(false);
        finishedTradesPnlWidget.setTitle("Finished Trades PNL");
        dailyDisciplineWidget.toggleView(false);
    }
    
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() instanceof ReviewReminderService && "reviewStateChanged".equals(evt.getPropertyName())) {
            // The review service told us the state changed (it was completed), so turn off the reminder.
            coachingCardPanel.setReviewDue(false);
            return;
        }

        if (!isLiveMode) return;
        
        SwingUtilities.invokeLater(() -> {
            switch (evt.getPropertyName()) {
                case "sessionStatsUpdated":
                    if (evt.getNewValue() instanceof LiveSessionTrackerService.SessionStats stats) {
                        updateLiveStatsWidgets(stats);
                    }
                    break;
                case "disciplineScoreUpdated":
                    if (evt.getNewValue() instanceof Integer score) {
                        dailyDisciplineWidget.setLiveData(score, 100);
                    }
                    break;
            }
        });
    }

    private void updateLiveStatsWidgets(LiveSessionTrackerService.SessionStats stats) {
        DecimalFormat pnlFormat = new DecimalFormat("+$#,##0.00;-$#,##0.00");
        DecimalFormat percentFormat = new DecimalFormat("0.0'%'");
        DecimalFormat decimalFormat = new DecimalFormat("0.00");

        realizedPnlWidget.setLiveValue(pnlFormat.format(stats.realizedPnl()), stats.realizedPnl().signum() >= 0 ? UIManager.getColor("app.color.accent") : UIManager.getColor("app.color.negative"));
        
        GaugeChart liveWinRateGauge = new GaugeChart(GaugeChart.GaugeType.FULL_CIRCLE);
        liveWinRateGauge.setData(stats.winRate());
        winRateWidget.setLiveValue(percentFormat.format(stats.winRate() * 100), UIManager.getColor("Label.foreground"));
        winRateWidget.setLiveGraphic(liveWinRateGauge);

        avgRrWidget.setLiveValue(decimalFormat.format(stats.avgRiskReward()), stats.avgRiskReward().compareTo(BigDecimal.ONE) >= 0 ? UIManager.getColor("app.color.positive") : UIManager.getColor("app.color.negative"));
        
        finishedTradesPnlWidget.updateData(stats.equityCurve());
        maxDrawdownValue.setText("-");
        maxRunupValue.setText("-");
    }
    
    private void clearAllWidgetsForLiveMode() {
        // Set initial live values to zero/default
        realizedPnlWidget.setLiveValue("+$0.00", UIManager.getColor("Label.foreground"));
        winRateWidget.setLiveValue("0.0%", UIManager.getColor("Label.foreground"));
        winRateWidget.setLiveGraphic(new GaugeChart(GaugeChart.GaugeType.FULL_CIRCLE));
        avgRrWidget.setLiveValue("0.00", UIManager.getColor("Label.foreground"));
        tradeEfficiencyWidget.setLiveValue("N/A", UIManager.getColor("Label.foreground"));
        tradeEfficiencyWidget.setLiveGraphic(null);
        
        finishedTradesPnlWidget.updateData(Collections.emptyList());
        maxDrawdownValue.setText("-");
        maxRunupValue.setText("-");

        dailyDisciplineWidget.setLiveData(100, 100); 

        // Make sure all widgets are showing the live view initially
        isShowingLiveView = true;
        rotateLiveDisplay();
    }

    private void updateOverallDisciplineWidget(List<Trade> allTrades) {
        int optimalCount = GamificationService.getInstance().getOptimalTradeCount();
        if (allTrades.isEmpty()) {
            dailyDisciplineWidget.setOverallData(0, optimalCount);
            return;
        }
        Optional<LocalDate> lastTradeDateOpt = allTrades.stream().map(trade -> trade.exitTime().atZone(ZoneOffset.UTC).toLocalDate()).max(LocalDate::compareTo);
        if (lastTradeDateOpt.isPresent()) {
            LocalDate lastTradeDate = lastTradeDateOpt.get();
            long tradesOnLastDay = allTrades.stream().filter(trade -> trade.exitTime().atZone(ZoneOffset.UTC).toLocalDate().equals(lastTradeDate)).count();
            dailyDisciplineWidget.setOverallData((int) tradesOnLastDay, optimalCount);
        } else {
            dailyDisciplineWidget.setOverallData(0, optimalCount);
        }
    }
    
    private void populateStreakViewModels() {
        streakViewModels.clear();
        GamificationService service = GamificationService.getInstance();
        
        if (service.wasStreakPaused()) {
            streakViewModels.add(new ProgressCardViewModel(
                ProgressCardViewModel.CardType.STREAK_PAUSED, "Streak Paused",
                "0 Days", "Consistency is key!", 0.0, "",
                quotes.get(random.nextInt(quotes.size()))
            ));
        } else {
            int[] streakGoals = {3, 7, 14, 30, 60, 90};
            int currentStreak = service.getCurrentPositiveStreak();
            int goal = 3;
            for (int g : streakGoals) {
                if (currentStreak < g) { goal = g; break; }
            }
            if (currentStreak >= streakGoals[streakGoals.length - 1]) {
                goal = currentStreak + 1;
            }

            if (currentStreak > 0) {
                streakViewModels.add(new ProgressCardViewModel(
                    ProgressCardViewModel.CardType.POSITIVE_STREAK, "Discipline Streak",
                    currentStreak + " Days", "Best: " + service.getBestPositiveStreak() + " Days",
                    (double) currentStreak / goal, "Goal: " + goal + " Days",
                    "Excellent discipline builds confidence."
                ));
            } else {
                 streakViewModels.add(new ProgressCardViewModel(
                    ProgressCardViewModel.CardType.EMPTY, "Daily Discipline",
                    "No Active Streak", "Best: " + service.getBestPositiveStreak() + " Days",
                    0.0, "Goal: " + goal + " Days",
                    "Start a new streak by trading without mistakes."
                ));
            }
        }

        AchievementService.getInstance().getNextLockedAchievement().ifPresent(nextAchievement -> {
             streakViewModels.add(new ProgressCardViewModel(
                ProgressCardViewModel.CardType.NEXT_ACHIEVEMENT, "Next Goal",
                nextAchievement.title(), "Unlock Your Next Achievement",
                0.0, "",
                nextAchievement.description()
            ));
        });
    }

    private List<ProgressCardViewModel> populateCoachingViewModels(List<Trade> allTrades, Optional<DataSourceManager.ChartDataSource> sourceOpt) {
        List<ProgressCardViewModel> models = new ArrayList<>();
        GamificationService gamificationService = GamificationService.getInstance();
        CoachingService coachingService = CoachingService.getInstance();

        gamificationService.getActiveDailyChallenge().ifPresent(challenge -> {
            if (!challenge.isComplete()) {
                models.add(new ProgressCardViewModel(
                    ProgressCardViewModel.CardType.DAILY_CHALLENGE, "Daily Challenge: " + challenge.title(),
                    "", "+" + challenge.xpReward() + " XP", 0.0, "",
                    challenge.description()
                ));
            }
        });
        
        int optimalCount = gamificationService.getOptimalTradeCount();
        List<Integer> peakHours = gamificationService.getPeakPerformanceHours();
        List<CoachingInsight> insights = coachingService.analyze(allTrades, optimalCount, peakHours, sourceOpt);
        
        for (CoachingInsight insight : insights) {
             models.add(new ProgressCardViewModel(
                insight.severity() == InsightSeverity.HIGH ? ProgressCardViewModel.CardType.CRITICAL_MISTAKE : ProgressCardViewModel.CardType.COACHING_INSIGHT,
                "Coaching Insight: " + insight.title(),
                "", "View Details", 0.0, "",
                insight.description()
            ));
        }

        if (models.isEmpty()) {
            models.add(new ProgressCardViewModel(
                ProgressCardViewModel.CardType.EMPTY, null, null, null, 0.0, null, null
            ));
        }
        return models;
    }

    private JLabel createStatCard(JPanel parent, String title, String tooltip) {
        JPanel card = new JPanel(new BorderLayout(0, 2));
        card.setOpaque(true);
        card.setBackground(UIManager.getColor("Panel.background"));
        card.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UIManager.getFont("app.font.widget_content"));
        titleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        titleLabel.setToolTipText(tooltip);
        JLabel valueLabel = new JLabel("-");
        valueLabel.setFont(UIManager.getFont("app.font.widget_title").deriveFont(Font.BOLD, 15f));
        valueLabel.setForeground(UIManager.getColor("Label.foreground"));
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        parent.add(card);
        return valueLabel;
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        long absSeconds = Math.abs(seconds);
        String positive = String.format("%d H, %d M, %d S",
                TimeUnit.SECONDS.toHours(absSeconds),
                TimeUnit.SECONDS.toMinutes(absSeconds) % 60,
                absSeconds % 60);
        return seconds < 0 ? "-" + positive : positive;
    }
    
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }
}