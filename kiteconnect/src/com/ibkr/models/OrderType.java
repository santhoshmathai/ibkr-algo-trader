package com.ibkr.models;

/**
 * Defines the type of order to be placed.
 */
public enum OrderType {
    MARKET,
    LIMIT,
    STOP,
    BUY_STOP,
    SELL_STOP,
    TRAILING_STOP
}
