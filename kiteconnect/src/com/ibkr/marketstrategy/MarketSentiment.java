package com.ibkr.marketstrategy;

/**
 * Represents the overall market sentiment, typically derived from analyzing
 * a basket of stocks or market indices at a specific point in time (e.g., market open).
 * This enum helps in categorizing the market's state to guide strategy decisions.
 */
public enum MarketSentiment {
    /**
     * Indicates a strong bullish sentiment at market open,
     * e.g., a significant majority of analyzed stocks gapped up.
     */
    BULLISH_OPEN,

    /**
     * Indicates a strong bearish sentiment at market open,
     * e.g., a significant majority of analyzed stocks gapped down.
     */
    BEARISH_OPEN,

    /**
     * Indicates a mixed or choppy sentiment at market open,
     * where there isn't a clear directional bias from gapping stocks.
     * For example, similar numbers of stocks gapped up and down, or most were flat.
     */
    MIXED_OPEN,

    /**
     * Indicates a neutral state, possibly due to insufficient data to make a determination,
     * or if the market shows no significant directional bias after the open.
     * This can also be a default state before analysis is complete.
     */
    NEUTRAL,

    /**
     * Indicates a generally strong upward trend observed during the trading session,
     * not necessarily just at the open. This might be based on a broader set of indicators
     * like advancing vs. declining stocks over a period.
     */
    STRONG_UP, // Added from com.ibkr.analysis.MarketSentimentAnalyzer

    /**
     * Indicates a generally strong downward trend observed during the trading session.
     */
    STRONG_DOWN // Added from com.ibkr.analysis.MarketSentimentAnalyzer
}
