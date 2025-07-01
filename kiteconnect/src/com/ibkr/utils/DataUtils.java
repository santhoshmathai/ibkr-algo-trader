package com.ibkr.utils;

import com.ibkr.AppContext;
import com.ibkr.models.PreviousDayData;
// Consider adding a Logger if warnings/errors are to be logged here.
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

public class DataUtils {
    // private static final Logger logger = LoggerFactory.getLogger(DataUtils.class); // Optional

    /**
     * Retrieves the Previous Day High (PDH) for a given stock symbol.
     *
     * This method relies on the {@link AppContext} having its {@code previousDayDataMap}
     * populated correctly, typically at the start of the trading day.
     *
     * @param appContext The application context containing shared data and services.
     * @param symbol The stock symbol (e.g., "AAPL") for which to find the PDH.
     * @return The Previous Day High as a Double, or {@code null} if the symbol is invalid,
     *         appContext is null, or if PDH data is not found for the symbol.
     */
    public static Double getPreviousDayHighForSymbol(AppContext appContext, String symbol) {
        if (appContext == null) {
            // logger.warn("AppContext is null. Cannot retrieve Previous Day High."); // Optional logging
            return null;
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            // logger.warn("Symbol is null or empty. Cannot retrieve Previous Day High."); // Optional logging
            return null;
        }

        PreviousDayData pdhData = appContext.getPreviousDayData(symbol);

        if (pdhData != null) {
            // Assuming getPreviousHigh() returns a double primitive.
            // If it can return null (e.g. if data was missing for high),
            // and the return type of getPreviousHigh() is Double, then no further check is needed.
            // If getPreviousHigh() returns 0.0 for missing data, that might be ambiguous.
            // For now, directly return what getPreviousHigh() provides.
            double previousHigh = pdhData.getPreviousHigh();
            if (previousHigh == 0.0) {
                // This could mean data was missing or PDH was actually 0.0 (unlikely for most stocks).
                // Depending on requirements, one might want to log this or treat 0.0 as 'not available'.
                // logger.debug("PreviousDayHigh for symbol {} is 0.0. This might indicate missing data or an actual value.", symbol);
            }
            return previousHigh; // If getPreviousHigh returns double, this is fine.
                                 // If it returns Double, it could be null.
        } else {
            // logger.warn("PreviousDayData not found for symbol: {}. Cannot retrieve PDH.", symbol); // Optional logging
            return null;
        }
    }
}
