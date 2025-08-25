package com.ibkr.data;

import com.ibkr.service.MarketDataService;
import com.ibkr.utils.TradingCalculations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class HistoricalVolumeService {

    private static final Logger logger = LoggerFactory.getLogger(HistoricalVolumeService.class);
    private final Map<String, Double> averageDailyVolume = new ConcurrentHashMap<>();
    private final MarketDataService marketDataService;

    public HistoricalVolumeService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    public void calculateAverageVolumes(List<String> symbols) {
        logger.info("Starting historical daily volume calculation for {} symbols.", symbols.size());

        // We need 14 days of data to calculate a 14-day average.
        // Requesting 15 to be safe, to account for today if it's a trading day but before market close.
        final int requiredBars = 15;
        Map<String, CompletableFuture<Double>> futures = new ConcurrentHashMap<>();
        for (String symbol : symbols) {
            CompletableFuture<Double> avgVolumeFuture = marketDataService.getDailyHistoricalData(symbol, requiredBars)
                    .thenApply(bars -> {
                        if (bars == null || bars.isEmpty()) {
                            logger.warn("Received empty or null historical data for {}. Cannot calculate average volume.", symbol);
                            return 0.0;
                        }
                        return TradingCalculations.calculateAverageVolume(bars);
                    });
            futures.put(symbol, avgVolumeFuture);
        }

        CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0])).join(); // Wait for all futures to complete

        for (Map.Entry<String, CompletableFuture<Double>> entry : futures.entrySet()) {
            String symbol = entry.getKey();
            try {
                Double avgVolume = entry.getValue().get(); // This should not block as we joined above
                if (avgVolume > 0) {
                    averageDailyVolume.put(symbol, avgVolume);
                    logger.info("Cached average DAILY volume for {}: {}", symbol, String.format("%.2f", avgVolume));
                }
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error processing symbol {} for daily volume.", symbol, e);
            }
        }

        logger.info("Historical daily volume calculation complete.");
    }

    public double getAverageDailyVolume(String symbol) {
        return averageDailyVolume.getOrDefault(symbol, 0.0);
    }
}
