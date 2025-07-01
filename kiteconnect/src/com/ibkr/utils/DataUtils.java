package com.ibkr.utils;

import com.ibkr.AppContext;
import com.ibkr.models.PreviousDayData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataUtils {
    private static final Logger logger = LoggerFactory.getLogger(DataUtils.class);

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
            logger.warn("AppContext is null. Cannot retrieve Previous Day High.");
            return null;
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            logger.warn("Symbol is null or empty. Cannot retrieve Previous Day High.");
            return null;
        }

        PreviousDayData pdhData = appContext.getPreviousDayData(symbol);

        if (pdhData != null) {
            double previousHigh = pdhData.getPreviousHigh();
            if (previousHigh == 0.0 && pdhData.getPreviousLow() == 0.0 && pdhData.getPreviousClose() == 0.0) {
                // If H, L, C are all 0.0, it's highly likely data wasn't properly populated or it's a non-trading entity.
                logger.warn("PreviousDayHigh for symbol {} is 0.0 (and L/C also 0.0), likely indicating missing or incomplete PDH data in AppContext.", symbol);
                // Depending on strategy, returning null might be safer than returning 0.0 if 0.0 isn't a valid high.
                // However, if 0.0 can be a legitimate (though rare) previous high, this check might need adjustment.
                // For now, if pdhData object exists, we trust its values.
                // If PDH is truly 0.0, it's still a value. The OrbStrategy would fail the PDH check naturally.
            }
            return previousHigh;
        } else {
            logger.warn("PreviousDayData not found in AppContext cache for symbol: {}. Cannot retrieve PDH. Ensure PDH data is fetched and populated at startup.", symbol);
            return null;
        }
    }
}
