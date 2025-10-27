package com.EcoChartPro.data.generator;

import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.chart.AbstractChartData;
import java.util.List;

/**
 * An interface for classes that can transform a list of base K-line data
 * into a different chart representation (e.g., Renko, Kagi).
 * This interface supports both initial bulk generation and incremental live updates.
 * @param <T> The type of chart data this generator produces.
 */
public interface ChartDataGenerator<T extends AbstractChartData> {

    /**
     * Generates a list of specialized chart data from a base list of K-lines.
     * This method is intended for initial, bulk processing and should clear
     * any prior internal state before generating the new data.
     *
     * @param baseData The input K-line data, typically at a low timeframe like M1.
     * @return A new list of the generated chart data points.
     */
    List<T> generate(List<KLine> baseData);
    
    /**
     * Incrementally updates the chart data with a single new K-line tick.
     * This method preserves the existing internal state and appends new data points
     * as they are formed, returning the complete, updated list.
     * 
     * @param newTick The single new K-line to process.
     * @return The complete, updated list of generated chart data points.
     */
    List<T> update(KLine newTick);
}