package com.EcoChartPro.plugins.inapp;

import com.EcoChartPro.api.indicator.*;
import com.EcoChartPro.api.indicator.drawing.*;
import com.EcoChartPro.core.indicator.IndicatorContext;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MultiTfStdevIndicator implements CustomIndicator {
    
    @Override
    public String getName() { return "Multi-TF Standard Deviations"; }

    @Override
    public IndicatorType getType() { return IndicatorType.OVERLAY; }

    @Override
    public List<Parameter> getParameters() {
        return List.of(
            new Parameter("htf", ParameterType.CHOICE, "1H", "15m", "30m", "1H", "4H", "1D"),
            new Parameter("lookback", ParameterType.INTEGER, 20),
            new Parameter("multiplier", ParameterType.DECIMAL, new BigDecimal("2.0")),
            new Parameter("color", ParameterType.COLOR, new Color(158, 158, 158, 50))
        );
    }

    @Override
    public List<DrawableObject> calculate(IndicatorContext context) {
        List<KLine> ltfData = context.klineData();
        if (ltfData.isEmpty()) return Collections.emptyList();
        
        // --- Get Settings & HTF Data ---
        Timeframe htf = Timeframe.fromString((String) context.settings().get("htf"));
        int lookback = (int) context.settings().get("lookback");
        BigDecimal multiplier = (BigDecimal) context.settings().get("multiplier");
        Color color = (Color) context.settings().get("color");
        
        List<KLine> htfData = context.getHigherTimeframeData().apply(htf);
        if (htfData.size() < lookback) return Collections.emptyList();

        List<DrawableObject> drawables = new ArrayList<>();
        int htfIndex = 0;

        // Loop through the Lower Timeframe (LTF) bars on the chart
        for (int i = 0; i < ltfData.size(); i++) {
            KLine ltfBar = ltfData.get(i);

            // Find the current HTF bar that corresponds to this LTF bar
            while (htfIndex < htfData.size() - 1 && htfData.get(htfIndex + 1).timestamp().isBefore(ltfBar.timestamp())) {
                htfIndex++;
            }
            
            // Now, htfData.get(htfIndex) is the current HTF bar.
            // Calculate StDev on the HTF data up to this point.
            if (htfIndex >= lookback - 1) {
                int start = htfIndex - lookback + 1;
                int end = htfIndex + 1;
                List<KLine> htfWindow = htfData.subList(start, end);
                
                // Calculate Mean and StDev for the HTF window
                BigDecimal[] stats = calculateMeanAndStdev(htfWindow);
                BigDecimal mean = stats[0];
                BigDecimal stdev = stats[1];
                
                BigDecimal stdevOffset = stdev.multiply(multiplier);
                BigDecimal upperBand = mean.add(stdevOffset);
                BigDecimal lowerBand = mean.subtract(stdevOffset);

                // Draw the zones for the duration of the LTF bar
                drawables.add(new DrawableBox(
                    new DataPoint(ltfBar.timestamp(), upperBand),
                    new DataPoint(ltfBar.timestamp(), lowerBand),
                    color, null, 0
                ));
            }
        }
        return drawables;
    }

    private BigDecimal[] calculateMeanAndStdev(List<KLine> data) {
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal sumSq = BigDecimal.ZERO;
        MathContext mc = new MathContext(10, RoundingMode.HALF_UP);
        
        for(KLine k : data) {
            sum = sum.add(k.close());
            sumSq = sumSq.add(k.close().pow(2));
        }
        
        BigDecimal n = new BigDecimal(data.size());
        BigDecimal mean = sum.divide(n, mc);
        BigDecimal variance = sumSq.divide(n, mc).subtract(mean.pow(2));
        if (variance.compareTo(BigDecimal.ZERO) < 0) variance = BigDecimal.ZERO;
        
        return new BigDecimal[]{mean, variance.sqrt(mc)};
    }
}