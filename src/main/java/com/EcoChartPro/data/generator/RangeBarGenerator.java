package com.EcoChartPro.data.generator;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.chart.RangeBar;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RangeBarGenerator implements ChartDataGenerator<RangeBar> {

    private final BigDecimal rangeSize;
    private final List<RangeBar> rangeBars = new ArrayList<>();

    // State for the currently forming bar
    private Instant currentStartTime;
    private BigDecimal currentOpen;
    private BigDecimal currentHigh;
    private BigDecimal currentLow;
    private BigDecimal currentVolume;

    public RangeBarGenerator(BigDecimal rangeSize) {
        if (rangeSize == null || rangeSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Range size must be positive.");
        }
        this.rangeSize = rangeSize;
    }

    @Override
    public List<RangeBar> generate(List<KLine> baseData) {
        this.rangeBars.clear();
        this.currentOpen = null; // Reset state
        return process(baseData);
    }

    @Override
    public List<RangeBar> update(KLine newTick) {
        return process(List.of(newTick));
    }

    private List<RangeBar> process(List<KLine> newData) {
        if (newData == null || newData.isEmpty()) {
            return new ArrayList<>(rangeBars);
        }

        if (currentOpen == null) {
            KLine firstKLine = newData.get(0);
            currentStartTime = firstKLine.startTime();
            currentOpen = firstKLine.open();
            currentHigh = firstKLine.high();
            currentLow = firstKLine.low();
            currentVolume = firstKLine.volume();
        }

        for (KLine m1Bar : newData) {
            currentHigh = currentHigh.max(m1Bar.high());
            currentLow = currentLow.min(m1Bar.low());
            currentVolume = currentVolume.add(m1Bar.volume());

            if (currentHigh.subtract(currentLow).compareTo(rangeSize) >= 0) {
                BigDecimal finalClose = m1Bar.close();
                Instant finalEndTime = m1Bar.endTime();

                rangeBars.add(new RangeBar(
                    currentStartTime,
                    finalEndTime,
                    currentOpen,
                    currentHigh,
                    currentLow,
                    finalClose,
                    currentVolume
                ));

                currentStartTime = finalEndTime;
                currentOpen = m1Bar.close();
                currentHigh = currentOpen;
                currentLow = currentOpen;
                currentVolume = BigDecimal.ZERO;
            }
        }
        
        return new ArrayList<>(rangeBars);
    }
}