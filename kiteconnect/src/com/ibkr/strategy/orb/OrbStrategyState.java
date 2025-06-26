package com.ibkr.strategy.orb;

import java.time.LocalTime;

/**
 * Holds the dynamic state for a single symbol being tracked by the Opening Range Breakout (ORB) strategy.
 */
public class OrbStrategyState {

    private final String symbol;

    // ORB Definition State
    public double orbHigh = 0.0;
    public double orbLow = Double.MAX_VALUE;
    public boolean orbDefined = false;
    public LocalTime orbCalculationEndTime; // Time when ORB was finalized

    // Previous Day High State
    public double previousDayHigh = 0.0;
    public boolean pdhFetched = false;

    // Consolidation Filter State (9:30 AM - 9:45 AM)
    public boolean inConsolidationPeriod = false; // True if current time is within consolidation window
    public boolean consolidationRangeValid = true; // Remains true if price stays in tight range
    public double consolidationPeriodHigh = 0.0;
    public double consolidationPeriodLow = Double.MAX_VALUE;

    // Breakout State
    public boolean breakoutOccurred = false; // True if a valid breakout (volume, PDH, bid-ask) happened
    public double breakoutLevel = 0.0; // The level that was broken (ORB High)

    // Retest State
    // Retest is a transient check on a candle, might not need explicit long-term state beyond breakoutOccurred.
    // The strategy will check candles post-breakout for retest conditions.

    // Trade Management State
    public boolean tradeTakenToday = false; // To ensure only one ORB trade per symbol per day

    public OrbStrategyState(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public void resetForNewDay() {
        orbHigh = 0.0;
        orbLow = Double.MAX_VALUE;
        orbDefined = false;
        orbCalculationEndTime = null;

        previousDayHigh = 0.0;
        pdhFetched = false;

        inConsolidationPeriod = false;
        consolidationRangeValid = true;
        consolidationPeriodHigh = 0.0;
        consolidationPeriodLow = Double.MAX_VALUE;

        breakoutOccurred = false;
        breakoutLevel = 0.0;

        tradeTakenToday = false;
        // Logger.info for symbol: state reset for new day
    }

    @Override
    public String toString() {
        return "OrbStrategyState{" +
                "symbol='" + symbol + '\'' +
                ", orbHigh=" + orbHigh +
                ", orbLow=" + orbLow +
                ", orbDefined=" + orbDefined +
                ", orbCalculationEndTime=" + orbCalculationEndTime +
                ", previousDayHigh=" + previousDayHigh +
                ", pdhFetched=" + pdhFetched +
                ", inConsolidationPeriod=" + inConsolidationPeriod +
                ", consolidationRangeValid=" + consolidationRangeValid +
                ", consolidationPeriodHigh=" + consolidationPeriodHigh +
                ", consolidationPeriodLow=" + consolidationPeriodLow +
                ", breakoutOccurred=" + breakoutOccurred +
                ", breakoutLevel=" + breakoutLevel +
                ", tradeTakenToday=" + tradeTakenToday +
                '}';
    }
}
