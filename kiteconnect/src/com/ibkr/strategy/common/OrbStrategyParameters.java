package com.ibkr.strategy.common;

/**
 * Parameters for the revised Opening Range Breakout (ORB) strategy.
 */
public class OrbStrategyParameters {

    private final int orbTimeframeMinutes;
    private final double stopLossAtrPercentage;
    private final double riskPerTrade;
    private final double maxLeverage;

    /**
     * Default constructor with typical values.
     * - 5-minute opening range.
     * - Stop loss at 10% of 14-day ATR.
     * - Risk 1% of capital per trade.
     * - Max leverage of 4x.
     */
    public OrbStrategyParameters() {
        this.orbTimeframeMinutes = 5;
        this.stopLossAtrPercentage = 0.10; // 10% of ATR
        this.riskPerTrade = 0.01; // 1% of capital
        this.maxLeverage = 4.0; // 4x
    }

    /**
     * Constructor allowing full customization of the ORB strategy parameters.
     *
     * @param orbTimeframeMinutes   The duration of the opening range in minutes (e.g., 5, 15, 30).
     * @param stopLossAtrPercentage The percentage of ATR to set the stop loss at (e.g., 0.1 for 10%).
     * @param riskPerTrade          The fraction of capital to risk per trade (e.g., 0.01 for 1%).
     * @param maxLeverage           The maximum leverage to use for position sizing.
     */
    public OrbStrategyParameters(int orbTimeframeMinutes, double stopLossAtrPercentage, double riskPerTrade, double maxLeverage) {
        this.orbTimeframeMinutes = orbTimeframeMinutes;
        this.stopLossAtrPercentage = stopLossAtrPercentage;
        this.riskPerTrade = riskPerTrade;
        this.maxLeverage = maxLeverage;
    }

    // Getters
    public int getOrbTimeframeMinutes() {
        return orbTimeframeMinutes;
    }

    public double getStopLossAtrPercentage() {
        return stopLossAtrPercentage;
    }

    public double getRiskPerTrade() {
        return riskPerTrade;
    }

    public double getMaxLeverage() {
        return maxLeverage;
    }
}
