package com.ibkr.alert;

import com.ib.client.Contract;
import com.ib.client.Execution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale; // For parsing IB's side "BOT" / "SLD"

public class TradeAlertLogger {

    private static final Logger logger = LoggerFactory.getLogger(TradeAlertLogger.class);
    private static final String ALERT_FILE_NAME = "TradeAlerts.txt"; // In the root project folder

    // IB's execution.time() is usually in "yyyyMMdd  HH:mm:ss" format, potentially with a timezone part.
    // Let's define a robust way to parse it or use current time if parsing is tricky.
    // For simplicity, we'll use current system time for the alert log timestamp for now,
    // but ideally, parse execution.time() if its format is consistent and includes timezone.
    // IBKR execution time format: "yyyyMMdd  HH:mm:ss" (lastTradeDate format)
    // This doesn't include timezone directly in the string usually.
    // Assuming TWS provides times in its configured timezone, often local to TWS or a specific exchange.
    // For alerts, a clear timestamp is good. Let's use system's default zone for alert log time for now.
    private static final DateTimeFormatter ALERT_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
    private static final DateTimeFormatter IB_EXECUTION_TIME_PARSER = DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss");


    /**
     * Logs the details of a trade execution to the TradeAlerts.txt file.
     * This method is synchronized to prevent concurrent write issues to the file.
     *
     * @param execution The Execution object from IBKR.
     * @param contract The Contract object associated with the execution.
     */
    public static synchronized void logTradeExecution(Execution execution, Contract contract) {
        if (execution == null || contract == null) {
            logger.warn("Attempted to log trade execution with null execution or contract details.");
            return;
        }

        try (FileWriter fw = new FileWriter(ALERT_FILE_NAME, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            ZonedDateTime alertTimestamp = ZonedDateTime.now(ZoneId.systemDefault());
            String formattedTimestamp = alertTimestamp.format(ALERT_TIMESTAMP_FORMAT);

            // execution.side() returns "BOT" for Buy, "SLD" for Sell
            String action = execution.side();
            if ("BOT".equalsIgnoreCase(action)) {
                action = "BUY";
            } else if ("SLD".equalsIgnoreCase(action)) {
                action = "SELL";
            }

            // Build the alert message
            StringBuilder alertMsg = new StringBuilder();
            alertMsg.append("Timestamp: ").append(formattedTimestamp);
            alertMsg.append(", Symbol: ").append(contract.symbol());
            alertMsg.append(", Action: ").append(action);
            alertMsg.append(", Quantity: ").append(execution.shares());
            alertMsg.append(", Price: ").append(String.format(Locale.US, "%.2f", execution.price())); // Format price to 2 decimal places
            alertMsg.append(", OrderID: ").append(execution.orderId());
            alertMsg.append(", ExecID: ").append(execution.execId());
            alertMsg.append(", IBTime: ").append(execution.time()); // Original IB execution time string

            out.println(alertMsg.toString());
            logger.info("Trade alert logged for symbol {}: {}", contract.symbol(), alertMsg.toString());

        } catch (IOException e) {
            logger.error("Failed to write trade alert to file {}: {}", ALERT_FILE_NAME, e.getMessage(), e);
        }
    }

    /**
     * Logs a generic system or strategy alert to the TradeAlerts.txt file.
     * This method is synchronized to prevent concurrent write issues to the file.
     *
     * @param alertType A string categorizing the alert (e.g., "PRICE_ACTION", "ORB_SIGNAL").
     * @param symbol The stock symbol related to the alert.
     * @param message A descriptive message for the alert.
     * @param contextPrice A relevant price associated with the alert (e.g., trigger price, current price). Can be 0 if not applicable.
     */
    public static synchronized void logSystemAlert(String alertType, String symbol, String message, double contextPrice) {
        if (alertType == null || symbol == null || message == null) {
            logger.warn("Attempted to log system alert with null alertType, symbol, or message.");
            return;
        }

        try (FileWriter fw = new FileWriter(ALERT_FILE_NAME, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            ZonedDateTime alertTimestamp = ZonedDateTime.now(ZoneId.systemDefault());
            String formattedTimestamp = alertTimestamp.format(ALERT_TIMESTAMP_FORMAT);

            StringBuilder alertMsg = new StringBuilder();
            alertMsg.append("Timestamp: ").append(formattedTimestamp);
            alertMsg.append(", Type: ").append(alertType);
            alertMsg.append(", Symbol: ").append(symbol);
            if (contextPrice != 0.0) { // Only include price if relevant (non-zero)
                alertMsg.append(", ContextPrice: ").append(String.format(Locale.US, "%.2f", contextPrice));
            }
            alertMsg.append(", Message: ").append(message);

            out.println(alertMsg.toString());
            logger.info("System alert logged for symbol {}: {}", symbol, alertMsg.toString());

        } catch (IOException e) {
            logger.error("Failed to write system alert to file {}: {}", ALERT_FILE_NAME, e.getMessage(), e);
        }
    }
}
