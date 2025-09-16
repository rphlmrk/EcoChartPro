package com.EcoChartPro.core.coaching;

import com.EcoChartPro.core.journal.JournalAnalysisService;
import com.EcoChartPro.model.EmotionalState;
import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.model.Trade;
import com.EcoChartPro.utils.DataSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A singleton service for advanced pattern analysis on trading behavior.
 * This service acts as a "trading coach" by identifying recurring issues.
 */
public final class CoachingService {

    private static final Logger logger = LoggerFactory.getLogger(CoachingService.class);
    private static volatile CoachingService instance;

    // --- Analysis Thresholds ---
    private static final int MIN_TRADES_FOR_PATTERN = 5;
    private static final int MIN_EVENTS_FOR_SEQUENCE = 3;
    private static final double HIGH_MISTAKE_FREQUENCY_THRESHOLD = 0.60; // 60%
    private static final double HIGH_SEQUENCE_FREQUENCY_THRESHOLD = 0.75; // 75%
    private static final double ASSET_DISPROPORTION_FACTOR = 2.5;
    private static final long SEQUENCE_ANALYSIS_WINDOW_HOURS = 2;
    private static final double FATIGUE_WIN_RATE_DROP_THRESHOLD = 0.25; // 25% absolute drop in win rate
    private static final BigDecimal WINNING_EFFICIENCY_THRESHOLD = new BigDecimal("0.60");
    private static final BigDecimal LOSER_PAIN_RATIO_THRESHOLD = new BigDecimal("1.5");
    private static final int MIN_TRADES_FOR_STRATEGY_ANALYSIS = 10;
    private static final BigDecimal A_PLUS_STRATEGY_PROFIT_FACTOR = new BigDecimal("2.5");
    private static final int MIN_TRADES_FOR_CHECKLIST_ANALYSIS = 10;
    private static final BigDecimal CHECKLIST_PROFIT_FACTOR_IMPROVEMENT_THRESHOLD = new BigDecimal("1.5"); // 50% improvement
    private static final double HIGH_OUTSIDE_HOURS_FREQUENCY = 0.30; // 30%

    private CoachingService() {}

    public static CoachingService getInstance() {
        if (instance == null) {
            synchronized (CoachingService.class) {
                if (instance == null) {
                    instance = new CoachingService();
                }
            }
        }
        return instance;
    }

    /**
     * The main public method to analyze a list of trades for all discoverable patterns.
     * This version is for callers without access to the session's data source.
     *
     * @param allTrades A list of all trades to be analyzed.
     * @param optimalTradeCount The user's historically optimal number of trades per day.
     * @param peakPerformanceHours A list of UTC hours where the user performs best.
     * @return A list of CoachingInsight objects, sorted by severity.
     */
    public List<CoachingInsight> analyze(List<Trade> allTrades, int optimalTradeCount, List<Integer> peakPerformanceHours) {
        return analyze(allTrades, optimalTradeCount, peakPerformanceHours, Optional.empty());
    }

    /**
     * The main public method to analyze a list of trades for all discoverable patterns, with data source context.
     *
     * @param allTrades A list of all trades to be analyzed.
     * @param optimalTradeCount The user's historically optimal number of trades per day.
     * @param peakPerformanceHours A list of UTC hours where the user performs best.
     * @param sourceOpt The data source for the trades, needed for advanced analysis like MFE/MAE.
     * @return A list of CoachingInsight objects, sorted by severity.
     */
    public List<CoachingInsight> analyze(List<Trade> allTrades, int optimalTradeCount, List<Integer> peakPerformanceHours, Optional<DataSourceManager.ChartDataSource> sourceOpt) {
        if (allTrades == null || allTrades.size() < MIN_TRADES_FOR_PATTERN) {
            return Collections.emptyList();
        }

        List<Trade> sortedTrades = allTrades.stream()
            .sorted(Comparator.comparing(Trade::exitTime))
            .collect(Collectors.toList());

        List<CoachingInsight> insights = new ArrayList<>();
        insights.addAll(findTimeBasedPatterns(sortedTrades));
        insights.addAll(findSequencePatterns(sortedTrades));
        insights.addAll(findAssetBasedPatterns(sortedTrades));
        insights.addAll(findTradeManagementPatterns(sortedTrades, sourceOpt));
        insights.addAll(findStrategyPerformancePatterns(sortedTrades));
        findOvertrainingPattern(sortedTrades, optimalTradeCount).ifPresent(insights::add);
        findEndOfDayFatiguePattern(sortedTrades, peakPerformanceHours).ifPresent(insights::add);
        findChecklistPerformancePattern(sortedTrades).ifPresent(insights::add);
        findSessionDisciplinePattern(sortedTrades).ifPresent(insights::add);
        
        // Sort insights to show the most critical ones first.
        insights.sort(Comparator.comparing(CoachingInsight::severity).reversed());
        
        logger.info("Coaching analysis complete. Found {} insights.", insights.size());
        return insights;
    }

