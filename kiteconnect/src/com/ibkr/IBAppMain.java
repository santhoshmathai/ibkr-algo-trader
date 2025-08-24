package com.ibkr;

import com.ibkr.service.MarketDataService;
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

        MarketDataService marketDataService = appContext.getMarketDataService();

        if (marketDataService == null) {
            logger.error("Failed to get MarketDataService from AppContext. Exiting.");
            return;
        }

        logger.info("Attempting to connect MarketDataService...");
        // Attempt to connect and subscribe
        try {
            // host, port, clientId from config or AppContext eventually
            String host = appContext.getTwsHost();
            int port = appContext.getTwsPort();
            int clientId = appContext.getTwsClientId();
            logger.info("Connecting to TWS on {}:{} with clientId {}", host, port, clientId);
            marketDataService.connect(host, port, clientId);
            // Assuming connect is synchronous enough or connectAck will confirm.
            // For robust check, use a latch or callback set by connectAck.
            logger.info("MarketDataService connect method called. Check logs for connectAck.");

        } catch (Exception e) {
            logger.error("Error during IBAppMain execution: {}", e.getMessage(), e);
        }
        // The application will continue running due to the message processing thread.
        // Add shutdown hooks if needed for graceful exit.

        final AppContext finalAppContext = appContext; // Effectively final for use in lambda
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook initiated by JVM...");
            if (finalAppContext != null) {
                logger.info("Calling AppContext shutdown...");
                finalAppContext.shutdown();
            } else {
                logger.warn("AppContext was null when shutdown hook ran.");
            }
            logger.info("Shutdown hook processing completed.");
        }, "IBAppShutdownHook")); // Giving the hook thread a name

        logger.info("IBAppMain setup complete. Application is running.");
    }
}