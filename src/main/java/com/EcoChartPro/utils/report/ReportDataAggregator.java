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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ReportDataAggregator {

    private static final Logger logger = LoggerFactory.getLogger(ReportDataAggregator.class);

    public record ReportData(
        OverallStats stats,
        List<PnlDistributionBin> pnlDistribution,
        Map<String, MistakeStats> mistakeAnalysis,
        List<CoachingInsight> insights
    ) {}

    private ReportDataAggregator() {}

    public static ReportData prepareReportData(ReplaySessionState state) {
        JournalAnalysisService service = new JournalAnalysisService();

        // 1. Collect trades safely
        List<Trade> allTrades = new ArrayList<>();
        if (state != null && state.symbolStates() != null) {
            state.symbolStates().values().forEach(s -> {
                if (s.tradeHistory() != null) {
                    allTrades.addAll(s.tradeHistory());
                }
            });
        }

        // 2. Basic Stats (Memory only, fast)
        BigDecimal totalPnl = allTrades.stream()
                .map(Trade::profitAndLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal currentBalance = (state != null && state.accountBalance() != null) 
                                    ? state.accountBalance() 
                                    : BigDecimal.ZERO;
        
        BigDecimal initialBalance = currentBalance.subtract(totalPnl);
        OverallStats stats = service.analyzeOverallPerformance(allTrades, initialBalance);

        List<PnlDistributionBin> pnlDistribution = service.getPnlDistribution(stats.trades(), 15);
        Map<String, MistakeStats> mistakeAnalysis = service.analyzeMistakes(stats.trades());

        // 3. Deep Analysis (Database Access - Potentially Blocking)
        List<CoachingInsight> insights = Collections.emptyList();
        try {
            GamificationService gs = GamificationService.getInstance();
            
            Optional<DataSourceManager.ChartDataSource> sourceOpt = Optional.empty();
            if (state != null && state.lastActiveSymbol() != null) {
                sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                        .filter(s -> s.symbol().equalsIgnoreCase(state.lastActiveSymbol()))
                        .findFirst();
            }

            insights = CoachingService.getInstance().analyze(
                stats.trades(), 
                gs.getOptimalTradeCount(), 
                gs.getPeakPerformanceHours(), 
                sourceOpt
            );
        } catch (Exception e) {
            // Critical: Catch DB locks or NPEs during coaching analysis so the report still loads
            logger.error("Failed to generate coaching insights. Skipping this section.", e);
            insights = new ArrayList<>();
            insights.add(new CoachingInsight("ERROR_ANALYSIS", "Analysis Incomplete", 
                "Detailed coaching insights could not be generated due to a data access error. Basic stats are shown.", 
                com.EcoChartPro.core.coaching.InsightSeverity.LOW, 
                com.EcoChartPro.core.coaching.InsightType.SEQUENCE_BASED));
        }

        return new ReportData(stats, pnlDistribution, mistakeAnalysis, insights);
    }
}