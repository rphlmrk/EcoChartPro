package com.EcoChartPro.ui.dialogs;

import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.ui.Analysis.TitledContentPanel;
import com.EcoChartPro.ui.dashboard.widgets.MfeMaeScatterPlot;
import com.EcoChartPro.ui.dashboard.widgets.MonthlyPerformanceChart;
import com.EcoChartPro.utils.DataSourceManager;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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

    public PerformanceAnalyticsPanel() {
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setLayout(new GridBagLayout());

        this.tradesPerDayChart = new MonthlyPerformanceChart();
        this.performanceByHourChart = new MonthlyPerformanceChart();
        this.mfeMaeScatterPlot = new MfeMaeScatterPlot();
        this.keyTakeawaysPane = new JTextPane();
        this.keyTakeawaysPane.setEditorKit(new HTMLEditorKit());
        this.keyTakeawaysPane.setEditable(false);
        this.keyTakeawaysPane.setOpaque(false);
        this.keyTakeawaysPane.setForeground(javax.swing.UIManager.getColor("Label.foreground"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);

        // --- Key Takeaways ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3; // Span all 3 columns
        gbc.weightx = 1.0;
        gbc.weighty = 0.2;
        add(new TitledContentPanel("Key Takeaways", new JScrollPane(keyTakeawaysPane)), gbc);

        // --- Performance vs. Trades per Day ---
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.33;
        gbc.weighty = 0.8;
        add(new TitledContentPanel("Performance vs. Trades per Day (by Expectancy)", tradesPerDayChart), gbc);

        // --- Performance by Hour of Day ---
        gbc.gridx = 1;
        add(new TitledContentPanel("Performance by Hour of Day (by Win Rate %)", performanceByHourChart), gbc);

        // --- MFE vs MAE Scatter Plot ---
        gbc.gridx = 2;
        add(new TitledContentPanel("MFE vs. MAE Scatter Plot", mfeMaeScatterPlot), gbc);
    }

    public void loadSessionData(ReplaySessionState state) {
        if (state == null || state.tradeHistory() == null || state.tradeHistory().isEmpty()) {
            tradesPerDayChart.updateData(Collections.emptyMap());
            performanceByHourChart.updateData(Collections.emptyMap());
            mfeMaeScatterPlot.updateData(Collections.emptyList());
            keyTakeawaysPane.setText("Not enough data available for analysis.");
            return;
        }

        List<Trade> trades = state.tradeHistory();
        GamificationService gamificationService = GamificationService.getInstance();
        int optimalCount = gamificationService.getOptimalTradeCount();
        List<Integer> peakHours = gamificationService.getPeakPerformanceHours();

        // --- Update MFE vs MAE Chart ---
        Optional<DataSourceManager.ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.symbol().equalsIgnoreCase(state.dataSourceSymbol())).findFirst();

        if (sourceOpt.isPresent()) {
            List<JournalAnalysisService.TradeMfeMae> mfeMaeData = analysisService.calculateMfeMaeForAllTrades(trades, sourceOpt.get());
            List<MfeMaeScatterPlot.TradeEfficiencyPoint> plotData = mfeMaeData.stream()
                    .map(d -> new MfeMaeScatterPlot.TradeEfficiencyPoint(d.mfe(), d.mae(), d.pnl()))
                    .collect(Collectors.toList());
            mfeMaeScatterPlot.updateData(plotData);
        } else {
            mfeMaeScatterPlot.updateData(Collections.emptyList());
        }

        // --- Update Trades Per Day Chart ---
        Map<Integer, JournalAnalysisService.PerformanceByTradeCount> perfByCount = analysisService.analyzePerformanceByTradeCount(trades);
        Map<Object, BigDecimal> chartData1 = new TreeMap<>();
        perfByCount.forEach((count, stats) -> chartData1.put(count, stats.expectancy()));
        tradesPerDayChart.updateData(chartData1);
        tradesPerDayChart.setHighlightedKeys(Set.of(optimalCount));

        // --- Update Performance By Hour Chart ---
        Map<Integer, JournalAnalysisService.PerformanceByHour> perfByHour = analysisService.analyzePerformanceByTimeOfDay(trades);
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