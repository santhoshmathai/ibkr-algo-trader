package com.ibkr.marketstrategy;

import com.ibkr.marketdata.reader.StockDataRecord; // Adjusted import

import java.util.ArrayList;
// import java.util.Comparator; // Not strictly used for sorting in current topN impl
import java.util.List;
// import java.util.stream.Collectors; // Not strictly used for sorting in current topN impl

/**
 * Implements reversal trading strategies.
 * This strategy identifies stocks that exhibit a significant opening gap
 * (either up or down) but then show price movement contrary to the gap direction
 * by the end of the period (using LTP vs Open as a proxy for daily reversal).
 * This suggests that the initial momentum indicated by the gap has failed.
 */
public class ReversalStrategy {

    private int topNStocksToSelect;
    private double minReversalGapPercent;       // Min absolute gap % to consider for reversal (e.g., 3.0 for +/-3%)
    private double reversalConfirmationPercent; // Min % move from Open against the gap (e.g., 1.0 for 1%)
    private double minLtpForTrade;

    /**
     * Constructs a ReversalStrategy with specified parameters.
     *
     * @param topNStocksToSelect          The number of top stocks to select for trade signals.
     *                                    (Note: Current implementation identifies all qualifying stocks;
     *                                    strict top N selection based on reversal strength is a future enhancement).
     * @param minReversalGapPercent       The minimum absolute gap percentage (e.g., 3.0 for +/-3%)
     *                                    required for a stock to be considered a reversal candidate.
     * @param reversalConfirmationPercent The minimum percentage move from the Open price, against the
     *                                    direction of the initial gap, required to confirm the reversal.
     * @param minLtpForTrade              The minimum Last Traded Price (LTP) for a stock to be eligible for trading.
     */
    public ReversalStrategy(int topNStocksToSelect, double minReversalGapPercent, double reversalConfirmationPercent, double minLtpForTrade) {
        this.topNStocksToSelect = topNStocksToSelect;
        this.minReversalGapPercent = Math.abs(minReversalGapPercent); // Ensure positive for threshold comparison
        this.reversalConfirmationPercent = Math.abs(reversalConfirmationPercent);
        this.minLtpForTrade = minLtpForTrade;
    }

    /**
     * Generates trade signals based on reversal criteria.
     * It looks for:
     * 1. Short Reversal (Faded Gap Up): Stocks that gapped up significantly but then closed (LTP)
     *    below their open price by a confirmation percentage.
     * 2. Long Reversal (Faded Gap Down): Stocks that gapped down significantly but then closed (LTP)
     *    above their open price by a confirmation percentage.
     *
     * @param records List of {@link StockDataRecord} for the day.
     * @return A list of {@link TradeSignal}s for identified reversal candidates.
     *         Currently returns all qualifying signals; future enhancement could rank and limit to topN.
     */
    public List<TradeSignal> generateSignals(List<StockDataRecord> records) {
        List<TradeSignal> signals = new ArrayList<>();
        if (records == null || records.isEmpty()) {
            return signals;
        }

        List<StockDataRecord> candidates = new ArrayList<>();

        for (StockDataRecord record : records) {
            Double gapPercent = record.getGapPercentage();
            Double open = record.getDouble(StockDataRecord.OPEN);
            Double ltp = record.getDouble(StockDataRecord.LTP);
            Double prevClose = record.getDouble(StockDataRecord.PREV_CLOSE);

            if (gapPercent == null || open == null || ltp == null || prevClose == null || ltp < minLtpForTrade) {
                continue;
            }

            // Potential Short Reversal (Faded Gap Up)
            // Stock gapped UP significantly, but LTP is now below Open by reversalConfirmationPercent
            if (gapPercent >= minReversalGapPercent) { // Gapped up
                double moveFromOpenPercent = ((ltp - open) / open) * 100.0;
                if (moveFromOpenPercent <= -reversalConfirmationPercent) { // Price moved down from open
                    candidateSetup(record, signals, TradeSignal.Action.SELL, "ReversalShort_FadedGapUp",
                        String.format("GapUp: %.2f%%, MoveVsOpen: %.2f%%", gapPercent, moveFromOpenPercent));
                    candidates.add(record); // Add for potential sorting/limiting if needed, though current logic adds directly
                }
            }
            // Potential Long Reversal (Faded Gap Down)
            // Stock gapped DOWN significantly, but LTP is now above Open by reversalConfirmationPercent
            else if (gapPercent <= -minReversalGapPercent) { // Gapped down
                double moveFromOpenPercent = ((ltp - open) / open) * 100.0;
                if (moveFromOpenPercent >= reversalConfirmationPercent) { // Price moved up from open
                     candidateSetup(record, signals, TradeSignal.Action.BUY, "ReversalLong_FadedGapDown",
                        String.format("GapDown: %.2f%%, MoveVsOpen: %.2f%%", gapPercent, moveFromOpenPercent));
                    candidates.add(record); // Collect candidate for potential sorting/limiting
                }
            }
        }

        // Note: The current implementation adds signals directly as they are found.
        // To strictly adhere to `topNStocksToSelect`, one would typically collect all
        // potential candidates (perhaps as TradeSignal objects with a strength score),
        // then sort them by that score and take the top N.
        // For simplicity in this version, it might generate more than N signals if many stocks qualify.
        // If `topNStocksToSelect` is critical, this part would need refinement
        // to sort `candidates` by some measure of "reversal strength" (e.g., magnitude of move vs open)
        // and then pick the top N before creating signals.

        // Example of how one might limit if signals were already created:
        // if (signals.size() > topNStocksToSelect && topNStocksToSelect > 0) {
        //     signals.sort(Comparator.comparingDouble(ts -> Math.abs(ts.getPrice() - record.getDouble(StockDataRecord.OPEN)))); // Example sort key
        //     signals = signals.subList(0, topNStocksToSelect);
        // }

        return signals;
    }

