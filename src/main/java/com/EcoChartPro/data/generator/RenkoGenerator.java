package com.EcoChartPro.data.generator;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.chart.RenkoBrick;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple, close-based, stateful generator for Renko charts.
 */
public class RenkoGenerator implements ChartDataGenerator<RenkoBrick> {

    private final BigDecimal brickSize;
    private final List<RenkoBrick> bricks = new ArrayList<>();
    private BigDecimal brickBase;

    public RenkoGenerator(BigDecimal brickSize) {
        if (brickSize == null || brickSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Brick size must be positive.");
        }
        this.brickSize = brickSize;
    }

    @Override
    public List<RenkoBrick> generate(List<KLine> baseData) {
        this.bricks.clear();
        this.brickBase = null; // Reset state for bulk generation
        return process(baseData);
    }
    
    @Override
    public List<RenkoBrick> update(KLine newTick) {
        // Incrementally process a single new tick
        return process(List.of(newTick));
    }

    private List<RenkoBrick> process(List<KLine> newData) {
        if (newData == null || newData.isEmpty()) {
            return new ArrayList<>(bricks);
        }

        if (brickBase == null) {
            KLine firstBar = newData.get(0);
            this.brickBase = firstBar.close().divide(brickSize, 0, RoundingMode.FLOOR).multiply(brickSize);
        }
        
        for (KLine currentBar : newData) {
            BigDecimal currentPrice = currentBar.close();
            BigDecimal priceMove = currentPrice.subtract(brickBase);

            if (priceMove.abs().compareTo(brickSize) >= 0) {
                int numBricks = priceMove.divide(brickSize, 0, RoundingMode.DOWN).intValue();
                
                for (int j = 0; j < Math.abs(numBricks); j++) {
                    BigDecimal open = brickBase;
                    BigDecimal close;
                    if (numBricks > 0) { // Up bricks
                        close = open.add(brickSize);
                    } else { // Down bricks
                        close = open.subtract(brickSize);
                    }
                    
                    bricks.add(new RenkoBrick(currentBar.timestamp(), currentBar.timestamp(), open, close));
                    brickBase = close;
                }
            }
        }
        return new ArrayList<>(bricks);
    }
}