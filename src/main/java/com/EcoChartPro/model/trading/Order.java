package com.EcoChartPro.model.trading;

import com.EcoChartPro.model.Symbol;
import com.EcoChartPro.model.TradeDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a request to enter a trade, which may be pending or filled.
 *
 * @param id                   The unique identifier for this order.
 * @param symbol               The symbol to trade.
 * @param type                 The order type (Market, Limit, Stop).
 * @param status               The current status of the order (Pending, Filled, Cancelled).
 * @param direction            The direction of the intended trade (Long or Short).
 * @param size                 The quantity of the asset to trade.
 * @param limitPrice           The price for a LIMIT or STOP order. Null for MARKET orders.
 * @param stopLoss             An optional price level to automatically close the position at a loss.
 * @param takeProfit           An optional price level to automatically close the position at a profit.
 * @param trailingStopDistance An optional distance for a trailing stop loss. If set, the stop loss will trail the price.
 * @param creationTime         The timestamp when the order was created.
 * @param checklistId          The ID of the checklist used for this trade, if any.
 */
public record Order(
    UUID id,
    Symbol symbol,
    OrderType type,
    OrderStatus status,
    TradeDirection direction,
    BigDecimal size,
    BigDecimal limitPrice,
    BigDecimal stopLoss,
    BigDecimal takeProfit,
    BigDecimal trailingStopDistance,
    Instant creationTime,
    UUID checklistId
) {}