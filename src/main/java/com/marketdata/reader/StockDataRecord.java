package com.marketdata.reader;

import java.util.Map;
import java.util.HashMap;

/**
 * Represents a single row of stock data parsed from a market data CSV file.
 * This class provides methods to access data fields by standardized keys and
 * includes utility methods for retrieving values as specific types (Double, Long),
 * handling basic parsing of numeric strings that might contain commas.
 */
public class StockDataRecord {
    private Map<String, Object> data;

    // Standardized keys for commonly used fields
    /** Key for the stock trading symbol. */
    /** Key for the stock trading symbol. */
    public static final String SYMBOL = "SYMBOL";
    /** Key for the opening price. */
    public static final String OPEN = "OPEN";
    /** Key for the highest price of the period. */
    public static final String HIGH = "HIGH";
    /** Key for the lowest price of the period. */
    public static final String LOW = "LOW";
    /** Key for the previous closing price. */
    public static final String PREV_CLOSE = "PREV_CLOSE";
    /** Key for the Last Traded Price. */
    public static final String LTP = "LTP";
    /** Key for the indicative closing price (if available). */
    public static final String INDICATIVE_CLOSE = "INDICATIVE_CLOSE";
    /** Key for the change in price. */
    public static final String CHNG = "CHNG";
    /** Key for the percentage change in price. */
    public static final String PERCENT_CHNG = "%CHNG";
    /** Key for the volume of shares traded. */
    public static final String VOLUME_SHARES = "VOLUME_SHARES";
    /** Key for the value of shares traded (typically in Crores). */
    public static final String VALUE_CRORES = "VALUE_CRORES";
    /** Key for Volume Weighted Average Price (VWAP) - not in current CSVs but a common field. */
    public static final String VWAP = "VWAP"; // Assuming VWAP might be useful, though not in current CSV
    /** Key for Open Interest (OI) - relevant for derivatives, not in current stock CSVs. */
    public static final String OI = "OI"; // Assuming OI might be useful, though not in current CSV
    /** Key for a timestamp, useful if evolving to ticker-like data. */
    public static final String TIMESTAMP = "TIMESTAMP"; // For ticker like data if we evolve

    /**
     * Constructs an empty StockDataRecord.
     */
    public StockDataRecord() {
        this.data = new HashMap<>();
    }

    /**
     * Stores a data value with the given key.
     * @param key The key for the data field.
     * @param value The value to store.
     */
    public void put(String key, Object value) {
        this.data.put(key, value);
    }

    /**
     * Retrieves a data value by its key.
     * @param key The key of the data field to retrieve.
     * @return The value as an Object, or null if the key is not found.
     */
    public Object get(String key) {
        return this.data.get(key);
    }

    /**
     * Retrieves a data value as a String.
     * @param key The key of the data field.
     * @return The string representation of the value, or null if not found or value is null.
     */
    public String getString(String key) {
        Object val = get(key);
        return val != null ? String.valueOf(val) : null;
    }

    /**
     * Retrieves a data value as a Double.
     * Handles parsing of numeric strings, including those with commas.
     * @param key The key of the data field.
     * @return The Double value, or null if not found, value is null, or parsing fails.
     */
    public Double getDouble(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            // Handle strings that might represent numbers with commas
            return Double.parseDouble(String.valueOf(value).replace(",", ""));
        } catch (NumberFormatException e) {
            // System.err.println("Warning: Could not parse double for key '" + key + "', value: '" + value + "'");
            return null; // Or throw exception, or return NaN
        }
    }

    /**
     * Retrieves a data value as a Long.
     * Handles parsing of numeric strings, including those with commas.
     * @param key The key of the data field.
     * @return The Long value, or null if not found, value is null, or parsing fails.
     */
    public Long getLong(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            // Handle strings that might represent numbers with commas
            return Long.parseLong(String.valueOf(value).replace(",", ""));
        } catch (NumberFormatException e) {
            // System.err.println("Warning: Could not parse long for key '" + key + "', value: '" + value + "'");
            return null; // Or throw exception
        }
    }

    /**
     * Returns a copy of all data stored in this record.
     * @return A map containing all key-value pairs.
     */
    public Map<String, Object> getAllData() {
        return new HashMap<>(this.data);
    }

    /**
     * Calculates the gap percentage between the previous close and the current open.
     * Gap % = (OPEN - PREV_CLOSE) / PREV_CLOSE * 100.
     * @return The gap percentage, or null if PREV_CLOSE or OPEN is unavailable or PREV_CLOSE is zero.
     */
    public Double getGapPercentage() {
        Double open = getDouble(OPEN);
        Double prevClose = getDouble(PREV_CLOSE);

        if (open == null || prevClose == null || prevClose == 0.0) {
            return null;
        }
        return ((open - prevClose) / prevClose) * 100.0;
    }

    /**
     * Calculates the initial move percentage from open to LTP (Last Traded Price).
     * Initial Move % = (LTP - OPEN) / OPEN * 100.
     * This serves as a proxy for intraday momentum using daily data.
     * @return The initial move percentage, or null if LTP or OPEN is unavailable or OPEN is zero.
     */
    public Double getInitialMovePercentage() {
        Double open = getDouble(OPEN);
        Double ltp = getDouble(LTP);

        if (open == null || ltp == null || open == 0.0) {
            return null;
        }
        return ((ltp - open) / open) * 100.0;
    }

    @Override
    public String toString() {
        return "StockDataRecord{" +
                "data=" + data +
                '}';
    }
}
