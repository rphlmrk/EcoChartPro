package com.EcoChartPro.data;

import com.EcoChartPro.model.KLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A utility class for transforming KLine data into different representations, such as Heikin-Ashi.
 */
public final class DataTransformer {

    private DataTransformer() {}

    /**
     * Transforms a list of standard K-lines into a list of Heikin-Ashi K-lines.
     *
     * @param standardKlines The input list of standard OHLC candles.
     * @return A new list containing the calculated Heikin-Ashi candles.
     */
    public static List<KLine> transformToHeikinAshi(List<KLine> standardKlines) {
        if (standardKlines == null || standardKlines.isEmpty()) {
            return Collections.emptyList();
        }

        List<KLine> heikinAshiKlines = new ArrayList<>(standardKlines.size());
        KLine previousHA = null;

        for (KLine current : standardKlines) {
            // HA_Close = (Open + High + Low + Close) / 4
            BigDecimal haClose = current.open().add(current.high()).add(current.low()).add(current.close())
                    .divide(BigDecimal.valueOf(4), 8, RoundingMode.HALF_UP);

            BigDecimal haOpen;
            if (previousHA == null) {
                // For the first candle, HA_Open = (Open + Close) / 2
                haOpen = current.open().add(current.close())
                        .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
            } else {
                // HA_Open = (Previous HA_Open + Previous HA_Close) / 2
                haOpen = previousHA.open().add(previousHA.close())
                        .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
            }

            // HA_High = Max(High, HA_Open, HA_Close)
            BigDecimal haHigh = current.high().max(haOpen).max(haClose);

            // HA_Low = Min(Low, HA_Open, HA_Close)
            BigDecimal haLow = current.low().min(haOpen).min(haClose);

            KLine haKline = new KLine(
                current.timestamp(),
                haOpen,
                haHigh,
                haLow,
                haClose,
                current.volume()
            );

            heikinAshiKlines.add(haKline);
            previousHA = haKline;
        }

        return heikinAshiKlines;
    }
}