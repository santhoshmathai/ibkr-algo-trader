package com.ibkr;

import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReaderSignal;
import com.ibkr.analysis.MarketSentimentAnalyzer;
import com.ibkr.analysis.SectorStrengthAnalyzer;
import com.ibkr.core.IBClient;
import com.ibkr.core.IBConnectionManager;
import com.ibkr.core.TradingEngine;
import com.ibkr.data.InstrumentRegistry;
import com.ibkr.data.TickAggregator;
import com.ibkr.indicators.VWAPAnalyzer;
import com.ibkr.indicators.VolumeAnalyzer;
import com.ibkr.liquidity.DarkPoolScanner;
import com.ibkr.risk.LiquidityMonitor;
import com.ibkr.risk.RiskManager;
import com.ibkr.risk.VolatilityAnalyzer;
import com.ibkr.safeguards.CircuitBreakerMonitor;
import com.ibkr.signal.BreakoutSignalGenerator;
import com.ibkr.strategy.TickProcessor;
import com.ibkr.data.MarketDataHandler;
import com.ibkr.models.PreviousDayData; // Added
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.FileInputStream;
import java.io.IOException; // Added
import java.io.InputStream;
import java.util.*;
import java.util.Properties;
import java.util.stream.Collectors;
import java.time.ZonedDateTime; // Added
import java.time.ZoneId; // Added
import java.time.LocalTime; // Added
import java.time.DayOfWeek; // Added


public class AppContext {
    private static final Logger logger = LoggerFactory.getLogger(AppContext.class);

    // Core Data and Client Infrastructure
    private final InstrumentRegistry instrumentRegistry;
    private final TickAggregator tickAggregator;
    private final IBConnectionManager ibConnectionManager;
    // private final EClientSocket eClientSocket; // Removed, will get from IBClient
    // private final EReaderSignal eReaderSignal; // Removed, will get from IBClient
    private final EClientSocket clientSocket; // Field to store the EClientSocket from IBClient
    private final EReaderSignal readerSignal;
    private final IBOrderExecutor ibOrderExecutor;
    private final IBClient ibClient;
    private final MarketDataHandler marketDataHandler; // Added

    // Analyzers
    private final MarketSentimentAnalyzer marketSentimentAnalyzer;
    private final SectorStrengthAnalyzer sectorStrengthAnalyzer;
    private final VWAPAnalyzer vwapAnalyzer;
    private final VolumeAnalyzer volumeAnalyzer;
    private final VolatilityAnalyzer volatilityAnalyzer;

    // Liquidity and Safeguards
    private final CircuitBreakerMonitor circuitBreakerMonitor;
    private final DarkPoolScanner darkPoolScanner;
    private final LiquidityMonitor liquidityMonitor;

    // Risk and Signal Generation
    private final RiskManager riskManager;
    private final BreakoutSignalGenerator breakoutSignalGenerator;

    // Strategy and Processing
    private final TradingEngine tradingEngine;
    private final TickProcessor tickProcessor;

    private final Set<String> top100USStocks;
    private Map<String, PreviousDayData> previousDayDataMap; // Added field

