package com.ibkr.data;

import com.ibkr.service.MarketDataService;
import com.ibkr.strategy.common.VolumeSpikeStrategyParameters;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class HistoricalVolumeService {

    private final Map<String, Double> averageDailyVolume = new ConcurrentHashMap<>();
    private final MarketDataService marketDataService;

    public HistoricalVolumeService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    public void calculateAverageVolumes(List<String> symbols) {
        System.out.println("Starting historical daily volume calculation for " + symbols.size() + " symbols using IBKR API.");

        Map<String, CompletableFuture<Double>> futures = new ConcurrentHashMap<>();
        for (String symbol : symbols) {
            futures.put(symbol, marketDataService.getAverageDailyVolume(symbol));
        }

        for (Map.Entry<String, CompletableFuture<Double>> entry : futures.entrySet()) {
            String symbol = entry.getKey();
            try {
                Double avgVolume = entry.getValue().get(); // Blocking call to wait for the result
                if (avgVolume > 0) {
                    averageDailyVolume.put(symbol, avgVolume);
                    System.out.println("Cached average DAILY volume for " + symbol + ": " + String.format("%.2f", avgVolume));
                }
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Error processing symbol " + symbol + " for daily volume: " + e.getMessage());
            }
        }

        System.out.println("Historical daily volume calculation complete.");
    }

    public double getAverageDailyVolume(String symbol) {
        return averageDailyVolume.getOrDefault(symbol, 0.0);
    }
}
