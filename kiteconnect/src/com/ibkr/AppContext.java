package com.ibkr;

import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReaderSignal;
import com.ibkr.analysis.MarketSentimentAnalyzer;
import com.ibkr.analysis.SectorStrengthAnalyzer;
import com.ibkr.analysis.SupportResistanceAnalyzer; // Added import
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
import java.util.concurrent.atomic.AtomicInteger;


public class AppContext {
    private static final Logger logger = LoggerFactory.getLogger(AppContext.class);
    private final AtomicInteger nextRequestId = new AtomicInteger(1001);

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
    private final SupportResistanceAnalyzer supportResistanceAnalyzer; // Added field
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
    private Map<String, PreviousDayData> previousDayDataMap = new ConcurrentHashMap<>(); // Initialized

    // TWS Connection Parameters
    private final String twsHost;
    private final int twsPort;
    private final int twsClientId;

    // Market Sentiment Analyzer Config
    private final int openingObservationMinutes;

    public AppContext() {
        // Configuration Data
        Properties properties = new Properties();
        String propertiesFileName = "app.properties";
        try (InputStream inputStream = new FileInputStream(propertiesFileName)) {
            properties.load(inputStream);
        } catch (IOException e) {
            logger.error("Failed to load {} in AppContext constructor. Error: {}. Using defaults for TWS config.", propertiesFileName, e.getMessage(), e);
            // Properties will be empty, defaults will be used.
        }

        this.twsHost = properties.getProperty("tws.host", "127.0.0.1");
        int tempPort;
        try {
            tempPort = Integer.parseInt(properties.getProperty("tws.port", "7496"));
        } catch (NumberFormatException e) {
            logger.error("Invalid format for 'tws.port' in app.properties. Using default 7496.", e);
            tempPort = 7496;
        }
        this.twsPort = tempPort;

        int tempClientId;
        try {
            tempClientId = Integer.parseInt(properties.getProperty("tws.clientId", "0"));
        } catch (NumberFormatException e) {
            logger.error("Invalid format for 'tws.clientId' in app.properties. Using default 0.", e);
            tempClientId = 0;
        }
        this.twsClientId = tempClientId;
        logger.info("Loaded TWS connection params: Host={}, Port={}, ClientId={}", this.twsHost, this.twsPort, this.twsClientId);

        int tempOpeningObservationMinutes;
        try {
            tempOpeningObservationMinutes = Integer.parseInt(properties.getProperty("market.opening.observation.minutes", "15"));
        } catch (NumberFormatException e) {
            logger.error("Invalid format for 'market.opening.observation.minutes' in app.properties. Using default 15.", e);
            tempOpeningObservationMinutes = 15;
        }
        this.openingObservationMinutes = tempOpeningObservationMinutes;
        logger.info("Loaded Market Opening Observation Minutes: {}", this.openingObservationMinutes);

        LocalTime actualMarketOpenTime = LocalTime.of(9, 30); // Default ET market open

        this.top100USStocks = loadTop100USStocks(properties); // Pass loaded properties
        Map<String, List<String>> sectorToStocksMap = loadSectorToStocksMap(properties); // Pass loaded properties
        Map<String, String> symbolToSectorMap = loadSymbolToSectorMap(sectorToStocksMap); // Pass the loaded map

        // Level 0: No dependencies or only external config
        this.instrumentRegistry = new InstrumentRegistry(this);
        this.marketSentimentAnalyzer = new MarketSentimentAnalyzer(
            this,
            this.top100USStocks,
            this.openingObservationMinutes,
            actualMarketOpenTime
        );
        this.supportResistanceAnalyzer = new SupportResistanceAnalyzer(this); // Added instantiation
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
            this, // Pass AppContext
            this.instrumentRegistry,
            this.tickAggregator,
            this.tickProcessor,
            this.ibOrderExecutor,
            this.marketDataHandler
        );

