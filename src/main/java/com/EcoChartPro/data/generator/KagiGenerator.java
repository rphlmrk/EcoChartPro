package com.EcoChartPro.data.generator;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.chart.KagiLine;
import com.EcoChartPro.model.chart.KagiLine.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class KagiGenerator implements ChartDataGenerator<KagiLine> {

    private final BigDecimal reversalAmount;
    private final List<KagiLine> kagiLines = new ArrayList<>();
    
    // State
    private BigDecimal currentLevel;
    private BigDecimal lastSwingHigh;
    private BigDecimal lastSwingLow;
    private boolean isGoingUp = true;
    private Type currentType = Type.YANG;
    private Instant lastTimestamp;


    public KagiGenerator(BigDecimal reversalAmount) {
        if (reversalAmount == null || reversalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Reversal amount must be positive.");
        }
        this.reversalAmount = reversalAmount;
    }

    @Override
    public List<KagiLine> generate(List<KLine> baseData) {
        // Reset state for bulk generation
        this.kagiLines.clear();
        this.currentLevel = null;
        this.lastSwingHigh = null;
        this.lastSwingLow = null;
        this.isGoingUp = true;
        this.currentType = Type.YANG;
        this.lastTimestamp = null;
        
        return process(baseData);
    }

    @Override
    public List<KagiLine> update(KLine newTick) {
        // Incrementally process a single new tick
        return process(List.of(newTick));
    }
    
    private List<KagiLine> process(List<KLine> newData) {
        if (newData == null || newData.isEmpty()) {
            return new ArrayList<>(kagiLines);
        }
        
        if (currentLevel == null) {
            KLine firstBar = newData.get(0);
            currentLevel = firstBar.close();
            lastSwingHigh = currentLevel;
            lastSwingLow = currentLevel;
            lastTimestamp = firstBar.timestamp();
        }

        for (KLine bar : newData) {
            BigDecimal price = bar.close();
            
            if (isGoingUp) {
                if (price.compareTo(currentLevel) >= 0) {
                    currentLevel = price;
                    lastTimestamp = bar.timestamp();
                } 
                else if (currentLevel.subtract(price).compareTo(reversalAmount) >= 0) {
                    kagiLines.add(new KagiLine(lastTimestamp, lastTimestamp, currentLevel, currentLevel, currentType));
                    
                    if (currentLevel.compareTo(lastSwingHigh) > 0) {
                        currentType = Type.YANG;
                        lastSwingHigh = currentLevel;
                    }
                    kagiLines.add(new KagiLine(lastTimestamp, bar.timestamp(), currentLevel, price, currentType));
                    
                    isGoingUp = false;
                    currentLevel = price;
                    lastTimestamp = bar.timestamp();
                }
            } else { // isGoingDown
                if (price.compareTo(currentLevel) <= 0) {
                    currentLevel = price;
                    lastTimestamp = bar.timestamp();
                }
                else if (price.subtract(currentLevel).compareTo(reversalAmount) >= 0) {
                    kagiLines.add(new KagiLine(lastTimestamp, lastTimestamp, currentLevel, currentLevel, currentType));
                    
                    if (currentLevel.compareTo(lastSwingLow) < 0) {
                        currentType = Type.YIN;
                        lastSwingLow = currentLevel;
                    }
                    kagiLines.add(new KagiLine(lastTimestamp, bar.timestamp(), currentLevel, price, currentType));

                    isGoingUp = true;
                    currentLevel = price;
                    lastTimestamp = bar.timestamp();
                }
            }
        }
        
        // Return a copy of the completed lines. The final forming line is held in the state variables.
        return new ArrayList<>(kagiLines);
    }
}