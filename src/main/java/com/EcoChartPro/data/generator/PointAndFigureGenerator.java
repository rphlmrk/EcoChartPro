package com.EcoChartPro.data.generator;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.chart.PointAndFigureColumn;
import com.EcoChartPro.model.chart.PointAndFigureColumn.Type;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PointAndFigureGenerator implements ChartDataGenerator<PointAndFigureColumn> {

    private final BigDecimal boxSize;
    private final int reversalAmount;
    private final BigDecimal reversalValue;
    private final List<PointAndFigureColumn> columns = new ArrayList<>();

    // State
    private Type currentDirection = null; 
    private BigDecimal columnHigh;
    private BigDecimal columnLow;
    private Instant columnStartTime;

    public PointAndFigureGenerator(BigDecimal boxSize, int reversalAmount) {
        if (boxSize == null || boxSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Box size must be positive.");
        }
        if (reversalAmount <= 0) {
            throw new IllegalArgumentException("Reversal amount must be positive.");
        }
        this.boxSize = boxSize;
        this.reversalAmount = reversalAmount;
        this.reversalValue = boxSize.multiply(BigDecimal.valueOf(reversalAmount));
    }

    @Override
    public List<PointAndFigureColumn> generate(List<KLine> baseData) {
        // Reset state for bulk generation
        this.columns.clear();
        this.currentDirection = null;
        this.columnHigh = null;
        this.columnLow = null;
        this.columnStartTime = null;

        return process(baseData);
    }

    @Override
    public List<PointAndFigureColumn> update(KLine newTick) {
        return process(List.of(newTick));
    }

    private List<PointAndFigureColumn> process(List<KLine> newData) {
        if (newData == null || newData.isEmpty()) {
            return new ArrayList<>(columns);
        }

        if (columnHigh == null) {
            KLine firstBar = newData.get(0);
            BigDecimal currentPrice = firstBar.close();
            columnHigh = quantize(currentPrice, true);
            columnLow = quantize(currentPrice, false);
            columnStartTime = firstBar.timestamp();
        }
        
        for (KLine bar : newData) {
            BigDecimal high = bar.high();
            BigDecimal low = bar.low();

            if (currentDirection == null) {
                if (high.compareTo(columnLow.add(boxSize)) >= 0) {
                    currentDirection = Type.COLUMN_OF_X;
                    columnHigh = quantize(high, true);
                    columnStartTime = bar.timestamp();
                } else if (low.compareTo(columnHigh.subtract(boxSize)) <= 0) {
                    currentDirection = Type.COLUMN_OF_O;
                    columnLow = quantize(low, false);
                    columnStartTime = bar.timestamp();
                }
            } else if (currentDirection == Type.COLUMN_OF_X) {
                if (high.compareTo(columnHigh.add(boxSize)) >= 0) {
                    columnHigh = quantize(high, true);
                } else if (low.compareTo(columnHigh.subtract(reversalValue)) <= 0) {
                    columns.add(new PointAndFigureColumn(columnStartTime, bar.timestamp(), columnHigh, columnLow, Type.COLUMN_OF_X));
                    
                    currentDirection = Type.COLUMN_OF_O;
                    columnStartTime = bar.timestamp();
                    BigDecimal newColumnHigh = columnHigh.subtract(boxSize);
                    columnLow = quantize(low, false);
                    columnHigh = newColumnHigh;
                }
            } else { // currentDirection == Type.COLUMN_OF_O
                if (low.compareTo(columnLow.subtract(boxSize)) <= 0) {
                    columnLow = quantize(low, false);
                } else if (high.compareTo(columnLow.add(reversalValue)) >= 0) {
                    columns.add(new PointAndFigureColumn(columnStartTime, bar.timestamp(), columnHigh, columnLow, Type.COLUMN_OF_O));
                    
                    currentDirection = Type.COLUMN_OF_X;
                    columnStartTime = bar.timestamp();
                    BigDecimal newColumnLow = columnLow.add(boxSize);
                    columnHigh = quantize(high, true);
                    columnLow = newColumnLow;
                }
            }
        }
        
        return new ArrayList<>(columns);
    }
    
    private BigDecimal quantize(BigDecimal price, boolean roundUp) {
        RoundingMode mode = roundUp ? RoundingMode.CEILING : RoundingMode.FLOOR;
        return price.divide(boxSize, 0, mode).multiply(boxSize);
    }
}