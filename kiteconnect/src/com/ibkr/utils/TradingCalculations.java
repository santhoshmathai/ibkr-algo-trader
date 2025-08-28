package com.ibkr.utils;

import com.zerodhatech.models.HistoricalData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class TradingCalculations {

    private static final Logger logger = LoggerFactory.getLogger(TradingCalculations.class);

    /**
     * Calculates the Average True Range (ATR) for a given period from a list of daily bars.
     *
     * @param bars   A list of HistoricalData objects, sorted from oldest to newest.
     * @param period The number of periods to calculate the ATR over (e.g., 14).
     * @return The ATR value, or 0.0 if the data is insufficient.
     */
    public static double calculateATR(List<HistoricalData> bars, int period) {
        if (bars == null || bars.size() < period) {
            logger.warn("Cannot calculate ATR: Not enough historical bars provided. Have {}, need {}.", bars == null ? 0 : bars.size(), period);
            return 0.0;
        }

        double[] trueRanges = new double[bars.size()];
        if (bars.size() > 1) {
            // First bar's TR requires previous close, which we assume is the close of the bar before it.
            // For simplicity in this context, we calculate from the second bar onwards where a previous close is available.
            trueRanges[0] = bars.get(0).high - bars.get(0).low; // Simplified TR for the very first bar in the set

            for (int i = 1; i < bars.size(); i++) {
                HistoricalData current = bars.get(i);
                HistoricalData previous = bars.get(i - 1);
                trueRanges[i] = calculateTrueRange(current.high, current.low, previous.close);
            }
        } else {
            return 0.0; // Not enough data for ATR
        }


        // Calculate the initial ATR as the simple average of the first 'period' true ranges.
        double atr = 0.0;
        for (int i = 0; i < period; i++) {
            atr += trueRanges[i];
        }
        atr /= period;

        // Smooth the rest of the ATR values
        for (int i = period; i < bars.size(); i++) {
            atr = ((atr * (period - 1)) + trueRanges[i]) / period;
        }

        return atr;
    }

    /**
     * Calculates the True Range for a single bar.
     *
     * @param high         The high of the current bar.
     * @param low          The low of the current bar.
     * @param previousClose The close of the previous bar.
     * @return The True Range value.
     */
    public static double calculateTrueRange(double high, double low, double previousClose) {
        double highMinusLow = high - low;
        double highMinusPrevClose = Math.abs(high - previousClose);
        double lowMinusPrevClose = Math.abs(low - previousClose);
        return Math.max(highMinusLow, Math.max(highMinusPrevClose, lowMinusPrevClose));
    }

    /**
     * Calculates the average volume for a given list of bars.
     *
     * @param bars A list of HistoricalData objects.
     * @return The average volume as a double.
     */
    public static double calculateAverageVolume(List<HistoricalData> bars) {
        if (bars == null || bars.isEmpty()) {
            logger.warn("No bars provided for average volume calculation");
            return 0.0;
        }

        // Debug: log all volumes
        logger.debug("Calculating average volume from {} bars:", bars.size());
        for (int i = 0; i < Math.min(bars.size(), 5); i++) { // Log first 5 bars
            HistoricalData bar = bars.get(i);
            logger.debug("Bar {}: timestamp={}, volume={}", i, bar.timeStamp, bar.volume);
        }

        double average = bars.stream()
                .mapToLong(bar -> bar.volume)
                .average()
                .orElse(0.0);

        logger.debug("Calculated average volume: {}", average);
        return average;
    }
}
