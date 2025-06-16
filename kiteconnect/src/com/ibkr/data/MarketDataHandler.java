package com.ibkr.data;

import com.ib.client.Contract;
import com.ib.client.TickAttrib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.zerodhatech.models.Tick; // Ensure this is imported for the public method
import com.zerodhatech.models.OHLC;  // For CSV formatting
import com.zerodhatech.models.Depth; // For CSV formatting
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat; // For CSV timestamp formatting
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList; // For depth formatting
import java.util.Date; // For CSV timestamp formatting
import java.util.Map;
import java.util.TimeZone; // For CSV timestamp formatting
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
// Removed Contract, TickAttrib, Collections, Optional, AtomicLong, Collectors, ConcurrentSkipListMap, CopyOnWriteArrayList
// Removed specific in-memory storage and related methods.

public class MarketDataHandler  {
    private static final Logger logger = LoggerFactory.getLogger(MarketDataHandler.class);
    private static final DateTimeFormatter DATE_FILENAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final boolean PERSIST_TO_DISK = true;
    private static final String DATA_DIRECTORY = "market_data";

    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, BufferedWriter> symbolWriters = new ConcurrentHashMap<>();
    private final SimpleDateFormat csvTimestampFormat;

    public MarketDataHandler() {
        initializeDataDirectory();
        csvTimestampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        csvTimestampFormat.setTimeZone(TimeZone.getTimeZone("IST")); // Or your desired timezone
    }