        // EClientSocket and EReaderSignal are created inside IBClient. Get them.
        this.clientSocket = this.ibClient.getClientSocket();
        this.readerSignal = this.ibClient.getReaderSignal(); // Assuming getReaderSignal() exists per plan

        // Now provide EClientSocket to IBOrderExecutor
        this.ibOrderExecutor.setClientSocket(this.clientSocket);

        // Create IBConnectionManager and provide it to IBClient
        this.ibConnectionManager = new IBConnectionManager(this.clientSocket);
        this.ibClient.setConnectionManager(this.ibConnectionManager);

        // Removed historical data fetching from here. It will be initiated from IBAppMain.
    }

    // Modified to accept Properties object
    private Set<String> loadTop100USStocks(Properties properties) {
        Set<String> stocks = new HashSet<>();
        String stocksProperty = properties.getProperty("us.stocks.top100");

        if (stocksProperty != null && !stocksProperty.trim().isEmpty()) {
                stocks.addAll(Arrays.asList(stocksProperty.split(",")));
                logger.info("Loaded {} stock symbols from app.properties (us.stocks.top100).", stocks.size());
            } else {
                logger.warn("'us.stocks.top100' property is missing or empty in app.properties. Using empty set.");
                // Optionally, return a default minimal set:
                // return Set.of("AAPL", "MSFT");
            }
        // Removed IOException catch as Properties object is passed in
        return stocks;
    }

    // Modified to accept Properties object
    private Map<String, List<String>> loadSectorToStocksMap(Properties properties) {
        Map<String, List<String>> sectorToStocksMap = new HashMap<>();
        logger.info("Loading sector to stocks map from app.properties");

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
                        logger.warn("Sector {} defined in app.properties but has no stocks listed.", sectorName.toUpperCase());
                        sectorToStocksMap.put(sectorName.toUpperCase(), Collections.emptyList());
                    }
                } else {
                    logger.warn("Sector {} has empty stock list in app.properties.", sectorName.toUpperCase());
                    sectorToStocksMap.put(sectorName.toUpperCase(), Collections.emptyList());
                }
            }
        }
        // Removed IOException catch as Properties object is passed in
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

    public int getNextRequestId() {
        return nextRequestId.getAndIncrement();
    }

    public String getTwsHost() { return twsHost; }
    public int getTwsPort() { return twsPort; }
    public int getTwsClientId() { return twsClientId; }

    public void setPreviousDayDataMap(Map<String, PreviousDayData> previousDayDataMap) {
        this.previousDayDataMap.clear(); // Clear old data
        if (previousDayDataMap != null) {
            this.previousDayDataMap.putAll(previousDayDataMap);
        }
        logger.info("AppContext.previousDayDataMap updated. Size: {}", this.previousDayDataMap.size());
    }

    public Map<String, PreviousDayData> getPreviousDayDataMap() { // Getter might be useful for IBClient
        return this.previousDayDataMap;
    }

    public void shutdown() {
        logger.info("Shutting down AppContext...");

        // Shutdown IBClient first to stop market data flow and order processing
        if (this.ibClient != null) {
            logger.info("Shutting down IBClient from AppContext...");
            this.ibClient.shutdown();
        } else {
            logger.warn("IBClient was null in AppContext during shutdown.");
        }

        // Shutdown MarketDataHandler to flush any pending writes
        if (this.marketDataHandler != null) {
            logger.info("Shutting down MarketDataHandler from AppContext...");
            this.marketDataHandler.shutdown();
        } else {
            logger.warn("MarketDataHandler was null in AppContext during shutdown.");
        }

        // Shutdown other resources if any were added that need it
        // e.g., other ExecutorServices managed by AppContext directly

        logger.info("AppContext shutdown process completed.");
    }
    // Add other getters as necessary
    public MarketSentimentAnalyzer getMarketSentimentAnalyzer() {
        return marketSentimentAnalyzer;
    }

    public SupportResistanceAnalyzer getSupportResistanceAnalyzer() {
        return supportResistanceAnalyzer;
    }
}
