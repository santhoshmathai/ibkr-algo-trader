package com.marketstrategy;

/**
 * Enum representing the overall market sentiment, typically determined
 * by analyzing market conditions at the open (e.g., percentage of stocks gapping up/down).
 */
public enum MarketSentiment {
    /** Indicates a predominantly positive sentiment at the market open. */
    BULLISH_OPEN,
    /** Indicates a predominantly negative sentiment at the market open. */
    BEARISH_OPEN,
    /** Indicates no clear directional bias at the market open; mixed signals. */
    MIXED_OPEN,
    /** Represents a default or undetermined sentiment, possibly due to insufficient data. */
    NEUTRAL
}
