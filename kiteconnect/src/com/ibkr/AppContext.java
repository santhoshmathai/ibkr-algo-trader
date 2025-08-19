package com.ibkr;

import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReaderSignal;
import com.ibkr.analysis.MarketSentimentAnalyzer;
import com.ibkr.analysis.SectorStrengthAnalyzer;
import com.ibkr.analysis.SupportResistanceAnalyzer;
import com.ibkr.core.IBClient;
import com.ibkr.core.IBConnectionManager;
import com.ibkr.core.TradingEngine;
import com.ibkr.data.InstrumentRegistry;
import com.ibkr.data.TickAggregator;
import com.ibkr.data.HistoricalDataService;
import com.ibkr.liquidity.DarkPoolScanner;
import com.ibkr.risk.LiquidityMonitor;
import com.ibkr.safeguards.CircuitBreakerMonitor;
import com.ibkr.screener.StockScreener;
import com.ibkr.strategy.TickProcessor;
import com.ibkr.data.MarketDataHandler;
import com.ibkr.models.PortfolioManager;
import com.ibkr.models.PreviousDayData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class AppContext {
    private static final Logger logger = LoggerFactory.getLogger(AppContext.class);
    private final AtomicInteger nextRequestId = new AtomicInteger(1001);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Core Components
    private final InstrumentRegistry instrumentRegistry;
    private final TickAggregator tickAggregator;
    private final IBConnectionManager ibConnectionManager;
    private final EClientSocket clientSocket;
    private final EReaderSignal readerSignal;
    private final IBOrderExecutor ibOrderExecutor;
    private final IBClient ibClient;
    private final MarketDataHandler marketDataHandler;
    private final TradingEngine tradingEngine;
    private final TickProcessor tickProcessor;
    private final HistoricalDataService historicalDataService;
    private final StockScreener stockScreener;
    private final PortfolioManager portfolioManager;

    // Analyzers needed by components other than the old TradingEngine logic
    private final MarketSentimentAnalyzer marketSentimentAnalyzer;
    private final SupportResistanceAnalyzer supportResistanceAnalyzer;

    // Liquidity and Safeguards that are still potentially useful or needed by IBOrderExecutor
    private final CircuitBreakerMonitor circuitBreakerMonitor;
    private final DarkPoolScanner darkPoolScanner;
    private final LiquidityMonitor liquidityMonitor;


    private final Set<String> top100USStocks;
    private final Map<String, PreviousDayData> previousDayDataMap = new ConcurrentHashMap<>();
    private final Map<Integer, String> historicalDataReqIdToSymbol = new ConcurrentHashMap<>();

    // TWS Connection Parameters
    private final String twsHost;
    private final int twsPort;
    private final int twsClientId;

    public AppContext() {
        Properties properties = new Properties();
        try (InputStream inputStream = new FileInputStream("app.properties")) {
            properties.load(inputStream);
        } catch (IOException e) {
            logger.error("Failed to load app.properties. Using defaults.", e);
        }

        this.twsHost = properties.getProperty("tws.host", "127.0.0.1");
        this.twsPort = Integer.parseInt(properties.getProperty("tws.port", "7497"));
        this.twsClientId = Integer.parseInt(properties.getProperty("tws.clientId", "0"));
        int openingObservationMinutes = Integer.parseInt(properties.getProperty("market.opening.observation.minutes", "15"));

        this.top100USStocks = loadTop100USStocks(properties);

        // --- Component Initialization ---

        // Level 0: Components with no internal dependencies
        this.instrumentRegistry = new InstrumentRegistry(this);
        this.marketDataHandler = new MarketDataHandler();
        this.tickAggregator = new TickAggregator(this.instrumentRegistry);
        this.supportResistanceAnalyzer = new SupportResistanceAnalyzer(this);
        this.marketSentimentAnalyzer = new MarketSentimentAnalyzer(this, this.top100USStocks, openingObservationMinutes, LocalTime.of(9, 30));
        this.circuitBreakerMonitor = new CircuitBreakerMonitor();
        this.darkPoolScanner = new DarkPoolScanner();
        this.liquidityMonitor = new LiquidityMonitor();

        // Level 1: Components that depend on Level 0
        this.portfolioManager = new PortfolioManager();
        this.ibOrderExecutor = new IBOrderExecutor(this.circuitBreakerMonitor, this.darkPoolScanner, this.instrumentRegistry, this.portfolioManager);
        this.tradingEngine = new TradingEngine(this, ibOrderExecutor, stockScreener, portfolioManager, historicalDataService);

        // TickProcessor is now simplified, it just needs to know about the engine to pass it bars.
        // The nulls are placeholders for obsolete dependencies (breakoutSignalGenerator, riskManager).
        this.tickProcessor = new TickProcessor(null, null, this.tradingEngine, this.ibOrderExecutor, this);

        // Level 2: IBClient depends on TickProcessor
        this.ibClient = new IBClient(this, this.instrumentRegistry, this.tickAggregator, this.tickProcessor, this.ibOrderExecutor, this.marketDataHandler);
        this.tickAggregator.setTradingEngine(this.tradingEngine);
        this.historicalDataService = new HistoricalDataService(this.ibClient, this.instrumentRegistry);
        this.stockScreener = new StockScreener(this.historicalDataService);


        // Level 3: Components that depend on IBClient
        this.clientSocket = this.ibClient.getClientSocket();
        this.readerSignal = this.ibClient.getReaderSignal();
        this.ibOrderExecutor.setClientSocket(this.clientSocket);
        this.ibConnectionManager = new IBConnectionManager(this.clientSocket);
        this.ibClient.setConnectionManager(this.ibConnectionManager);

        // Final Step: Initialize TradingEngine services that required the IBClient, breaking the circular dependency.
        this.tradingEngine.initializeServices(this.ibClient, this.instrumentRegistry);

        long orbTimeframeMinutes = tradingEngine.getOrbStrategyParameters().getOrbTimeframeMinutes();
        this.scheduler.schedule(() -> {
            try {
                tradingEngine.onOpeningRangeEnd();
            } catch (Exception e) {
                logger.error("Error executing scheduled task 'onOpeningRangeEnd'", e);
            }
        }, orbTimeframeMinutes, TimeUnit.MINUTES);

        // Schedule end-of-day task
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/New_York"));
        ZonedDateTime endOfDay = now.with(LocalTime.of(16, 0));
        if (now.isAfter(endOfDay)) {
            endOfDay = endOfDay.plusDays(1);
        }
        long delay = Duration.between(now, endOfDay).toMillis();
        this.scheduler.schedule(() -> {
            try {
                tradingEngine.closeAllPositions();
            } catch (Exception e) {
                logger.error("Error executing scheduled task 'closeAllPositions'", e);
            }
        }, delay, TimeUnit.MILLISECONDS);


        logger.info("AppContext initialized successfully with new refactored flow.");
    }

    private Set<String> loadTop100USStocks(Properties properties) {
        String stocksProperty = properties.getProperty("us.stocks.top100", "");
        return new HashSet<>(Arrays.asList(stocksProperty.split(",")));
    }

    // --- Getters for major components ---
    public IBClient getIbClient() { return ibClient; }
    public Set<String> getTop100USStocks() { return top100USStocks; }
    public IBConnectionManager getIbConnectionManager() { return ibConnectionManager; }
    public InstrumentRegistry getInstrumentRegistry() { return instrumentRegistry; }
    public TickAggregator getTickAggregator() { return tickAggregator; }
    public TickProcessor getTickProcessor() { return tickProcessor; }
    public IBOrderExecutor getIbOrderExecutor() { return ibOrderExecutor; }
    public TradingEngine getTradingEngine() { return tradingEngine; }
    public SupportResistanceAnalyzer getSupportResistanceAnalyzer() { return supportResistanceAnalyzer; }
    public MarketSentimentAnalyzer getMarketSentimentAnalyzer() { return marketSentimentAnalyzer; }
    public HistoricalDataService getHistoricalDataService() { return historicalDataService; }
    public StockScreener getStockScreener() { return stockScreener; }
    public PortfolioManager getPortfolioManager() { return portfolioManager; }

    // --- Other methods ---
    public int getNextRequestId() { return nextRequestId.getAndIncrement(); }
    public String getTwsHost() { return twsHost; }
    public int getTwsPort() { return twsPort; }
    public int getTwsClientId() { return twsClientId; }

    public PreviousDayData getPreviousDayData(String symbol) { return previousDayDataMap.get(symbol); }
    public void setPreviousDayDataMap(Map<String, PreviousDayData> dataMap) {
        this.previousDayDataMap.clear();
        if (dataMap != null) {
            this.previousDayDataMap.putAll(dataMap);
        }
    }

    public void registerHistoricalDataRequest(int reqId, String symbol) { historicalDataReqIdToSymbol.put(reqId, symbol); }
    public String getSymbolForHistoricalDataRequest(int reqId) { return historicalDataReqIdToSymbol.get(reqId); }
    public void removeHistoricalDataRequest(int reqId) { historicalDataReqIdToSymbol.remove(reqId); }

    public void updatePdhForSymbol(String symbol, PreviousDayData pdhData) {
        if (symbol == null || pdhData == null) return;
        previousDayDataMap.put(symbol, pdhData);
        // The old TradingEngine's initializeStrategyForSymbol is no longer needed here.
        // The new flow handles state reset differently (e.g., in runPreMarketScreening).
    }

    public double getAccountCapital() {
        // TODO: Replace with dynamic value from IB account updates.
        return 100000.00;
    }

    public void shutdown() {
        logger.info("Shutting down AppContext...");
        if (this.ibClient != null) {
            this.ibClient.shutdown();
        }
        if (this.marketDataHandler != null) {
            this.marketDataHandler.shutdown();
        }
        logger.info("AppContext shutdown process completed.");
    }
}
