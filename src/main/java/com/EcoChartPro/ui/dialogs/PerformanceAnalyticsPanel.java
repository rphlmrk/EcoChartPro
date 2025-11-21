package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.coaching.Challenge;
import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.Analysis.TitledContentPanel;
import com.EcoChartPro.ui.dashboard.widgets.HistogramChart;
import com.EcoChartPro.ui.dashboard.widgets.MfeMaeScatterPlot;
import com.EcoChartPro.ui.dashboard.widgets.MonthlyPerformanceChart;
import com.EcoChartPro.utils.DataSourceManager;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A panel that displays detailed performance analytics, including charts on
 * trade frequency and time-of-day performance.
 */
public class PerformanceAnalyticsPanel extends JPanel {

    private final JournalAnalysisService analysisService = new JournalAnalysisService();
    private final MonthlyPerformanceChart tradesPerDayChart;
    private final MonthlyPerformanceChart performanceByHourChart;
    private final JTextPane keyTakeawaysPane;
    private final MfeMaeScatterPlot mfeMaeScatterPlot;
    private final HistogramChart pnlDistributionChart;
    private final TitledContentPanel challengePanel;
    private final JTextArea challengeDescriptionArea;

    public PerformanceAnalyticsPanel() {
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setLayout(new GridBagLayout());

        this.tradesPerDayChart = new MonthlyPerformanceChart();
        this.performanceByHourChart = new MonthlyPerformanceChart();
        this.mfeMaeScatterPlot = new MfeMaeScatterPlot();
        this.pnlDistributionChart = new HistogramChart();
        this.keyTakeawaysPane = new JTextPane();
        this.keyTakeawaysPane.setEditorKit(new HTMLEditorKit());
        this.keyTakeawaysPane.setEditable(false);
        this.keyTakeawaysPane.setOpaque(false);
        this.keyTakeawaysPane.setForeground(javax.swing.UIManager.getColor("Label.foreground"));
        
        this.challengeDescriptionArea = new JTextArea();
        this.challengeDescriptionArea.setOpaque(false);
        this.challengeDescriptionArea.setEditable(false);
        this.challengeDescriptionArea.setLineWrap(true);
        this.challengeDescriptionArea.setWrapStyleWord(true);
        this.challengeDescriptionArea.setFont(UIManager.getFont("app.font.widget_content"));
        this.challengeDescriptionArea.setForeground(UIManager.getColor("Label.foreground"));
        this.challengePanel = new TitledContentPanel("Active Daily Challenge", challengeDescriptionArea);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);

        // --- Row 0: Daily Challenge ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3; // Span all 3 columns
        gbc.weightx = 1.0;
        gbc.weighty = 0; // Don't take up much vertical space
        add(challengePanel, gbc);

        // --- Row 1: Key Takeaways ---
        gbc.gridy = 1;
        gbc.weighty = 0.2;
        add(new TitledContentPanel("Key Takeaways", new JScrollPane(keyTakeawaysPane)), gbc);

        // --- Row 2: Top Charts ---
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0.33;
        gbc.weighty = 0.4;
        add(new TitledContentPanel("Performance vs. Trades per Day (by Expectancy)", tradesPerDayChart), gbc);

        gbc.gridx = 1;
        add(new TitledContentPanel("Performance by Hour of Day (by Win Rate %)", performanceByHourChart), gbc);

        gbc.gridx = 2;
        add(new TitledContentPanel("MFE vs. MAE Scatter Plot", mfeMaeScatterPlot), gbc);
        
