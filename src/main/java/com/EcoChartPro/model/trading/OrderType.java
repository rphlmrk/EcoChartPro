package com.EcoChartPro.model.trading;

/**
 * Defines the type of an order, which determines how it is filled.
 */
public enum OrderType {
    /**
     * An order to be filled immediately at the current market price.
     */
    MARKET,

    /**
     * An order to buy below the current price or sell above the current price.
     */
    LIMIT,

    /**
     * An order to buy above the current price or sell below the current price,
     * often used to enter a trade on a breakout or to cap losses.
     */
    STOP
}