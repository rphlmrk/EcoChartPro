package com.EcoChartPro.core.indicator;

import com.EcoChartPro.api.indicator.ApiKLine;
import com.EcoChartPro.api.indicator.drawing.DrawableObject;
import com.EcoChartPro.core.indicator.IndicatorContext.DebugLogEntry;
import com.EcoChartPro.core.model.ChartDataModel;
import com.EcoChartPro.model.KLine;
import com.EcoChartPro.model.Timeframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages the calculation for a single indicator instance using the new contextual API.
 * Its job is to prepare the IndicatorContext and invoke the indicator's `calculate` method.
 */
public class IndicatorRunner {
    private static final Logger logger = LoggerFactory.getLogger(IndicatorRunner.class);

    private final Indicator indicator;
    private final ChartDataModel dataModel;
    private final Map<Timeframe, List<KLine>> mtfCache = new HashMap<>();
    
    // The persistent state store for this indicator instance.
    private final Map<String, Object> stateStore = new HashMap<>();
    
    // Flag to signal a state reset to the indicator
    private boolean isResetNeeded = true;


    public record CalculationResult(List<DrawableObject> drawables, List<DebugLogEntry> debugLogs) {}

    public IndicatorRunner(Indicator indicator, ChartDataModel dataModel) {
        this.indicator = indicator;
        this.dataModel = dataModel;
    }

    public void reset() {
        this.indicator.reset();
        this.mtfCache.clear();
        this.stateStore.clear();
        this.isResetNeeded = true;
    }

    public CalculationResult recalculate(List<KLine> dataSlice) {
        if (dataSlice == null || dataSlice.isEmpty()) {
            return new CalculationResult(Collections.emptyList(), Collections.emptyList());
        }

        // Convert the internal data model to the public API model to enforce the API boundary.
        List<ApiKLine> apiDataSlice = dataSlice.stream()
                .map(k -> new ApiKLine(k.timestamp(), k.open(), k.high(), k.low(), k.close(), k.volume()))
                .collect(Collectors.toList());

        final List<DebugLogEntry> collectedLogs = new ArrayList<>();
        Consumer<DebugLogEntry> loggerConsumer = collectedLogs::add;

        Function<Timeframe, List<KLine>> mtfDataProvider = (timeframe) -> {
            return mtfCache.computeIfAbsent(timeframe, dataModel::getResampledDataForView);
        };

        // [MODIFIED] Create the context with the new footprint data
        IndicatorContext context = new IndicatorContext(
            apiDataSlice,
            indicator.getSettings(),
            mtfDataProvider,
            loggerConsumer,
            this.stateStore,
            this.isResetNeeded,
            dataModel.getFootprintData() // Pass footprint data from the model
        );

        try {
            // Clear the MTF cache before every calculation to ensure fresh data
            mtfCache.clear();
            List<DrawableObject> drawables = indicator.calculate(context);
            
            // After a reset calculation, turn the flag off
            if (this.isResetNeeded) {
                this.isResetNeeded = false;
            }

            return new CalculationResult(drawables, collectedLogs);
        } catch (Exception e) {
            logger.error("Error during calculation for indicator '{}'", indicator.getName(), e);
            return new CalculationResult(Collections.emptyList(), Collections.emptyList());
        }
    }
    
    /**
     * Method to trigger the onSettingsChanged hook on the underlying plugin.
     * @param newSettings The new settings map.
     */
    public void onSettingsChanged(Map<String, Object> newSettings) {
        indicator.setSettings(newSettings);
        this.isResetNeeded = true; // Signal that a reset is required on next calculation.
        if (indicator instanceof CustomIndicatorAdapter adapter) {
            try {
                adapter.getPlugin().onSettingsChanged(newSettings, this.stateStore);
            } catch (Exception e) {
                logger.error("Error calling onSettingsChanged for indicator '{}'", indicator.getName(), e);
            }
        }
    }

    /**
     * Method to trigger the onRemoved hook on the underlying plugin.
     */
    public void onRemoved() {
        if (indicator instanceof CustomIndicatorAdapter adapter) {
            try {
                adapter.getPlugin().onRemoved(this.stateStore);
            } catch (Exception e) {
                logger.error("Error calling onRemoved for indicator '{}'", indicator.getName(), e);
            }
        }
        this.stateStore.clear(); // Clear state as a final cleanup step.
    }
    
    /**
     * Getter to expose the underlying indicator to the manager.
     */
    public Indicator getIndicator() {
        return this.indicator;
    }
}