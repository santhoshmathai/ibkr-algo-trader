package com.ibkr.analysis;

import com.ibkr.AppContext;
import com.ibkr.models.PreviousDayData; // Added
import com.zerodhatech.models.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MarketSentimentAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(MarketSentimentAnalyzer.class); // Added
    private final Map<String, Double> openingPrices = new ConcurrentHashMap<>();
    private final Map<String, Integer> tickerDirection = new ConcurrentHashMap<>();
    private final Set<String> monitoredSymbols = ConcurrentHashMap.newKeySet();
    private final long marketOpenTime;
    private boolean isInitialized = false;
    private final AppContext appContext; // Added

    // Thresholds
    private static final double SENTIMENT_THRESHOLD = 0.6; // 60%
    private static final long ANALYSIS_WINDOW_MS = 30 * 60 * 1000; // 30 minutes

    public MarketSentimentAnalyzer(AppContext appContext, Collection<String> symbols) { // Added appContext
        this.appContext = appContext; // Added
        this.monitoredSymbols.addAll(symbols);
        this.marketOpenTime = System.currentTimeMillis();
        logger.info("MarketSentimentAnalyzer initialized with {} symbols. AppContext provided. Market open time set to: {}", symbols.size(), new Date(this.marketOpenTime));
    }

    public synchronized void update(Tick tick) {
        logger.trace("Update called for tick: {}", tick.getSymbol());
        if (!isInitialized && isMarketOpen()) {
            logger.debug("Market open and not initialized. Initializing opening prices for: {}", tick.getSymbol());
            initializeOpeningPrices(tick);
            return;
        }

        if (!isInAnalysisWindow()) {
            logger.trace("Not in analysis window. Skipping update for {}", tick.getSymbol());
            return;
        }
        if (!monitoredSymbols.contains(tick.getSymbol())) {
            logger.trace("Symbol {} not in monitored set. Skipping update.", tick.getSymbol());
            return;
        }

        Double openingPrice = openingPrices.get(tick.getSymbol());
        if (openingPrice == null) {
            logger.warn("Opening price for {} is null, cannot calculate price change. Initializing.", tick.getSymbol());
            initializeOpeningPrices(tick); // Attempt to initialize if missed
            return;
        }
        if (openingPrice == 0) {
            logger.warn("Opening price for {} is zero, cannot calculate current direction. Skipping update.", tick.getSymbol());
            return;
        }

        double currentPrice = tick.getLastTradedPrice();
        int direction = 0; // Default to neutral

        PreviousDayData pdData = appContext.getPreviousDayData(tick.getSymbol());

        if (pdData == null || pdData.getPreviousClose() == 0) {
            if (pdData == null) logger.warn("PreviousDayData not found for symbol: {}. Using simple price vs open logic.", tick.getSymbol());
            else logger.warn("PreviousDayClose is 0 for symbol: {}. Using simple price vs open logic.", tick.getSymbol());

            // Fallback logic: current price vs open price
            if (currentPrice > openingPrice) direction = 1;
            else if (currentPrice < openingPrice) direction = -1;
        } else {
            // Refined "Rising" and "Falling" criteria
            boolean isRising = currentPrice > openingPrice && currentPrice > pdData.getPreviousClose();
            boolean isFalling = currentPrice < openingPrice && currentPrice < pdData.getPreviousClose();

            if (isRising) {
                direction = 1;
            } else if (isFalling) {
                direction = -1;
            }
            // If neither, direction remains 0 (neutral), or could be based on just vs open or vs PDC.
            // For now, strict definition: must satisfy both conditions for clear rising/falling.
            // Otherwise, it's neutral or mixed.
            if (direction == 0) { // If not strictly rising or falling by the new definition
                // Fallback to simple current vs open for a less strict neutral definition
                 if (currentPrice > openingPrice) direction = 1; // Still considered somewhat up if > open
                 else if (currentPrice < openingPrice) direction = -1; // Still considered somewhat down if < open
                 logger.trace("Symbol {} did not meet strict rising/falling. Using current vs open. Direction: {}", tick.getSymbol(), direction);
            }
        }

        tickerDirection.put(tick.getSymbol(), direction);
        logger.debug("Updated direction for {}: CurrentPrice={}, OpenPrice={}, PDC={}, Direction={}",
            tick.getSymbol(), currentPrice, openingPrice, (pdData != null ? pdData.getPreviousClose() : "N/A"), direction);
    }

    public MarketSentiment getMarketSentiment() {
        if (!isInAnalysisWindow()) {
            logger.debug("Not in analysis window. Returning NEUTRAL sentiment.");
            return MarketSentiment.NEUTRAL;
        }

        long upCount = tickerDirection.values().stream().filter(v -> v == 1).count();
        long downCount = tickerDirection.values().stream().filter(v -> v == -1).count();
        double totalMonitored = monitoredSymbols.size();

        if (totalMonitored == 0) {
            logger.warn("No symbols monitored or directions recorded. Returning NEUTRAL sentiment.");
            return MarketSentiment.NEUTRAL;
        }

        double upRatio = upCount / totalMonitored;
        double downRatio = downCount / totalMonitored;

        logger.debug("Market sentiment calculation: Up: {}, Down: {}, Total: {}, UpRatio: {:.2f}, DownRatio: {:.2f}",
                upCount, downCount, totalMonitored, upRatio, downRatio);

        if (upRatio >= SENTIMENT_THRESHOLD) {
            logger.info("Market sentiment: STRONG_UP");
            return MarketSentiment.STRONG_UP;
        } else if (downRatio >= SENTIMENT_THRESHOLD) {
            logger.info("Market sentiment: STRONG_DOWN");
            return MarketSentiment.STRONG_DOWN;
        }
        logger.info("Market sentiment: NEUTRAL");
        return MarketSentiment.NEUTRAL;
    }

    public List<String> getTopPerformers(int count, boolean top) {
        logger.debug("Getting {} {} performers.", count, top ? "top" : "bottom");
        List<String> performers = tickerDirection.entrySet().stream()
                .sorted(top ?
                        Map.Entry.<String, Integer>comparingByValue().reversed() :
                        Map.Entry.comparingByValue())
                .limit(count)
                .map(Map.Entry::getKey)
                .toList();
        logger.debug("Found performers: {}", performers);
        return performers;
    }

    // Additional helper methods
    private boolean isMarketOpen() {
        // Implement market hours check
        // For now, assume market is always open for logging purposes if this method is called.
        logger.trace("isMarketOpen check performed (currently hardcoded true).");
        return true; // Simplified for example
    }

    public boolean isInAnalysisWindow() {
        boolean inWindow = System.currentTimeMillis() - marketOpenTime <= ANALYSIS_WINDOW_MS;
        logger.trace("isInAnalysisWindow check: CurrentTimeMillis: {}, MarketOpenTime: {}, AnalysisWindowMs: {}. Result: {}",
                System.currentTimeMillis(), marketOpenTime, ANALYSIS_WINDOW_MS, inWindow);
        return inWindow;
    }

    private synchronized void initializeOpeningPrices(Tick tick) {
        String symbol = tick.getSymbol();
        if (!openingPrices.containsKey(symbol)) {
            double openPrice = tick.getLastTradedPrice(); // Assuming tick at open is the open price
            openingPrices.put(symbol, openPrice);
            logger.info("Initialized opening price for {}: {}", symbol, openPrice);

            PreviousDayData pdData = appContext.getPreviousDayData(symbol);
            if (pdData != null && pdData.getPreviousClose() != 0) {
                boolean isGapUp = openPrice > pdData.getPreviousHigh();
                // Using previous close for gap down as per subtask refinement
                boolean isGapDown = openPrice < pdData.getPreviousClose();

                if (isGapUp) {
                    logger.info("Symbol {} gapped UP. Open: {}, PDH: {}", symbol, openPrice, pdData.getPreviousHigh());
                } else if (isGapDown) {
                    logger.info("Symbol {} gapped DOWN. Open: {}, PDC: {}", symbol, openPrice, pdData.getPreviousClose());
                } else {
                    logger.info("Symbol {} opened near previous close/within PDH/PDL. Open: {}, PDC: {}, PDH: {}",
                                symbol, openPrice, pdData.getPreviousClose(), pdData.getPreviousHigh());
                }
                // This information (isGapUp, isGapDown) is logged.
                // If it needs to be part of tickerDirection directly, that logic would go into update().
            } else {
                if (pdData == null) logger.warn("PreviousDayData not available for {} during open price initialization.", symbol);
                else logger.warn("PreviousDayClose is 0 for {} during open price initialization. Cannot determine gap.", symbol);
            }

            if (openingPrices.size() == monitoredSymbols.size()) {
                isInitialized = true;
                logger.info("All monitored symbols ({} of {}) have their opening prices initialized.", openingPrices.size(), monitoredSymbols.size());
            }
        }
    }

    public enum MarketSentiment {
        STRONG_UP, STRONG_DOWN, NEUTRAL
    }
}