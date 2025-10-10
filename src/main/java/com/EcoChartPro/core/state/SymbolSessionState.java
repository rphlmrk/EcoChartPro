package com.EcoChartPro.core.state;

import com.EcoChartPro.model.Trade;
import com.EcoChartPro.model.drawing.DrawingObject;
import com.EcoChartPro.model.trading.Order;
import com.EcoChartPro.model.trading.Position;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * [NEW] Represents the complete, serializable state for a single symbol
 * within a multi-symbol replay session.
 */
public record SymbolSessionState(
    /**
     * The index of the last processed bar in the 1-minute base data for this symbol.
     */
    int replayHeadIndex,

    /**
     * A list of all open positions for this symbol.
     */
    List<Position> openPositions,

    /**
     * A list of all active orders (e.g., limit, stop) for this symbol.
     */
    List<Order> pendingOrders,
    
    /**
     * A complete history of all closed trades for this symbol.
     */
    List<Trade> tradeHistory,

    /**
     * A list of all user-created drawings on the chart for this symbol.
     */
    List<DrawingObject> drawings,
    
    /**
     * The timestamp of the last bar processed for this symbol.
     */
    Instant lastTimestamp

) implements Serializable {

    // Jackson constructor for robust deserialization
    @JsonCreator
    public SymbolSessionState(
        @JsonProperty("replayHeadIndex") int replayHeadIndex,
        @JsonProperty("openPositions") List<Position> openPositions,
        @JsonProperty("pendingOrders") List<Order> pendingOrders,
        @JsonProperty("tradeHistory") List<Trade> tradeHistory,
        @JsonProperty("drawings") List<DrawingObject> drawings,
        @JsonProperty("lastTimestamp") Instant lastTimestamp
    ) {
        this.replayHeadIndex = replayHeadIndex;
        this.openPositions = openPositions;
        this.pendingOrders = pendingOrders;
        this.tradeHistory = tradeHistory;
        this.drawings = drawings;
        this.lastTimestamp = lastTimestamp;
    }
}