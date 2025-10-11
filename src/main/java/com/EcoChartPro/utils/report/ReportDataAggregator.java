package com.EcoChartPro.utils.report;

import com.EcoChartPro.core.coaching.CoachingInsight;
import com.EcoChartPro.core.coaching.CoachingService;
import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.journal.JournalAnalysisService.OverallStats;
import com.EcoChartPro.core.journal.JournalAnalysisService.PnlDistributionBin;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.MistakeStats;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.utils.DataSourceManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A utility class to aggregate and analyze data for report generation.
 * This centralizes the logic used by both HTML and PDF report generators.
 */
public final class ReportDataAggregator {

    /**
     * A record to hold all the analyzed data required for generating a report.
     * @param stats The overall performance statistics.
     * @param pnlDistribution The distribution of profit and loss across trades.
     * @param mistakeAnalysis A map of mistake names to their aggregated statistics.
     * @param insights A list of coaching insights based on the trading session.
     */
    public record ReportData(
        OverallStats stats,
        List<PnlDistributionBin> pnlDistribution,
        Map<String, MistakeStats> mistakeAnalysis,
        List<CoachingInsight> insights
    ) {}

    private ReportDataAggregator() {} // Prevent instantiation

    /**
     * Prepares and analyzes all data from a replay session state for reporting.
     * @param state The session state to analyze.
     * @return A ReportData object containing all the analyzed information.
     */
    public static ReportData prepareReportData(ReplaySessionState state) {
        JournalAnalysisService service = new JournalAnalysisService();

        // 1. Collect all trades from all symbols for a comprehensive report
        List<Trade> allTrades = new ArrayList<>();
        if (state.symbolStates() != null) {
            state.symbolStates().values().forEach(s -> {
                if (s.tradeHistory() != null) {
                    allTrades.addAll(s.tradeHistory());
                }
            });
        }

        // 2. Calculate initial balance and overall stats
        BigDecimal totalPnl = allTrades.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal initialBalance = state.accountBalance().subtract(totalPnl);
        OverallStats stats = service.analyzeOverallPerformance(allTrades, initialBalance);

        // 3. Perform detailed analysis
        List<PnlDistributionBin> pnlDistribution = service.getPnlDistribution(stats.trades(), 15);
        Map<String, MistakeStats> mistakeAnalysis = service.analyzeMistakes(stats.trades());

        // 4. Get coaching insights
        GamificationService gs = GamificationService.getInstance();
        Optional<DataSourceManager.ChartDataSource> sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                .filter(s -> s.symbol().equalsIgnoreCase(state.lastActiveSymbol())).findFirst();
        List<CoachingInsight> insights = CoachingService.getInstance().analyze(stats.trades(), gs.getOptimalTradeCount(), gs.getPeakPerformanceHours(), sourceOpt);

        // 5. Return the aggregated data
        return new ReportData(stats, pnlDistribution, mistakeAnalysis, insights);
    }
}