package com.EcoChartPro.core.journal;

import com.EcoChartPro.model.*;
import com.EcoChartPro.utils.DataSourceManager;
import com.EcoChartPro.utils.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provides methods to analyze a list of trades and generate statistical summaries.
 */
public class JournalAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(JournalAnalysisService.class);

    // --- Data Transfer Objects (DTOs) for Analysis Results ---

    public record DailyStats(LocalDate date, int tradeCount, BigDecimal totalPnl, double winRatio, double planFollowedPercentage) {}
    public record WeeklyStats(int weekOfYear, int tradeCount, BigDecimal totalPnl, double winRatio, double planFollowedPercentage) {}
    public record DateRange(LocalDate minDate, LocalDate maxDate) {}
    public record PerformanceByTradeCount(int tradesPerDay, int dayCount, BigDecimal totalPnl, double winRate, BigDecimal avgPnlPerDay, BigDecimal expectancy) {}
    public record PerformanceByHour(int hourOfDay, int tradeCount, BigDecimal totalPnl, double winRate, BigDecimal expectancy) {}
    public record TradeEfficiencyStats(
        BigDecimal averageWinningTradeEfficiency,
        BigDecimal averageLoserPainRatio,
        BigDecimal averageWinnerPnl,
        BigDecimal averageWinnerMfe,
        BigDecimal averageLoserPnl, // Stored as a positive value
        BigDecimal averageLoserMae
    ) {}
    public record TradeMfeMae(BigDecimal mfe, BigDecimal mae, BigDecimal pnl) {}

    /**
     * A DTO holding key performance metrics for a specific user-defined trade tag.
     */
    public record TagPerformanceStats(
        String tag,
        int tradeCount,
        double winRate,
        BigDecimal profitFactor,
        BigDecimal expectancy
    ) {}

    public record OverallStats(
            List<Trade> trades,
            BigDecimal startBalance,
            BigDecimal endBalance,
            BigDecimal totalPnl,
            int totalTrades,
            int winningTrades,
            int losingTrades,
            double winRate,
            BigDecimal avgWinPnl,
            BigDecimal avgLossPnl,
            double avgRiskReward,
            BigDecimal profitFactor,
            BigDecimal expectancy,
            Duration avgTradeDuration,
            BigDecimal totalFees,
            List<EquityPoint> equityCurve,
            BigDecimal maxDrawdown,
            BigDecimal maxRunup
    ) {}
    public record EquityPoint(Instant timestamp, BigDecimal cumulativeBalance) {}


    // --- Public Analysis Methods ---

    /**
     * Analyzes performance metrics for each unique tag found in the provided list of trades.
     *
     * @param trades The list of all trades to analyze.
     * @return A map where the key is the tag name and the value is a {@link TagPerformanceStats} object with the calculated metrics.
     */
    public Map<String, TagPerformanceStats> analyzePerformanceByTag(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. Group trades by each of their tags. A single trade can belong to multiple groups.
        Map<String, List<Trade>> tradesByTag = new HashMap<>();
        for (Trade trade : trades) {
            if (trade.tags() != null && !trade.tags().isEmpty()) {
                for (String tag : trade.tags()) {
                    if (tag != null && !tag.isBlank()) {
                        tradesByTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(trade);
                    }
                }
            }
        }

        // 2. Analyze the trades for each tag to calculate performance stats.
        Map<String, TagPerformanceStats> results = new HashMap<>();
        for (Map.Entry<String, List<Trade>> entry : tradesByTag.entrySet()) {
            String tag = entry.getKey();
            List<Trade> tagTrades = entry.getValue();

            int tradeCount = tagTrades.size();
            double winRate = calculateWinRatio(tagTrades);
            BigDecimal profitFactor = calculateProfitFactor(tagTrades);
            BigDecimal expectancy = calculateExpectancy(tagTrades);

            results.put(tag, new TagPerformanceStats(tag, tradeCount, winRate, profitFactor, expectancy));
        }

        return results;
    }

    /**
     * Analyzes a list of trades and groups the statistics by calendar day.
     */
    public Map<LocalDate, DailyStats> analyzeTradesByDay(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<LocalDate, List<Trade>> tradesByDay = trades.stream()
                .collect(Collectors.groupingBy(trade -> trade.exitTime().atZone(ZoneOffset.UTC).toLocalDate()));

        return tradesByDay.entrySet().stream()
                .map(entry -> {
                    List<Trade> dailyTrades = entry.getValue();
                    BigDecimal totalPnl = calculateTotalPnl(dailyTrades);
                    double winRatio = calculateWinRatio(dailyTrades);
                    double planPercentage = calculatePlanFollowedPercentage(dailyTrades);
                    return new DailyStats(entry.getKey(), dailyTrades.size(), totalPnl, winRatio, planPercentage);
                })
                .collect(Collectors.toMap(DailyStats::date, Function.identity()));
    }

    /**
     * Analyzes a list of trades and groups the statistics by week of the year.
     */
    public Map<Integer, WeeklyStats> analyzeTradesByWeek(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return Collections.emptyMap();
        }
        WeekFields weekFields = WeekFields.of(Locale.US);

        Map<Integer, List<Trade>> tradesByWeek = trades.stream()
                .collect(Collectors.groupingBy(trade -> trade.exitTime().atZone(ZoneOffset.UTC).get(weekFields.weekOfWeekBasedYear())));

        return tradesByWeek.entrySet().stream()
                .map(entry -> {
                    List<Trade> weeklyTrades = entry.getValue();
                    BigDecimal totalPnl = calculateTotalPnl(weeklyTrades);
                    double winRatio = calculateWinRatio(weeklyTrades);
                    double planPercentage = calculatePlanFollowedPercentage(weeklyTrades);
                    return new WeeklyStats(entry.getKey(), weeklyTrades.size(), totalPnl, winRatio, planPercentage);
                })
                .collect(Collectors.toMap(WeeklyStats::weekOfYear, Function.identity()));
    }
    
    /**
     * Calculates the daily profit and loss for a given list of trades.
     * @param trades The list of trades to analyze.
     * @return A map where the key is the date and the value is the total PNL for that day.
     */
    public Map<LocalDate, BigDecimal> calculateDailyPnl(List<Trade> trades) {
        if (trades == null) {
            return Collections.emptyMap();
        }
        return trades.stream()
            .collect(Collectors.groupingBy(
                trade -> trade.exitTime().atZone(ZoneOffset.UTC).toLocalDate(),
                Collectors.mapping(Trade::profitAndLoss, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
            ));
    }

    /**
     * Analyzes trading performance based on the number of trades taken per day.
     * @param trades The list of all trades to analyze.
     * @return A map where the key is the number of trades per day, and the value contains aggregated performance metrics.
     */
    public Map<Integer, PerformanceByTradeCount> analyzePerformanceByTradeCount(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. Group all trades by their execution day
        Map<LocalDate, List<Trade>> tradesByDay = trades.stream()
                .collect(Collectors.groupingBy(trade -> trade.exitTime().atZone(ZoneOffset.UTC).toLocalDate()));

        // 2. Group the days by the number of trades executed on them
        Map<Integer, List<List<Trade>>> daysGroupedByTradeCount = tradesByDay.values().stream()
                .collect(Collectors.groupingBy(List::size));

        // 3. Process each group to calculate metrics
        return daysGroupedByTradeCount.entrySet().stream()
                .map(entry -> {
                    int tradesPerDay = entry.getKey();
                    List<List<Trade>> daysInGroup = entry.getValue();
                    int dayCount = daysInGroup.size();

                    // Flatten the list of daily trades into a single list for this group
                    List<Trade> allTradesInGroup = daysInGroup.stream()
                            .flatMap(List::stream)
                            .collect(Collectors.toList());

                    BigDecimal totalPnl = calculateTotalPnl(allTradesInGroup);
                    double winRatio = calculateWinRatio(allTradesInGroup);
                    BigDecimal expectancy = calculateExpectancy(allTradesInGroup);
                    BigDecimal avgPnlPerDay = totalPnl.divide(BigDecimal.valueOf(dayCount), 2, RoundingMode.HALF_UP);

                    return new PerformanceByTradeCount(tradesPerDay, dayCount, totalPnl, winRatio, avgPnlPerDay, expectancy);
                })
                .collect(Collectors.toMap(PerformanceByTradeCount::tradesPerDay, Function.identity()));
    }

    /**
     * Analyzes trading performance based on the hour of the day trades were executed.
     * @param trades The list of all trades to analyze.
     * @return A map where the key is the hour of the day (0-23 UTC), and the value contains performance metrics for that hour.
     */
    public Map<Integer, PerformanceByHour> analyzePerformanceByTimeOfDay(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. Group trades by the hour of their exit time
        Map<Integer, List<Trade>> tradesByHour = trades.stream()
                .collect(Collectors.groupingBy(trade -> trade.exitTime().atZone(ZoneOffset.UTC).getHour()));

        // 2. Process each hourly group
        return tradesByHour.entrySet().stream()
                .map(entry -> {
                    int hourOfDay = entry.getKey();
                    List<Trade> hourlyTrades = entry.getValue();
                    int tradeCount = hourlyTrades.size();
                    BigDecimal totalPnl = calculateTotalPnl(hourlyTrades);
                    double winRatio = calculateWinRatio(hourlyTrades);
                    BigDecimal expectancy = calculateExpectancy(hourlyTrades);

                    return new PerformanceByHour(hourOfDay, tradeCount, totalPnl, winRatio, expectancy);
                })
                .collect(Collectors.toMap(PerformanceByHour::hourOfDay, Function.identity()));
    }


    /**
     * Performs a comprehensive analysis of the entire trade history.
     */
    public OverallStats analyzeOverallPerformance(List<Trade> trades, BigDecimal startingBalance) {
        if (trades == null || trades.isEmpty()) {
            return new OverallStats(Collections.emptyList(), startingBalance, startingBalance, BigDecimal.ZERO, 0, 0, 0, 0.0, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, BigDecimal.ZERO, BigDecimal.ZERO, Duration.ZERO, BigDecimal.ZERO, Collections.emptyList(), BigDecimal.ZERO, BigDecimal.ZERO);
        }

        List<Trade> sortedTrades = trades.stream()
                .sorted(Comparator.comparing(Trade::exitTime))
                .collect(Collectors.toList());

        BigDecimal totalPnl = calculateTotalPnl(sortedTrades);
        BigDecimal endBalance = startingBalance.add(totalPnl);
        int totalTrades = sortedTrades.size();

        List<Trade> winners = sortedTrades.stream().filter(t -> t.profitAndLoss().signum() > 0).collect(Collectors.toList());
        List<Trade> losers = sortedTrades.stream().filter(t -> t.profitAndLoss().signum() < 0).collect(Collectors.toList());
        int winningTrades = winners.size();
        int losingTrades = losers.size();

        double winRate = (totalTrades > 0) ? (double) winningTrades / totalTrades : 0.0;
        BigDecimal grossProfit = calculateTotalPnl(winners);
        BigDecimal grossLoss = calculateTotalPnl(losers).abs();

        BigDecimal avgWinPnl = (winningTrades > 0) ? grossProfit.divide(BigDecimal.valueOf(winningTrades), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgLossPnl = (losingTrades > 0) ? grossLoss.divide(BigDecimal.valueOf(losingTrades), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        double avgRiskReward = (avgLossPnl.signum() != 0) ? avgWinPnl.divide(avgLossPnl, 2, RoundingMode.HALF_UP).doubleValue() : 0.0;

        BigDecimal profitFactor = (grossLoss.signum() != 0) ? grossProfit.divide(grossLoss, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        BigDecimal expectancy = (avgWinPnl.multiply(BigDecimal.valueOf(winRate)))
                .subtract(avgLossPnl.multiply(BigDecimal.valueOf(1 - winRate)));

        Duration avgTradeDuration = calculateAverageDuration(sortedTrades);

        // Equity Curve and Drawdown/Run-up Calculation
        List<EquityPoint> equityCurve = new ArrayList<>();
        equityCurve.add(new EquityPoint(sortedTrades.get(0).entryTime().minusSeconds(1), startingBalance));
        BigDecimal cumulativePnl = BigDecimal.ZERO;
        BigDecimal peakEquity = startingBalance;
        BigDecimal troughEquity = startingBalance;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal maxRunup = BigDecimal.ZERO;

        for (Trade trade : sortedTrades) {
            cumulativePnl = cumulativePnl.add(trade.profitAndLoss());
            BigDecimal currentEquity = startingBalance.add(cumulativePnl);
            equityCurve.add(new EquityPoint(trade.exitTime(), currentEquity));

            if (currentEquity.compareTo(peakEquity) > 0) {
                peakEquity = currentEquity;
                troughEquity = currentEquity;
            } else if (currentEquity.compareTo(troughEquity) < 0) {
                troughEquity = currentEquity;
            }

            BigDecimal drawdown = peakEquity.subtract(currentEquity);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }

            BigDecimal runup = currentEquity.subtract(troughEquity);
             if (runup.compareTo(maxRunup) > 0) {
                maxRunup = runup;
            }
        }

        return new OverallStats(sortedTrades, startingBalance, endBalance, totalPnl, totalTrades, winningTrades, losingTrades, winRate, avgWinPnl, avgLossPnl, avgRiskReward, profitFactor, expectancy, avgTradeDuration, BigDecimal.ZERO, equityCurve, maxDrawdown.negate(), maxRunup);
    }
    
    public Optional<LocalDate> getLastTradeDate(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return Optional.empty();
        }
        return trades.stream()
                .map(Trade::exitTime)
                .max(Instant::compareTo)
                .map(instant -> instant.atZone(ZoneOffset.UTC).toLocalDate());
    }
    
    public Optional<DateRange> getDateRange(List<Trade> trades) {
        if (trades == null || trades.size() < 2) {
            return Optional.empty();
        }
        var exitTimes = trades.stream().map(Trade::exitTime).collect(Collectors.toList());
        Instant min = Collections.min(exitTimes);
        Instant max = Collections.max(exitTimes);
        return Optional.of(new DateRange(
            min.atZone(ZoneOffset.UTC).toLocalDate(),
            max.atZone(ZoneOffset.UTC).toLocalDate()
        ));
    }

    public TradeEfficiencyStats calculateTradeEfficiency(List<Trade> trades, DataSourceManager.ChartDataSource source) {
        if (trades == null || trades.isEmpty() || source == null) {
            return new TradeEfficiencyStats(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        List<Trade> winners = trades.stream().filter(t -> t.profitAndLoss().signum() > 0).collect(Collectors.toList());
        List<Trade> losers = trades.stream().filter(t -> t.profitAndLoss().signum() < 0).collect(Collectors.toList());

        if (winners.isEmpty() && losers.isEmpty()) {
            return new TradeEfficiencyStats(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        Optional<Instant> minInstantOpt = trades.stream().map(Trade::entryTime).min(Instant::compareTo);
        Optional<Instant> maxInstantOpt = trades.stream().map(Trade::exitTime).max(Instant::compareTo);

        if (minInstantOpt.isEmpty() || maxInstantOpt.isEmpty()) {
            return new TradeEfficiencyStats(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        List<KLine> allKlines;
        try (DatabaseManager db = new DatabaseManager("jdbc:sqlite:" + source.dbPath().toAbsolutePath())) {
            allKlines = db.getKLinesBetween(new Symbol(source.symbol()), "1m", minInstantOpt.get(), maxInstantOpt.get());
        } catch (Exception e) {
            logger.error("Failed to retrieve K-lines for trade efficiency analysis.", e);
            return new TradeEfficiencyStats(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        if (allKlines.isEmpty()) {
            return new TradeEfficiencyStats(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal totalMfeForWinners = BigDecimal.ZERO;
        for (Trade winner : winners) {
            List<KLine> tradeKlines = allKlines.stream()
                .filter(k -> !k.timestamp().isBefore(winner.entryTime()) && !k.timestamp().isAfter(winner.exitTime()))
                .collect(Collectors.toList());
            MfeMaeResult result = calculateMfeMaeForTrade(winner, tradeKlines);
            totalMfeForWinners = totalMfeForWinners.add(result.mfe());
        }

        BigDecimal grossWinPnl = calculateTotalPnl(winners);
        BigDecimal avgWinPnl = winners.isEmpty() ? BigDecimal.ZERO : grossWinPnl.divide(BigDecimal.valueOf(winners.size()), 4, RoundingMode.HALF_UP);
        BigDecimal avgMfeWinners = winners.isEmpty() ? BigDecimal.ZERO : totalMfeForWinners.divide(BigDecimal.valueOf(winners.size()), 4, RoundingMode.HALF_UP);
        BigDecimal winningEfficiency = (avgMfeWinners.signum() == 0) ? BigDecimal.ZERO : avgWinPnl.divide(avgMfeWinners, 4, RoundingMode.HALF_UP);

        BigDecimal totalMaeForLosers = BigDecimal.ZERO;
        for (Trade loser : losers) {
            List<KLine> tradeKlines = allKlines.stream()
                .filter(k -> !k.timestamp().isBefore(loser.entryTime()) && !k.timestamp().isAfter(loser.exitTime()))
                .collect(Collectors.toList());
            MfeMaeResult result = calculateMfeMaeForTrade(loser, tradeKlines);
            totalMaeForLosers = totalMaeForLosers.add(result.mae());
        }

        BigDecimal grossLossPnl = calculateTotalPnl(losers).abs();
        BigDecimal avgLossPnl = losers.isEmpty() ? BigDecimal.ZERO : grossLossPnl.divide(BigDecimal.valueOf(losers.size()), 4, RoundingMode.HALF_UP);
        BigDecimal avgMaeLosers = losers.isEmpty() ? BigDecimal.ZERO : totalMaeForLosers.divide(BigDecimal.valueOf(losers.size()), 4, RoundingMode.HALF_UP);
        BigDecimal loserPainRatio = (avgLossPnl.signum() == 0) ? BigDecimal.ZERO : avgMaeLosers.divide(avgLossPnl, 4, RoundingMode.HALF_UP);
        
        return new TradeEfficiencyStats(winningEfficiency, loserPainRatio, avgWinPnl, avgMfeWinners, avgLossPnl, avgMaeLosers);
    }
    
    public List<TradeMfeMae> calculateMfeMaeForAllTrades(List<Trade> trades, DataSourceManager.ChartDataSource source) {
        if (trades == null || trades.isEmpty() || source == null) {
            return Collections.emptyList();
        }

        Optional<Instant> minInstantOpt = trades.stream().map(Trade::entryTime).min(Instant::compareTo);
        Optional<Instant> maxInstantOpt = trades.stream().map(Trade::exitTime).max(Instant::compareTo);

        if (minInstantOpt.isEmpty() || maxInstantOpt.isEmpty()) {
            return Collections.emptyList();
        }

        List<KLine> allKlines;
        try (DatabaseManager db = new DatabaseManager("jdbc:sqlite:" + source.dbPath().toAbsolutePath())) {
            allKlines = db.getKLinesBetween(new Symbol(source.symbol()), "1m", minInstantOpt.get(), maxInstantOpt.get());
        } catch (Exception e) {
            logger.error("Failed to retrieve K-lines for MFE/MAE analysis.", e);
            return Collections.emptyList();
        }

        if (allKlines.isEmpty()) {
            return Collections.emptyList();
        }

        return trades.stream().map(trade -> {
            List<KLine> tradeKlines = allKlines.stream()
                .filter(k -> !k.timestamp().isBefore(trade.entryTime()) && !k.timestamp().isAfter(trade.exitTime()))
                .collect(Collectors.toList());
            MfeMaeResult result = calculateMfeMaeForTrade(trade, tradeKlines);
            return new TradeMfeMae(result.mfe(), result.mae(), trade.profitAndLoss());
        }).collect(Collectors.toList());
    }


    // --- Private Helper Methods ---
    private record MfeMaeResult(BigDecimal mfe, BigDecimal mae) {}

    private BigDecimal calculateTotalPnl(List<Trade> trades) {
        return trades.stream()
                .map(Trade::profitAndLoss)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private double calculateWinRatio(List<Trade> trades) {
        if (trades.isEmpty()) return 0.0;
        long winCount = trades.stream().filter(t -> t.profitAndLoss().signum() > 0).count();
        return (double) winCount / trades.size();
    }
    
    private BigDecimal calculateExpectancy(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<Trade> winners = trades.stream().filter(t -> t.profitAndLoss().signum() > 0).collect(Collectors.toList());
        List<Trade> losers = trades.stream().filter(t -> t.profitAndLoss().signum() < 0).collect(Collectors.toList());

        int winningTrades = winners.size();
        int losingTrades = losers.size();
        int totalTrades = trades.size();

        if (totalTrades == 0) return BigDecimal.ZERO;

        double winRate = (double) winningTrades / totalTrades;

        BigDecimal grossProfit = calculateTotalPnl(winners);
        BigDecimal grossLoss = calculateTotalPnl(losers).abs();

        BigDecimal avgWinPnl = (winningTrades > 0) ? grossProfit.divide(BigDecimal.valueOf(winningTrades), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgLossPnl = (losingTrades > 0) ? grossLoss.divide(BigDecimal.valueOf(losingTrades), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        return (avgWinPnl.multiply(BigDecimal.valueOf(winRate)))
                .subtract(avgLossPnl.multiply(BigDecimal.valueOf(1 - winRate)));
    }

    private Duration calculateAverageDuration(List<Trade> trades) {
        if (trades.isEmpty()) return Duration.ZERO;
        long totalSeconds = trades.stream()
                .mapToLong(t -> Duration.between(t.entryTime(), t.exitTime()).getSeconds())
                .sum();
        return Duration.ofSeconds(totalSeconds / trades.size());
    }

    private double calculatePlanFollowedPercentage(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0.0;
        }

        long followedCount = trades.stream()
            .filter(t -> {
                PlanAdherence pa = t.planAdherence();
                return pa == PlanAdherence.PERFECT_EXECUTION || pa == PlanAdherence.MINOR_DEVIATION;
            })
            .count();
        
        long ratedCount = trades.stream()
            .filter(t -> t.planAdherence() != PlanAdherence.NOT_RATED)
            .count();
            
        return (ratedCount > 0) ? (double) followedCount / ratedCount : 0.0;
    }
    
    private BigDecimal calculateProfitFactor(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal grossProfit = trades.stream()
            .map(Trade::profitAndLoss)
            .filter(pnl -> pnl.signum() > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal grossLoss = trades.stream()
            .map(Trade::profitAndLoss)
            .filter(pnl -> pnl.signum() < 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .abs();

        if (grossLoss.signum() == 0) {
            return (grossProfit.signum() > 0) ? new BigDecimal("999.00") : BigDecimal.ZERO;
        }

        return grossProfit.divide(grossLoss, 2, RoundingMode.HALF_UP);
    }

    private MfeMaeResult calculateMfeMaeForTrade(Trade trade, List<KLine> tradeKlines) {
        if (tradeKlines.isEmpty()) {
            return new MfeMaeResult(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        
        BigDecimal highestHigh = trade.entryPrice();
        BigDecimal lowestLow = trade.entryPrice();

        for (KLine k : tradeKlines) {
            if (k.high().compareTo(highestHigh) > 0) highestHigh = k.high();
            if (k.low().compareTo(lowestLow) < 0) lowestLow = k.low();
        }
        
        BigDecimal mfe, mae;
        if (trade.direction() == TradeDirection.LONG) {
            mfe = (highestHigh.subtract(trade.entryPrice())).multiply(trade.quantity());
            mae = (trade.entryPrice().subtract(lowestLow)).multiply(trade.quantity());
        } else { // SHORT
            mfe = (trade.entryPrice().subtract(lowestLow)).multiply(trade.quantity());
            mae = (highestHigh.subtract(trade.entryPrice())).multiply(trade.quantity());
        }
        
        return new MfeMaeResult(mfe.max(BigDecimal.ZERO), mae.max(BigDecimal.ZERO));
    }
}