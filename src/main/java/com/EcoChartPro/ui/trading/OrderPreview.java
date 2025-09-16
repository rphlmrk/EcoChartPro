package com.EcoChartPro.ui.trading;

import java.math.BigDecimal;

/**
 * A data-transfer-object to hold the price levels for an order preview on the chart.
 * Fields can be null if they are not set or are invalid.
 *
 * @param entryPrice The proposed entry price.
 * @param stopLoss The proposed stop loss price.
 * @param takeProfit The proposed take profit price.
 */
public record OrderPreview(
    BigDecimal entryPrice,
    BigDecimal stopLoss,
    BigDecimal takeProfit
) {}