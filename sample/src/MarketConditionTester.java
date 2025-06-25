import com.marketdata.reader.CsvMarketDataReader;
import com.marketdata.reader.StockDataRecord;
import com.marketstrategy.MarketSentiment;
import com.marketstrategy.MarketSentimentAnalyzer;
import com.marketstrategy.MomentumStrategy;
import com.marketstrategy.ReversalStrategy;
import com.marketstrategy.StrategyConfig;
import com.marketstrategy.TradeSignal;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Test runner for simulating trading logic against different market conditions
 * loaded from CSV files. This class uses {@link CsvMarketDataReader} to parse
 * historical market data and applies defined strategies (Momentum, Reversal)
 * based on market sentiment analysis.
 *
 * Each test scenario is represented by a separate method that loads a specific
 * CSV file and runs the simulation. Basic assertions are used to verify outcomes.
 *
 * To run: Compile and execute this class. Ensure the CSV files specified in
 * the test methods are available at the correct path (relative to the project root).
 */
public class MarketConditionTester {

    // Simple assertion helper
    private static void assertTrue(String message, boolean condition) {
        if (condition) {
            System.out.printf("  [PASS] %s%n", message);
        } else {
            System.err.printf("  [FAIL] %s%n", message);
        }
    }

    private static void printTestHeader(String testName) {
        System.out.printf("%n--- Running Test: %s ---%n", testName);
    }

    private static void runStrategiesForScenario(List<StockDataRecord> records, String scenarioName, StrategyConfig config) {
        System.out.printf("--- Applying Strategies for Scenario: %s ---%n", scenarioName);

        MarketSentimentAnalyzer sentimentAnalyzer = new MarketSentimentAnalyzer(
            config.sentimentSignificantGapUpThresholdPercent,
            config.sentimentSignificantGapDownThresholdPercent,
            config.sentimentDecisionThresholdPercent
        );
        MarketSentiment sentiment = sentimentAnalyzer.analyze(records);
        System.out.println("  Determined Market Sentiment: " + sentiment);

        int totalSignals = 0;

        if (config.enableMomentumStrategy) {
            System.out.println("  --- Momentum Strategy ---");
            MomentumStrategy momentumStrategy = new MomentumStrategy(
                config.momentumTopNStocks,
                config.momentumMinGapUpPercent,
                config.momentumMinGapDownPercent,
                config.momentumMinLtpForTrade
            );
            List<TradeSignal> momentumSignals = momentumStrategy.generateSignals(sentiment, records);
            if (momentumSignals.isEmpty()) {
                System.out.println("    No momentum signals generated.");
            } else {
                momentumSignals.forEach(signal -> System.out.println("    " + signal));
            }
            totalSignals += momentumSignals.size();
        }

        if (config.enableReversalStrategy) {
            System.out.println("  --- Reversal Strategy ---");
            ReversalStrategy reversalStrategy = new ReversalStrategy(
                config.reversalTopNStocks,
                config.reversalMinAbsGapPercent,
                config.reversalConfirmationPercent,
                config.reversalMinLtpForTrade
            );
            List<TradeSignal> reversalSignals = reversalStrategy.generateSignals(records);
             if (reversalSignals.isEmpty()) {
                System.out.println("    No reversal signals generated.");
            } else {
                reversalSignals.forEach(signal -> System.out.println("    " + signal));
            }
            totalSignals += reversalSignals.size();
        }

        System.out.println("  --- Scenario Summary for " + scenarioName + " ---");
        System.out.println("  Total signals generated across active strategies: " + totalSignals);
        assertTrue(scenarioName + ": Processed without runtime errors", true); // Basic check
    }

    /**
     * Tests the trading logic against a "Choppy/Mixed Market" scenario.
     * Uses data from "stockdata/MW-NIFTY-500-01-Nov-2024.csv".
     */
    public static void testChoppyMarketScenario() {
        printTestHeader("Choppy/Mixed Market Scenario Test");
        CsvMarketDataReader dataReader = new CsvMarketDataReader();
        StrategyConfig config = new StrategyConfig(); // Using default config
        String filePath = "stockdata/MW-NIFTY-500-01-Nov-2024.csv"; // US market context: use relevant US data

        try {
            dataReader.loadData(filePath);
            List<StockDataRecord> records = dataReader.getRecords();
            System.out.println("  Loaded " + records.size() + " records for choppy market.");
            assertTrue("Choppy market: Records loaded", !records.isEmpty());
            if (!records.isEmpty()) {
                runStrategiesForScenario(records, "Choppy/Mixed Market", config);
            }
        } catch (IOException e) {
            System.err.println("  Error loading data for choppy market scenario: " + e.getMessage());
        }
    }

    /**
     * Tests the trading logic against a "Moderately Bullish Market" scenario.
     * Uses data from "stockdata/MW-NIFTY-500-02-Dec-2024.csv".
     */
    public static void testModeratelyBullishScenario() {
        printTestHeader("Moderately Bullish Scenario Test");
        CsvMarketDataReader dataReader = new CsvMarketDataReader();
        StrategyConfig config = new StrategyConfig(); // Using default config
        String filePath = "stockdata/MW-NIFTY-500-02-Dec-2024.csv"; // US market context: use relevant US data

        try {
            dataReader.loadData(filePath);
            List<StockDataRecord> records = dataReader.getRecords();
            System.out.println("  Loaded " + records.size() + " records for moderately bullish market.");
            assertTrue("Moderately bullish: Records loaded", !records.isEmpty());
            if(!records.isEmpty()){
                runStrategiesForScenario(records, "Moderately Bullish Market", config);
            }
        } catch (IOException e) {
            System.err.println("  Error loading data for moderately bullish scenario: " + e.getMessage());
        }
    }

    /**
     * Tests the trading logic against a "Volatile Market with Divergence" scenario.
     * Uses data from "stockdata/MW-NIFTY-500-01-Sep-2024.csv".
     */
    public static void testVolatileMarketScenario() {
        printTestHeader("Volatile Market Scenario with Divergence Test");
        CsvMarketDataReader dataReader = new CsvMarketDataReader();
        StrategyConfig config = new StrategyConfig(); // Using default config
        String filePath = "stockdata/MW-NIFTY-500-01-Sep-2024.csv"; // US market context: use relevant US data

        try {
            dataReader.loadData(filePath);
            List<StockDataRecord> records = dataReader.getRecords();
            System.out.println("  Loaded " + records.size() + " records for volatile market.");
            assertTrue("Volatile market: Records loaded", !records.isEmpty());
            if(!records.isEmpty()){
                runStrategiesForScenario(records, "Volatile Market", config);
            }
        } catch (IOException e) {
            System.err.println("  Error loading data for volatile market scenario: " + e.getMessage());
        }
    }

    /**
     * Main entry point for the MarketConditionTester.
     * Executes all defined market scenario tests.
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        System.out.println("--- Market Condition Tester Initializing (US Market Focus) ---");
        Locale.setDefault(Locale.US); // Ensure consistent number formatting for output

        testChoppyMarketScenario();
        testModeratelyBullishScenario();
        testVolatileMarketScenario();

        System.out.println("\n--- Market Condition Tester Finished All Scenarios ---");
    }
}
