package com.EcoChartPro.ui.home;

import com.EcoChartPro.core.coaching.CoachingService;
import com.EcoChartPro.core.controller.LiveSessionTrackerService;
import com.EcoChartPro.core.gamification.AchievementService;
import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.gamification.ProgressCardViewModel;
import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.journal.JournalAnalysisService.OverallStats;
import com.EcoChartPro.core.service.ReviewReminderService;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.PrimaryFrame;
import com.EcoChartPro.ui.home.theme.UITheme;
import com.EcoChartPro.ui.home.widgets.*;
import com.EcoChartPro.ui.dialogs.AchievementsDialog;
import com.EcoChartPro.utils.DataSourceManager;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ComprehensiveReportPanel extends JPanel implements PropertyChangeListener, Scrollable {
    private static final Logger logger = LoggerFactory.getLogger(ComprehensiveReportPanel.class);

    private final MetricCard pnlCard;
    private final MetricCard winRateCard;
    private final MetricCard efficiencyCard;
    private final MetricCard riskRewardCard;

    private final EquityCurveChart equityChart;
    private final CoachingCardPanel coachingPanel;
    private final DailyDisciplineWidget disciplineWidget;
    private final ProgressCardPanel streakPanel;

    private ReplaySessionState currentSessionState;
    private LiveSessionTrackerService liveSessionTracker;
    private boolean isLiveMode = false;

    private final Timer cosmeticTimer;
    private final List<ProgressCardViewModel> coachingModels = new ArrayList<>();
    private int coachingIndex = 0;

    public ComprehensiveReportPanel() {
        setOpaque(false);
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        pnlCard = new MetricCard();
        winRateCard = new MetricCard();
        efficiencyCard = new MetricCard();
        riskRewardCard = new MetricCard();

        equityChart = new EquityCurveChart();
        coachingPanel = new CoachingCardPanel();
        disciplineWidget = new DailyDisciplineWidget();
        streakPanel = new ProgressCardPanel();

        buildLayout();
        setupInteractions();

        cosmeticTimer = new Timer(8000, e -> rotateCoachingCards());
        cosmeticTimer.start();

        ReviewReminderService.getInstance().addPropertyChangeListener(this);
    }

    private void buildLayout() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;

        // Row 0: Top Metrics (6 columns)
        gbc.weighty = 0.20;
        gbc.gridy = 0;
        gbc.gridwidth = 1;

        gbc.gridx = 0;
        gbc.weightx = 1.0;
        add(new DashboardCard("Realized P&L", pnlCard), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        add(new DashboardCard("Win Rate", winRateCard), gbc);
        gbc.gridx = 2;
        gbc.weightx = 1.0;
        add(new DashboardCard("Avg R:R", riskRewardCard), gbc);
        gbc.gridx = 3;
        gbc.weightx = 1.0;
        add(new DashboardCard("Efficiency", efficiencyCard), gbc);
        gbc.gridx = 4;
        gbc.weightx = 1.0;
        add(new DashboardCard("Discipline", disciplineWidget), gbc);

        gbc.gridx = 5;
        gbc.weightx = 0.2;

        JButton exportBtn = new JButton("Export");
        exportBtn.setIcon(UITheme.getIcon(UITheme.Icons.EXPORT, 16, 16));

        JPopupMenu exportMenu = new JPopupMenu();
        JMenuItem htmlItem = new JMenuItem("Export to HTML");
        htmlItem.addActionListener(e -> exportReportToHtml());
        JMenuItem pdfItem = new JMenuItem("Export to PDF");
        pdfItem.addActionListener(e -> exportReportToPdf());
        exportMenu.add(htmlItem);
        exportMenu.add(pdfItem);

        exportBtn.addActionListener(e -> exportMenu.show(exportBtn, 0, exportBtn.getHeight()));

        add(new DashboardCard("Actions", createActionPanel(exportBtn)), gbc);

        // Row 1: Main Chart & Coaching
        gbc.gridy = 1;
        gbc.weighty = 0.80;

        gbc.gridx = 0;
        gbc.gridwidth = 4;
        gbc.weightx = 4.0;
        add(new DashboardCard("Session Equity Curve", equityChart), gbc);

        gbc.gridx = 4;
        gbc.gridwidth = 2;
        gbc.weightx = 1.2;

        JPanel sidebarPanel = new JPanel(new GridLayout(2, 1, 0, 10));
        sidebarPanel.setOpaque(false);
        sidebarPanel.add(coachingPanel);
        sidebarPanel.add(streakPanel);

        add(sidebarPanel, gbc);
    }

    private JPanel createActionPanel(JButton... buttons) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER));
        p.setOpaque(false);
        for (JButton b : buttons)
            p.add(b);
        return p;
    }

    private void setupInteractions() {
        coachingPanel.addInsightsButtonListener(e -> {
            Window window = SwingUtilities.getWindowAncestor(this);
            // [FIX] Navigate to the new Analysis Tab instead of opening a dialog
            if (window instanceof PrimaryFrame frame) {
                frame.getTitleBarManager().getAnalysisNavButton().doClick();
            }
        });

        streakPanel.addInsightsButtonListener(e -> {
            Window owner = SwingUtilities.getWindowAncestor(this);
            if (owner != null)
                new AchievementsDialog((Frame) owner).setVisible(true);
        });
    }

    private void exportReportToHtml() {
        if (this.currentSessionState == null) {
            JOptionPane.showMessageDialog(this, "No session data available to export.", "Export Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save HTML Report");
        fileChooser.setFileFilter(new FileNameExtensionFilter("HTML Files (*.html)", "html"));

        String defaultFilename = String.format("EcoChartPro_Report_%s_%s.html",
                this.currentSessionState.lastActiveSymbol() != null ? this.currentSessionState.lastActiveSymbol()
                        : "Session",
                LocalDate.now().toString());
        fileChooser.setSelectedFile(new File(defaultFilename));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".html")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".html");
            }
            final File finalFile = fileToSave;

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    HtmlReportGenerator.generate(currentSessionState, finalFile);
                    return null;
                }

                @Override
                protected void done() {
                    handleExportCompletion(this, finalFile);
                }
            }.execute();
        }
    }

    private void exportReportToPdf() {
        if (this.currentSessionState == null) {
            JOptionPane.showMessageDialog(this, "No session data available to export.", "Export Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save PDF Report");
        fileChooser.setFileFilter(new FileNameExtensionFilter("PDF Documents (*.pdf)", "pdf"));

        String defaultFilename = String.format("EcoChartPro_Report_%s_%s.pdf",
                this.currentSessionState.lastActiveSymbol() != null ? this.currentSessionState.lastActiveSymbol()
                        : "Session",
                LocalDate.now().toString());
        fileChooser.setSelectedFile(new File(defaultFilename));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".pdf")) {
                fileToSave = new File(fileToSave.getParentFile(), fileToSave.getName() + ".pdf");
            }
            final File finalFile = fileToSave;

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    PdfReportGenerator.generate(currentSessionState, finalFile);
                    return null;
                }

                @Override
                protected void done() {
                    handleExportCompletion(this, finalFile);
                }
            }.execute();
        }
    }

    private void handleExportCompletion(SwingWorker<Void, Void> worker, File outputFile) {
        try {
            worker.get();
            int choice = JOptionPane.showConfirmDialog(this,
                    "Report successfully exported to:\n" + outputFile.getAbsolutePath()
                            + "\n\nDo you want to open it now?",
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

    public void updateData(ReplaySessionState state) {
        if (state == null) {
            updateData(null, null, null);
            return;
        }

        JournalAnalysisService service = new JournalAnalysisService();

        List<Trade> allTradesInSession = new ArrayList<>();
        if (state.symbolStates() != null) {
            state.symbolStates().values().forEach(symbolState -> {
                if (symbolState.tradeHistory() != null) {
                    allTradesInSession.addAll(symbolState.tradeHistory());
                }
            });
        }

        BigDecimal totalPnl = allTradesInSession.stream()
                .map(Trade::profitAndLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal currentBalance = state.accountBalance() != null ? state.accountBalance() : BigDecimal.ZERO;
        BigDecimal initialBalance = currentBalance.subtract(totalPnl);

        OverallStats stats = service.analyzeOverallPerformance(allTradesInSession, initialBalance);
        updateData(stats, service, state);
    }

    public void updateData(OverallStats stats, JournalAnalysisService service, ReplaySessionState state) {
        if (stats == null) {
            pnlCard.setOverallData("-", UIManager.getColor("Label.foreground"));
            winRateCard.setOverallData("-", UIManager.getColor("Label.foreground"));
            riskRewardCard.setOverallData("-", UIManager.getColor("Label.foreground"));
            efficiencyCard.setOverallData("-", UIManager.getColor("Label.foreground"));
            equityChart.updateData(null);
            return;
        }

        this.currentSessionState = state;

        DecimalFormat pnlFmt = new DecimalFormat("+$#,##0.00;-$#,##0.00");
        DecimalFormat pctFmt = new DecimalFormat("0.0%");
        DecimalFormat decFmt = new DecimalFormat("0.00");

        pnlCard.setOverallData(pnlFmt.format(stats.totalPnl()),
                stats.totalPnl().signum() >= 0 ? UIManager.getColor("app.color.positive")
                        : UIManager.getColor("app.color.negative"));

        winRateCard.setOverallData(pctFmt.format(stats.winRate()), UIManager.getColor("Label.foreground"));
        riskRewardCard.setOverallData(decFmt.format(stats.avgRiskReward()), UIManager.getColor("Label.foreground"));

        BigDecimal efficiency = calculateEfficiency(stats, service, state);
        efficiencyCard.setOverallData(pctFmt.format(efficiency), UIManager.getColor("Label.foreground"));

        equityChart.updateData(stats.equityCurve());
        updateGamification(stats.trades());
    }

    private BigDecimal calculateEfficiency(OverallStats stats, JournalAnalysisService service,
            ReplaySessionState state) {
        if (state == null || state.lastActiveSymbol() == null)
            return BigDecimal.ZERO;

        Optional<DataSourceManager.ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources()
                .stream()
                .filter(s -> s.symbol().equalsIgnoreCase(state.lastActiveSymbol()))
                .findFirst();

        if (sourceOpt.isPresent()) {
            List<JournalAnalysisService.TradeMfeMae> mfeMaeData = service.calculateMfeMaeForAllTrades(stats.trades(),
                    sourceOpt.get());
            List<JournalAnalysisService.TradeMfeMae> winners = mfeMaeData.stream().filter(d -> d.pnl().signum() > 0)
                    .collect(Collectors.toList());

            if (!winners.isEmpty()) {
                BigDecimal totalWinnerPnl = winners.stream().map(JournalAnalysisService.TradeMfeMae::pnl)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalWinnerMfe = winners.stream().map(JournalAnalysisService.TradeMfeMae::mfe)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (totalWinnerMfe.signum() > 0) {
                    return totalWinnerPnl.divide(totalWinnerMfe, 4, RoundingMode.HALF_UP);
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private void updateGamification(List<Trade> trades) {
        int optimal = GamificationService.getInstance().getOptimalTradeCount();
        disciplineWidget.setOverallData(trades.size(), optimal);

        streakPanel.updateViewModel(GamificationService.getInstance().getLatestProgressViewModel());

        coachingModels.clear();
        GamificationService.getInstance().getActiveDailyChallenge().ifPresent(challenge -> {
            if (!challenge.isComplete()) {
                coachingModels.add(new ProgressCardViewModel(
                        ProgressCardViewModel.CardType.DAILY_CHALLENGE, "Challenge: " + challenge.title(),
                        "", "+" + challenge.xpReward() + " XP", 0.0, "",
                        challenge.description()));
            }
        });

        Optional<DataSourceManager.ChartDataSource> sourceOpt = Optional.empty();
        if (currentSessionState != null && currentSessionState.lastActiveSymbol() != null) {
            sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                    .filter(s -> s.symbol().equalsIgnoreCase(currentSessionState.lastActiveSymbol()))
                    .findFirst();
        }

        List<Integer> peakHours = GamificationService.getInstance().getPeakPerformanceHours();
        CoachingService.getInstance().analyze(trades, optimal, peakHours, sourceOpt).forEach(insight -> {
            coachingModels.add(new ProgressCardViewModel(
                    ProgressCardViewModel.CardType.COACHING_INSIGHT, "Insight: " + insight.title(),
                    "", "View Details", 0.0, "",
                    insight.description()));
        });

        if (coachingModels.isEmpty()) {
            coachingModels.add(new ProgressCardViewModel(
                    ProgressCardViewModel.CardType.EMPTY, "No Active Insights",
                    "", "", 0.0, "", "Great trading! No critical issues detected."));
        }
        rotateCoachingCards();
    }

    private void rotateCoachingCards() {
        if (!coachingModels.isEmpty()) {
            coachingPanel.updateViewModel(coachingModels.get(coachingIndex));
            coachingIndex = (coachingIndex + 1) % coachingModels.size();
        }
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 40; // Faster scrolling
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 40;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    // --- Live Mode ---

    public void activateLiveMode(LiveSessionTrackerService tracker) {
        this.isLiveMode = true;
        this.liveSessionTracker = tracker;
        tracker.addPropertyChangeListener(this);

        pnlCard.toggleMode(true);
        winRateCard.toggleMode(true);
        efficiencyCard.toggleMode(true);
        riskRewardCard.toggleMode(true);
        disciplineWidget.toggleView(true);
    }

    public void deactivateLiveMode() {
        this.isLiveMode = false;
        if (liveSessionTracker != null)
            liveSessionTracker.removePropertyChangeListener(this);

        pnlCard.toggleMode(false);
        winRateCard.toggleMode(false);
        efficiencyCard.toggleMode(false);
        riskRewardCard.toggleMode(false);
        disciplineWidget.toggleView(false);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!isLiveMode)
            return;

        if ("sessionStatsUpdated".equals(evt.getPropertyName())) {
            LiveSessionTrackerService.SessionStats stats = (LiveSessionTrackerService.SessionStats) evt.getNewValue();
            SwingUtilities.invokeLater(() -> updateLiveWidgets(stats));
        }
    }

    private void updateLiveWidgets(LiveSessionTrackerService.SessionStats stats) {
        DecimalFormat pnlFmt = new DecimalFormat("+$#,##0.00;-$#,##0.00");
        DecimalFormat pctFmt = new DecimalFormat("0.0%");
        DecimalFormat decFmt = new DecimalFormat("0.00");

        pnlCard.setLiveData(pnlFmt.format(stats.realizedPnl()),
                stats.realizedPnl().signum() >= 0 ? UIManager.getColor("app.color.positive")
                        : UIManager.getColor("app.color.negative"));

        winRateCard.setLiveData(pctFmt.format(stats.winRate()), UIManager.getColor("Label.foreground"));
        riskRewardCard.setLiveData(decFmt.format(stats.avgRiskReward()), UIManager.getColor("Label.foreground"));

        equityChart.updateData(stats.equityCurve());
    }
}