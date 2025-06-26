package com.ibkr.marketdata.reader;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents a single record of stock data, typically a row from a CSV file.
 * This class acts as a flexible container using a Map to store various data fields
 * associated with a stock, such as open, high, low, close, volume, etc.
 * It provides standardized static keys for common data fields and typed getters
 * to retrieve values, with built-in null-handling and type conversion.
 */
public class StockDataRecord {

    // Standardized keys for common data fields
    public static final String SYMBOL = "SYMBOL";
    public static final String OPEN = "OPEN";
    public static final String HIGH = "HIGH";
    public static final String LOW = "LOW";
    public static final String PREV_CLOSE = "PREV_CLOSE"; // Previous Day Close
    public static final String LTP = "LTP"; // Last Traded Price
    public static final String INDICATIVE_CLOSE = "INDICATIVE_CLOSE"; // NSE specific, often same as LTP post-market
    public static final String CHNG = "CHNG"; // Change from Previous Close
    public static final String PERCENT_CHNG = "%CHNG"; // Percentage Change
    public static final String VOLUME_SHARES = "VOLUME_SHARES"; // Volume in number of shares
    public static final String VALUE_CRORES = "VALUE_CRORES"; // Value in Crores of Rupees (e.g., ₹ Crores)
    // Add more constants as needed, e.g., for VWAP, 52W_HIGH, 52W_LOW etc.

    private Map<String, Object> data;

    /**
     * Constructs an empty StockDataRecord.
     */
    public StockDataRecord() {
        this.data = new HashMap<>();
    }

    /**
     * Puts a key-value pair into the record.
     * @param key The key (preferably one of the static constants).
     * @param value The value associated with the key.
     */
    public void put(String key, Object value) {
        data.put(key, value);
    }

    /**
     * Retrieves a value by its key.
     * @param key The key of the value to retrieve.
     * @return The value as an Object, or null if the key is not found.
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * Retrieves a value as a String.
     * @param key The key of the value to retrieve.
     * @return The value as a String, or null if not found or not a String.
     */
    public String getString(String key) {
        Object value = data.get(key);
        return (value instanceof String) ? (String) value : (value != null ? String.valueOf(value) : null);
    }

    /**
     * Retrieves a value as a Double.
     * Handles cases where the value might be stored as Long or String.
     * @param key The key of the value to retrieve.
     * @return The value as a Double, or null if not found or not convertible to Double.
     */
    public Double getDouble(String key) {
        Object value = data.get(key);
        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Number) { // Handles Long, Integer, etc.
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).replace(",", ""));
            } catch (NumberFormatException e) {
                return null; // Cannot parse string to double
            }
        }
        return null;
    }

    /**
     * Retrieves a value as a Long.
     * Handles cases where the value might be stored as Integer or String.
     * @param key The key of the value to retrieve.
     * @return The value as a Long, or null if not found or not convertible to Long.
     */
    public Long getLong(String key) {
        Object value = data.get(key);
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Number) { // Handles Integer, Double (truncates), etc.
            return ((Number) value).longValue();
        } else if (value instanceof String) {
            try {
                // Remove commas for parsing, handle potential decimals by parsing as double first then long
                return (long) Double.parseDouble(((String) value).replace(",", ""));
            } catch (NumberFormatException e) {
                return null; // Cannot parse string to long
            }
        }
        return null;
    }

    /**
     * Returns a set of all keys present in this record.
     * @return A Set of String keys.
     */
    public Set<String> getKeys() {
        return data.keySet();
    }

    /**
     * Returns the underlying map holding all data for this record.
     * Useful for iterating over all entries or for debugging.
     * @return The raw data map.
     */
    public Map<String, Object> getAllData() {
        return data;
    }


    /**
     * Calculates the percentage gap between the open price and the previous close price.
     * Gap % = ((Open - PrevClose) / PrevClose) * 100
     * @return The gap percentage as a Double, or null if Open or PrevClose data is missing/invalid.
     */
    public Double getGapPercentage() {
        Double open = getDouble(OPEN);
        Double prevClose = getDouble(PREV_CLOSE);

        if (open != null && prevClose != null && prevClose != 0) {
            return ((open - prevClose) / prevClose) * 100.0;
        }
        return null;
    }

    /**
     * Calculates the percentage change of the Last Traded Price (LTP) from the open price.
     * Initial Move % = ((LTP - Open) / Open) * 100
     * @return The initial move percentage as a Double, or null if LTP or Open data is missing/invalid.
     */
    public Double getInitialMovePercentage() {
        Double ltp = getDouble(LTP);
        Double open = getDouble(OPEN);

        if (ltp != null && open != null && open != 0) {
            return ((ltp - open) / open) * 100.0;
        }
        return null;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("StockDataRecord{");
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
        }
        // Remove last comma and space
        if (data.size() > 0) {
            sb.setLength(sb.length() - 2);
        }
        sb.append("}");
        return sb.toString();
    }
}