        // --- Row 3: P&L Distribution Histogram ---
        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 3; // Span all columns
        gbc.weightx = 1.0;
        gbc.weighty = 0.4;
        add(new TitledContentPanel("P&L Distribution (# of Trades)", pnlDistributionChart), gbc);
    }

    public void loadSessionData(ReplaySessionState state) {
        // [FIX] Handle null state gracefully to prevent UI hangs
        if (state == null) {
            tradesPerDayChart.updateData(Collections.emptyMap());
            performanceByHourChart.updateData(Collections.emptyMap());
            mfeMaeScatterPlot.updateData(Collections.emptyList());
            pnlDistributionChart.updateData(Collections.emptyList());
            keyTakeawaysPane.setText("");
            challengePanel.setVisible(false);
            return;
        }

        GamificationService gamificationService = GamificationService.getInstance();
        
        // --- Update Daily Challenge ---
        Optional<Challenge> challengeOpt = gamificationService.getActiveDailyChallenge();
        if (challengeOpt.isPresent()) {
            Challenge challenge = challengeOpt.get();
            String title = String.format("Active Daily Challenge: %s (+%d XP)", challenge.title(), challenge.xpReward());
            challengePanel.setVisible(true);
            challengePanel.setTitle(title);
            String challengeText = challenge.isComplete() ? "Completed Today! " + challenge.description() : challenge.description();
            challengeDescriptionArea.setText(challengeText);
        } else {
            challengePanel.setVisible(false);
        }

        // Collect all trades from the multi-symbol state
        List<Trade> allTrades = new ArrayList<>();
        if (state.symbolStates() != null) {
            state.symbolStates().values().forEach(s -> {
                if (s.tradeHistory() != null) {
                    allTrades.addAll(s.tradeHistory());
                }
            });
        }
        
        if (allTrades.isEmpty()) {
            tradesPerDayChart.updateData(Collections.emptyMap());
            performanceByHourChart.updateData(Collections.emptyMap());
            mfeMaeScatterPlot.updateData(Collections.emptyList());
            pnlDistributionChart.updateData(Collections.emptyList());
            keyTakeawaysPane.setText("Not enough data available for analysis.");
            return;
        }

        int optimalCount = gamificationService.getOptimalTradeCount();
        List<Integer> peakHours = gamificationService.getPeakPerformanceHours();

        // --- Update P&L Distribution Chart ---
        List<JournalAnalysisService.PnlDistributionBin> pnlDistribution = analysisService.getPnlDistribution(allTrades, 20);
        pnlDistributionChart.updateData(pnlDistribution);

        // --- Update MFE vs MAE Chart ---
        // Use lastActiveSymbol to find the correct data source for analysis
        // [FIX] Safe handling for lastActiveSymbol
        Optional<DataSourceManager.ChartDataSource> sourceOpt = Optional.empty();
        if (state.lastActiveSymbol() != null) {
            sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                    .filter(s -> s.symbol().equalsIgnoreCase(state.lastActiveSymbol())).findFirst();
        }

        if (sourceOpt.isPresent()) {
            List<JournalAnalysisService.TradeMfeMae> mfeMaeData = analysisService.calculateMfeMaeForAllTrades(allTrades, sourceOpt.get());
            List<MfeMaeScatterPlot.TradeEfficiencyPoint> plotData = mfeMaeData.stream()
                    .map(d -> new MfeMaeScatterPlot.TradeEfficiencyPoint(d.mfe(), d.mae(), d.pnl()))
                    .collect(Collectors.toList());
            mfeMaeScatterPlot.updateData(plotData);
        } else {
            mfeMaeScatterPlot.updateData(Collections.emptyList());
        }

        // --- Update Trades Per Day Chart ---
        Map<Integer, JournalAnalysisService.PerformanceByTradeCount> perfByCount = analysisService.analyzePerformanceByTradeCount(allTrades);
        Map<Object, BigDecimal> chartData1 = new TreeMap<>();
        perfByCount.forEach((count, stats) -> chartData1.put(count, stats.expectancy()));
        tradesPerDayChart.updateData(chartData1);
        tradesPerDayChart.setHighlightedKeys(Set.of(optimalCount));

        // --- Update Performance By Hour Chart ---
        Map<Integer, JournalAnalysisService.PerformanceByHour> perfByHour = analysisService.analyzePerformanceByTimeOfDay(allTrades);
        Map<Object, BigDecimal> chartData2 = new LinkedHashMap<>();
        DateTimeFormatter hourFormatter = DateTimeFormatter.ofPattern("ha");
        for (int i = 0; i < 24; i++) {
            String hourKey = LocalTime.of(i, 0).format(hourFormatter);
            if (perfByHour.containsKey(i)) {
                JournalAnalysisService.PerformanceByHour stats = perfByHour.get(i);
                chartData2.put(hourKey, BigDecimal.valueOf(stats.winRate() * 100));
            } else {
                chartData2.put(hourKey, BigDecimal.ZERO); // Use zero for hours with no trades
            }
        }
        performanceByHourChart.updateData(chartData2);
        Set<Object> peakHourKeys = peakHours.stream()
                .map(hour -> LocalTime.of(hour, 0).format(hourFormatter))
                .collect(Collectors.toSet());
        performanceByHourChart.setHighlightedKeys(peakHourKeys);

        // --- Update Key Takeaways ---
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family: sans-serif; font-size: 11pt; padding: 5px;'>");
        sb.append("<b>Finding:</b> Your profitability (expectancy) is highest when taking <b>").append(optimalCount).append(" trades</b> per day.<br><br>");

        if (!peakHours.isEmpty()) {
            String peakHoursString = peakHours.stream()
                    .sorted()
                    .map(hour -> LocalTime.of(hour, 0).format(hourFormatter))
                    .collect(Collectors.joining(", "));
            sb.append("<b>Finding:</b> Your performance is strongest during these hours: <b>").append(peakHoursString).append("</b> (UTC).");
        } else {
            sb.append("<b>Finding:</b> No consistent peak performance hours have been identified yet. More trading data is needed.");
        }
        sb.append("</body></html>");
        keyTakeawaysPane.setText(sb.toString());
        keyTakeawaysPane.setCaretPosition(0);
    }
}