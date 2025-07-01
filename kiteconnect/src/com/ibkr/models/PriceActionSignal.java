package com.ibkr.models;

/**
 * Represents the outcome of an intraday price action analysis,
 * indicating whether a significant positive or negative change has been observed
 * after certain conditions were met, or if no significant change or conditions
 * have yet been met.
 */
public enum PriceActionSignal {
    /**
     * A significant positive price movement was observed after the primary trigger conditions were met.
     */
    SIGNIFICANT_POSITIVE_CHANGE,

    /**
     * A significant negative price movement (drawdown) was observed after the primary trigger conditions were met.
     */
    SIGNIFICANT_NEGATIVE_CHANGE,

    /**
     * The primary trigger conditions have been met, but no subsequent significant positive or negative change
     * has yet been observed according to the defined thresholds.
     */
    NO_SIGNIFICANT_CHANGE_POST_TRIGGER,

    /**
     * The primary trigger conditions for the analysis have not yet been met for the day.
     */
    CONDITION_NOT_MET_YET,

    /**
     * Default or neutral state, analysis might not be applicable or no specific signal.
     */
    NEUTRAL_OR_NO_ACTION
}
