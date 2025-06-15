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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class MarketDataHandler  {
    private static final Logger logger = LoggerFactory.getLogger(MarketDataHandler.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // Configuration
    private static final int MAX_MEMORY_TICKS = 10000; // Keep last 10,000 ticks in memory per symbol
    private static final boolean PERSIST_TO_DISK = true;
    private static final String DATA_DIRECTORY = "market_data";

    // In-memory storage
    private final Map<String, TickDataSeries> tickData = new ConcurrentHashMap<>();
    private final Map<String, Contract> symbolToContract = new ConcurrentHashMap<>();
    ///private final OrderManager orderManager;

    // Disk persistence
    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, BufferedWriter> symbolWriters = new ConcurrentHashMap<>();

  /*  public MarketDataHandler(OrderManager orderManager) {
        //this.orderManager = orderManager;
        initializeDataDirectory();
    }*/


    public MarketDataHandler() {
        //this.orderManager = orderManager;
        initializeDataDirectory();
    }

    private void initializeDataDirectory() {
        if (PERSIST_TO_DISK) {
            try {
                Path path = Paths.get(DATA_DIRECTORY);
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                }
            } catch (IOException e) {
                logger.error("Failed to create data directory", e);
            }
        }
    }

    //@Override
    public void onTickPrice(Contract contract, int field, double price, TickAttrib attribs) {
        String symbol = contract.symbol();
        long timestamp = System.currentTimeMillis();

        // Store contract reference if new
        symbolToContract.putIfAbsent(symbol, contract);

        // Create tick record
        Tick tick = new Tick(timestamp, field, price, 0, attribs);

        // Store in memory
        storeTickInMemory(symbol, tick);

        // Persist to disk if enabled
        if (PERSIST_TO_DISK) {
            persistTickAsync(symbol, tick);
        }

        // Process tick
        processTick(contract, tick);
    }

   // @Override
    public void onTickSize(Contract contract, int field, int size) {
        String symbol = contract.symbol();
        long timestamp = System.currentTimeMillis();

        // Store contract reference if new
        symbolToContract.putIfAbsent(symbol, contract);

        // Create tick record
        Tick tick = new Tick(timestamp, field, 0, size, null);

        // Store in memory
        storeTickInMemory(symbol, tick);

        // Persist to disk if enabled
        if (PERSIST_TO_DISK) {
            persistTickAsync(symbol, tick);
        }

        // Process tick
        processTick(contract, tick);
    }

    private void storeTickInMemory(String symbol, Tick tick) {
        tickData.computeIfAbsent(symbol, k -> new TickDataSeries())
                .addTick(tick);
    }

    private void persistTickAsync(String symbol, Tick tick) {
        persistenceExecutor.execute(() -> {
            try {
                BufferedWriter writer = symbolWriters.computeIfAbsent(symbol, this::createWriterForSymbol);
                writer.write(tick.toCsvString());
                writer.newLine();
            } catch (IOException e) {
                logger.error("Failed to persist tick for {}", symbol, e);
            }
        });
    }

    private BufferedWriter createWriterForSymbol(String symbol) {
        try {
            String dateStr = LocalDate.now().format(DATE_FORMAT);
            String filename = String.format("%s/%s_%s.csv", DATA_DIRECTORY, symbol, dateStr);
            return new BufferedWriter(new FileWriter(filename, true));
        } catch (IOException e) {
            logger.error("Failed to create writer for {}", symbol, e);
            return null;
        }
    }

    private void processTick(Contract contract, Tick tick) {
        // Implement your tick processing logic here
    /*    switch (tick.getField()) {
            case TickType.LAST:
                logger.debug("Last price for {}: {}", contract.symbol(), tick.getPrice());
                break;
            case TickType.BID:
                logger.debug("Bid price for {}: {}", contract.symbol(), tick.getPrice());
                break;
            case TickType.ASK:
                logger.debug("Ask price for {}: {}", contract.symbol(), tick.getPrice());
                break;
            case TickType.VOLUME:
                logger.debug("Volume for {}: {}", contract.symbol(), tick.getSize());
                break;
        }*/
    }

    // Data retrieval methods
    public List<Tick> getTicks(String symbol, long startTime, long endTime) {
        TickDataSeries series = tickData.get(symbol);
        if (series != null) {
            return series.getTicks(startTime, endTime);
        }
        return Collections.emptyList();
    }

    public Optional<Tick> getLastTick(String symbol) {
        TickDataSeries series = tickData.get(symbol);
        if (series != null) {
            return series.getLastTick();
        }
        return Optional.empty();
    }

    public Map<Integer, List<Tick>> getTicksByType(String symbol, long startTime, long endTime) {
        TickDataSeries series = tickData.get(symbol);
        if (series != null) {
            return series.getTicksByType(startTime, endTime);
        }
        return Collections.emptyMap();
    }

    public void shutdown() {
        persistenceExecutor.shutdown();
        try {
            if (!persistenceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                persistenceExecutor.shutdownNow();
            }

            // Close all writers
            for (BufferedWriter writer : symbolWriters.values()) {
                try {
                    writer.close();
                } catch (IOException e) {
                    logger.error("Error closing writer", e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Data structures
    private static class Tick {
        private final long timestamp;
        private final int field;
        private final double price;
        private final int size;
        private final TickAttrib attribs;

        public Tick(long timestamp, int field, double price, int size, TickAttrib attribs) {
            this.timestamp = timestamp;
            this.field = field;
            this.price = price;
            this.size = size;
            this.attribs = attribs;
        }

        public String toCsvString() {
            return String.join(",",
                    String.valueOf(timestamp),
                    String.valueOf(field),
                    String.valueOf(price),
                    String.valueOf(size),
                    attribs != null ? attribs.toString() : ""
            );
        }

        // Getters
        public long getTimestamp() { return timestamp; }
        public int getField() { return field; }
        public double getPrice() { return price; }
        public int getSize() { return size; }
        public TickAttrib getAttribs() { return attribs; }
    }

    private static class TickDataSeries {
        private final ConcurrentSkipListMap<Long, Tick> ticksByTime = new ConcurrentSkipListMap<>();
        private final Map<Integer, List<Tick>> ticksByType = new ConcurrentHashMap<>();
        private final AtomicLong count = new AtomicLong();

        public void addTick(Tick tick) {
            // Clean old ticks if we're over limit
            if (count.incrementAndGet() > MAX_MEMORY_TICKS) {
                Map.Entry<Long, Tick> first = ticksByTime.firstEntry();
                if (first != null) {
                    ticksByTime.remove(first.getKey());
                    count.decrementAndGet();
                }
            }

            ticksByTime.put(tick.getTimestamp(), tick);
            ticksByType.computeIfAbsent(tick.getField(), k -> new CopyOnWriteArrayList<>())
                    .add(tick);
        }

        public List<Tick> getTicks(long startTime, long endTime) {
            return new ArrayList<>(ticksByTime.subMap(startTime, endTime).values());
        }

        public Optional<Tick> getLastTick() {
            Map.Entry<Long, Tick> last = ticksByTime.lastEntry();
            return last != null ? Optional.of(last.getValue()) : Optional.empty();
        }

        public Map<Integer, List<Tick>> getTicksByType(long startTime, long endTime) {
            Map<Integer, List<Tick>> result = new HashMap<>();
            for (Map.Entry<Integer, List<Tick>> entry : ticksByType.entrySet()) {
                List<Tick> filtered = entry.getValue().stream()
                        .filter(t -> t.getTimestamp() >= startTime && t.getTimestamp() <= endTime)
                        .collect(Collectors.toList());
                if (!filtered.isEmpty()) {
                    result.put(entry.getKey(), filtered);
                }
            }
            return result;
        }
    }

    // Utility methods for loading historical data
    public List<Tick> loadHistoricalTicks(String symbol, LocalDate date) throws IOException {
        String filename = String.format("%s/%s_%s.csv", DATA_DIRECTORY, symbol, date.format(DATE_FORMAT));
        Path path = Paths.get(filename);

        if (!Files.exists(path)) {
            return Collections.emptyList();
        }

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return reader.lines()
                    .map(this::parseTickFromCsv)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    private Tick parseTickFromCsv(String line) {
        try {
            String[] parts = line.split(",");
            long timestamp = Long.parseLong(parts[0]);
            int field = Integer.parseInt(parts[1]);
            double price = Double.parseDouble(parts[2]);
            int size = Integer.parseInt(parts[3]);
            TickAttrib attribs = parts.length > 4 && !parts[4].isEmpty() ?
                    new TickAttrib() : null; // Simplified - would need proper TickAttrib parsing

            return new Tick(timestamp, field, price, size, attribs);
        } catch (Exception e) {
            logger.error("Failed to parse tick from CSV: {}", line, e);
            return null;
        }
    }
}