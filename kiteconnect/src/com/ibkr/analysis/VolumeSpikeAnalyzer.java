package com.ibkr.analysis;

import com.ibkr.data.HistoricalVolumeService;
import com.zerodhatech.models.Tick;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Analyzes real-time tick data to detect significant volume spikes
 * based on a projection against the average daily volume.
 */
public class VolumeSpikeAnalyzer {

    private final HistoricalVolumeService historicalVolumeService;


    // Maps to track real-time volume
    private final Map<String, Long> currentIntervalVolume = new ConcurrentHashMap<>();
    private final Map<String, Integer> currentIntervalIndex = new ConcurrentHashMap<>();
    private final Set<String> activeSpikeSymbols = ConcurrentHashMap.newKeySet();

    public VolumeSpikeAnalyzer(HistoricalVolumeService historicalVolumeService) {
        this.historicalVolumeService = historicalVolumeService;

    }

    /**
     * Processes a live market data tick to update volume information.
     *
     * @param tick The live tick data.
     * @param symbol The symbol for the tick.
     */
    public void update(Tick tick, String symbol) {
        if (tick == null || symbol == null) {
            return;
        }

        Long lastTradedQty = (long) tick.getLastTradedQuantity();
        if (lastTradedQty <= 0) {
            return;
        }

        int newIntervalIndex = getCurrentIntervalIndex();
        int previousIntervalIndex = currentIntervalIndex.getOrDefault(symbol, -1);

        // Reset if we have entered a new 15-minute interval
        if (newIntervalIndex != previousIntervalIndex) {
            currentIntervalIndex.put(symbol, newIntervalIndex);
            currentIntervalVolume.put(symbol, 0L);
            activeSpikeSymbols.remove(symbol);
        }

        long updatedVolume = currentIntervalVolume.merge(symbol, lastTradedQty, Long::sum);

        // Check for spike using the corrected logic
        double averageDailyVolume = historicalVolumeService.getAverageDailyVolume(symbol);
        if (averageDailyVolume > 0) {
            long projectedDailyVolume = updatedVolume * 390; // 390 intervals of 1 minute in a trading day
            if (projectedDailyVolume > (averageDailyVolume * 2.0)) { // Spike threshold of 2.0
                if (activeSpikeSymbols.add(symbol)) {
                    System.out.println("INFO: Volume spike detected for " + symbol + ". Projected Volume: " + projectedDailyVolume + ", Avg Daily Volume: " + String.format("%.2f", averageDailyVolume));
                }
            }
        }
    }

    /**
     * Checks if a symbol is currently experiencing a volume spike.
     */
    public boolean isHappening(String symbol) {
        return activeSpikeSymbols.contains(symbol);
    }

    private int getCurrentIntervalIndex() {
        LocalDateTime now = LocalDateTime.now();
        int intervalMinutes = 15; // 15-minute intervals
        if (intervalMinutes <= 0) {
            return now.getHour() * 60 + now.getMinute(); // Fallback to 1-minute intervals
        }
        return (now.getHour() * 60 + now.getMinute()) / intervalMinutes;
    }
}
