package com.ibkr.marketstrategy;

import com.ibkr.marketdata.reader.StockDataRecord; // Adjusted import
import java.util.List;
import java.util.ArrayList; // Added for main method example

/**
 * Analyzes a list of stock data records for a given day to determine
 * the overall market sentiment at the open.
 * This is based on the percentage of stocks gapping up or down significantly,
 * using configurable thresholds.
 */
public class MarketSentimentAnalyzer {

    private double significantGapUpThresholdPercent;
    private double significantGapDownThresholdPercent; // Should be negative
    private double sentimentDecisionThresholdPercent;

    /**
     * Constructs a MarketSentimentAnalyzer with specified thresholds.
     *
     * @param significantGapUpThresholdPercent   The minimum positive percentage gap (e.g., 1.0 for +1%)
     *                                           to be considered significant for an individual stock.
     * @param significantGapDownThresholdPercent The minimum negative percentage gap (e.g., -1.0 for -1%)
     *                                           to be considered significant for an individual stock.
     * @param sentimentDecisionThresholdPercent  The percentage of total valid stocks that need to show a
     *                                           directional gap for the overall market sentiment to be
     *                                           classified as BULLISH_OPEN or BEARISH_OPEN (e.g., 60.0 for 60%).
     */
    public MarketSentimentAnalyzer(double significantGapUpThresholdPercent, double significantGapDownThresholdPercent, double sentimentDecisionThresholdPercent) {
        this.significantGapUpThresholdPercent = significantGapUpThresholdPercent;
        this.significantGapDownThresholdPercent = significantGapDownThresholdPercent;
        this.sentimentDecisionThresholdPercent = sentimentDecisionThresholdPercent;
    }

    /**
     * Analyzes the provided stock records to determine the market sentiment at open.
     * It counts the number of stocks that gapped up or down significantly based on the
     * configured thresholds and then determines if a predominant direction meets the
     * sentiment decision threshold.
     *
     * @param records A list of {@link StockDataRecord} for a single trading day.
     *                It's assumed these records have previous close and open prices populated.
     * @return The determined {@link MarketSentiment} (BULLISH_OPEN, BEARISH_OPEN, MIXED_OPEN, or NEUTRAL).
     */
    public MarketSentiment analyze(List<StockDataRecord> records) {
        if (records == null || records.isEmpty()) {
            return MarketSentiment.NEUTRAL; // Or throw an IllegalArgumentException
        }

        int totalStocks = records.size();
        int gappedUpSignificantly = 0;
        int gappedDownSignificantly = 0;
        int validRecordsForGapAnalysis = 0;

        for (StockDataRecord record : records) {
            Double gapPercent = record.getGapPercentage();
            if (gapPercent != null) {
                validRecordsForGapAnalysis++;
                if (gapPercent >= significantGapUpThresholdPercent) {
                    gappedUpSignificantly++;
                } else if (gapPercent <= significantGapDownThresholdPercent) {
                    gappedDownSignificantly++;
                }
            }
        }

        if (validRecordsForGapAnalysis == 0) {
            return MarketSentiment.NEUTRAL; // Not enough data to determine
        }

        double percentGappedUp = ((double) gappedUpSignificantly / validRecordsForGapAnalysis) * 100.0;
        double percentGappedDown = ((double) gappedDownSignificantly / validRecordsForGapAnalysis) * 100.0;

        // Optional: For debugging or verbose output
        // System.out.printf("[SentimentAnalyzer] Total Valid for Gap: %d, Gapped Up Sig.: %d (%.2f%%), Gapped Down Sig.: %d (%.2f%%)%n",
        //        validRecordsForGapAnalysis, gappedUpSignificantly, percentGappedUp, gappedDownSignificantly, percentGappedDown);


        if (percentGappedUp >= sentimentDecisionThresholdPercent) {
            return MarketSentiment.BULLISH_OPEN;
        } else if (percentGappedDown >= sentimentDecisionThresholdPercent) {
            return MarketSentiment.BEARISH_OPEN;
        } else {
            return MarketSentiment.MIXED_OPEN;
        }
    }

