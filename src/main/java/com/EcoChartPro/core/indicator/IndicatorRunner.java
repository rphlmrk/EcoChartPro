package com.EcoChartPro.core.indicator;

import com.EcoChartPro.api.indicator.drawing.DrawableObject;
import com.EcoChartPro.core.indicator.IndicatorContext.DebugLogEntry;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Manages the calculation for a single indicator instance using the new contextual API.
 * Its job is to prepare the IndicatorContext and invoke the indicator's `calculate` method.
 */
public class IndicatorRunner {
    private static final Logger logger = LoggerFactory.getLogger(IndicatorRunner.class);

    private final Indicator indicator;
    private final ChartDataModel dataModel;
    private final Map<Timeframe, List<KLine>> mtfCache = new HashMap<>();


    public record CalculationResult(List<DrawableObject> drawables, List<DebugLogEntry> debugLogs) {}

    public IndicatorRunner(Indicator indicator, ChartDataModel dataModel) {
        this.indicator = indicator;
        this.dataModel = dataModel;
    }

    public void reset() {
        this.indicator.reset();
        this.mtfCache.clear();
    }

    public CalculationResult recalculate(List<KLine> dataSlice) {
        if (dataSlice == null || dataSlice.isEmpty()) {
            return new CalculationResult(Collections.emptyList(), Collections.emptyList());
        }

        final List<DebugLogEntry> collectedLogs = new ArrayList<>();
        Consumer<DebugLogEntry> loggerConsumer = collectedLogs::add;

        Function<Timeframe, List<KLine>> mtfDataProvider = (timeframe) -> {
            return mtfCache.computeIfAbsent(timeframe, dataModel::getResampledDataForView);
        };

        IndicatorContext context = new IndicatorContext(
            dataSlice,
            indicator.getSettings(),
            mtfDataProvider,
            loggerConsumer
        );

        try {
            // [FIX] The cache is no longer cleared here, allowing it to work across ticks/repaints.
            List<DrawableObject> drawables = indicator.calculate(context);
            return new CalculationResult(drawables, collectedLogs);
        } catch (Exception e) {
            logger.error("Error during calculation for indicator '{}'", indicator.getName(), e);
            return new CalculationResult(Collections.emptyList(), Collections.emptyList());
        }
    }
}