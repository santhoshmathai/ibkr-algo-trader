package com.ibkr.analysis;

import com.ibkr.AppContext;
import com.ibkr.models.LevelStrength;
import com.ibkr.models.LevelType;
import com.ibkr.models.PreviousDayData;
import com.ibkr.models.SupportResistanceLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList; // Using CopyOnWriteArrayList for thread-safe iteration if needed

public class SupportResistanceAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(SupportResistanceAnalyzer.class);
    private final Map<String, List<SupportResistanceLevel>> symbolSrLevels = new ConcurrentHashMap<>();
    private final AppContext appContext;

    public SupportResistanceAnalyzer(AppContext appContext) {
        if (appContext == null) {
            throw new IllegalArgumentException("AppContext cannot be null for SupportResistanceAnalyzer.");
        }
        this.appContext = appContext;
        logger.info("SupportResistanceAnalyzer initialized.");
    }

    /**
     * Calculates daily support and resistance levels based on previous day's high and low.
     * This method is intended to be called once per symbol per day after PreviousDayData is available.
     * It clears any existing levels for the symbol before adding new daily levels.
     * @param symbol The stock symbol for which to calculate levels.
     */
    public void calculateDailyLevels(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            logger.warn("Symbol is null or empty. Cannot calculate daily S/R levels.");
            return;
        }

        PreviousDayData pdData = appContext.getPreviousDayData(symbol);

        if (pdData == null) {
            logger.warn("No PreviousDayData available for symbol: {}. Cannot calculate daily S/R levels.", symbol);
            return;
        }

        // Clear existing levels for this symbol before adding new daily ones to avoid stale/duplicate daily levels
        List<SupportResistanceLevel> levels = symbolSrLevels.computeIfAbsent(symbol, k -> new CopyOnWriteArrayList<>());
        // A more robust clear would be to filter out only "DailyHighLow" method based levels if mixing methods later.
        // For now, assuming only daily levels are managed by this method for a symbol, or it's a fresh calculation.
        levels.clear(); // Clears all levels for the symbol if this method is the sole populator for daily levels.

        if (pdData.getPreviousHigh() > 0) {
            SupportResistanceLevel resistance = new SupportResistanceLevel(
                    pdData.getPreviousHigh(),
                    LevelType.RESISTANCE,
                    LevelStrength.MODERATE, // Default strength for daily levels
                    "DailyHighLow"
            );
            levels.add(resistance);
            logger.debug("Added daily resistance for {}: {}", symbol, resistance);
        } else {
            logger.debug("Previous day high for {} is 0 or not set. Skipping resistance calculation.", symbol);
        }

        if (pdData.getPreviousLow() > 0) {
            SupportResistanceLevel support = new SupportResistanceLevel(
                    pdData.getPreviousLow(),
                    LevelType.SUPPORT,
                    LevelStrength.MODERATE, // Default strength for daily levels
                    "DailyHighLow"
            );
            levels.add(support);
            logger.debug("Added daily support for {}: {}", symbol, support);
        } else {
            logger.debug("Previous day low for {} is 0 or not set. Skipping support calculation.", symbol);
        }

        // Optionally, add previous day close as a level
        if (pdData.getPreviousClose() > 0) {
            // Determine if it's support or resistance based on where it is relative to current price (not known here)
            // Or, add it with a NEUTRAL strength or type if such a concept exists.
            // For now, let's consider it a WEAK level. Type depends on context not available here.
            // Or, simply add it as a "PreviousClose" type if LevelType enum is expanded.
            // Adding it as a WEAK level, type depends on its relation to open or previous H/L.
            // For simplicity, we can assign based on typical behavior or mark it specifically.
            // Let's add it as a general interest level.
            SupportResistanceLevel prevCloseLevel = new SupportResistanceLevel(
                pdData.getPreviousClose(),
                LevelType.SUPPORT, // Placeholder, could be either. Breakout generator may interpret based on price approach.
                LevelStrength.WEAK,
                "DailyClose"
            );
            // levels.add(prevCloseLevel); // Decided to keep it simple with High/Low for now.
            // logger.debug("Noted previous day close for {}: {}", symbol, prevCloseLevel.getLevelPrice());
        }


        if (levels.isEmpty()) {
            logger.info("No daily S/R levels calculated for {} (possibly due to missing PDay High/Low).", symbol);
        } else {
            logger.info("Calculated {} daily S/R levels for symbol: {}", levels.size(), symbol);
        }
    }

    /**
     * Retrieves the calculated S/R levels for a given symbol.
     * @param symbol The stock symbol.
     * @return A list of SupportResistanceLevel objects, or an empty list if none are found.
     */
    public List<SupportResistanceLevel> getLevels(String symbol) {
        if (symbol == null) return Collections.emptyList();
        return symbolSrLevels.getOrDefault(symbol, Collections.emptyList());
    }

    /**
     * Clears S/R levels for a specific symbol.
     * @param symbol The stock symbol for which to clear levels.
     */
    public void clearLevels(String symbol) {
        if (symbol == null) return;
        symbolSrLevels.remove(symbol);
        logger.info("Cleared S/R levels for symbol: {}", symbol);
    }

    /**
     * Clears all S/R levels for all symbols.
     * Useful for daily reset.
     */
    public void clearAllLevels() {
        symbolSrLevels.clear();
        logger.info("Cleared all S/R levels for all symbols.");
    }
}
