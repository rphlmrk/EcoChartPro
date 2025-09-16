package com.EcoChartPro.model.trading;

/**
 * Defines the lifecycle status of an order.
 */
public enum OrderStatus {
    /**
     * The order has been submitted but not yet filled.
     */
    PENDING,

    /**
     * The order has been executed and resulted in a position.
     */
    FILLED,

    /**
     * The order was cancelled before it could be filled.
     */
    CANCELLED
}