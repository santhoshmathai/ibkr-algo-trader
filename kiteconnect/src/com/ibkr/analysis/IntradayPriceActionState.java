package com.ibkr.analysis;

/**
 * Holds the dynamic state for a single symbol being tracked by the IntradayPriceActionAnalyzer.
 */
public class IntradayPriceActionState {

    public final String symbol;

    public double intradayOpen = 0.0;
    public double intradayHigh = 0.0;
    public double intradayLow = Double.MAX_VALUE;
    public long volumeAccumulatedAtHighs = 0L; // Volume accumulated when new intraday highs are made
    public long timeOfLastIntradayHigh = 0L;   // Timestamp of the last intraday high bar

    public double initialTriggerPrice = 0.0;    // Price at which the primary condition was met
    public double lowestPriceAfterTrigger = Double.MAX_VALUE;
    public double highestPriceAfterTrigger = 0.0;

    public boolean recentVolumeSpikeOccurred = false; // Flag if a recent volume spike was observed relevant to conditions
    public boolean primaryConditionMet = false;     // True if the main set of conditions (PDH, range, volume etc.) were met

    public double pdh = 0.0; // Previous Day's High

    // Tracks the number of bars processed for certain rolling calculations if needed, e.g., for volume spike duration
    public int barsProcessedSinceLastSpikeReset = 0;


    public IntradayPriceActionState(String symbol) {
        this.symbol = symbol;
    }

    public void resetForNewDay(double pdh, double intradayOpen) {
        this.pdh = pdh;
        this.intradayOpen = intradayOpen; // Set based on the first bar of the day

        this.intradayHigh = intradayOpen; // Initialize with open
        this.intradayLow = intradayOpen;  // Initialize with open
        this.volumeAccumulatedAtHighs = 0L;
        this.timeOfLastIntradayHigh = 0L;

        this.initialTriggerPrice = 0.0;
        this.lowestPriceAfterTrigger = Double.MAX_VALUE;
        this.highestPriceAfterTrigger = 0.0;

        this.recentVolumeSpikeOccurred = false;
        this.primaryConditionMet = false;
        this.barsProcessedSinceLastSpikeReset = 0;
        // logger.info for symbol: state reset for new day
    }

    @Override
    public String toString() {
        return "IntradayPriceActionState{" +
                "symbol='" + symbol + '\'' +
                ", intradayOpen=" + intradayOpen +
                ", intradayHigh=" + intradayHigh +
                ", intradayLow=" + intradayLow +
                ", volumeAccumulatedAtHighs=" + volumeAccumulatedAtHighs +
                ", timeOfLastIntradayHigh=" + timeOfLastIntradayHigh +
                ", initialTriggerPrice=" + initialTriggerPrice +
                ", lowestPriceAfterTrigger=" + lowestPriceAfterTrigger +
                ", highestPriceAfterTrigger=" + highestPriceAfterTrigger +
                ", recentVolumeSpikeOccurred=" + recentVolumeSpikeOccurred +
                ", primaryConditionMet=" + primaryConditionMet +
                ", pdh=" + pdh +
                '}';
    }
}
