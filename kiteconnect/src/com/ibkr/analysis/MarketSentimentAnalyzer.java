package com.ibkr.analysis;

import com.zerodhatech.models.Tick;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MarketSentimentAnalyzer {
    private final Map<String, Double> openingPrices = new ConcurrentHashMap<>();
    private final Map<String, Integer> tickerDirection = new ConcurrentHashMap<>();
    private final Set<String> monitoredSymbols = ConcurrentHashMap.newKeySet();
    private final long marketOpenTime;
    private boolean isInitialized = false;

    // Thresholds
    private static final double SENTIMENT_THRESHOLD = 0.6; // 60%
    private static final long ANALYSIS_WINDOW_MS = 30 * 60 * 1000; // 30 minutes

    public MarketSentimentAnalyzer(Collection<String> symbols) {
        this.monitoredSymbols.addAll(symbols);
        this.marketOpenTime = System.currentTimeMillis();
    }

    public synchronized void update(Tick tick) {
        if (!isInitialized && isMarketOpen()) {
            initializeOpeningPrices(tick);
            return;
        }

        if (!isInAnalysisWindow() || !monitoredSymbols.contains(tick.getSymbol())) {
            return;
        }

        double priceChange = (tick.getLastTradedPrice() - openingPrices.get(tick.getSymbol()))
                / openingPrices.get(tick.getSymbol());

        tickerDirection.put(tick.getSymbol(),
                priceChange > 0.005 ? 1 :  // 0.5% up
                        priceChange < -0.005 ? -1 : // 0.5% down
                                0);                         // neutral
    }

    public MarketSentiment getMarketSentiment() {
        if (!isInAnalysisWindow()) return MarketSentiment.NEUTRAL;

        long upCount = tickerDirection.values().stream().filter(v -> v == 1).count();
        long downCount = tickerDirection.values().stream().filter(v -> v == -1).count();
        double total = monitoredSymbols.size();

        if (upCount / total >= SENTIMENT_THRESHOLD) {
            return MarketSentiment.STRONG_UP;
        } else if (downCount / total >= SENTIMENT_THRESHOLD) {
            return MarketSentiment.STRONG_DOWN;
        }
        return MarketSentiment.NEUTRAL;
    }

    public List<String> getTopPerformers(int count, boolean top) {
        return tickerDirection.entrySet().stream()
                .sorted(top ?
                        Map.Entry.<String, Integer>comparingByValue().reversed() :
                        Map.Entry.comparingByValue())
                .limit(count)
                .map(Map.Entry::getKey)
                .toList();
    }
    // Additional helper methods
    private boolean isMarketOpen() {
        // Implement market hours check
        return true; // Simplified for example
    }
    public boolean isInAnalysisWindow() {
        return System.currentTimeMillis() - marketOpenTime <= ANALYSIS_WINDOW_MS;
    }

    private synchronized void initializeOpeningPrices(Tick tick) {
        if (!openingPrices.containsKey(tick.getSymbol())) {
            openingPrices.put(tick.getSymbol(), tick.getLastTradedPrice());

            if (openingPrices.size() == monitoredSymbols.size()) {
                isInitialized = true;
            }
        }
    }

    public enum MarketSentiment {
        STRONG_UP, STRONG_DOWN, NEUTRAL
    }
}