package com.EcoChartPro.core.model.calculators;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.TradeTick;
import com.EcoChartPro.model.chart.FootprintBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the calculation, aggregation, and storage of footprint bar data.
 * This class can approximate footprint data from historical K-lines or build
 * precise data from live trade ticks.
 */
public class FootprintCalculator {

    private static final Logger logger = LoggerFactory.getLogger(FootprintCalculator.class);
    private final Map<Instant, FootprintBar> footprintData;
    private BigDecimal lastCalculatedPriceStep;

    public FootprintCalculator() {
        this.footprintData = new ConcurrentHashMap<>();
        this.lastCalculatedPriceStep = new BigDecimal("0.05"); // Default value
    }

    /**
     * Calculates historical footprint bars by approximating volume distribution
     * from K-lines.
     * This also calculates a dynamic price step based on the average candle range.
     * 
     * @param klineHistory The list of historical candles to process.
     */
    public void calculateHistoricalFootprints(List<KLine> klineHistory) {
        if (klineHistory == null || klineHistory.isEmpty()) {
            return;
        }

        clear();
        calculateDynamicPriceStep(klineHistory);

        for (KLine kline : klineHistory) {
            FootprintBar fpBar = new FootprintBar(kline.timestamp());
            fpBar.approximateFromKline(kline, this.lastCalculatedPriceStep);
            footprintData.put(kline.timestamp(), fpBar);
        }
        logger.info("Approximated footprint data for {} historical candles with a price step of {}.",
                klineHistory.size(), lastCalculatedPriceStep);
    }

    /**
     * Updates the currently forming footprint bar with a live trade tick.
     * 
     * [MODIFIED] This now relies strictly on the 'currentlyFormingCandle' timestamp
     * to ensure alignment with the View Timeframe (e.g., 45m), regardless of the
     * incoming tick's timestamp.
     * 
     * @param tick                   The incoming trade data.
     * @param currentlyFormingCandle The K-line that this tick belongs to (View
     *                               Timeframe).
     */
    public void addLiveTrade(TradeTick tick, KLine currentlyFormingCandle) {
        if (currentlyFormingCandle == null || tick == null) {
            return;
        }

        // Use the View Candle's timestamp, not the tick's timestamp or a calculated
        // one.
        // This ensures 10:03 tick goes into the 10:00 bar (if target is 5m)
        // or 10:00 bar (if target is 45m).
        Instant candleTimestamp = currentlyFormingCandle.timestamp();

        FootprintBar currentFpBar = footprintData.computeIfAbsent(candleTimestamp, ts -> {
            FootprintBar newBar = new FootprintBar(ts);
            newBar.setPriceStep(this.lastCalculatedPriceStep);
            return newBar;
        });

        currentFpBar.addTrade(tick);
    }

    private void calculateDynamicPriceStep(List<KLine> klineHistory) {
        if (klineHistory.size() > 1) {
            BigDecimal totalRange = BigDecimal.ZERO;
            int count = 0;
            for (KLine k : klineHistory) {
                BigDecimal range = k.high().subtract(k.low());
                if (range.compareTo(BigDecimal.ZERO) > 0) {
                    totalRange = totalRange.add(range);
                    count++;
                }
            }
            BigDecimal averageRange = (count > 0)
                    ? totalRange.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            // Aim for roughly 15-20 bins per average candle
            BigDecimal dynamicPriceStep = averageRange.divide(BigDecimal.valueOf(15), 8, RoundingMode.HALF_UP);

            // Ensure the step is not ridiculously small for low-priced assets
            BigDecimal lastPrice = klineHistory.get(klineHistory.size() - 1).close();
            if (lastPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal minReasonableStep = lastPrice.multiply(new BigDecimal("0.00001"));
                if (dynamicPriceStep.compareTo(minReasonableStep) < 0) {
                    dynamicPriceStep = minReasonableStep;
                }
            }
            this.lastCalculatedPriceStep = (dynamicPriceStep.compareTo(BigDecimal.ZERO) > 0) ? dynamicPriceStep
                    : new BigDecimal("0.00001");
        } else {
            this.lastCalculatedPriceStep = new BigDecimal("0.05"); // Fallback for single candle
        }
    }

    public BigDecimal getLastCalculatedPriceStep() {
        return lastCalculatedPriceStep;
    }

    /**
     * Gets the full map of calculated footprint data, keyed by timestamp.
     * 
     * @return An unmodifiable map of the footprint data.
     */
    public Map<Instant, FootprintBar> getFootprintData() {
        return Collections.unmodifiableMap(footprintData);
    }

    /**
     * Clears all stored footprint data.
     */
    public void clear() {
        footprintData.clear();
    }
}