    private void initializeDataDirectory() {
        if (PERSIST_TO_DISK) {
            try {
                Path path = Paths.get(DATA_DIRECTORY);
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                    logger.info("Created data directory: {}", path.toAbsolutePath());
                } else {
                    logger.info("Data directory already exists: {}", path.toAbsolutePath());
                }
            } catch (IOException e) {
                logger.error("Failed to create/access data directory: {}", DATA_DIRECTORY, e);
            }
        }
    }

    public void persistTick(com.zerodhatech.models.Tick zerodhaTick) {
        if (!PERSIST_TO_DISK || zerodhaTick == null || zerodhaTick.getSymbol() == null) {
            if (zerodhaTick != null && zerodhaTick.getSymbol() == null) {
                logger.warn("Cannot persist tick due to null symbol. Tick: {}", zerodhaTick);
            }
            return;
        }
        persistTickAsync(zerodhaTick.getSymbol(), zerodhaTick);
    }

    private void persistTickAsync(String symbol, com.zerodhatech.models.Tick tick) {
        persistenceExecutor.execute(() -> {
            try {
                BufferedWriter writer = symbolWriters.computeIfAbsent(symbol, this::createWriterForSymbol);
                if (writer != null) {
                    writer.write(toCsvString(tick));
                    writer.newLine();
                }
            } catch (IOException e) {
                logger.error("Failed to persist tick for symbol {}", symbol, e);
            }
        });
    }

    private BufferedWriter createWriterForSymbol(String symbol) {
        if (symbol == null || symbol.isEmpty()) {
            logger.error("Symbol is null or empty, cannot create writer.");
            return null;
        }
        try {
            String dateStr = LocalDate.now().format(DATE_FILENAME_FORMAT);
            // Sanitize symbol name for filename (e.g., replace slashes if any)
            String sanitizedSymbol = symbol.replace("/", "_").replace("\\", "_");
            String filename = String.format("%s/%s_%s.csv", DATA_DIRECTORY, sanitizedSymbol, dateStr);

            Path filePath = Paths.get(filename);
            boolean fileExists = Files.exists(filePath);

            BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true)); // Append mode
            if (!fileExists) {
                logger.info("New CSV file created: {}. Writing header.", filename);
                writer.write(getCsvHeader());
                writer.newLine();
            }
            return writer;
        } catch (IOException e) {
            logger.error("Failed to create writer for symbol {}", symbol, e);
            return null;
        }
    }

    private String getCsvHeader() {
        // Simplified header for now
        return "instrumentToken,symbol,tickTimestamp,lastTradedTime,lastPrice,lastTradedQuantity,averagePrice,volumeTradedToday,openPrice,highPrice,lowPrice,closePrice,mode,ohlc_open,ohlc_high,ohlc_low,ohlc_close,depth_buy_csv,depth_sell_csv";
    }

    private String formatDateForCsv(Date date) {
        if (date == null) return "";
        return csvTimestampFormat.format(date);
    }

    private String escapeCsv(String data) {
        if (data == null) return "";
        if (data.contains(",") || data.contains("\"") || data.contains("\n")) {
            return "\"" + data.replace("\"", "\"\"") + "\"";
        }
        return data;
    }

    private String toCsvString(com.zerodhatech.models.Tick tick) {
        StringBuilder sb = new StringBuilder();
        sb.append(tick.getInstrumentToken()).append(",");
        sb.append(escapeCsv(tick.getSymbol())).append(",");
        sb.append(formatDateForCsv(tick.getTickTimestamp())).append(",");
        sb.append(formatDateForCsv(tick.getLastTradedTime())).append(",");
        sb.append(tick.getLastTradedPrice()).append(",");
        sb.append(tick.getLastTradedQuantity()).append(",");
        sb.append(tick.getAveragePrice()).append(",");
        sb.append(tick.getVolumeTradedToday()).append(",");
        // totalBuyQuantity and totalSellQuantity from Tick are often not directly from IB L1.
        // Using bestBidQuantity and bestAskQuantity as placeholders or if they are more relevant.
        // For true total day quantities, another source or aggregation might be needed.
        // sb.append(tick.getTotalBuyQuantity()).append(",");
        // sb.append(tick.getTotalSellQuantity()).append(",");
        sb.append(tick.getOpenPrice()).append(",");
        sb.append(tick.getHighPrice()).append(",");
        sb.append(tick.getLowPrice()).append(",");
        sb.append(tick.getClosePrice()).append(",");
        sb.append(escapeCsv(tick.getMode())).append(",");

        OHLC ohlc = tick.getOhlc();
        if (ohlc != null) {
            sb.append(ohlc.getOpen()).append(",");
            sb.append(ohlc.getHigh()).append(",");
            sb.append(ohlc.getLow()).append(",");
            sb.append(ohlc.getClose()); // Last OHLC field, no comma after
        } else {
            sb.append(",,,"); // Empty fields for OHLC
        }
        sb.append(",");


        Map<String, ArrayList<Depth>> depthMap = tick.getMarketDepth();
        if (depthMap != null) {
            ArrayList<Depth> buyDepth = depthMap.getOrDefault("buy", new ArrayList<>());
            ArrayList<Depth> sellDepth = depthMap.getOrDefault("sell", new ArrayList<>());

            // Simplified depth: "price1@qty1;price2@qty2|price1@qty1;price2@qty2"
            String buyDepthStr = buyDepth.stream()
                                    .map(d -> String.format("%.2f@%d", d.getPrice(), d.getQuantity()))
                                    .collect(Collectors.joining(";"));
            sb.append(escapeCsv(buyDepthStr)).append(",");

            String sellDepthStr = sellDepth.stream()
                                     .map(d -> String.format("%.2f@%d", d.getPrice(), d.getQuantity()))
                                     .collect(Collectors.joining(";"));
            sb.append(escapeCsv(sellDepthStr));
        } else {
            sb.append(","); // buy_depth_csv
            sb.append("");  // sell_depth_csv
        }
        return sb.toString();
    }

    public void shutdown() {
        logger.info("Shutting down MarketDataHandler persistence executor...");
        persistenceExecutor.shutdown();
        try {
            if (!persistenceExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("Persistence executor did not terminate in 10 seconds. Forcing shutdown.");
                persistenceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for persistence executor to shutdown.");
            persistenceExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("Closing {} symbol writers.", symbolWriters.size());
        for (Map.Entry<String, BufferedWriter> entry : symbolWriters.entrySet()) {
            try {
                entry.getValue().close();
            } catch (IOException e) {
                logger.error("Error closing writer for symbol {}", entry.getKey(), e);
            }
        }
        symbolWriters.clear();
        logger.info("MarketDataHandler shutdown complete.");
    }
}