    public AppContext() {
        // Configuration Data
        this.top100USStocks = loadTop100USStocks();
        Map<String, List<String>> sectorToStocksMap = loadSectorToStocksMap();
        Map<String, String> symbolToSectorMap = loadSymbolToSectorMap(sectorToStocksMap); // Pass the loaded map

        // Level 0: No dependencies or only external config
        this.instrumentRegistry = new InstrumentRegistry();
        this.marketSentimentAnalyzer = new MarketSentimentAnalyzer(this, this.top100USStocks); // Pass AppContext
        // Ensure SectorStrengthAnalyzer gets valid, though possibly empty, maps
        this.sectorStrengthAnalyzer = new SectorStrengthAnalyzer(
            sectorToStocksMap != null ? sectorToStocksMap : new HashMap<>(),
            symbolToSectorMap != null ? symbolToSectorMap : new HashMap<>()
        );
        this.vwapAnalyzer = new VWAPAnalyzer();
        this.volumeAnalyzer = new VolumeAnalyzer();
        this.volatilityAnalyzer = new VolatilityAnalyzer();
        this.circuitBreakerMonitor = new CircuitBreakerMonitor();
        this.darkPoolScanner = new DarkPoolScanner();
        this.liquidityMonitor = new LiquidityMonitor();
        this.riskManager = new RiskManager(this.liquidityMonitor, this.volatilityAnalyzer);
        this.marketDataHandler = new MarketDataHandler(); // Added instantiation


        // Level 1: Depend on Level 0
        this.tradingEngine = new TradingEngine(
            this.vwapAnalyzer, this.volumeAnalyzer, this.circuitBreakerMonitor, this.darkPoolScanner,
            this.marketSentimentAnalyzer, this.sectorStrengthAnalyzer, this.instrumentRegistry
        );
        this.breakoutSignalGenerator = new BreakoutSignalGenerator(
            this.volatilityAnalyzer, this.vwapAnalyzer, this.volumeAnalyzer, this.sectorStrengthAnalyzer
        );
        this.tickAggregator = new TickAggregator(this.instrumentRegistry);

        // Create IBOrderExecutor without EClientSocket first
        this.ibOrderExecutor = new IBOrderExecutor(
            this.circuitBreakerMonitor, this.darkPoolScanner, this.instrumentRegistry
        );

        // Create IBClient (EWrapper)
        this.tickProcessor = new TickProcessor(
            this.breakoutSignalGenerator, this.riskManager, this.tradingEngine, this.ibOrderExecutor, this // Injected AppContext
        );
        this.ibClient = new IBClient(
            this.instrumentRegistry, this.tickAggregator, this.tickProcessor, this.ibOrderExecutor, this.marketDataHandler // Injected marketDataHandler
        );

        // EClientSocket and EReaderSignal are created inside IBClient. Get them.
        this.clientSocket = this.ibClient.getClientSocket();
        this.readerSignal = this.ibClient.getReaderSignal(); // Assuming getReaderSignal() exists per plan

        // Now provide EClientSocket to IBOrderExecutor
        this.ibOrderExecutor.setClientSocket(this.clientSocket);

        // Create IBConnectionManager and provide it to IBClient
        this.ibConnectionManager = new IBConnectionManager(this.clientSocket);
        this.ibClient.setConnectionManager(this.ibConnectionManager);

        // Fetch Previous Day Data - This needs to be called after IBClient is somewhat ready for requests,
        // typically after connection. For now, we'll call it here.
        // A better place might be in IBAppMain after connect() and startMessageProcessing()
        // or if IBClient exposes a method that's called post-connection.
        // For this subtask, let's assume it's called here for simplicity of AppContext setup.
        // This will be problematic if fetchPreviousDayDataForAllStocks requires an active connection.
        // TODO: Revisit placement of fetchPreviousDayDataForAllStocks if connection is required first.
        logger.info("Attempting to fetch previous day data for {} stocks.", this.top100USStocks.size());
        if (this.ibClient != null && this.top100USStocks != null && !this.top100USStocks.isEmpty()) {
            try {
                // This is a blocking call as implemented in the plan for IBClient
                this.previousDayDataMap = this.ibClient.fetchPreviousDayDataForAllStocks(this.top100USStocks);
                logger.info("Fetched previous day data for {} symbols.", this.previousDayDataMap.size());
            } catch (Exception e) { // Catching general Exception as await might throw InterruptedException
                logger.error("Error fetching previous day data: {}", e.getMessage(), e);
                this.previousDayDataMap = new HashMap<>(); // Initialize to empty on error
            }
        } else {
            logger.warn("IBClient or stock list is null/empty. Skipping fetch of previous day data.");
            this.previousDayDataMap = new HashMap<>(); // Initialize to empty
        }
    }

    private Set<String> loadTop100USStocks() {
        Properties properties = new Properties();
        String propertiesFileName = "app.properties";
        Set<String> stocks = new HashSet<>();

        try (InputStream inputStream = new FileInputStream(propertiesFileName)) {
            properties.load(inputStream);
            String stocksProperty = properties.getProperty("us.stocks.top100");

            if (stocksProperty != null && !stocksProperty.trim().isEmpty()) {
                stocks.addAll(Arrays.asList(stocksProperty.split(",")));
                logger.info("Loaded {} stock symbols from {} (us.stocks.top100).", stocks.size(), propertiesFileName);
            } else {
                logger.warn("'us.stocks.top100' property is missing or empty in {}. Using empty set.", propertiesFileName);
                // Optionally, return a default minimal set:
                // return Set.of("AAPL", "MSFT");
            }
        } catch (IOException e) {
            logger.error("Failed to load {} from filesystem. Error: {}. Using empty set.", propertiesFileName, e.getMessage(), e);
            // Optionally, return a default minimal set:
            // return Set.of("AAPL", "MSFT");
        }
        return stocks;
    }

