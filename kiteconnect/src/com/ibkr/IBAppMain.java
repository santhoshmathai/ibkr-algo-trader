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
        AppContext appContext = new AppContext(false);
        logger.info("AppContext initialized.");

        appContext.start();
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