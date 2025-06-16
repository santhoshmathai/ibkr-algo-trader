package com.ibkr;

// import com.ibkr.core.IBClient; // Replaced by AppContext
import com.ibkr.core.IBClient; // Still need this for type
// EClientSocket and IBConnectionManager imports are not strictly needed here anymore
// import com.ib.client.EClientSocket;
// import com.ibkr.core.IBConnectionManager;
import org.slf4j.Logger; // Added
import org.slf4j.LoggerFactory; // Added
import java.util.Set; // For subscribing to symbols from AppContext


public class IBAppMain {
    private static final Logger logger = LoggerFactory.getLogger(IBAppMain.class); // Added

    public static void main(String[] args) {
        logger.info("Application starting...");
        AppContext appContext = new AppContext();
        logger.info("AppContext initialized.");

        IBClient client = appContext.getIbClient();

        if (client == null) {
            logger.error("Failed to get IBClient from AppContext. Exiting.");
            return;
        }

        logger.info("Attempting to connect IBClient...");
        // Attempt to connect and subscribe
        try {
            // host, port, clientId from config or AppContext eventually
            String host = "127.0.0.1";
            int port = 7496; // Default TWS port for paper trading
            int clientId = 0;
            logger.info("Connecting to TWS on {}:{} with clientId {}", host, port, clientId);
            client.connect(host, port, clientId);
            // Assuming connect is synchronous enough or connectAck will confirm.
            // For robust check, use a latch or callback set by connectAck.
            logger.info("IBClient connect method called. Check logs for connectAck.");

            // It's crucial that IBClient.startMessageProcessing() is called to handle incoming messages.
            logger.info("Starting IBClient message processing thread...");
            client.startMessageProcessing();

            // Subscribe to instruments from AppContext's list if desired
            Set<String> stocksToSubscribe = appContext.getTop100USStocks(); // Example
            logger.info("Subscribing to symbols: {}", stocksToSubscribe);
            for (String stock : stocksToSubscribe) {
                 if (stock == null || stock.trim().isEmpty()) continue; // Basic validation
                 logger.debug("Subscribing to symbol: {}", stock);
                 client.subscribeToSymbol(stock, "SMART"); // Assuming SMART exchange for all
            }
            logger.info("Finished subscribing to symbols.");

        } catch (Exception e) {
            logger.error("Error during IBAppMain execution: {}", e.getMessage(), e);
        }
        // The application will continue running due to the message processing thread.
        // Add shutdown hooks if needed for graceful exit.
        logger.info("IBAppMain setup complete. Application is running.");
    }
}