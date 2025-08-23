package com.ibkr.data;

import com.ibkr.IBKRApiService;
import com.ibkr.strategy.common.VolumeSpikeStrategyParameters;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class HistoricalVolumeService {

    private final Map<String, Double> averageDailyVolume = new ConcurrentHashMap<>();
    private final IBKRApiService ibkrApiService;

    public HistoricalVolumeService(IBKRApiService ibkrApiService) {
        this.ibkrApiService = ibkrApiService;
    }

    public void calculateAverageVolumes(List<String> symbols) {
        System.out.println("Starting historical daily volume calculation for " + symbols.size() + " symbols using IBKR API.");

        Map<String, CompletableFuture<Double>> futures = new ConcurrentHashMap<>();
        for (String symbol : symbols) {
            futures.put(symbol, ibkrApiService.getAverageDailyVolume(symbol));
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

    public static void main(String[] args) {
        System.out.println("Running HistoricalVolumeService self-test...");
        IBKRApiService ibkrApiService = new IBKRApiService();
        try {
            // 1. Setup
            ibkrApiService.connect("127.0.0.1", 7497, 0); // Connect to TWS or Gateway
            System.out.println("Waiting for IBKR API connection to be established...");
            ibkrApiService.awaitConnection();
            System.out.println("IBKR API connection established.");

            HistoricalVolumeService service = new HistoricalVolumeService(ibkrApiService);
            List<String> symbols = java.util.Collections.singletonList("AAPL");

            // 2. Execute
            service.calculateAverageVolumes(symbols);
            double result = service.getAverageDailyVolume("AAPL");

            // 3. Assert
            // The expected value will depend on the actual data from IBKR.
            // For this test, we just check if the result is greater than 0.
            if (result <= 0) {
                throw new AssertionError("Expected a positive average volume, but got " + result);
            }
            System.out.println("Self-test PASSED! Average daily volume for AAPL is " + result);

        } catch (Exception e) {
            System.err.println("Self-test FAILED!");
            e.printStackTrace();
        } finally {
            System.out.println("Disconnecting from IBKR API.");
            ibkrApiService.disconnect();
        }
    }
}
