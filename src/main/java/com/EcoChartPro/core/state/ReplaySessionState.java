package com.EcoChartPro.core.state;

import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.trading.Order;
import com.EcoChartPro.model.trading.Position;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Represents the complete, serializable state of a replay session.
 * This object can be persisted to a file (e.g., JSON) and loaded
 * to resume a session later.
 */
public record ReplaySessionState(
    /**
     * The symbol being traded (e.g., "btcusdt"). This links the session to a data source.
     */
    String dataSourceSymbol,

    /**
     * The index of the last processed bar in the 1-minute base data,
     * allowing the session to resume from the exact point it was saved.
     */
    int replayHeadIndex,

    /**
     * The current account balance at the time the session was saved.
     */
    BigDecimal accountBalance,

    /**
     * A list of all open positions that have not yet been closed.
     */
    List<Position> openPositions,

    /**
     * A list of all active orders (e.g., limit, stop) that have not yet been filled.
     */
    List<Order> pendingOrders,
    
    /**
     * A complete history of all closed trades made during the session.
     */
    List<Trade> tradeHistory,

    /**
     * A list of all user-created drawings on the chart.
     */
    List<DrawingObject> drawings,
    
    /**
     * The timestamp of the last bar processed.
     */
    Instant lastTimestamp

) implements Serializable {}