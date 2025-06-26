package com.ibkr.strategy.common;

import java.time.LocalTime;

public class OrbStrategyParameters {

    // ORB Definition
    private final LocalTime orbStartTime; // e.g., 9:15 AM
    private final LocalTime orbEndTime;   // e.g., 9:30 AM
    private final int orbDurationMinutes; // e.g., 15

    // Breakout Conditions
    private final LocalTime tradeStartTime; // e.g., 9:30 AM (when to start looking for breakouts)
    private final double volumeSpikeMultiplier; // e.g., 1.5 for current volume > 1.5 * avg_vol
    private final int volumeAverageLookbackCandles; // e.g., 5 candles for average volume
    private final double minBidAskRatio; // e.g., 1.5 for TotalBidQty / TotalAskQty

    // Consolidation Filter
    private final LocalTime consolidationStartTime; // e.g., 9:30 AM
    private final LocalTime consolidationEndTime;   // e.g., 9:45 AM
    private final double consolidationMaxRangePercent; // e.g., 0.005 for 0.5%

    // Retest Condition
    private final double retestProximityPercent; // e.g., 0.001 for 0.1% proximity to ORB high

    // Default constructor with typical values
    public OrbStrategyParameters() {
        this.orbStartTime = LocalTime.of(9, 15);
        this.orbEndTime = LocalTime.of(9, 30);
        this.orbDurationMinutes = 15;

        this.tradeStartTime = LocalTime.of(9, 30);
        this.volumeSpikeMultiplier = 1.5;
        this.volumeAverageLookbackCandles = 5;
        this.minBidAskRatio = 1.5;

        this.consolidationStartTime = LocalTime.of(9, 30);
        this.consolidationEndTime = LocalTime.of(9, 45);
        this.consolidationMaxRangePercent = 0.005; // 0.5%

        this.retestProximityPercent = 0.001; // 0.1% for retest proximity
    }

    // Constructor allowing full customization
    public OrbStrategyParameters(LocalTime orbStartTime, LocalTime orbEndTime, int orbDurationMinutes,
                                 LocalTime tradeStartTime, double volumeSpikeMultiplier, int volumeAverageLookbackCandles,
                                 double minBidAskRatio, LocalTime consolidationStartTime, LocalTime consolidationEndTime,
                                 double consolidationMaxRangePercent, double retestProximityPercent) {
        this.orbStartTime = orbStartTime;
        this.orbEndTime = orbEndTime;
        this.orbDurationMinutes = orbDurationMinutes;
        this.tradeStartTime = tradeStartTime;
        this.volumeSpikeMultiplier = volumeSpikeMultiplier;
        this.volumeAverageLookbackCandles = volumeAverageLookbackCandles;
        this.minBidAskRatio = minBidAskRatio;
        this.consolidationStartTime = consolidationStartTime;
        this.consolidationEndTime = consolidationEndTime;
        this.consolidationMaxRangePercent = consolidationMaxRangePercent;
        this.retestProximityPercent = retestProximityPercent;
    }

    // Getters
    public LocalTime getOrbStartTime() {
        return orbStartTime;
    }

    public LocalTime getOrbEndTime() {
        return orbEndTime;
    }

    public int getOrbDurationMinutes() {
        return orbDurationMinutes;
    }

    public LocalTime getTradeStartTime() {
        return tradeStartTime;
    }

    public double getVolumeSpikeMultiplier() {
        return volumeSpikeMultiplier;
    }

    public int getVolumeAverageLookbackCandles() {
        return volumeAverageLookbackCandles;
    }

    public double getMinBidAskRatio() {
        return minBidAskRatio;
    }

    public LocalTime getConsolidationStartTime() {
        return consolidationStartTime;
    }

    public LocalTime getConsolidationEndTime() {
        return consolidationEndTime;
    }

    public double getConsolidationMaxRangePercent() {
        return consolidationMaxRangePercent;
    }

    public double getRetestProximityPercent() {
        return retestProximityPercent;
    }
}
