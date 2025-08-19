package com.ibkr.strategy.orb;

import com.zerodhatech.models.OHLC;

/**
 * Holds the dynamic state for a single symbol being tracked by the revised Opening Range Breakout (ORB) strategy.
 */
public class OrbStrategyState {

    private final String symbol;

    public enum CandleDirection {
        UNDEFINED,
        BULLISH,
        BEARISH,
        DOJI
    }

    // Opening Range State
    public OHLC openingRangeCandle;
    public long openingRangeVolume = 0;
    public CandleDirection candleDirection = CandleDirection.UNDEFINED;
    public boolean rangeDefined = false;

    // Data passed from screener
    public double atr14day = 0.0;
    public double avgOpeningRangeVolume14day = 0.0; // New field for 14-day average ORB volume

    // Calculated values
    public double relativeVolume = 0.0; // New field for the calculated relative volume


    // Trade Management State
    public boolean stopOrderPlaced = false;

    public OrbStrategyState(String symbol) {
        this.symbol = symbol;
        resetForNewDay(); // Initialize with default values
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Resets the state for a new trading day.
     */
    public void resetForNewDay() {
        this.openingRangeCandle = null;
        this.openingRangeVolume = 0;
        this.candleDirection = CandleDirection.UNDEFINED;
        this.rangeDefined = false;
        this.atr14day = 0.0;
        this.avgOpeningRangeVolume14day = 0.0;
        this.relativeVolume = 0.0;
        this.stopOrderPlaced = false;
    }

    @Override
    public String toString() {
        return "OrbStrategyState{" +
                "symbol='" + symbol + '\'' +
                ", rangeDefined=" + rangeDefined +
                ", candleDirection=" + candleDirection +
                ", atr14day=" + atr14day +
                ", stopOrderPlaced=" + stopOrderPlaced +
                ", openingRangeCandle=" + (openingRangeCandle != null ? "OHLC{...}" : "null") +
                '}';
    }
}
