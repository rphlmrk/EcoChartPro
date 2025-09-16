package com.EcoChartPro.model.trading;

import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.model.TradeDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents an open, active trade that has been filled.
 *
 * @param id                   The unique identifier for this position (same as the originating order).
 * @param symbol               The symbol being traded.
 * @param direction            The direction of the position (Long or Short).
 * @param size                 The quantity of the asset held.
 * @param entryPrice           The price at which the position was opened.
 * @param stopLoss             The price level that will trigger a stop loss. For a trailing stop, this is the current trailed value.
 * @param takeProfit           The price level that will trigger a take profit.
 * @param trailingStopDistance The distance for the trailing stop. If null or zero, the stop is fixed.
 * @param openTimestamp        The timestamp when the position was opened.
 * @param checklistId          The ID of the checklist used for this trade, if any.
 */
public record Position(
    UUID id,
    Symbol symbol,
    TradeDirection direction,
    BigDecimal size,
    BigDecimal entryPrice,
    BigDecimal stopLoss,
    BigDecimal takeProfit,
    BigDecimal trailingStopDistance,
    Instant openTimestamp,
    UUID checklistId
) {}