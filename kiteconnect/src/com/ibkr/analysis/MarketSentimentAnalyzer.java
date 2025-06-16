package com.ibkr.analysis;

import com.ibkr.AppContext;
import com.ibkr.models.OpeningMarketTrend; // New import
import com.ibkr.models.PreviousDayData;
import com.zerodhatech.models.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalTime; // New import
import java.time.ZoneId; // New import
import java.time.ZonedDateTime; // New import
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MarketSentimentAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(MarketSentimentAnalyzer.class);
    private final Map<String, Double> openingPrices = new ConcurrentHashMap<>(); // Still used for stock's daily open
    private final Map<String, Integer> tickerDirection = new ConcurrentHashMap<>(); // For general sentiment
    private final Set<String> monitoredSymbols = ConcurrentHashMap.newKeySet();
    // private final long marketOpenTime; // Replaced by LocalTime logic
    private boolean isInitialized = false; // Might be repurposed or removed if new init logic covers it
    private final AppContext appContext;

    // New fields for Opening Trend
    private int openingObservationMinutes;
    private LocalTime actualMarketOpenTime;
    private LocalTime openingObservationEndTime;
    private LocalTime openingAnalysisWindowEndTime;

    private boolean openingTrendCalculated = false;
    private OpeningMarketTrend determinedOpeningTrend = OpeningMarketTrend.OUTSIDE_ANALYSIS_WINDOW;
    private final Map<String, Double> stockLastPriceInObservation = new ConcurrentHashMap<>();


    // Thresholds for general sentiment
    private static final double SENTIMENT_THRESHOLD = 0.6; // 60%
    // private static final long ANALYSIS_WINDOW_MS = 30 * 60 * 1000; // Replaced by openingAnalysisWindowEndTime

    public MarketSentimentAnalyzer(AppContext appContext, Collection<String> symbolsToMonitor, int openingObservationMinutes, LocalTime actualMarketOpenTime) {
        this.appContext = appContext;
        this.monitoredSymbols.addAll(symbolsToMonitor);
        this.openingObservationMinutes = openingObservationMinutes;
        this.actualMarketOpenTime = actualMarketOpenTime;

        this.openingObservationEndTime = this.actualMarketOpenTime.plusMinutes(this.openingObservationMinutes);
        this.openingAnalysisWindowEndTime = this.actualMarketOpenTime.plusMinutes(30); // Fixed 30 min total opening window

        updateCurrentOpeningTrendStatus(); // Initialize trend status

        logger.info("MarketSentimentAnalyzer initialized. Symbols: {}, Observation: {} mins, Market Open: {}, Observation End: {}, Analysis Window End: {}",
                  symbolsToMonitor.size(), openingObservationMinutes, this.actualMarketOpenTime, this.openingObservationEndTime, this.openingAnalysisWindowEndTime);
    }

    // Old update method, to be reviewed/refactored.
    // For now, let's assume it's for general sentiment outside opening trend.
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
        // This method needs to be updated to use the new time logic if it's still relevant
        // For opening trend, openingAnalysisWindowEndTime is used.
        // For general sentiment, this might define a different window or be removed.
        // For now, let's assume it refers to the opening analysis window.
        ZoneId marketTimeZone = ZoneId.of("America/New_York");
        LocalTime currentTimeET = ZonedDateTime.now(marketTimeZone).toLocalTime();
        boolean inWindow = currentTimeET.isAfter(actualMarketOpenTime) && currentTimeET.isBefore(openingAnalysisWindowEndTime);
        logger.trace("isInAnalysisWindow check: CurrentTimeET: {}, ActualMarketOpenTime: {}, OpeningAnalysisWindowEndTime: {}. Result: {}",
                currentTimeET, actualMarketOpenTime, openingAnalysisWindowEndTime, inWindow);
        return inWindow;
    }

    // This method's role is partially taken by updateOpeningTickAnalysis for recording openingPrices.
    // It also had gap logic. Review if this is still needed or if gap logic moves.
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

    private void updateCurrentOpeningTrendStatus() {
        if (appContext == null) {
            this.determinedOpeningTrend = OpeningMarketTrend.OUTSIDE_ANALYSIS_WINDOW;
            return;
        }
        ZoneId marketTimeZone = ZoneId.of("America/New_York");
        LocalTime currentTimeET = ZonedDateTime.now(marketTimeZone).toLocalTime();

        if (currentTimeET.isBefore(actualMarketOpenTime) || currentTimeET.isAfter(openingAnalysisWindowEndTime)) {
            this.determinedOpeningTrend = OpeningMarketTrend.OUTSIDE_ANALYSIS_WINDOW;
            this.openingTrendCalculated = true;
        } else if (currentTimeET.isBefore(openingObservationEndTime)) {
            this.determinedOpeningTrend = OpeningMarketTrend.OBSERVATION_PERIOD;
            this.openingTrendCalculated = false;
        } else {
            // If trend hasn't been calculated, it's pending. updateOpeningTickAnalysis will trigger it.
            // If it was already calculated, it holds its value.
            // No change to determinedOpeningTrend here directly unless to ensure it's not OBSERVATION_PERIOD.
        }
    }

    public synchronized void updateOpeningTickAnalysis(Tick tick) {
        if (!monitoredSymbols.contains(tick.getSymbol())) {
            return;
        }

        ZoneId marketTimeZone = ZoneId.of("America/New_York");
        LocalTime currentTimeET = ZonedDateTime.now(marketTimeZone).toLocalTime();

        openingPrices.putIfAbsent(tick.getSymbol(), tick.getOpenPrice() != 0 ? tick.getOpenPrice() : tick.getLastTradedPrice());

        if (currentTimeET.isAfter(actualMarketOpenTime) && currentTimeET.isBefore(openingObservationEndTime) && !openingTrendCalculated) {
            this.determinedOpeningTrend = OpeningMarketTrend.OBSERVATION_PERIOD;
            stockLastPriceInObservation.put(tick.getSymbol(), tick.getLastTradedPrice());
            return;
        }

        if (currentTimeET.isAfter(openingObservationEndTime) && currentTimeET.isBefore(openingAnalysisWindowEndTime) && !openingTrendCalculated) {
            calculateDeterminedOpeningTrend();
        }
        // Optionally call general sentiment update: this.update(tick);
    }

    private synchronized void calculateDeterminedOpeningTrend() {
        if (openingTrendCalculated) return;

        logger.info("Calculating determined opening market trend after observation period.");
        long stocksUp = 0;
        long stocksDown = 0;
        int trackedStocksWithData = 0;

        for (String symbol : monitoredSymbols) {
            Double openPrice = openingPrices.get(symbol);
            Double lastObservedPrice = stockLastPriceInObservation.get(symbol);

            if (openPrice == null || lastObservedPrice == null || openPrice == 0) {
                continue;
            }
            trackedStocksWithData++;

            double threshold = 0.001;
            if (lastObservedPrice > openPrice * (1 + threshold)) {
                stocksUp++;
            } else if (lastObservedPrice < openPrice * (1 - threshold)) {
                stocksDown++;
            }
        }

        if (trackedStocksWithData == 0) {
            logger.warn("No stock data available to calculate opening market trend. Setting to NEUTRAL.");
            this.determinedOpeningTrend = OpeningMarketTrend.TREND_NEUTRAL;
        } else {
            double upRatio = (double) stocksUp / trackedStocksWithData;
            double downRatio = (double) stocksDown / trackedStocksWithData;
            logger.info("Opening trend calculation: Stocks Up: {}, Stocks Down: {}, Tracked: {}, UpRatio: {:.2f}, DownRatio: {:.2f}",
                stocksUp, stocksDown, trackedStocksWithData, upRatio, downRatio);

            if (upRatio >= 0.70) {
                this.determinedOpeningTrend = OpeningMarketTrend.TREND_UP;
            } else if (downRatio >= 0.70) {
                this.determinedOpeningTrend = OpeningMarketTrend.TREND_DOWN;
            } else {
                this.determinedOpeningTrend = OpeningMarketTrend.TREND_NEUTRAL;
            }
        }
        this.openingTrendCalculated = true;
        logger.info("Determined Opening Market Trend: {}", this.determinedOpeningTrend);
    }

    public OpeningMarketTrend getDeterminedOpeningTrend() {
        ZoneId marketTimeZone = ZoneId.of("America/New_York");
        LocalTime currentTimeET = ZonedDateTime.now(marketTimeZone).toLocalTime();

        if (currentTimeET.isBefore(actualMarketOpenTime) || currentTimeET.isAfter(openingAnalysisWindowEndTime)) {
            return OpeningMarketTrend.OUTSIDE_ANALYSIS_WINDOW;
        }
        if (!openingTrendCalculated && currentTimeET.isBefore(openingObservationEndTime)) {
            return OpeningMarketTrend.OBSERVATION_PERIOD;
        }
        // Consider if calculateDeterminedOpeningTrend should be called here if conditions met and not yet calculated.
        // Current logic in updateOpeningTickAnalysis might be sufficient if ticks are frequent.
        return this.determinedOpeningTrend;
    }

    public Map<String, Double> getStockOpeningPrices() {
        return Collections.unmodifiableMap(this.openingPrices);
    }
}