    private List<CoachingInsight> findTimeBasedPatterns(List<Trade> trades) {
        List<CoachingInsight> foundInsights = new ArrayList<>();
        Map<DayOfWeek, List<Trade>> tradesByDay = trades.stream()
            .collect(Collectors.groupingBy(trade -> trade.exitTime().atZone(ZoneOffset.UTC).getDayOfWeek()));

        for (Map.Entry<DayOfWeek, List<Trade>> entry : tradesByDay.entrySet()) {
            DayOfWeek day = entry.getKey();
            List<Trade> dayTrades = entry.getValue();

            if (dayTrades.size() < MIN_TRADES_FOR_PATTERN) continue;

            List<String> mistakesOnDay = dayTrades.stream()
                .filter(t -> t.identifiedMistakes() != null)
                .flatMap(t -> t.identifiedMistakes().stream())
                .collect(Collectors.toList());
            
            if (mistakesOnDay.isEmpty()) continue;

            Map.Entry<String, Long> topMistakeEntry = mistakesOnDay.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);

            if (topMistakeEntry == null) continue;
            
            String topMistake = topMistakeEntry.getKey();
            double frequency = (double) topMistakeEntry.getValue() / mistakesOnDay.size();

            if (frequency >= HIGH_MISTAKE_FREQUENCY_THRESHOLD) {
                BigDecimal pnlImpact = dayTrades.stream()
                    .filter(t -> t.identifiedMistakes() != null && t.identifiedMistakes().contains(topMistake))
                    .map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add);
                
                if (pnlImpact.signum() < 0) {
                    String title = String.format("Costly %ss", day.toString().charAt(0) + day.toString().substring(1).toLowerCase());
                    String description = String.format("On %ss, '%s' is your most frequent mistake, occurring %.0f%% of the time and costing you $%.2f.",
                        day, topMistake, frequency * 100, pnlImpact.abs());
                    
                    foundInsights.add(new CoachingInsight("TIME_" + day + "_" + topMistake.replaceAll("\\s", ""),
                        title, description, InsightSeverity.HIGH, InsightType.TIME_BASED));
                }
            }
        }
        return foundInsights;
    }
    
    private List<CoachingInsight> findSequencePatterns(List<Trade> sortedTrades) {
        long losingTradeCount = sortedTrades.stream().filter(t -> t.profitAndLoss().signum() < 0).count();
        if (losingTradeCount == 0) {
            return Collections.emptyList();
        }

        BigDecimal avgLoss = sortedTrades.stream()
            .filter(t -> t.profitAndLoss().signum() < 0)
            .map(t -> t.profitAndLoss().abs())
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(losingTradeCount), 2, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ONE) < 0) return Collections.emptyList();

        BigDecimal significantLossThreshold = avgLoss.multiply(new BigDecimal("1.5"));
        int significantLossEvents = 0;
        int impulsiveTradesAfterLoss = 0;

        for (int i = 0; i < sortedTrades.size() - 1; i++) {
            Trade currentTrade = sortedTrades.get(i);
            if (currentTrade.profitAndLoss().signum() < 0 && currentTrade.profitAndLoss().abs().compareTo(significantLossThreshold) >= 0) {
                significantLossEvents++;
                Trade nextTrade = sortedTrades.get(i + 1);

                long hoursBetween = Duration.between(currentTrade.exitTime(), nextTrade.entryTime()).toHours();
                if (hoursBetween <= SEQUENCE_ANALYSIS_WINDOW_HOURS) {
                    if (nextTrade.emotionalState() == EmotionalState.REVENGE_TRADING || nextTrade.emotionalState() == EmotionalState.FOMO) {
                        impulsiveTradesAfterLoss++;
                    }
                }
            }
        }

        if (significantLossEvents >= MIN_EVENTS_FOR_SEQUENCE) {
            double rate = (double) impulsiveTradesAfterLoss / significantLossEvents;
            if (rate >= HIGH_SEQUENCE_FREQUENCY_THRESHOLD) {
                String title = "Post-Loss Impulsivity";
                String description = String.format("After a significant loss, you enter an impulsive trade %.0f%% of the time. Consider a mandatory break after large losses.", rate * 100);
                return List.of(new CoachingInsight("SEQ_POST_LOSS_IMPULSE", title, description, InsightSeverity.HIGH, InsightType.SEQUENCE_BASED));
            }
        }

        return Collections.emptyList();
    }
    
    private List<CoachingInsight> findAssetBasedPatterns(List<Trade> trades) {
        List<CoachingInsight> foundInsights = new ArrayList<>();
        Map<String, Long> overallMistakeCounts = trades.stream()
            .filter(t -> t.identifiedMistakes() != null)
            .flatMap(t -> t.identifiedMistakes().stream()).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        long totalMistakes = overallMistakeCounts.values().stream().mapToLong(Long::longValue).sum();
        if (totalMistakes == 0) return Collections.emptyList();

        Map<Symbol, List<Trade>> tradesBySymbol = trades.stream().collect(Collectors.groupingBy(Trade::symbol));

        for (Map.Entry<Symbol, List<Trade>> entry : tradesBySymbol.entrySet()) {
            Symbol symbol = entry.getKey();
            List<Trade> symbolTrades = entry.getValue();
            if (symbolTrades.size() < MIN_TRADES_FOR_PATTERN) continue;

            Map<String, Long> symbolMistakeCounts = symbolTrades.stream()
                .filter(t -> t.identifiedMistakes() != null)
                .flatMap(t -> t.identifiedMistakes().stream()).collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            long totalSymbolMistakes = symbolMistakeCounts.values().stream().mapToLong(Long::longValue).sum();
            if (totalSymbolMistakes == 0) continue;
            
            for (Map.Entry<String, Long> mistakeEntry : symbolMistakeCounts.entrySet()) {
                String mistake = mistakeEntry.getKey();
                double baselineFrequency = (double) overallMistakeCounts.getOrDefault(mistake, 0L) / totalMistakes;
                double symbolFrequency = (double) mistakeEntry.getValue() / totalSymbolMistakes;

                if (symbolFrequency > baselineFrequency * ASSET_DISPROPORTION_FACTOR && symbolFrequency > 0.25) { // Is disproportionate AND makes up at least 25% of mistakes on this asset
                    String title = String.format("Habit on %s", symbol.name());
                    String description = String.format("You tend to '%s' %.0f%% of the time on %s, which is significantly higher than your average. Review your rules for this asset.", mistake, symbolFrequency * 100, symbol.name());
                    foundInsights.add(new CoachingInsight("ASSET_" + symbol.name() + "_" + mistake.replaceAll("\\s", ""),
                        title, description, InsightSeverity.MEDIUM, InsightType.ASSET_BASED));
                }
            }
        }
        return foundInsights;
    }
    
    private Optional<CoachingInsight> findOvertrainingPattern(List<Trade> allTrades, int optimalTradeCount) {
        // Group trades by day
        Map<LocalDate, List<Trade>> tradesByDay = allTrades.stream()
            .collect(Collectors.groupingBy(trade -> trade.exitTime().atZone(ZoneOffset.UTC).toLocalDate()));

        List<Trade> excessTrades = new ArrayList<>();
        for (List<Trade> dailyTrades : tradesByDay.values()) {
            if (dailyTrades.size() > optimalTradeCount) {
                // Sort trades within the day to correctly identify the excess ones
                dailyTrades.sort(Comparator.comparing(Trade::entryTime));
                excessTrades.addAll(dailyTrades.subList(optimalTradeCount, dailyTrades.size()));
            }
        }

        if (excessTrades.size() < MIN_TRADES_FOR_PATTERN) {
            return Optional.empty();
        }

        BigDecimal excessPnl = excessTrades.stream()
            .map(Trade::profitAndLoss)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // If the cumulative PNL of excess trades is negative
        if (excessPnl.signum() < 0) {
            String title = "Diminishing Returns Detected";
            String description = String.format(
                "Your data shows you are most profitable within your first %d trades of the day. Trades taken beyond this limit have resulted in a cumulative loss of $%.2f. Consider setting a daily trade limit.",
                optimalTradeCount,
                excessPnl.abs()
            );
            CoachingInsight insight = new CoachingInsight(
                "OVERTRAINING_DIMINISHING_RETURNS",
                title,
                description,
                InsightSeverity.HIGH,
                InsightType.SEQUENCE_BASED 
            );
            return Optional.of(insight);
        }
        return Optional.empty();
    }
    
    private Optional<CoachingInsight> findEndOfDayFatiguePattern(List<Trade> allTrades, List<Integer> peakPerformanceHours) {
        if (peakPerformanceHours == null || peakPerformanceHours.isEmpty()) {
            return Optional.empty();
        }

        Set<Integer> peakHoursSet = new HashSet<>(peakPerformanceHours);
        Map<Boolean, List<Trade>> partitionedTrades = allTrades.stream()
            .collect(Collectors.partitioningBy(
                trade -> peakHoursSet.contains(trade.entryTime().atZone(ZoneOffset.UTC).getHour())
            ));

        List<Trade> peakTrades = partitionedTrades.get(true);
        List<Trade> offPeakTrades = partitionedTrades.get(false);

        if (peakTrades.size() < MIN_TRADES_FOR_PATTERN || offPeakTrades.size() < MIN_TRADES_FOR_PATTERN) {
            return Optional.empty();
        }

        double peakWinRate = (double) peakTrades.stream().filter(t -> t.profitAndLoss().signum() > 0).count() / peakTrades.size();
        double offPeakWinRate = (double) offPeakTrades.stream().filter(t -> t.profitAndLoss().signum() > 0).count() / offPeakTrades.size();

        if (peakWinRate - offPeakWinRate >= FATIGUE_WIN_RATE_DROP_THRESHOLD) {
            int latestPeakHour = Collections.max(peakPerformanceHours);
            String timeString = LocalTime.of(latestPeakHour, 0).format(DateTimeFormatter.ofPattern("h:00 a"));

            String title = "End-of-Day Fatigue";
            String description = String.format(
                "Your win rate is %.0f%% lower on trades taken after %s. This may indicate decision fatigue. Consider finishing your trading day earlier.",
                (peakWinRate - offPeakWinRate) * 100,
                timeString
            );
            CoachingInsight insight = new CoachingInsight(
                "FATIGUE_END_OF_DAY",
                title,
                description,
                InsightSeverity.MEDIUM,
                InsightType.TIME_BASED
            );
            return Optional.of(insight);
        }

        return Optional.empty();
    }

    private List<CoachingInsight> findTradeManagementPatterns(List<Trade> trades, Optional<DataSourceManager.ChartDataSource> sourceOpt) {
        if (sourceOpt.isEmpty()) {
            return Collections.emptyList();
        }

        List<Trade> winners = trades.stream().filter(t -> t.profitAndLoss().signum() > 0).collect(Collectors.toList());
        List<Trade> losers = trades.stream().filter(t -> t.profitAndLoss().signum() < 0).collect(Collectors.toList());

        if (winners.size() < MIN_TRADES_FOR_PATTERN || losers.size() < MIN_TRADES_FOR_PATTERN) {
            return Collections.emptyList();
        }

        JournalAnalysisService analysisService = new JournalAnalysisService();
        JournalAnalysisService.TradeEfficiencyStats efficiencyStats = analysisService.calculateTradeEfficiency(trades, sourceOpt.get());
        List<CoachingInsight> foundInsights = new ArrayList<>();

        // Check for "Leaving Money on the Table"
        if (efficiencyStats.averageWinningTradeEfficiency().compareTo(WINNING_EFFICIENCY_THRESHOLD) < 0) {
            String description = String.format(
                "Your winning trades reach an average peak profit of $%.2f, but you are only capturing $%.2f of it (%.0f%% efficiency). Consider using a trailing stop or setting higher profit targets to maximize your winners.",
                efficiencyStats.averageWinnerMfe(),
                efficiencyStats.averageWinnerPnl(),
                efficiencyStats.averageWinningTradeEfficiency().multiply(BigDecimal.valueOf(100))
            );
            foundInsights.add(new CoachingInsight(
                "TRADE_MGMT_PREMATURE_PROFIT",
                "Premature Profit-Taking",
                description,
                InsightSeverity.MEDIUM,
                InsightType.SEQUENCE_BASED
            ));
        }

        // Check for "Letting Losers Run"
        if (efficiencyStats.averageLoserPainRatio().compareTo(LOSER_PAIN_RATIO_THRESHOLD) > 0) {
            String description = String.format(
                "On average, your losing trades go against you by $%.2f before you exit, for an average final loss of $%.2f. This suggests you are enduring significant drawdown before accepting the loss. Respect your initial stop-loss to protect your capital.",
                efficiencyStats.averageLoserMae(),
                efficiencyStats.averageLoserPnl()
            );
            foundInsights.add(new CoachingInsight(
                "TRADE_MGMT_LETTING_LOSERS_RUN",
                "Hesitation on Losing Trades",
                description,
                InsightSeverity.HIGH,
                InsightType.SEQUENCE_BASED
            ));
        }

        return foundInsights;
    }
    
    private List<CoachingInsight> findStrategyPerformancePatterns(List<Trade> trades) {
        JournalAnalysisService analysisService = new JournalAnalysisService();
        Map<String, JournalAnalysisService.TagPerformanceStats> tagStats = analysisService.analyzePerformanceByTag(trades);

        if (tagStats.isEmpty()) {
            return Collections.emptyList();
        }

        List<CoachingInsight> foundInsights = new ArrayList<>();

        // Find the "A+ Setup"
        Optional<JournalAnalysisService.TagPerformanceStats> bestSetup = tagStats.values().stream()
            .filter(stats -> stats.tradeCount() >= MIN_TRADES_FOR_STRATEGY_ANALYSIS)
            .filter(stats -> stats.profitFactor().compareTo(A_PLUS_STRATEGY_PROFIT_FACTOR) >= 0)
            .max(Comparator.comparing(JournalAnalysisService.TagPerformanceStats::profitFactor));

        bestSetup.ifPresent(stats -> {
            String description = String.format(
                "Your '%s' strategy has an exceptional profit factor of %.2f over %d trades. This is your strongest performing setup. Focus on mastering its execution and finding more opportunities that fit its criteria.",
                stats.tag(), stats.profitFactor(), stats.tradeCount()
            );
            foundInsights.add(new CoachingInsight(
                "STRATEGY_A_PLUS_" + stats.tag().replaceAll("\\s", ""),
                "A+ Setup Identified: " + stats.tag(),
                description,
                InsightSeverity.LOW, // Positive insights are low severity
                InsightType.ASSET_BASED // Using ASSET_BASED as it's strategy-related
            ));
        });

        // Find underperforming strategies
        List<JournalAnalysisService.TagPerformanceStats> underperformingSetups = tagStats.values().stream()
            .filter(stats -> stats.tradeCount() >= MIN_TRADES_FOR_STRATEGY_ANALYSIS)
            .filter(stats -> stats.expectancy().signum() < 0)
            .collect(Collectors.toList());
            
        for (JournalAnalysisService.TagPerformanceStats stats : underperformingSetups) {
            String description = String.format(
                "Your '%s' strategy has a negative expectancy, resulting in a net loss over the last %d trades. It may be time to review the rules for this setup or pause trading it until you can refine your edge.",
                stats.tag(), stats.tradeCount()
            );
            foundInsights.add(new CoachingInsight(
                "STRATEGY_UNDERPERFORMING_" + stats.tag().replaceAll("\\s", ""),
                "Strategy Review Needed: " + stats.tag(),
                description,
                InsightSeverity.MEDIUM,
                InsightType.ASSET_BASED
            ));
        }

        return foundInsights;
    }

    private Optional<CoachingInsight> findChecklistPerformancePattern(List<Trade> allTrades) {
        Map<Boolean, List<Trade>> partitionedTrades = allTrades.stream()
            .collect(Collectors.partitioningBy(trade -> trade.checklistId() != null));

        List<Trade> tradesWithChecklist = partitionedTrades.get(true);
        List<Trade> tradesWithoutChecklist = partitionedTrades.get(false);

        if (tradesWithChecklist.size() < MIN_TRADES_FOR_CHECKLIST_ANALYSIS || tradesWithoutChecklist.size() < MIN_TRADES_FOR_CHECKLIST_ANALYSIS) {
            return Optional.empty();
        }

        JournalAnalysisService analysisService = new JournalAnalysisService();
        // Starting balance doesn't affect profit factor or expectancy calculation
        JournalAnalysisService.OverallStats checklistStats = analysisService.analyzeOverallPerformance(tradesWithChecklist, BigDecimal.ZERO);
        JournalAnalysisService.OverallStats noChecklistStats = analysisService.analyzeOverallPerformance(tradesWithoutChecklist, BigDecimal.ZERO);

        BigDecimal checklistPf = checklistStats.profitFactor();
        BigDecimal noChecklistPf = noChecklistStats.profitFactor();

        // Condition: Both must have positive profit factors for a meaningful ratio comparison,
        // and the checklist performance must be significantly better.
        if (checklistPf.compareTo(BigDecimal.ZERO) > 0 && noChecklistPf.compareTo(BigDecimal.ZERO) > 0) {
            if (checklistPf.compareTo(noChecklistPf.multiply(CHECKLIST_PROFIT_FACTOR_IMPROVEMENT_THRESHOLD)) >= 0) {
                String description = String.format(
                    "Your trades using a checklist have a profit factor of %.2f, compared to %.2f for trades without one. Following your process is clearly improving your results. Stick to the plan!",
                    checklistPf, noChecklistPf
                );
                CoachingInsight insight = new CoachingInsight(
                    "BEHAVIOR_CHECKLIST_EDGE",
                    "Process is Your Edge",
                    description,
                    InsightSeverity.LOW, // Positive reinforcement
                    InsightType.SEQUENCE_BASED // Relates to the sequence of actions before a trade
                );
                return Optional.of(insight);
            }
        }

        // Alternative strong condition: Checklist trades are profitable (positive expectancy) while non-checklist trades are not.
        if (checklistStats.expectancy().compareTo(BigDecimal.ZERO) > 0 && noChecklistStats.expectancy().compareTo(BigDecimal.ZERO) <= 0) {
             String description = String.format(
                "Trades executed with a checklist have a positive expectancy of $%.2f, while trades without one are unprofitable. This shows that your structured approach is critical to your success.",
                checklistStats.expectancy()
            );
            return Optional.of(new CoachingInsight("BEHAVIOR_CHECKLIST_PROFITABILITY", "Discipline Creates Profitability", description, InsightSeverity.MEDIUM, InsightType.SEQUENCE_BASED));
        }
        return Optional.empty();
    }
    
    private Optional<CoachingInsight> findSessionDisciplinePattern(List<Trade> allTrades) {
        long tradesOutsideHours = allTrades.stream()
            .filter(t -> t.tags() != null && t.tags().contains("Out-Side-Trading-Hours"))
            .count();

        if (allTrades.isEmpty() || tradesOutsideHours < MIN_TRADES_FOR_PATTERN) {
            return Optional.empty();
        }

        double frequency = (double) tradesOutsideHours / allTrades.size();

        if (frequency >= HIGH_OUTSIDE_HOURS_FREQUENCY) {
            BigDecimal pnlImpact = allTrades.stream()
                .filter(t -> t.tags() != null && t.tags().contains("Out-Side-Trading-Hours"))
                .map(Trade::profitAndLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (pnlImpact.signum() < 0) {
                String title = "Trading Outside Preferred Hours";
                String description = String.format(
                    "You take %.0f%% of your trades outside of your preferred sessions. This has resulted in a cumulative loss of $%.2f. Consider sticking to your defined trading hours.",
                    frequency * 100,
                    pnlImpact.abs()
                );

                CoachingInsight insight = new CoachingInsight(
                    "BEHAVIOR_OUTSIDE_HOURS",
                    title,
                    description,
                    InsightSeverity.MEDIUM,
                    InsightType.TIME_BASED
                );
                return Optional.of(insight);
            }
        }

        return Optional.empty();
    }
}