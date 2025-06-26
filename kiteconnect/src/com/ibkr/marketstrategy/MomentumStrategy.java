package com.ibkr.marketstrategy;

import com.ibkr.marketdata.reader.StockDataRecord; // Adjusted import

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements momentum-based trading strategies (long and short).
 * This strategy identifies potential trading opportunities based on the overall
 * market sentiment (e.g., BULLISH_OPEN, BEARISH_OPEN) and individual stock
 * characteristics such as opening gap and initial price movement.
 * It aims to select the top N stocks that exhibit strong momentum in the
 * direction of the market sentiment.
 */
public class MomentumStrategy {

    private int topNStocksToSelect;
    private double minGapUpPercentForLong;
    private double minGapDownPercentForShort; // Should be negative, e.g., -2.0 for -2%
    private double minLtpForTrade;

    /**
     * Constructs a MomentumStrategy with specified parameters.
     *
     * @param topNStocksToSelect        The number of top-ranking stocks to select for trade signals.
     * @param minGapUpPercentForLong    The minimum positive gap-up percentage required for a stock
     *                                  to be considered for a long momentum trade.
     * @param minGapDownPercentForShort The minimum negative gap-down percentage (e.g., -2.0 for -2%)
     *                                  required for a stock to be considered for a short momentum trade.
     * @param minLtpForTrade            The minimum Last Traded Price (LTP) for a stock to be eligible for trading,
     *                                  helping to filter out very low-priced stocks.
     */
    public MomentumStrategy(int topNStocksToSelect, double minGapUpPercentForLong, double minGapDownPercentForShort, double minLtpForTrade) {
        this.topNStocksToSelect = topNStocksToSelect;
        this.minGapUpPercentForLong = minGapUpPercentForLong;
        this.minGapDownPercentForShort = minGapDownPercentForShort;
        this.minLtpForTrade = minLtpForTrade;
    }

    /**
     * Generates trade signals based on the prevailing market sentiment and individual stock momentum.
     * If market sentiment is BULLISH_OPEN, it looks for long opportunities.
     * If market sentiment is BEARISH_OPEN, it looks for short opportunities.
     * No signals are generated for MIXED_OPEN or NEUTRAL sentiment by this strategy.
     *
     * @param sentiment The overall market sentiment determined by {@link MarketSentimentAnalyzer}.
     * @param records   A list of {@link StockDataRecord} for the current trading day.
     * @return A list of {@link TradeSignal} objects representing potential trades.
     *         Returns an empty list if no suitable signals are found or if records are null/empty.
     */
    public List<TradeSignal> generateSignals(MarketSentiment sentiment, List<StockDataRecord> records) {
        List<TradeSignal> signals = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            return signals;
        }

        if (sentiment == MarketSentiment.BULLISH_OPEN) {
            signals.addAll(findLongMomentumSignals(records));
        } else if (sentiment == MarketSentiment.BEARISH_OPEN) {
            signals.addAll(findShortMomentumSignals(records));
        }
        // No signals for MIXED_OPEN or NEUTRAL from this specific momentum strategy by default

