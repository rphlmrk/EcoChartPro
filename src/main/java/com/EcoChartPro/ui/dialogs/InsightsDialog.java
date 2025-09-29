package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.coaching.CoachingInsight;
import com.EcoChartPro.core.coaching.CoachingService;
import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.service.ReviewReminderService;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.core.trading.PaperTradingService;
import com.EcoChartPro.model.MistakeStats;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.Analysis.ComparativeAnalysisPanel;
import com.EcoChartPro.ui.Analysis.MistakeAnalysisPanel;
import com.EcoChartPro.ui.dialogs.PerformanceAnalyticsPanel;
import com.EcoChartPro.ui.Analysis.TradeExplorerPanel;
import com.EcoChartPro.ui.dashboard.ComprehensiveReportPanel;
import com.EcoChartPro.utils.DataSourceManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InsightsDialog extends JDialog implements PropertyChangeListener {

    private final ComprehensiveReportPanel reportPanel;
    private final JList<CoachingInsight> coachingInsightsList;
    private final PerformanceAnalyticsPanel performanceAnalyticsPanel;
    private final MistakeAnalysisPanel mistakeAnalysisPanel;
    private final ComparativeAnalysisPanel comparativeAnalysisPanel;
    private final TradeExplorerPanel tradeExplorerPanel;

    private List<Trade> allTrades; // Store trades for reminder service
    private boolean reviewReminderReset = false;

    public InsightsDialog(Frame owner) {
        super(owner, "Eco Chart Pro - Insights", false);
        setSize(1400, 800);
        setLocationRelativeTo(owner);

        JTabbedPane tabbedPane = new JTabbedPane();

        // --- TAB 1: REPORT ---
        this.reportPanel = new ComprehensiveReportPanel();
        JScrollPane reportScrollPane = new JScrollPane(this.reportPanel);
        reportScrollPane.setBorder(null);
        reportScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        tabbedPane.addTab("Report", reportScrollPane);

        // --- TAB 2: TRADE EXPLORER (Refactored) ---
        this.tradeExplorerPanel = new TradeExplorerPanel();
        tabbedPane.addTab("Trade Explorer", this.tradeExplorerPanel);
        
        // --- TAB 3: COMPARATIVE INSIGHTS ---
        this.comparativeAnalysisPanel = new ComparativeAnalysisPanel();
        tabbedPane.addTab("Comparative Insights", this.comparativeAnalysisPanel);

        // --- TAB 4: PERFORMANCE ANALYTICS ---
        this.performanceAnalyticsPanel = new PerformanceAnalyticsPanel();
        tabbedPane.addTab("Performance Analytics", this.performanceAnalyticsPanel);

        // --- TAB 5: MISTAKE ANALYSIS ---
        this.mistakeAnalysisPanel = new MistakeAnalysisPanel();
        tabbedPane.addTab("Mistake Analysis", this.mistakeAnalysisPanel);

        // --- TAB 6: COACHING ---
        this.coachingInsightsList = new JList<>();
        tabbedPane.addTab("Coaching", createCoachingPanel());

        // --- FINAL ASSEMBLY & LISTENERS ---
        setContentPane(tabbedPane);

        tabbedPane.addChangeListener(e -> {
            if (!reviewReminderReset && tabbedPane.getSelectedIndex() == 2) { // 2 is the index for "Comparative Insights"
                ReviewReminderService.getInstance().markReviewComplete(allTrades);
                reviewReminderReset = true;
            }
        });
        
        PaperTradingService.getInstance().addPropertyChangeListener(this);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
    }

    private JComponent createCoachingPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel headerLabel = new JLabel("Detected Patterns & Insights");
        headerLabel.setFont(UIManager.getFont("app.font.heading"));
        panel.add(headerLabel, BorderLayout.NORTH);

        coachingInsightsList.setCellRenderer(new CoachingInsightRenderer());
        coachingInsightsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        coachingInsightsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                CoachingInsight selected = coachingInsightsList.getSelectedValue();
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(coachingInsightsList);
        scrollPane.setBorder(UIManager.getBorder("TextField.border"));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    @Override
    public void dispose() {
        PaperTradingService.getInstance().removePropertyChangeListener(this);
        super.dispose();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("tradeHistoryUpdated".equals(evt.getPropertyName())) {
            SwingUtilities.invokeLater(() -> {
                ReplaySessionState state = PaperTradingService.getInstance().getCurrentSessionState();
                if (state != null) {
                    loadSessionData(state);
                }
            });
        }
    }

    public void loadSessionData(ReplaySessionState state) {
        if (state == null || state.tradeHistory() == null) {
            this.allTrades = Collections.emptyList();
        } else {
            this.allTrades = state.tradeHistory();
        }

        JournalAnalysisService service = new JournalAnalysisService();
        BigDecimal initialBalance = (state != null) ? state.accountBalance().subtract(allTrades.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add)) : new BigDecimal("100000");
        JournalAnalysisService.OverallStats stats = service.analyzeOverallPerformance(allTrades, initialBalance);
        
        this.reportPanel.updateData(stats, service, state);
        this.tradeExplorerPanel.loadData(allTrades);
        this.comparativeAnalysisPanel.loadData(allTrades);
        this.performanceAnalyticsPanel.loadSessionData(state);

        Map<String, MistakeStats> mistakeData = service.analyzeMistakes(allTrades);
        this.mistakeAnalysisPanel.updateData(mistakeData);

        GamificationService gamificationService = GamificationService.getInstance();
        Optional<DataSourceManager.ChartDataSource> sourceOpt = (state != null)
            ? DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.symbol().equalsIgnoreCase(state.dataSourceSymbol())).findFirst()
            : Optional.empty();

        List<CoachingInsight> insights = CoachingService.getInstance().analyze(
            allTrades,
            gamificationService.getOptimalTradeCount(),
            gamificationService.getPeakPerformanceHours(),
            sourceOpt
        );
        DefaultListModel<CoachingInsight> listModel = new DefaultListModel<>();
        listModel.addAll(insights);
        coachingInsightsList.setModel(listModel);
    }
}