package com.EcoChartPro.model.chart;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.TradeTick;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * Represents a single bar on a footprint chart, containing aggregated trade data.
 * This class is responsible for binning trades into price levels and calculating
 * metrics like delta and Point of Control (POC).
 */
public class FootprintBar {

    /**
     * An inner record to hold the separated buy (ask) and sell (bid) volume
     * for a single price level within the bar.
     */
    public record BidAskVolume(BigDecimal bidVolume, BigDecimal askVolume) {
        public BigDecimal getTotalVolume() {
            return bidVolume.add(askVolume);
        }
    }

    private final Instant timestamp;
    private final TreeMap<BigDecimal, BidAskVolume> clusters = new TreeMap<>();
    private BigDecimal priceStep = new BigDecimal("0.05"); // Default price step, can be refined

    // Cached values to avoid recalculation on every repaint
    private BigDecimal pocPrice = BigDecimal.ZERO;
    private BigDecimal totalDelta = BigDecimal.ZERO;
    private boolean isDirty = true;

    public FootprintBar(Instant timestamp) {
        this.timestamp = timestamp;
    }

    // --- Public Getters ---

    public Instant getTimestamp() {
        return timestamp;
    }

    public TreeMap<BigDecimal, BidAskVolume> getClusters() {
        return clusters;
    }

    public BigDecimal getPriceStep() {
        return priceStep;
    }

    public BigDecimal getPocPrice() {
        if (isDirty) {
            recalculateMetrics();
        }
        return pocPrice;
    }

    public BigDecimal getTotalDelta() {
        if (isDirty) {
            recalculateMetrics();
        }
        return totalDelta;
    }

    // --- Data Aggregation Methods ---

    /**
     * Adds a live trade tick to the bar, aggregating it into the correct price cluster.
     * @param tick The incoming trade data.
     */
    public void addTrade(TradeTick tick) {
        // Determine the price bin for this trade by flooring it to the nearest price step.
        BigDecimal binPrice = tick.price().divide(priceStep, 0, RoundingMode.FLOOR).multiply(priceStep);
        
        clusters.compute(binPrice, (price, currentVolume) -> {
            if (currentVolume == null) {
                currentVolume = new BidAskVolume(BigDecimal.ZERO, BigDecimal.ZERO);
            }
            // "buy" side means a buyer was the aggressor (lifted the ask)
            if ("buy".equalsIgnoreCase(tick.side())) {
                return new BidAskVolume(currentVolume.bidVolume(), currentVolume.askVolume().add(tick.quantity()));
            } else { // "sell" side means a seller was the aggressor (hit the bid)
                return new BidAskVolume(currentVolume.bidVolume().add(tick.quantity()), currentVolume.askVolume());
            }
        });
        isDirty = true; // Mark metrics as needing recalculation
    }

    /**
     * [NEW] Allows setting the price step after creation, primarily for initializing live bars.
     * @param priceStep The new price step to use for binning trades.
     */
    public void setPriceStep(BigDecimal priceStep) {
        if (priceStep != null && priceStep.compareTo(BigDecimal.ZERO) > 0) {
            this.priceStep = priceStep;
        }
    }
    
    /**
     * Approximates footprint data from a K-line. Used for historical data where
     * tick data is not available. This is not precise.
     * @param kline The K-line to approximate from.
     * @param priceStep The price granularity for clusters.
     */
    public void approximateFromKline(KLine kline, BigDecimal priceStep) {
        this.priceStep = priceStep;
        this.clusters.clear();
        
        boolean isUp = kline.close().compareTo(kline.open()) >= 0;
        BigDecimal range = kline.high().subtract(kline.low());
        if (range.compareTo(BigDecimal.ZERO) <= 0) range = priceStep;

        int numBins = range.divide(priceStep, 0, RoundingMode.CEILING).intValue();
        if (numBins == 0) numBins = 1;

        BigDecimal volPerBin = kline.volume().divide(BigDecimal.valueOf(numBins), 2, RoundingMode.HALF_UP);

        for (BigDecimal p = kline.low(); p.compareTo(kline.high()) < 0; p = p.add(priceStep)) {
            // Skew volume based on candle direction. This is a very rough guess.
            BigDecimal askVol = isUp ? volPerBin.multiply(new BigDecimal("0.6")) : volPerBin.multiply(new BigDecimal("0.4"));
            BigDecimal bidVol = volPerBin.subtract(askVol);
            
            BigDecimal binPrice = p.divide(priceStep, 0, RoundingMode.FLOOR).multiply(priceStep);
            clusters.put(binPrice, new BidAskVolume(bidVol, askVol));
        }
        isDirty = true;
    }

    /**
     * Recalculates expensive metrics like POC and total delta. This is called lazily
     * only when the data is requested and has been marked as dirty.
     */
    private void recalculateMetrics() {
        BigDecimal maxVolume = BigDecimal.ZERO;
        BigDecimal newPocPrice = BigDecimal.ZERO;
        BigDecimal totalAsk = BigDecimal.ZERO;
        BigDecimal totalBid = BigDecimal.ZERO;

        for (Map.Entry<BigDecimal, BidAskVolume> entry : clusters.entrySet()) {
            BidAskVolume volume = entry.getValue();
            BigDecimal totalClusterVolume = volume.getTotalVolume();

            totalAsk = totalAsk.add(volume.askVolume());
            totalBid = totalBid.add(volume.bidVolume());

            if (totalClusterVolume.compareTo(maxVolume) >= 0) { // Use >= to get the highest POC price in case of tie
                maxVolume = totalClusterVolume;
                newPocPrice = entry.getKey();
            }
        }
        
        this.pocPrice = newPocPrice;
        this.totalDelta = totalAsk.subtract(totalBid);
        this.isDirty = false;
    }
}