        return signals;
    }

    /**
     * Identifies potential long (BUY) momentum signals.
     * Stocks are filtered based on a significant gap up and positive initial price movement (LTP vs Open).
     * Candidates are then ranked by the strength of their initial move, and the top N are selected.
     *
     * @param records List of {@link StockDataRecord} for the day.
     * @return A list of BUY {@link TradeSignal}s.
     */
    private List<TradeSignal> findLongMomentumSignals(List<StockDataRecord> records) {
        List<StockDataRecord> candidates = records.stream()
                .filter(r -> {
                    Double gap = r.getGapPercentage();
                    Double ltp = r.getDouble(StockDataRecord.LTP);
                    Double initialMove = r.getInitialMovePercentage();
                    return gap != null && gap >= minGapUpPercentForLong &&
                           ltp != null && ltp >= minLtpForTrade &&
                           initialMove != null && initialMove > 0; // Ensure positive initial move
                })
                .sorted(Comparator.comparingDouble(StockDataRecord::getInitialMovePercentage).reversed()) // Higher initial move is better
                .limit(topNStocksToSelect)
                .collect(Collectors.toList());

        List<TradeSignal> signals = new ArrayList<>();
        for (StockDataRecord candidate : candidates) {
            signals.add(new TradeSignal(
                    candidate.getString(StockDataRecord.SYMBOL),
                    TradeSignal.Action.BUY,
                    candidate.getDouble(StockDataRecord.LTP), // Signal at LTP
                    "MomentumLong",
                    String.format("Gap: %.2f%%, InitialMove: %.2f%%", candidate.getGapPercentage(), candidate.getInitialMovePercentage())
            ));
        }
        return signals;
    }

    /**
     * Identifies potential short (SELL) momentum signals.
     * Stocks are filtered based on a significant gap down and negative initial price movement (LTP vs Open).
     * Candidates are then ranked by the strength of their initial move (more negative is better),
     * and the top N are selected.
     *
     * @param records List of {@link StockDataRecord} for the day.
     * @return A list of SELL {@link TradeSignal}s.
     */
    private List<TradeSignal> findShortMomentumSignals(List<StockDataRecord> records) {
        List<StockDataRecord> candidates = records.stream()
                .filter(r -> {
                    Double gap = r.getGapPercentage();
                    Double ltp = r.getDouble(StockDataRecord.LTP);
                    Double initialMove = r.getInitialMovePercentage();
                    return gap != null && gap <= minGapDownPercentForShort && // Gap down significantly
                           ltp != null && ltp >= minLtpForTrade &&     // Still above min price
                           initialMove != null && initialMove < 0; // Ensure negative initial move
                })
                .sorted(Comparator.comparingDouble(StockDataRecord::getInitialMovePercentage)) // Lower (more negative) initial move is better
                .limit(topNStocksToSelect)
                .collect(Collectors.toList());

        List<TradeSignal> signals = new ArrayList<>();
        for (StockDataRecord candidate : candidates) {
            signals.add(new TradeSignal(
                    candidate.getString(StockDataRecord.SYMBOL),
                    TradeSignal.Action.SELL, // Representing a short sell
                    candidate.getDouble(StockDataRecord.LTP),  // Signal at LTP
                    "MomentumShort",
                     String.format("Gap: %.2f%%, InitialMove: %.2f%%", candidate.getGapPercentage(), candidate.getInitialMovePercentage())
            ));
        }
        return signals;
    }

    /**
     * Main method for basic self-testing of the MomentumStrategy.
     * Creates dummy stock data and simulates bullish and bearish market sentiments
     * to verify signal generation.
     * @param args Command line arguments (not used).
     */
     public static void main(String[] args) {
        // Dummy data for testing MomentumStrategy
        List<StockDataRecord> dummyRecords = new ArrayList<>();

        StockDataRecord r1 = new StockDataRecord(); // Strong buy candidate
        r1.put(StockDataRecord.SYMBOL, "STOCK_A");
        r1.put(StockDataRecord.PREV_CLOSE, 100.0);
        r1.put(StockDataRecord.OPEN, 103.0); // 3% gap up
        r1.put(StockDataRecord.LTP, 108.0);  // Further 4.85% initial move
        r1.put(StockDataRecord.VOLUME_SHARES, 10000L);
        dummyRecords.add(r1);

        StockDataRecord r2 = new StockDataRecord(); // Good buy candidate
        r2.put(StockDataRecord.SYMBOL, "STOCK_B");
        r2.put(StockDataRecord.PREV_CLOSE, 150.0);
        r2.put(StockDataRecord.OPEN, 155.0); // ~3.3% gap up
        r2.put(StockDataRecord.LTP, 160.0);  // ~3.2% initial move
        dummyRecords.add(r2);

        StockDataRecord r3 = new StockDataRecord(); // Weak buy candidate (small initial move)
        r3.put(StockDataRecord.SYMBOL, "STOCK_C");
        r3.put(StockDataRecord.PREV_CLOSE, 200.0);
        r3.put(StockDataRecord.OPEN, 205.0); // 2.5% gap up
        r3.put(StockDataRecord.LTP, 205.5);  // ~0.24% initial move
        dummyRecords.add(r3);

        StockDataRecord r4 = new StockDataRecord(); // Strong sell candidate
        r4.put(StockDataRecord.SYMBOL, "STOCK_D");
        r4.put(StockDataRecord.PREV_CLOSE, 100.0);
        r4.put(StockDataRecord.OPEN, 97.0);  // -3% gap down
        r4.put(StockDataRecord.LTP, 92.0);   // Further ~-5.15% initial move
        dummyRecords.add(r4);

        StockDataRecord r5 = new StockDataRecord(); // Good sell candidate
        r5.put(StockDataRecord.SYMBOL, "STOCK_E");
        r5.put(StockDataRecord.PREV_CLOSE, 120.0);
        r5.put(StockDataRecord.OPEN, 115.0); // ~-4.17% gap down
        r5.put(StockDataRecord.LTP, 112.0);  // ~-2.6% initial move
        dummyRecords.add(r5);

        StockDataRecord r6 = new StockDataRecord(); // No gap, but positive move (not picked by this logic)
        r6.put(StockDataRecord.SYMBOL, "STOCK_F");
        r6.put(StockDataRecord.PREV_CLOSE, 100.0);
        r6.put(StockDataRecord.OPEN, 100.0);
        r6.put(StockDataRecord.LTP, 105.0);
        dummyRecords.add(r6);

        // Config: Top 2 stocks, Gap >= 2% for long, Gap <= -2% for short, LTP >= 50
        MomentumStrategy strategy = new MomentumStrategy(2, 2.0, -2.0, 50.0);

        System.out.println("--- Testing BULLISH_OPEN Sentiment ---");
        List<TradeSignal> bullishSignals = strategy.generateSignals(MarketSentiment.BULLISH_OPEN, dummyRecords);
        bullishSignals.forEach(System.out::println);
        // Expected: STOCK_A, STOCK_B (ranked by initial move)

        System.out.println("\n--- Testing BEARISH_OPEN Sentiment ---");
        List<TradeSignal> bearishSignals = strategy.generateSignals(MarketSentiment.BEARISH_OPEN, dummyRecords);
        bearishSignals.forEach(System.out::println);
        // Expected: STOCK_D, STOCK_E (ranked by initial move - more negative is better)

        System.out.println("\n--- Testing MIXED_OPEN Sentiment ---");
        List<TradeSignal> mixedSignals = strategy.generateSignals(MarketSentiment.MIXED_OPEN, dummyRecords);
        System.out.println("Signals for MIXED_OPEN: " + mixedSignals.size());
        // Expected: 0 signals from this strategy for mixed sentiment
    }
}