    private Map<String, List<String>> loadSectorToStocksMap() {
        Properties properties = new Properties();
        String propertiesFileName = "app.properties";
        Map<String, List<String>> sectorToStocksMap = new HashMap<>();
        logger.info("Loading sector to stocks map from {}", propertiesFileName);

        try (InputStream inputStream = new FileInputStream(propertiesFileName)) {
            properties.load(inputStream);
            for (String propertyName : properties.stringPropertyNames()) {
                if (propertyName.startsWith("sector.") && propertyName.endsWith(".stocks")) {
                    // Extract sector name: sector.TECHNOLOGY.stocks -> TECHNOLOGY
                    String sectorName = propertyName.substring("sector.".length(), propertyName.lastIndexOf(".stocks"));
                    String stocksListStr = properties.getProperty(propertyName);
                    if (stocksListStr != null && !stocksListStr.trim().isEmpty()) {
                        List<String> stocksInSector = Arrays.asList(stocksListStr.split(","))
                                .stream()
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList());
                        if (!stocksInSector.isEmpty()) {
                            sectorToStocksMap.put(sectorName.toUpperCase(), stocksInSector);
                            logger.debug("Loaded sector {}: {} stocks", sectorName.toUpperCase(), stocksInSector.size());
                        } else {
                            logger.warn("Sector {} defined in {} but has no stocks listed.", sectorName.toUpperCase(), propertiesFileName);
                            sectorToStocksMap.put(sectorName.toUpperCase(), Collections.emptyList());
                        }
                    } else {
                        logger.warn("Sector {} has empty stock list in {}.", sectorName.toUpperCase(), propertiesFileName);
                        sectorToStocksMap.put(sectorName.toUpperCase(), Collections.emptyList());
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load {} for sector definitions. Error: {}. Returning empty map.", propertiesFileName, e.getMessage(), e);
            return new HashMap<>(); // Return empty map on error
        }
        logger.info("Loaded {} sectors from properties file.", sectorToStocksMap.size());
        return sectorToStocksMap;
    }

    private Map<String, String> loadSymbolToSectorMap(Map<String, List<String>> sectorToStocksMap) {
        Map<String, String> symbolToSectorMap = new HashMap<>();
        if (sectorToStocksMap == null || sectorToStocksMap.isEmpty()) {
            logger.warn("Sector-to-stocks map is empty. Cannot build symbol-to-sector map.");
            return symbolToSectorMap;
        }
        logger.info("Building symbol to sector map...");
        for (Map.Entry<String, List<String>> entry : sectorToStocksMap.entrySet()) {
            String sector = entry.getKey();
            List<String> stocksInSector = entry.getValue();
            if (stocksInSector != null) {
                for (String symbol : stocksInSector) {
                    if (symbolToSectorMap.containsKey(symbol)) {
                        logger.warn("Symbol {} is listed in multiple sectors. Overwriting with sector {}. Previous sector: {}",
                                symbol, sector, symbolToSectorMap.get(symbol));
                    }
                    symbolToSectorMap.put(symbol, sector);
                }
            }
        }
        logger.info("Symbol to sector map built with {} entries.", symbolToSectorMap.size());
        return symbolToSectorMap;
    }

    // Getter methods for components needed by IBAppMain or other external access
    public IBClient getIbClient() {
        return ibClient;
    }

    public Set<String> getTop100USStocks() { // Added getter for the configured stocks
        return top100USStocks;
    }

    // Expose other components if needed by IBAppMain or tests
    public IBConnectionManager getIbConnectionManager() {
        return ibConnectionManager;
    }

    public EClientSocket getClientSocket() {
        return clientSocket;
    }

    public EReaderSignal getReaderSignal() {
        return readerSignal;
    }

    public InstrumentRegistry getInstrumentRegistry() {
        return instrumentRegistry;
    }

    public TickAggregator getTickAggregator() {
        return tickAggregator;
    }

    public TickProcessor getTickProcessor() {
        return tickProcessor;
    }

    public IBOrderExecutor getIbOrderExecutor() {
        return ibOrderExecutor;
    }

    public PreviousDayData getPreviousDayData(String symbol) {
        return previousDayDataMap.get(symbol);
    }

    public boolean isMarketInOpeningWindow() {
        try {
            ZoneId marketTimeZone = ZoneId.of("America/New_York");
            ZonedDateTime marketTime = ZonedDateTime.now(marketTimeZone);
            DayOfWeek day = marketTime.getDayOfWeek();

            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                logger.trace("Market is closed on weekends. Not in opening window.");
                return false; // Market closed on weekends
            }

            LocalTime marketOpen = LocalTime.of(9, 30);
            LocalTime openingWindowEnd = LocalTime.of(10, 0); // First 30 minutes

            LocalTime currentTimeET = marketTime.toLocalTime();

            boolean isInWindow = !currentTimeET.isBefore(marketOpen) && currentTimeET.isBefore(openingWindowEnd);

            logger.debug("Current ET: {}, Market Open: {}, Window End: {}. IsInOpeningWindow: {}",
                        currentTimeET, marketOpen, openingWindowEnd, isInWindow);
            return isInWindow;

        } catch (Exception e) {
            logger.error("Error checking market opening window: {}", e.getMessage(), e);
            return false; // Default to false on error
        }
    }
    // Add other getters as necessary
}
