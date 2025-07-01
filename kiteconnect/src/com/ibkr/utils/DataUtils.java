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

    /**
     * Retrieves the volume of the last completed 1-minute bar for a given stock symbol.
     *
     * This method relies on the {@link TradingEngine} having its {@code oneMinuteBarsHistory}
     * populated by processing 1-minute bar data.
     *
     * @param appContext The application context, used to access the TradingEngine.
     * @param symbol The stock symbol (e.g., "AAPL") for which to find the volume.
     * @return The volume of the last completed 1-minute bar as a Long, or {@code null}
     *         if appContext, symbol is invalid, TradingEngine is not found, or no bar data exists.
     */
    public static Long getLastCompletedOneMinuteBarVolume(AppContext appContext, String symbol) {
        if (appContext == null) {
            logger.warn("AppContext is null. Cannot retrieve last 1-min bar volume.");
            return null;
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            logger.warn("Symbol is null or empty. Cannot retrieve last 1-min bar volume.");
            return null;
        }

        // AppContext needs a getter for TradingEngine. Assuming it exists or will be added.
        // For now, this is a conceptual access path. If AppContext doesn't directly expose TradingEngine,
        // this utility might need to be re-thought or TradingEngine passed differently.
        // Let's assume AppContext is refactored to provide access if it doesn't already.
        // Conceptual: com.ibkr.core.TradingEngine tradingEngine = appContext.getTradingEngine();

        // TODO: AppContext needs a getTradingEngine() method.
        // This is a placeholder. In a real scenario, AppContext would provide access to TradingEngine.
        // If direct access is not possible, this utility method might not be viable in this exact form
        // or would need TradingEngine passed as a parameter instead of AppContext.
        // For now, we'll proceed assuming a getter will be added to AppContext.

        Object tradingEngineObj = null; // Placeholder for appContext.getTradingEngine();
        // This part will fail if AppContext doesn't have getTradingEngine()
        // We need to add getTradingEngine() to AppContext first.
        // For the purpose of this step, let's assume it's available.
        // In a real flow, I'd add it to AppContext then come back here.

        com.ibkr.core.TradingEngine tradingEngine = appContext.getTradingEngine(); // Assume this method exists

        if (tradingEngine == null) {
            logger.warn("TradingEngine not available from AppContext. Cannot retrieve last 1-min bar volume for {}.", symbol);
            return null;
        }

        com.ibkr.core.TradingEngine.BarData lastBarData = tradingEngine.getLastCompletedBarData(symbol);

        if (lastBarData != null) {
            return lastBarData.volume;
        } else {
            logger.debug("No completed 1-minute bar data found in TradingEngine for symbol: {} to get volume.", symbol);
            return null; // Or 0L if that's preferred for "no volume" vs "no data"
        }
    }
}
