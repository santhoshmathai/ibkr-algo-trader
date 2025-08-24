package com.ibkr.screener;

import com.ibkr.service.MarketDataService;
import com.ibkr.utils.TradingCalculations;
import com.zerodhatech.models.HistoricalData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Performs a two-phase screening process to identify "Stocks in Play"
 * based on pre-market data and opening range activity.
 */
public class StockScreener {

    private static final Logger logger = LoggerFactory.getLogger(StockScreener.class);

    private final MarketDataService marketDataService;
    private final int requiredBars = 15; // e.g., 14 for ATR + 1 previous close

    // Screening criteria
    private static final double MIN_PRICE = 5.0;
    private static final long MIN_AVG_VOLUME = 1_000_000;
    private static final double MIN_ATR = 0.50;
    private static final double MIN_RELATIVE_VOLUME = 1.0;
    private static final int TOP_N_STOCKS = 20;


    public StockScreener(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    /**
     * DTO class to hold the results of the screening process for a single stock.
     */
    public static class ScreeningResult {
        public final String symbol;
        public final double atr14;
        public final double avgVolume14;
        public double relativeVolume; // Can be updated in phase 2
        public final double lastClosePrice;

        public ScreeningResult(String symbol, double atr14, double avgVolume14, double lastClosePrice) {
            this.symbol = symbol;
            this.atr14 = atr14;
            this.avgVolume14 = avgVolume14;
            this.lastClosePrice = lastClosePrice;
            this.relativeVolume = 0; // Default value
        }

        public void setRelativeVolume(double relativeVolume) {
            this.relativeVolume = relativeVolume;
        }

        @Override
        public String toString() {
            return String.format("ScreeningResult{symbol='%s', lastClose=%.2f, atr14=%.2f, avgVolume14=%.0f, relativeVolume=%.2f}",
                    symbol, lastClosePrice, atr14, avgVolume14, relativeVolume);
        }
    }

    /**
     * Phase 1: Pre-market screening based on historical daily data.
     */
    public CompletableFuture<List<ScreeningResult>> runPreMarketScreen(Set<String> symbolsToScreen) {
        logger.info("Starting pre-market screen for {} symbols.", symbolsToScreen.size());

        List<CompletableFuture<ScreeningResult>> futures = symbolsToScreen.stream()
                .map(this::processSymbolForPreMarketScreen)
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(result -> result != null) // Filter out symbols that failed processing
                        .filter(this::passesPreMarketCriteria) // Apply screening criteria
                        .collect(Collectors.toList()));
    }

    private CompletableFuture<ScreeningResult> processSymbolForPreMarketScreen(String symbol) {
        return marketDataService.getDailyHistoricalData(symbol, requiredBars)
                .thenApply(bars -> {
                    if (bars == null || bars.size() < requiredBars) {
                        logger.warn("Insufficient daily data for {}. Needed {}, got {}. Skipping.", symbol, requiredBars, bars == null ? 0 : bars.size());
                        return null;
                    }
                    double atr14 = TradingCalculations.calculateATR(bars, 14);
                    double avgVolume14 = TradingCalculations.calculateAverageVolume(bars);
                    double lastClosePrice = bars.get(bars.size() - 1).close;
                    return new ScreeningResult(symbol, atr14, avgVolume14, lastClosePrice);
                }).exceptionally(ex -> {
                    logger.error("Failed to process pre-market data for symbol: {}", symbol, ex);
                    return null;
                });
    }

    private boolean passesPreMarketCriteria(ScreeningResult result) {
        if (result.lastClosePrice < MIN_PRICE) {
            logger.debug("FILTERED (Price): {} - Price ${} < ${}", result.symbol, result.lastClosePrice, MIN_PRICE);
            return false;
        }
        if (result.avgVolume14 < MIN_AVG_VOLUME) {
            logger.debug("FILTERED (Volume): {} - AvgVol {} < {}", result.symbol, (long)result.avgVolume14, MIN_AVG_VOLUME);
            return false;
        }
        if (result.atr14 < MIN_ATR) {
            logger.debug("FILTERED (ATR): {} - ATR ${} < ${}", result.symbol, result.atr14, MIN_ATR);
            return false;
        }
        logger.info("PASSED Pre-Market Screen: {}", result);
        return true;
    }

    /**
     * Phase 2: Opening range screening based on relative volume.
     */
    public CompletableFuture<List<ScreeningResult>> runOpeningRangeScreen(
            List<ScreeningResult> preScreenedStocks,
            Map<String, Long> currentOpeningRangeVolume) {

        logger.info("Starting opening range screen for {} pre-screened stocks.", preScreenedStocks.size());

        List<CompletableFuture<ScreeningResult>> futures = preScreenedStocks.stream()
                .map(result -> calculateAndSetRelativeVolume(result, currentOpeningRangeVolume.get(result.symbol)))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(result -> result != null)
                        .filter(result -> result.relativeVolume >= MIN_RELATIVE_VOLUME)
                        .sorted(Comparator.comparingDouble(res -> -res.relativeVolume)) // Sort descending
                        .limit(TOP_N_STOCKS)
                        .collect(Collectors.toList()));
    }

    private CompletableFuture<ScreeningResult> calculateAndSetRelativeVolume(ScreeningResult result, Long currentVolume) {
        if (currentVolume == null) {
            logger.warn("No current opening range volume for {}. Cannot calculate RV.", result.symbol);
            return CompletableFuture.completedFuture(null);
        }

        // TODO: Get market open time from config
        return marketDataService.getOpeningRangeVolumeHistory(result.symbol, 14, 5, "09:30:00")
                .thenApply(history -> {
                    if (history == null || history.isEmpty()) {
                        logger.warn("No historical opening range volume for {}. Cannot calculate RV.", result.symbol);
                        return null;
                    }
                    double avgHistoricalVolume = history.values().stream().mapToLong(l -> l).average().orElse(0.0);
                    if (avgHistoricalVolume == 0) {
                        logger.warn("Average historical opening range volume for {} is zero. Cannot calculate RV.", result.symbol);
                        return null;
                    }
                    double rv = currentVolume / avgHistoricalVolume;
                    result.setRelativeVolume(rv);
                    logger.info("Calculated RV for {}: CurrentVol={}, AvgHistVol={:.0f}, RV={:.2f}", result.symbol, currentVolume, avgHistoricalVolume, rv);
                    return result;
                }).exceptionally(ex -> {
                    logger.error("Failed to calculate relative volume for {}: {}", result.symbol, ex.getMessage());
                    return null;
                });
    }
}
