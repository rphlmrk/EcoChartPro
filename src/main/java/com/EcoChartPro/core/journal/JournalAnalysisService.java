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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JournalAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(JournalAnalysisService.class);

    // --- DTOs ---
    public record DailyStats(LocalDate date, int tradeCount, BigDecimal totalPnl, double winRatio, double planFollowedPercentage) {}
    public record WeeklyStats(int weekOfYear, int tradeCount, BigDecimal totalPnl, double winRatio, double planFollowedPercentage) {}
    public record MonthlyStats(YearMonth yearMonth, int tradeCount, BigDecimal totalPnl, double winRatio, double planFollowedPercentage) {}
    public record DateRange(LocalDate minDate, LocalDate maxDate) {}
    public record PerformanceByTradeCount(int tradesPerDay, int dayCount, BigDecimal totalPnl, double winRate, BigDecimal avgPnlPerDay, BigDecimal expectancy) {}
    public record PerformanceByHour(int hourOfDay, int tradeCount, BigDecimal totalPnl, double winRate, BigDecimal expectancy) {}
    public record TradeMfeMae(BigDecimal mfe, BigDecimal mae, BigDecimal pnl) {}
    public record TagPerformanceStats(String tag, int tradeCount, double winRate, BigDecimal profitFactor, BigDecimal expectancy) {}
    public record PnlDistributionBin(String label, int count, BigDecimal lowerBound, BigDecimal upperBound) {}

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

    // --- MFE/MAE Calculation with Fallback ---

    public List<TradeMfeMae> calculateMfeMaeForAllTrades(List<Trade> trades, DataSourceManager.ChartDataSource source) {
        if (trades == null || trades.isEmpty()) {
            return Collections.emptyList();
        }

        // [FIX] Prepare a fallback database manager if needed
        DatabaseManager fallbackDb = null;
        boolean useFallback = false;
        
        // Check if we need to access the source DB (Replay scenario)
        if (source != null && source.dbPath() != null) {
            // We initialize this lazily inside the loop or check availability, 
            // but simple approach is to open it if we encounter misses.
            // For efficiency, we'll open it once here if we suspect we need it.
            useFallback = true;
        }

        try {
            if (useFallback) {
                fallbackDb = new DatabaseManager("jdbc:sqlite:" + source.dbPath().toAbsolutePath());
            }
            
            final DatabaseManager dbToUse = fallbackDb;

            return trades.stream().map(trade -> {
                // 1. Try Fast Cache (Application DB)
                List<KLine> tradeKlines = DatabaseManager.getInstance().getCandlesForTrade(trade.id(), "1m");

                // 2. Fallback to Source DB (Replay/Historical Data)
                if (tradeKlines.isEmpty() && dbToUse != null) {
                    // Buffer time by 1 minute to ensure we capture high/low
                    Instant start = trade.entryTime().minusSeconds(60);
                    Instant end = trade.exitTime().plusSeconds(60);
                    // Assuming symbol name matches. If replay uses mapped names, might need adjustment.
                    Symbol lookupSymbol = trade.symbol(); 
                    tradeKlines = dbToUse.getKLinesBetween(lookupSymbol, "1m", start, end);
                }

                MfeMaeResult result = calculateMfeMaeForTrade(trade, tradeKlines);
                return new TradeMfeMae(result.mfe(), result.mae(), trade.profitAndLoss());
            }).collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error during MFE/MAE calculation", e);
            return Collections.emptyList();
        } finally {
            if (fallbackDb != null) {
                fallbackDb.close();
            }
        }
    }

    // --- Existing Methods (Kept identical to preserve logic) ---

    public Map<String, MistakeStats> analyzeMistakes(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) return Collections.emptyMap();
        Map<String, MutableMistakeStats> tempStats = new HashMap<>();
        for (Trade trade : trades) {
            List<String> mistakes = trade.identifiedMistakes();
            if (mistakes != null) {
                for (String mistake : mistakes) {
                    if (mistake != null && !mistake.isBlank()) {
                        MutableMistakeStats stats = tempStats.computeIfAbsent(mistake, k -> new MutableMistakeStats());
                        stats.frequency++;
                        stats.totalPnl = stats.totalPnl.add(trade.profitAndLoss());
                    }
                }
            }
        }
        Map<String, MistakeStats> finalStats = new HashMap<>();
        for (Map.Entry<String, MutableMistakeStats> entry : tempStats.entrySet()) {
            String name = entry.getKey();
            MutableMistakeStats stats = entry.getValue();
            BigDecimal avg = stats.totalPnl.divide(BigDecimal.valueOf(stats.frequency), 2, RoundingMode.HALF_UP);
            finalStats.put(name, new MistakeStats(name, stats.frequency, stats.totalPnl, avg));
        }
        return finalStats;
    }

    private static class MutableMistakeStats { int frequency = 0; BigDecimal totalPnl = BigDecimal.ZERO; }

    public List<PnlDistributionBin> getPnlDistribution(List<Trade> trades, int numBins) {
        if (trades == null || trades.isEmpty() || numBins <= 0) return Collections.emptyList();
        List<BigDecimal> pnlValues = trades.stream().map(Trade::profitAndLoss).collect(Collectors.toList());
        BigDecimal min = Collections.min(pnlValues);
        BigDecimal max = Collections.max(pnlValues);
        if (min.compareTo(max) == 0) return List.of(new PnlDistributionBin(String.format("$%.2f", min), trades.size(), min, max));
        
        BigDecimal range = max.subtract(min);
        BigDecimal binWidth = range.add(new BigDecimal("0.0001")).divide(BigDecimal.valueOf(numBins), 4, RoundingMode.CEILING);
        if (binWidth.signum() == 0) return List.of(new PnlDistributionBin(String.format("$%.2f", min), trades.size(), min, max));

        int[] counts = new int[numBins];
        for (BigDecimal pnl : pnlValues) {
            int idx = pnl.subtract(min).divide(binWidth, 0, RoundingMode.FLOOR).intValue();
            if (idx >= 0 && idx < numBins) counts[idx]++;
        }
        List<PnlDistributionBin> result = new ArrayList<>();
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        for (int i = 0; i < numBins; i++) {
            BigDecimal lower = min.add(binWidth.multiply(BigDecimal.valueOf(i)));
            BigDecimal upper = lower.add(binWidth);
            result.add(new PnlDistributionBin(String.format("%s to %s", df.format(lower), df.format(upper)), counts[i], lower, upper));
        }
        return result;
    }

    public OverallStats analyzeOverallPerformance(List<Trade> trades, BigDecimal startingBalance) {
        if (trades == null || trades.isEmpty()) {
            return new OverallStats(Collections.emptyList(), startingBalance, startingBalance, BigDecimal.ZERO, 0, 0, 0, 0.0, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, BigDecimal.ZERO, BigDecimal.ZERO, Duration.ZERO, BigDecimal.ZERO, Collections.emptyList(), BigDecimal.ZERO, BigDecimal.ZERO);
        }
        List<Trade> sorted = trades.stream().sorted(Comparator.comparing(Trade::exitTime)).collect(Collectors.toList());
        BigDecimal totalPnl = calculateTotalPnl(sorted);
        BigDecimal endBalance = startingBalance.add(totalPnl);
        int total = sorted.size();
        List<Trade> winners = sorted.stream().filter(t -> t.profitAndLoss().signum() > 0).collect(Collectors.toList());
        List<Trade> losers = sorted.stream().filter(t -> t.profitAndLoss().signum() < 0).collect(Collectors.toList());
        
        double winRate = (double) winners.size() / total;
        BigDecimal avgWin = winners.isEmpty() ? BigDecimal.ZERO : calculateTotalPnl(winners).divide(BigDecimal.valueOf(winners.size()), 2, RoundingMode.HALF_UP);
        BigDecimal avgLoss = losers.isEmpty() ? BigDecimal.ZERO : calculateTotalPnl(losers).divide(BigDecimal.valueOf(losers.size()), 2, RoundingMode.HALF_UP);
        double rr = (avgLoss.signum() != 0) ? avgWin.divide(avgLoss.abs(), 2, RoundingMode.HALF_UP).doubleValue() : 0.0;
        
        BigDecimal grossProfit = calculateTotalPnl(winners);
        BigDecimal grossLoss = calculateTotalPnl(losers).abs();
        BigDecimal pf = (grossLoss.signum() != 0) ? grossProfit.divide(grossLoss, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        
        BigDecimal expectancy = avgWin.multiply(BigDecimal.valueOf(winRate)).add(avgLoss.multiply(BigDecimal.valueOf(1 - winRate)));
        
        List<EquityPoint> curve = new ArrayList<>();
        curve.add(new EquityPoint(sorted.get(0).entryTime().minusSeconds(1), startingBalance));
        BigDecimal runningPnl = BigDecimal.ZERO;
        BigDecimal peak = startingBalance, trough = startingBalance, maxDD = BigDecimal.ZERO, maxRun = BigDecimal.ZERO;
        
        for(Trade t : sorted) {
            runningPnl = runningPnl.add(t.profitAndLoss());
            BigDecimal eq = startingBalance.add(runningPnl);
            curve.add(new EquityPoint(t.exitTime(), eq));
            if(eq.compareTo(peak) > 0) { peak = eq; trough = eq; }
            if(eq.compareTo(trough) < 0) trough = eq;
            maxDD = maxDD.max(peak.subtract(eq));
            maxRun = maxRun.max(eq.subtract(trough));
        }
        
        return new OverallStats(sorted, startingBalance, endBalance, totalPnl, total, winners.size(), losers.size(), winRate, avgWin, avgLoss, rr, pf, expectancy, calculateAverageDuration(sorted), BigDecimal.ZERO, curve, maxDD.negate(), maxRun);
    }

    // --- Boilerplate Analysis Methods ---
    
    public Map<String, TagPerformanceStats> analyzePerformanceByTag(List<Trade> trades) {
        if (trades == null) return Collections.emptyMap();
        Map<String, List<Trade>> map = new HashMap<>();
        for (Trade t : trades) {
            if (t.tags() != null) t.tags().forEach(tag -> map.computeIfAbsent(tag, k -> new ArrayList<>()).add(t));
        }
        Map<String, TagPerformanceStats> res = new HashMap<>();
        map.forEach((tag, list) -> res.put(tag, new TagPerformanceStats(tag, list.size(), calculateWinRatio(list), calculateProfitFactor(list), calculateExpectancy(list))));
        return res;
    }

    public Map<Integer, PerformanceByTradeCount> analyzePerformanceByTradeCount(List<Trade> trades) {
        if (trades == null) return Collections.emptyMap();
        Map<Integer, List<List<Trade>>> grouped = trades.stream()
                .collect(Collectors.groupingBy(t -> t.exitTime().atZone(ZoneOffset.UTC).toLocalDate()))
                .values().stream().collect(Collectors.groupingBy(List::size));
        return grouped.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
            List<Trade> flat = e.getValue().stream().flatMap(List::stream).collect(Collectors.toList());
            return new PerformanceByTradeCount(e.getKey(), e.getValue().size(), calculateTotalPnl(flat), calculateWinRatio(flat), calculateTotalPnl(flat).divide(BigDecimal.valueOf(e.getValue().size()), 2, RoundingMode.HALF_UP), calculateExpectancy(flat));
        }));
    }

    public Map<Integer, PerformanceByHour> analyzePerformanceByTimeOfDay(List<Trade> trades) {
        if (trades == null) return Collections.emptyMap();
        return trades.stream().collect(Collectors.groupingBy(t -> t.exitTime().atZone(ZoneOffset.UTC).getHour()))
            .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new PerformanceByHour(e.getKey(), e.getValue().size(), calculateTotalPnl(e.getValue()), calculateWinRatio(e.getValue()), calculateExpectancy(e.getValue()))));
    }

    public Map<LocalDate, DailyStats> analyzeTradesByDay(List<Trade> trades) {
        if (trades == null) return Collections.emptyMap();
        return trades.stream().collect(Collectors.groupingBy(t -> t.exitTime().atZone(ZoneOffset.UTC).toLocalDate()))
            .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new DailyStats(e.getKey(), e.getValue().size(), calculateTotalPnl(e.getValue()), calculateWinRatio(e.getValue()), calculatePlanFollowedPercentage(e.getValue()))));
    }

    public Map<YearMonth, MonthlyStats> analyzePerformanceByMonth(List<Trade> trades) {
        if (trades == null) return Collections.emptyMap();
        return trades.stream().collect(Collectors.groupingBy(t -> YearMonth.from(t.exitTime().atZone(ZoneOffset.UTC))))
            .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new MonthlyStats(e.getKey(), e.getValue().size(), calculateTotalPnl(e.getValue()), calculateWinRatio(e.getValue()), calculatePlanFollowedPercentage(e.getValue()))));
    }

    public Map<Integer, WeeklyStats> analyzeTradesByWeek(List<Trade> trades) {
        if (trades == null) return Collections.emptyMap();
        WeekFields wf = WeekFields.of(Locale.US);
        return trades.stream().collect(Collectors.groupingBy(t -> t.exitTime().atZone(ZoneOffset.UTC).get(wf.weekOfWeekBasedYear())))
            .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new WeeklyStats(e.getKey(), e.getValue().size(), calculateTotalPnl(e.getValue()), calculateWinRatio(e.getValue()), calculatePlanFollowedPercentage(e.getValue()))));
    }
    
    public Map<LocalDate, OverallStats> analyzePerformanceByPeriod(List<Trade> allTrades, ChronoUnit periodUnit, long periodAmount) {
        if (allTrades == null || allTrades.isEmpty()) return Collections.emptyMap();
        Map<LocalDate, List<Trade>> tradesByPeriod = allTrades.stream()
            .collect(Collectors.groupingBy(trade -> getPeriodStart(trade.exitTime().atZone(ZoneOffset.UTC).toLocalDate(), periodUnit, periodAmount)));
        Map<LocalDate, OverallStats> statsByPeriod = new TreeMap<>(Comparator.reverseOrder());
        for (Map.Entry<LocalDate, List<Trade>> entry : tradesByPeriod.entrySet()) {
            statsByPeriod.put(entry.getKey(), analyzeOverallPerformance(entry.getValue(), BigDecimal.ZERO));
        }
        return statsByPeriod;
    }
    
    public Optional<DateRange> getDateRange(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) return Optional.empty();
        var exitTimes = trades.stream().map(Trade::exitTime).collect(Collectors.toList());
        return Optional.of(new DateRange(Collections.min(exitTimes).atZone(ZoneOffset.UTC).toLocalDate(), Collections.max(exitTimes).atZone(ZoneOffset.UTC).toLocalDate()));
    }

    // --- Helpers ---
    private BigDecimal calculateTotalPnl(List<Trade> t) { return t.stream().map(Trade::profitAndLoss).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private double calculateWinRatio(List<Trade> t) { return t.isEmpty() ? 0.0 : (double) t.stream().filter(x -> x.profitAndLoss().signum() > 0).count() / t.size(); }
    private double calculatePlanFollowedPercentage(List<Trade> t) { return t.isEmpty() ? 0.0 : (double) t.stream().filter(x -> x.planAdherence() == PlanAdherence.PERFECT_EXECUTION || x.planAdherence() == PlanAdherence.MINOR_DEVIATION).count() / t.stream().filter(x->x.planAdherence()!=PlanAdherence.NOT_RATED).count(); }
    private BigDecimal calculateProfitFactor(List<Trade> t) {
        BigDecimal win = t.stream().map(Trade::profitAndLoss).filter(p->p.signum()>0).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal loss = t.stream().map(Trade::profitAndLoss).filter(p->p.signum()<0).reduce(BigDecimal.ZERO, BigDecimal::add).abs();
        return loss.signum() == 0 ? (win.signum() > 0 ? new BigDecimal("999") : BigDecimal.ZERO) : win.divide(loss, 2, RoundingMode.HALF_UP);
    }
    private BigDecimal calculateExpectancy(List<Trade> t) {
        if (t.isEmpty()) return BigDecimal.ZERO;
        double wr = calculateWinRatio(t);
        List<Trade> w = t.stream().filter(x->x.profitAndLoss().signum()>0).collect(Collectors.toList());
        List<Trade> l = t.stream().filter(x->x.profitAndLoss().signum()<0).collect(Collectors.toList());
        BigDecimal avgW = w.isEmpty() ? BigDecimal.ZERO : calculateTotalPnl(w).divide(BigDecimal.valueOf(w.size()), 2, RoundingMode.HALF_UP);
        BigDecimal avgL = l.isEmpty() ? BigDecimal.ZERO : calculateTotalPnl(l).divide(BigDecimal.valueOf(l.size()), 2, RoundingMode.HALF_UP);
        return avgW.multiply(BigDecimal.valueOf(wr)).add(avgL.multiply(BigDecimal.valueOf(1-wr)));
    }
    private Duration calculateAverageDuration(List<Trade> t) { return t.isEmpty() ? Duration.ZERO : Duration.ofSeconds(t.stream().mapToLong(x->Duration.between(x.entryTime(), x.exitTime()).getSeconds()).sum() / t.size()); }
    
    private MfeMaeResult calculateMfeMaeForTrade(Trade trade, List<KLine> klines) {
        if (klines.isEmpty()) return new MfeMaeResult(BigDecimal.ZERO, BigDecimal.ZERO);
        BigDecimal hh = trade.entryPrice(), ll = trade.entryPrice();
        for (KLine k : klines) {
            if (k.high().compareTo(hh) > 0) hh = k.high();
            if (k.low().compareTo(ll) < 0) ll = k.low();
        }
        boolean isLong = trade.direction() == TradeDirection.LONG;
        BigDecimal mfe = isLong ? hh.subtract(trade.entryPrice()) : trade.entryPrice().subtract(ll);
        BigDecimal mae = isLong ? trade.entryPrice().subtract(ll) : hh.subtract(trade.entryPrice());
        return new MfeMaeResult(mfe.multiply(trade.quantity()).max(BigDecimal.ZERO), mae.multiply(trade.quantity()).max(BigDecimal.ZERO));
    }
    private record MfeMaeResult(BigDecimal mfe, BigDecimal mae) {}
    
    private LocalDate getPeriodStart(LocalDate date, ChronoUnit unit, long amt) {
        if (unit == ChronoUnit.MONTHS) {
            if (amt == 3) return date.withMonth(((date.getMonthValue()-1)/3)*3+1).withDayOfMonth(1);
            if (amt == 6) return date.withMonth(((date.getMonthValue()-1)/6)*6+1).withDayOfMonth(1);
            return date.withDayOfMonth(1);
        }
        return date.withDayOfYear(1);
    }
}