    /**
     * Helper method to create and add a trade signal to the list.
     * @param record The stock data record for the signal.
     * @param signals The list to add the new signal to.
     * @param action The trade action (BUY/SELL).
     * @param strategyName The name of the strategy.
     * @param reason The reason for the signal.
     */
    private void candidateSetup(StockDataRecord record, List<TradeSignal> signals, TradeSignal.Action action, String strategyName, String reason) {
        signals.add(new TradeSignal(
                record.getString(StockDataRecord.SYMBOL),
                action,
                record.getDouble(StockDataRecord.LTP), // Signal at LTP
                strategyName,
                reason
        ));
    }

    /**
     * Main method for basic self-testing of the ReversalStrategy.
     * Creates dummy stock data to simulate various gap and reversal scenarios
     * and prints the signals generated by the strategy.
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        List<StockDataRecord> dummyRecords = new ArrayList<>();

        StockDataRecord r1 = new StockDataRecord(); // Potential Short Reversal
        r1.put(StockDataRecord.SYMBOL, "REV_SHORT_A");
        r1.put(StockDataRecord.PREV_CLOSE, 100.0);
        r1.put(StockDataRecord.OPEN, 105.0); // 5% Gap Up
        r1.put(StockDataRecord.LTP, 103.0);  // Reversed by ~1.9% from Open (103 vs 105)
        dummyRecords.add(r1);

        StockDataRecord r2 = new StockDataRecord(); // Potential Long Reversal
        r2.put(StockDataRecord.SYMBOL, "REV_LONG_B");
        r2.put(StockDataRecord.PREV_CLOSE, 100.0);
        r2.put(StockDataRecord.OPEN, 95.0);  // -5% Gap Down
        r2.put(StockDataRecord.LTP, 97.0);   // Reversed by ~2.1% from Open (97 vs 95)
        dummyRecords.add(r2);

        StockDataRecord r3 = new StockDataRecord(); // Gapped up, but continued up (No Short Reversal)
        r3.put(StockDataRecord.SYMBOL, "NO_REV_C");
        r3.put(StockDataRecord.PREV_CLOSE, 100.0);
        r3.put(StockDataRecord.OPEN, 104.0); // 4% Gap Up
        r3.put(StockDataRecord.LTP, 106.0);  // Continued up
        dummyRecords.add(r3);

        StockDataRecord r4 = new StockDataRecord(); // Gapped down, but continued down (No Long Reversal)
        r4.put(StockDataRecord.SYMBOL, "NO_REV_D");
        r4.put(StockDataRecord.PREV_CLOSE, 100.0);
        r4.put(StockDataRecord.OPEN, 96.0);  // -4% Gap Down
        r4.put(StockDataRecord.LTP, 94.0);   // Continued down
        dummyRecords.add(r4);

        StockDataRecord r5 = new StockDataRecord(); // Gapped up, but small reversal (might not meet threshold)
        r5.put(StockDataRecord.SYMBOL, "WEAK_REV_E");
        r5.put(StockDataRecord.PREV_CLOSE, 100.0);
        r5.put(StockDataRecord.OPEN, 105.0); // 5% Gap Up
        r5.put(StockDataRecord.LTP, 104.8);  // Only -0.19% reversal from open
        dummyRecords.add(r5);

        // Config: Top 2 stocks (though not strictly implemented for reversal yet),
        // min 3% absolute gap for consideration, min 1% move against gap for confirmation, LTP > 50
        ReversalStrategy strategy = new ReversalStrategy(2, 3.0, 1.0, 50.0);

        System.out.println("--- Testing Reversal Strategy ---");
        List<TradeSignal> reversalSignals = strategy.generateSignals(dummyRecords);
        reversalSignals.forEach(System.out::println);
        // Expected:
        // REV_SHORT_A (SELL signal because gapped up >3%, then LTP fell >1% from open)
        // REV_LONG_B (BUY signal because gapped down >3%, then LTP rose >1% from open)
    }
}