    /**
     * Main method for basic self-testing of the MarketSentimentAnalyzer.
     * Creates dummy stock data to simulate different market open scenarios (bullish, bearish, mixed)
     * and prints the sentiment determined by the analyzer.
     * This is primarily for illustrative and basic verification purposes.
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        // This is a conceptual test. In a real scenario, you'd load records using CsvMarketDataReader.
        // For now, creating a dummy list.
        List<StockDataRecord> dummyRecords = new ArrayList<>();

        // Bullish scenario example
        StockDataRecord r1 = new StockDataRecord(); // Gap up
        r1.put(StockDataRecord.OPEN, 102.0); r1.put(StockDataRecord.PREV_CLOSE, 100.0);
        StockDataRecord r2 = new StockDataRecord(); // Gap up
        r2.put(StockDataRecord.OPEN, 103.0); r2.put(StockDataRecord.PREV_CLOSE, 100.0);
        StockDataRecord r3 = new StockDataRecord(); // Gap up
        r3.put(StockDataRecord.OPEN, 102.5); r3.put(StockDataRecord.PREV_CLOSE, 100.0);
        StockDataRecord r4 = new StockDataRecord(); // Small gap up (not significant for this example's threshold)
        r4.put(StockDataRecord.OPEN, 100.5); r4.put(StockDataRecord.PREV_CLOSE, 100.0);
        StockDataRecord r5 = new StockDataRecord(); // Gap down (not significant)
        r5.put(StockDataRecord.OPEN, 99.5); r5.put(StockDataRecord.PREV_CLOSE, 100.0);
        dummyRecords.add(r1); dummyRecords.add(r2); dummyRecords.add(r3); dummyRecords.add(r4); dummyRecords.add(r5);

        MarketSentimentAnalyzer analyzer = new MarketSentimentAnalyzer(1.0, -1.0, 60.0);
        MarketSentiment sentiment = analyzer.analyze(dummyRecords);
        System.out.println("Calculated Market Sentiment (Example 1 - Bullish): " + sentiment); // Expect BULLISH_OPEN (3/5 = 60% are >= 1%)

        dummyRecords.clear();
        // Bearish scenario example
        StockDataRecord r6 = new StockDataRecord(); // Gap down
        r6.put(StockDataRecord.OPEN, 98.0); r6.put(StockDataRecord.PREV_CLOSE, 100.0);
        StockDataRecord r7 = new StockDataRecord(); // Gap down
        r7.put(StockDataRecord.OPEN, 97.0); r7.put(StockDataRecord.PREV_CLOSE, 100.0);
        StockDataRecord r8 = new StockDataRecord(); // Gap down
        r8.put(StockDataRecord.OPEN, 97.5); r8.put(StockDataRecord.PREV_CLOSE, 100.0);
        StockDataRecord r9 = new StockDataRecord(); // Small gap down
        r9.put(StockDataRecord.OPEN, 99.5); r9.put(StockDataRecord.PREV_CLOSE, 100.0);
        StockDataRecord r10 = new StockDataRecord(); // Gap up (not significant)
        r10.put(StockDataRecord.OPEN, 100.5); r10.put(StockDataRecord.PREV_CLOSE, 100.0);
        dummyRecords.add(r6); dummyRecords.add(r7); dummyRecords.add(r8); dummyRecords.add(r9); dummyRecords.add(r10);

        sentiment = analyzer.analyze(dummyRecords);
        System.out.println("Calculated Market Sentiment (Example 2 - Bearish): " + sentiment); // Expect BEARISH_OPEN

        dummyRecords.clear();
        // Mixed scenario example
        StockDataRecord r11 = new StockDataRecord(); // Gap up sig
        r11.put(StockDataRecord.OPEN, 102.0); r11.put(StockDataRecord.PREV_CLOSE, 100.0);
        StockDataRecord r12 = new StockDataRecord(); // Gap up sig
        r12.put(StockDataRecord.OPEN, 103.0); r12.put(StockDataRecord.PREV_CLOSE, 100.0);
        StockDataRecord r13 = new StockDataRecord(); // Gap down sig
        r13.put(StockDataRecord.OPEN, 97.5); r13.put(StockDataRecord.PREV_CLOSE, 100.0);
        StockDataRecord r14 = new StockDataRecord(); // Gap down sig
        r14.put(StockDataRecord.OPEN, 98.0); r14.put(StockDataRecord.PREV_CLOSE, 100.0);
        StockDataRecord r15 = new StockDataRecord(); // Neutral
        r15.put(StockDataRecord.OPEN, 100.1); r15.put(StockDataRecord.PREV_CLOSE, 100.0);
        dummyRecords.add(r11); dummyRecords.add(r12); dummyRecords.add(r13); dummyRecords.add(r14); dummyRecords.add(r15);

        sentiment = analyzer.analyze(dummyRecords);
        System.out.println("Calculated Market Sentiment (Example 3 - Mixed): " + sentiment); // Expect MIXED_OPEN (2/5 up, 2/5 down)
    }
}
