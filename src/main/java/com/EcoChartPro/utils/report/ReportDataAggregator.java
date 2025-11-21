package com.EcoChartPro.utils.report;

import com.EcoChartPro.core.coaching.CoachingInsight;
import com.EcoChartPro.core.coaching.CoachingService;
import com.EcoChartPro.core.gamification.GamificationService;
import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.core.journal.JournalAnalysisService.OverallStats;
import com.EcoChartPro.core.journal.JournalAnalysisService.PnlDistributionBin;
import com.EcoChartPro.core.journal.JournalAnalysisService.TagPerformanceStats;
import com.EcoChartPro.core.journal.JournalAnalysisService.TradeMfeMae;
import com.EcoChartPro.core.state.ReplaySessionState;
import com.EcoChartPro.model.MistakeStats;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.utils.DataSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public final class ReportDataAggregator {

    private static final Logger logger = LoggerFactory.getLogger(ReportDataAggregator.class);

    public record ReportData(
        OverallStats stats,
        List<PnlDistributionBin> pnlDistribution,
        Map<String, MistakeStats> mistakeAnalysis,
        Map<String, TagPerformanceStats> strategyPerformance, // [NEW] Strategy stats
        Map<UUID, TradeMfeMae> tradeMetrics,                  // [NEW] Per-trade MFE/MAE data
        List<CoachingInsight> insights
    ) {}

    private ReportDataAggregator() {}

    public static ReportData prepareReportData(ReplaySessionState state) {
        JournalAnalysisService service = new JournalAnalysisService();

        // 1. Collect trades
        List<Trade> allTrades = new ArrayList<>();
        if (state != null && state.symbolStates() != null) {
            state.symbolStates().values().forEach(s -> {
                if (s.tradeHistory() != null) {
                    allTrades.addAll(s.tradeHistory());
                }
            });
        }

        // 2. Basic Stats
        BigDecimal totalPnl = allTrades.stream()
                .map(Trade::profitAndLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal currentBalance = (state != null && state.accountBalance() != null) 
                                    ? state.accountBalance() 
                                    : BigDecimal.ZERO;
        
        BigDecimal initialBalance = currentBalance.subtract(totalPnl);
        OverallStats stats = service.analyzeOverallPerformance(allTrades, initialBalance);

        // 3. Advanced Analytics
        List<PnlDistributionBin> pnlDistribution = service.getPnlDistribution(stats.trades(), 15);
        Map<String, MistakeStats> mistakeAnalysis = service.analyzeMistakes(stats.trades());
        Map<String, TagPerformanceStats> strategyPerformance = service.analyzePerformanceByTag(stats.trades());

        // 4. MFE/MAE Metrics (Leveraging the new Persistent Cache)
        Map<UUID, TradeMfeMae> tradeMetrics = new HashMap<>();
        try {
            Optional<DataSourceManager.ChartDataSource> sourceOpt = Optional.empty();
            if (state != null && state.lastActiveSymbol() != null) {
                sourceOpt = DataSourceManager.getInstance().getAvailableSources().stream()
                        .filter(s -> s.symbol().equalsIgnoreCase(state.lastActiveSymbol()))
                        .findFirst();
            }
            
            // If source is present, this uses the RAM/Disk cache we built in Phase 2
            if (sourceOpt.isPresent()) {
                List<TradeMfeMae> metricsList = service.calculateMfeMaeForAllTrades(stats.trades(), sourceOpt.get());
                // Map metrics back to UUIDs. Note: The list order matches the input trade list order.
                // However, to be safe, we iterate with an index since TradeMfeMae doesn't hold the ID.
                for (int i = 0; i < stats.trades().size(); i++) {
                    if (i < metricsList.size()) {
                        tradeMetrics.put(stats.trades().get(i).id(), metricsList.get(i));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to calculate trade metrics for report.", e);
        }

        // 5. Coaching Insights
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
            logger.error("Failed to generate coaching insights.", e);
            insights = new ArrayList<>();
        }

        return new ReportData(stats, pnlDistribution, mistakeAnalysis, strategyPerformance, tradeMetrics, insights);
    }
}