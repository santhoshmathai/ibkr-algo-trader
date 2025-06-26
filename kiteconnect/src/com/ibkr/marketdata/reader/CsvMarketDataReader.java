package com.ibkr.marketdata.reader;

/**
 * Reads market data from CSV files specific to this project's format.
 * It handles parsing, data cleaning (like removing commas from numbers, handling hyphens),
 * and maps CSV columns to standardized keys defined in {@link StockDataRecord}.
 * The reader can load data from a file path or an InputStream.
 * It also includes a main method for basic self-testing.
 */
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CsvMarketDataReader {

    private List<StockDataRecord> records;
    private Map<String, Integer> headerMap;

    /**
     * Constructs a CsvMarketDataReader, initializing internal storage for records and headers.
     */
    public CsvMarketDataReader() {
        this.records = new ArrayList<>();
        this.headerMap = new HashMap<>();
    }

    /**
     * Returns a list of all loaded stock data records.
     * @return A new list containing all {@link StockDataRecord}s. Returns an empty list if no data loaded.
     */
    public List<StockDataRecord> getRecords() {
        return new ArrayList<>(records); // Return a copy
    }

    /**
     * Retrieves a specific stock data record by its trading symbol.
     * @param symbol The trading symbol to search for.
     * @return The {@link StockDataRecord} for the given symbol, or null if not found.
     */
    public StockDataRecord getRecordBySymbol(String symbol) {
        for (StockDataRecord record : records) {
            if (symbol.equals(record.getString(StockDataRecord.SYMBOL))) {
                return record;
            }
        }
        return null;
    }

    /**
     * Cleans a raw header string by removing newline characters, quotes,
     * trimming whitespace, and converting to uppercase.
     * @param rawHeader The raw header string from the CSV.
     * @return A sanitized header string.
     */
    private String sanitizeHeader(String rawHeader) {
        return rawHeader.replace("\n", "").replace("\"", "").trim().toUpperCase();
    }

    private String getCleanedKey(String rawKey) {
        // Standardize common variations
        if (rawKey.startsWith("SYMBOL")) return StockDataRecord.SYMBOL;
        if (rawKey.startsWith("OPEN")) return StockDataRecord.OPEN;
        if (rawKey.startsWith("HIGH")) return StockDataRecord.HIGH;
        if (rawKey.startsWith("LOW")) return StockDataRecord.LOW;
        if (rawKey.startsWith("PREV. CLOSE")) return StockDataRecord.PREV_CLOSE;
        if (rawKey.startsWith("LTP")) return StockDataRecord.LTP;
        if (rawKey.startsWith("INDICATIVE CLOSE")) return StockDataRecord.INDICATIVE_CLOSE;
        if (rawKey.startsWith("CHNG") && !rawKey.startsWith("%CHNG")) return StockDataRecord.CHNG; // Order matters
        if (rawKey.startsWith("%CHNG")) return StockDataRecord.PERCENT_CHNG;
        if (rawKey.startsWith("VOLUME")) return StockDataRecord.VOLUME_SHARES; // Assuming "VOLUME (shares)"
        if (rawKey.startsWith("VALUE")) return StockDataRecord.VALUE_CRORES; // Assuming "VALUE (₹ Crores)"

        // Add more specific mappings if needed based on CSV variations
        return rawKey; // Fallback to the sanitized raw key
    }

    /**
     * Loads and parses market data from a CSV file specified by its path.
     * Clears any previously loaded records.
     * Handles CSV quirks like BOM characters, quoted values, and specific data cleaning.
     * The first non-header row is assumed to be a summary row (e.g., "NIFTY 500") and is skipped.
     *
     * @param filePath The path to the CSV file.
     * @throws IOException If an I/O error occurs reading the file.
     */
    public void loadData(String filePath) throws IOException {
        this.records.clear();
        this.headerMap.clear();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath, StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;

            // Regex to split CSV line by comma, respecting quotes
            Pattern pattern = Pattern.compile("\"([^\"]*)\"|(?<=,|^)([^,]*)(?:,|$)");

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                // Handle potential BOM character at the start of the file
                if (isHeader && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }

                List<String> values = new ArrayList<>();
                Matcher matcher = pattern.matcher(line);
                while(matcher.find()){
                    if(matcher.group(1)!=null){
                        values.add(matcher.group(1).trim());
                    } else {
                        values.add(matcher.group(2).trim());
                    }
                }

                if (isHeader) {
                    for (int i = 0; i < values.size(); i++) {
                        String sanitized = sanitizeHeader(values.get(i));
                        // System.out.println("Raw Header: '" + values.get(i) + "', Sanitized: '" + sanitized + "'"); // Debug
                        headerMap.put(sanitized, i);
                    }
                    isHeader = false;
                } else {
                    if (values.size() < headerMap.size() && values.size() < 2) { // Basic check for malformed line
                        // System.err.println("Skipping malformed or short line: " + line);
                        continue;
                    }
                    StockDataRecord record = new StockDataRecord();
                    for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                        String rawHeaderKey = entry.getKey();
                        Integer index = entry.getValue();
                        String standardizedKey = getCleanedKey(rawHeaderKey);

                        if (index < values.size()) {
                            String rawValue = values.get(index);
                            if ("-".equals(rawValue) || rawValue.isEmpty()) {
                                record.put(standardizedKey, null);
                            } else {
                                // Attempt to parse as number, otherwise store as string
                                try {
                                    if (rawValue.contains(".") || standardizedKey.equals(StockDataRecord.CHNG) || standardizedKey.equals(StockDataRecord.PERCENT_CHNG)) {
                                         // CHNG and %CHNG can be negative or decimal.
                                        record.put(standardizedKey, Double.parseDouble(rawValue.replace(",", "")));
                                    } else {
                                        record.put(standardizedKey, Long.parseLong(rawValue.replace(",", "")));
                                    }
                                } catch (NumberFormatException e) {
                                    record.put(standardizedKey, rawValue); // Store as string if not a simple number
                                }
                            }
                        } else {
                            record.put(standardizedKey, null); // Missing value for this column
                        }
                    }
                    // Skip the first data line if it's the "NIFTY 500" aggregate
                    if (!"NIFTY 500".equals(record.getString(StockDataRecord.SYMBOL))) {
                        records.add(record);
                    }
                }
            }
        }
    }

    /**
     * Loads and parses market data from an {@link InputStream}.
     * Useful for reading data from resources within a JAR.
     * Clears any previously loaded records.
     * Handles CSV quirks like BOM characters, quoted values, and specific data cleaning.
     * The first non-header row is assumed to be a summary row (e.g., "NIFTY 500") and is skipped.
     *
     * @param inputStream The InputStream to read CSV data from.
     * @throws IOException If an I/O error occurs reading from the stream.
     */
    public void loadData(InputStream inputStream) throws IOException {
        this.records.clear();
        this.headerMap.clear();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            boolean isHeader = true;
            Pattern pattern = Pattern.compile("\"([^\"]*)\"|(?<=,|^)([^,]*)(?:,|$)");

            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                if (isHeader && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }

                List<String> values = new ArrayList<>();
                Matcher matcher = pattern.matcher(line);
                 while(matcher.find()){
                    if(matcher.group(1)!=null){
                        values.add(matcher.group(1).trim());
                    } else {
                        values.add(matcher.group(2).trim());
                    }
                }

                if (isHeader) {
                    for (int i = 0; i < values.size(); i++) {
                         String sanitized = sanitizeHeader(values.get(i));
                        headerMap.put(sanitized, i);
                    }
                    isHeader = false;
                } else {
                     if (values.size() < headerMap.size() && values.size() < 2) {
                        continue;
                    }
                    StockDataRecord record = new StockDataRecord();
                    for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                        String rawHeaderKey = entry.getKey();
                        Integer index = entry.getValue();
                        String standardizedKey = getCleanedKey(rawHeaderKey);

                        if (index < values.size()) {
                            String rawValue = values.get(index);
                            if ("-".equals(rawValue) || rawValue.isEmpty()) {
                                record.put(standardizedKey, null);
                            } else {
                                try {
                                     if (rawValue.contains(".") || standardizedKey.equals(StockDataRecord.CHNG) || standardizedKey.equals(StockDataRecord.PERCENT_CHNG)) {
                                        record.put(standardizedKey, Double.parseDouble(rawValue.replace(",", "")));
                                    } else {
                                        record.put(standardizedKey, Long.parseLong(rawValue.replace(",", "")));
                                    }
                                } catch (NumberFormatException e) {
                                    record.put(standardizedKey, rawValue);
                                }
                            }
                        } else {
                            record.put(standardizedKey, null);
                        }
                    }
                    if (!"NIFTY 500".equals(record.getString(StockDataRecord.SYMBOL))) {
                        records.add(record);
                    }
                }
            }
        }
    }

    /**
     * Main method for basic testing of the CsvMarketDataReader.
     * Attempts to load a sample CSV file (hardcoded path, assumes it exists)
     * and prints some information about the loaded data.
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        // Example usage:
        CsvMarketDataReader reader = new CsvMarketDataReader();
        try {
            // Create a dummy CSV file for testing in the root directory or provide a valid path
            // For this example, let's assume "stockdata/MW-NIFTY-500-01-Nov-2024.csv" exists
            // and is accessible from where this will be run.
            // If not, this main method will throw a FileNotFoundException.
            String testFilePath = "stockdata/MW-NIFTY-500-01-Nov-2024.csv";
            System.out.println("Attempting to load: " + testFilePath);
            reader.loadData(testFilePath);
            System.out.println("Loaded " + reader.getRecords().size() + " records.");

            if (!reader.getRecords().isEmpty()) {
                System.out.println("\nFirst few records:");
                for (int i = 0; i < Math.min(5, reader.getRecords().size()); i++) {
                    StockDataRecord record = reader.getRecords().get(i);
                    System.out.println("Symbol: " + record.getString(StockDataRecord.SYMBOL) +
                                       ", LTP: " + record.getDouble(StockDataRecord.LTP) +
                                       ", Volume: " + record.getLong(StockDataRecord.VOLUME_SHARES));
                }

                System.out.println("\nHeader map discovered:");
                for(Map.Entry<String, Integer> entry : reader.headerMap.entrySet()){
                    System.out.println("Header: '" + entry.getKey() + "' -> Index: " + entry.getValue());
                }

                System.out.println("\nCleaned keys from first record:");
                 StockDataRecord firstRecord = reader.getRecords().get(0);
                 for(String key : firstRecord.getAllData().keySet()){
                     System.out.println("Cleaned Key: '" + key + "', Value: '" + firstRecord.get(key) + "'");
                 }


                // Test fetching a specific symbol (replace with an actual symbol from your test file)
                String testSymbol = "CIPLA"; // Example, ensure this symbol exists in your test CSV
                StockDataRecord specificRecord = reader.getRecordBySymbol(testSymbol);
                if (specificRecord != null) {
                    System.out.println("\nData for " + testSymbol + ":");
                    System.out.println("  Open: " + specificRecord.getDouble(StockDataRecord.OPEN));
                    System.out.println("  High: " + specificRecord.getDouble(StockDataRecord.HIGH));
                    System.out.println("  Low: " + specificRecord.getDouble(StockDataRecord.LOW));
                    System.out.println("  LTP: " + specificRecord.getDouble(StockDataRecord.LTP));
                    System.out.println("  %Change: " + specificRecord.getDouble(StockDataRecord.PERCENT_CHNG));

                } else {
                    System.out.println("\nSymbol " + testSymbol + " not found.");
                }
            }

        } catch (IOException e) {
            System.err.println("Error loading or processing CSV file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
