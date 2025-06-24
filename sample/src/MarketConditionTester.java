import com.marketdata.reader.CsvMarketDataReader;
import com.marketdata.reader.StockDataRecord;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Test runner for simulating trading logic against different market conditions
 * loaded from CSV files. This class uses {@link CsvMarketDataReader} to parse
 * historical market data and applies a simple trading strategy to demonstrate
 * how one might test algorithms under various scenarios.
 *
 * Each test scenario is represented by a separate method that loads a specific
 * CSV file and runs the simulation. Basic assertions are used to verify outcomes.
 *
 * To run: Compile and execute this class. Ensure the CSV files specified in
 * the test methods are available at the correct path (relative to the project root).
 */
public class MarketConditionTester {

    private static final double INITIAL_CASH = 1_000_000.0; // Hypothetical starting cash for simulation

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

    /**
     * Simulates a predefined trading rule against a list of stock records for a given scenario.
     * The rule is:
     * - BUY if %CHNG > 3% and LTP > 100
     * - SELL if %CHNG < -3% and LTP > 50
     * Tracks a hypothetical portfolio cash balance.
     *
     * @param records The list of {@link StockDataRecord} to process.
     * @param scenarioName A descriptive name for the scenario being tested.
     */
    private static void simulateTradingForRule(List<StockDataRecord> records, String scenarioName) {
        System.out.printf("--- Simulating for Scenario: %s ---%n", scenarioName);
        int buySignals = 0;
        int sellSignals = 0;
        int holdSignals = 0;
        double portfolioCash = INITIAL_CASH;
        int sharesToTrade = 10; // Fixed number of shares per trade for simplicity

        for (StockDataRecord record : records) {
            String symbol = record.getString(StockDataRecord.SYMBOL);
            Double percentChange = record.getDouble(StockDataRecord.PERCENT_CHNG);
            Double ltp = record.getDouble(StockDataRecord.LTP);

            if (symbol == null || percentChange == null || ltp == null || ltp <= 0) {
                continue; // Skip records with insufficient data for this simulation
            }

            // Buy Condition: %CHNG > 3% and LTP > 100
            if (percentChange > 3.0 && ltp > 100) {
                System.out.printf(Locale.US,"  Signal: BUY %s at %.2f (%%Change: %.2f%%)%n", symbol, ltp, percentChange);
                buySignals++;
                portfolioCash -= (ltp * sharesToTrade); // Simulate cost
            }
            // Sell Condition: %CHNG < -3% and LTP > 50 (assuming we can short or sell existing)
            else if (percentChange < -3.0 && ltp > 50) {
                System.out.printf(Locale.US,"  Signal: SELL %s at %.2f (%%Change: %.2f%%)%n", symbol, ltp, percentChange);
                sellSignals++;
                portfolioCash += (ltp * sharesToTrade); // Simulate proceeds
            } else {
                holdSignals++;
            }
        }

        System.out.println("\n  --- Simulation Summary for " + scenarioName + " ---");
        System.out.println("  Total BUY signals: " + buySignals);
        System.out.println("  Total SELL signals: " + sellSignals);
        System.out.println("  Total HOLD signals (no action): " + holdSignals);
        System.out.printf(Locale.US,"  Ending Portfolio Cash (simulated): %.2f%n", portfolioCash);

        // Example assertions (can be made more specific)
        assertTrue(scenarioName + ": At least one trade signal generated", (buySignals + sellSignals) > 0);
        if (scenarioName.toLowerCase().contains("bullish")) {
            assertTrue(scenarioName + ": More buy signals than sell signals expected", buySignals > sellSignals);
        } else if (scenarioName.toLowerCase().contains("choppy") || scenarioName.toLowerCase().contains("volatile")) {
             assertTrue(scenarioName + ": Both buy and sell signals expected", buySignals > 0 && sellSignals > 0);
        }
    }

    /**
     * Tests the trading logic against a "Choppy/Mixed Market" scenario.
     * Uses data from "stockdata/MW-NIFTY-500-01-Nov-2024.csv".
     */
    public static void testChoppyMarketScenario() {
        printTestHeader("Choppy/Mixed Market Scenario Test");
        CsvMarketDataReader dataReader = new CsvMarketDataReader();
        String filePath = "stockdata/MW-NIFTY-500-01-Nov-2024.csv";
        try {
            dataReader.loadData(filePath);
            List<StockDataRecord> records = dataReader.getRecords();
            System.out.println("  Loaded " + records.size() + " records for choppy market.");
            assertTrue("Choppy market: Records loaded", !records.isEmpty());
            if (!records.isEmpty()) {
                simulateTradingForRule(records, "Choppy/Mixed Market");
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
        String filePath = "stockdata/MW-NIFTY-500-02-Dec-2024.csv";
        try {
            dataReader.loadData(filePath);
            List<StockDataRecord> records = dataReader.getRecords();
            System.out.println("  Loaded " + records.size() + " records for moderately bullish market.");
            assertTrue("Moderately bullish: Records loaded", !records.isEmpty());
            if(!records.isEmpty()){
                simulateTradingForRule(records, "Moderately Bullish Market");
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
        String filePath = "stockdata/MW-NIFTY-500-01-Sep-2024.csv";
        try {
            dataReader.loadData(filePath);
            List<StockDataRecord> records = dataReader.getRecords();
            System.out.println("  Loaded " + records.size() + " records for volatile market.");
            assertTrue("Volatile market: Records loaded", !records.isEmpty());
            if(!records.isEmpty()){
                simulateTradingForRule(records, "Volatile Market");
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
        System.out.println("--- Market Condition Tester Initializing ---");
        Locale.setDefault(Locale.US); // Ensure consistent number formatting for output

        testChoppyMarketScenario();
        testModeratelyBullishScenario();
        testVolatileMarketScenario();

        System.out.println("\n--- Market Condition Tester Finished All Scenarios ---");
    }
}
