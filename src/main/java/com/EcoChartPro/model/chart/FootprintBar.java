package com.EcoChartPro.model.chart;

import com.EcoChartPro.model.KLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * Holds aggregated trade data for a single chart bar, bucketed by price level.
 * This is the core data structure for rendering a Volume Footprint chart.
 */
public class FootprintBar {

    public record VolumeBucket(BigDecimal buyVolume, BigDecimal sellVolume) {
        public BigDecimal totalVolume() {
            return buyVolume.add(sellVolume);
        }
    }

    private final Instant timestamp;
    private final TreeMap<BigDecimal, VolumeBucket> priceBuckets = new TreeMap<>();
    private BigDecimal pointOfControl = BigDecimal.ZERO;
    private BigDecimal maxVolumeAtPrice = BigDecimal.ZERO;

    public FootprintBar(Instant timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * A temporary method to simulate footprint data by distributing a KLine's volume.
     * In a real implementation, this would be replaced by aggregating actual TradeTicks.
     * @param kline The KLine to approximate data from.
     * @param priceStep The price increment for each volume bucket.
     */
    public void approximateFromKline(KLine kline, BigDecimal priceStep) {
        if (priceStep.compareTo(BigDecimal.ZERO) <= 0) return;

        long numLevels = kline.high().subtract(kline.low()).divide(priceStep, 0, RoundingMode.UP).longValue();
        if (numLevels == 0) numLevels = 1;
        BigDecimal volumePerLevel = kline.volume().divide(BigDecimal.valueOf(numLevels), 8, RoundingMode.HALF_UP);

        boolean isUpBar = kline.close().compareTo(kline.open()) >= 0;

        for (BigDecimal price = kline.low(); price.compareTo(kline.high()) <= 0; price = price.add(priceStep)) {
            BigDecimal binPrice = price.divide(priceStep, 0, RoundingMode.FLOOR).multiply(priceStep);
            
            // In this simulation, we assign all volume to buy or sell based on candle direction
            VolumeBucket bucket = isUpBar 
                ? new VolumeBucket(volumePerLevel, BigDecimal.ZERO) 
                : new VolumeBucket(BigDecimal.ZERO, volumePerLevel);
            
            priceBuckets.put(binPrice, bucket);

            if (bucket.totalVolume().compareTo(maxVolumeAtPrice) > 0) {
                maxVolumeAtPrice = bucket.totalVolume();
                pointOfControl = binPrice;
            }
        }
    }

    public Instant getTimestamp() { return timestamp; }
    public Map<BigDecimal, VolumeBucket> getPriceBuckets() { return priceBuckets; }
    public BigDecimal getPointOfControl() { return pointOfControl; }
    public BigDecimal getMaxVolumeAtPrice() { return maxVolumeAtPrice; }
}