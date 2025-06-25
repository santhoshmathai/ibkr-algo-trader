package com.marketstrategy;

/**
 * Holds configuration parameters for the trading strategies and market analysis.
 * Instances of this class can be created and public fields modified to tune strategy behavior.
 * Future enhancements could include loading these configurations from an external file.
 */
public class StrategyConfig {

    // --- General Strategy Enablement ---
    /** Whether the Momentum Strategy should be run. Default: true */
    public boolean enableMomentumStrategy = true;
    /** Whether the Reversal Strategy should be run. Default: true */
    public boolean enableReversalStrategy = true;

    // --- Market Sentiment Analyzer Configuration ---
    /**
     * Minimum positive percentage gap for a stock to be considered significantly gapped up
     * in market sentiment analysis. Example: 1.0 for +1.0%. Default: 1.0
     */
    public double sentimentSignificantGapUpThresholdPercent = 1.0;
    /**
     * Minimum negative percentage gap for a stock to be considered significantly gapped down
     * (should be a negative value). Example: -1.0 for -1.0%. Default: -1.0
     */
    public double sentimentSignificantGapDownThresholdPercent = -1.0;
    /**
     * Percentage of stocks that need to show a directional gap for the market
     * to be classified as BULLISH_OPEN or BEARISH_OPEN. Example: 60.0 for 60%. Default: 60.0
     */
    public double sentimentDecisionThresholdPercent = 60.0;

    // --- Momentum Strategy Configuration ---
    /** Number of top stocks to select for momentum signals. Default: 3 */
    public int momentumTopNStocks = 3;
    /**
     * Minimum positive gap percentage for a stock to be considered for a long momentum trade.
     * Example: 2.0 for +2.0%. Default: 2.0
     */
    public double momentumMinGapUpPercent = 2.0;
    /**
     * Minimum negative gap percentage for a stock to be considered for a short momentum trade
     * (should be a negative value). Example: -2.0 for -2.0%. Default: -2.0
     */
    public double momentumMinGapDownPercent = -2.0;
    /**
     * Minimum Last Traded Price (LTP) for a stock to be considered in the momentum strategy.
     * Helps avoid trading very low-priced (penny) stocks. Default: 50.0
     */
    public double momentumMinLtpForTrade = 50.0;

    // --- Reversal Strategy Configuration ---
    /**
     * Number of top stocks to select for reversal signals.
     * Note: Current ReversalStrategy implementation identifies all qualifying stocks;
     * strict top N selection is a potential future enhancement. Default: 2
     */
    public int reversalTopNStocks = 2;
    /**
     * Minimum absolute gap percentage for a stock to be considered for a reversal trade.
     * Example: 3.0 for +/-3.0%. Default: 3.0
     */
    public double reversalMinAbsGapPercent = 3.0;
    /**
     * Minimum percentage move from Open against the gap direction to confirm a reversal.
     * Example: 1.0 for 1.0%. Default: 1.0
     */
    public double reversalConfirmationPercent = 1.0;
    /**
     * Minimum Last Traded Price (LTP) for a stock to be considered in the reversal strategy.
     * Default: 50.0
     */
    public double reversalMinLtpForTrade = 50.0;

    /**
     * Default constructor. Initializes configuration parameters with their default values.
     * These public fields can be modified after instantiation to customize strategy behavior.
     */
    public StrategyConfig() {
        // Default constructor, values are initialized with defaults above.
    }

    // Future enhancement: could add methods to load config from a file.
}
