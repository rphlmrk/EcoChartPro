package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.coaching.CoachingInsight;
import com.EcoChartPro.core.coaching.CoachingService;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.MistakeStats;
import com.EcoChartPro.ui.Analysis.ComparativeAnalysisPanel;
import com.EcoChartPro.ui.Analysis.JournalViewPanel;
import com.EcoChartPro.ui.Analysis.MistakeAnalysisPanel;
import com.EcoChartPro.ui.Analysis.TradeExplorerPanel;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * A comprehensive dialog that provides deep insights into trading performance.
 */
public class InsightsDialog extends JDialog {

    private final JTabbedPane tabbedPane;
    private final PerformanceAnalyticsPanel performancePanel;
    private final MistakeAnalysisPanel mistakePanel;
    private final JournalViewPanel journalPanel;
    private final ComparativeAnalysisPanel comparativePanel;
    private final TradeExplorerPanel explorerPanel;
    private final JList<CoachingInsight> insightsList;
    
    // --- NEW: For loading state ---
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainPanel;
    private final JPanel loadingPanel;

    public InsightsDialog(Frame owner) {
        super(owner, "Performance Insights & Journal", true);
        setSize(1200, 800);
        setLocationRelativeTo(owner);

        // --- Main container with CardLayout for loading state ---
        mainPanel = new JPanel(cardLayout);

        // --- Loading Panel ---
        loadingPanel = new JPanel(new GridBagLayout());
        JLabel loadingLabel = new JLabel("Analyzing Performance Data...");
        loadingLabel.setFont(UIManager.getFont("app.font.heading"));
        loadingLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        loadingPanel.add(loadingLabel);

        // --- Content Panel ---
        JPanel contentPanel = new JPanel(new BorderLayout());
        
        insightsList = new JList<>();
        insightsList.setCellRenderer(new CoachingInsightRenderer());
        JScrollPane insightsScrollPane = new JScrollPane(insightsList);
        insightsScrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Add padding
        
        tabbedPane = new JTabbedPane();
        performancePanel = new PerformanceAnalyticsPanel();
        mistakePanel = new MistakeAnalysisPanel();
        journalPanel = new JournalViewPanel();
        comparativePanel = new ComparativeAnalysisPanel();
        explorerPanel = new TradeExplorerPanel();

        tabbedPane.addTab("Coaching Insights", insightsScrollPane);
        tabbedPane.addTab("Performance Analytics", performancePanel);
        tabbedPane.addTab("Trade Explorer", explorerPanel);
        tabbedPane.addTab("Trading Journal", journalPanel);
        tabbedPane.addTab("Mistake Analysis", mistakePanel);
        tabbedPane.addTab("Comparative Analysis", comparativePanel);

        contentPanel.add(tabbedPane, BorderLayout.CENTER);

        mainPanel.add(loadingPanel, "loading");
        mainPanel.add(contentPanel, "content");
        
        setContentPane(mainPanel);
    }
    
    private record AnalysisResult(
        List<CoachingInsight> insights,
        Map<String, MistakeStats> mistakeStats
    ) {}

    public void loadSessionData(ReplaySessionState state) {
        cardLayout.show(mainPanel, "loading");
        
        SwingWorker<AnalysisResult, Void> worker = new SwingWorker<>() {
            @Override
            protected AnalysisResult doInBackground() throws Exception {
                // Perform all heavy analysis in the background
                CoachingService coachingService = CoachingService.getInstance();
                com.EcoChartPro.core.journal.JournalAnalysisService journalService = new com.EcoChartPro.core.journal.JournalAnalysisService();
                
                int optimalTrades = com.EcoChartPro.core.gamification.GamificationService.getInstance().getOptimalTradeCount();
                List<Integer> peakHours = com.EcoChartPro.core.gamification.GamificationService.getInstance().getPeakPerformanceHours();
                
                List<CoachingInsight> insights = coachingService.analyze(state.tradeHistory(), optimalTrades, peakHours);
                Map<String, MistakeStats> mistakeStats = journalService.analyzeMistakes(state.tradeHistory());
                
                return new AnalysisResult(insights, mistakeStats);
            }

            @Override
            protected void done() {
                try {
                    AnalysisResult result = get();
                    
                    // Update UI components on the Event Dispatch Thread
                    insightsList.setListData(result.insights().toArray(new CoachingInsight[0]));
                    performancePanel.loadSessionData(state);
                    mistakePanel.updateData(result.mistakeStats());
                    journalPanel.loadSessionData(state.tradeHistory());
                    comparativePanel.loadData(state.tradeHistory());
                    explorerPanel.loadData(state.tradeHistory());

                    cardLayout.show(mainPanel, "content");
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    // Handle error: show an error message panel
                    cardLayout.show(mainPanel, "loading"); // Or an error panel
                    ((JLabel)loadingPanel.getComponent(0)).setText("Error loading analysis.");
                }
            }
        };
        
        worker.execute();
    }
}