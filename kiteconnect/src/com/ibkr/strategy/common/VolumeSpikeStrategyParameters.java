package com.ibkr.strategy.common;

/**
 * Encapsulates parameters for the Volume Spike analysis logic.
 */
public class VolumeSpikeStrategyParameters {

    private String dataDirectory = "market_data";
    private int daysToAnalyze = 5;
    private double volumeSpikeThreshold = 1.40; // 40% spike
    private int volumeAggregationIntervalMinutes = 15;
    private int tradingIntervalsPerDay = 26; // Default for 9:30-4:00 -> 6.5h -> 26 intervals

    // --- Getters ---

    public String getDataDirectory() {
        return dataDirectory;
    }

    public int getDaysToAnalyze() {
        return daysToAnalyze;
    }

    public double getVolumeSpikeThreshold() {
        return volumeSpikeThreshold;
    }

    public int getVolumeAggregationIntervalMinutes() {
        return volumeAggregationIntervalMinutes;
    }

    public int getTradingIntervalsPerDay() {
        return tradingIntervalsPerDay;
    }

    // --- Setters for testing and dynamic configuration ---

    public void setDataDirectory(String dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public void setDaysToAnalyze(int daysToAnalyze) {
        this.daysToAnalyze = daysToAnalyze;
    }
}
