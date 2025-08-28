package com.ibkr;

import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReaderSignal;
import com.ibkr.analysis.MarketSentimentAnalyzer;
import com.ibkr.analysis.SectorStrengthAnalyzer;
import com.ibkr.analysis.SupportResistanceAnalyzer;
import com.ibkr.core.TradingEngine;
import com.ibkr.data.InstrumentRegistry;
import com.ibkr.data.TickAggregator;
import com.ibkr.liquidity.DarkPoolScanner;
import com.ibkr.risk.LiquidityMonitor;
import com.ibkr.safeguards.CircuitBreakerMonitor;
import com.ibkr.screener.StockScreener;
import com.ibkr.service.BacktestMarketDataService;
import com.ibkr.service.IbkrMarketDataService;
import com.ibkr.service.IbkrOrderService;
import com.ibkr.service.MarketDataService;
import com.ibkr.service.OrderService;
import com.ibkr.strategy.TickProcessor;
import com.ibkr.data.MarketDataHandler;
import com.ibkr.models.PortfolioManager;
import com.ibkr.models.PreviousDayData;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
    private final EClientSocket clientSocket;
    private final EReaderSignal readerSignal;
    private final IBOrderExecutor ibOrderExecutor;
    private final MarketDataService marketDataService;
    private final MarketDataHandler marketDataHandler;
    private final TradingEngine tradingEngine;
    private final TickProcessor tickProcessor;
    private final StockScreener stockScreener;
    private final PortfolioManager portfolioManager;

    // Analyzers needed by components other than the old TradingEngine logic
    private final MarketSentimentAnalyzer marketSentimentAnalyzer;
    private final SupportResistanceAnalyzer supportResistanceAnalyzer;
    private final SectorStrengthAnalyzer sectorStrengthAnalyzer;
    private final com.ibkr.indicators.VWAPAnalyzer vwapAnalyzer;
    private final com.ibkr.indicators.VolumeAnalyzer volumeAnalyzer;
    private final com.ibkr.risk.RiskManager riskManager;
    private final com.ibkr.signal.BreakoutSignalGenerator breakoutSignalGenerator;


    // Liquidity and Safeguards that are still potentially useful or needed by IBOrderExecutor
    private final CircuitBreakerMonitor circuitBreakerMonitor;
    private final DarkPoolScanner darkPoolScanner;
    private final LiquidityMonitor liquidityMonitor;


    private final Set<String> top100USStocks;
    private final Map<String, PreviousDayData> previousDayDataMap = new ConcurrentHashMap<>();
    private final Map<Integer, String> historicalDataReqIdToSymbol = new ConcurrentHashMap<>();
    private final Map<String, List<String>> sectorToStocks = new ConcurrentHashMap<>();
    private final Map<String, String> symbolToSector = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    // TWS Connection Parameters
    private final String twsHost;
    private final int twsPort;
    private final int twsClientId;

    public AppContext() {
        this(false);
    }

    public AppContext(boolean isBacktest) {
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

        loadSectorData(properties);
        this.top100USStocks = loadTop100USStocks(properties);

        // --- Component Initialization ---

        // Level 0: Components with no internal dependencies
        this.meterRegistry = new SimpleMeterRegistry();
        this.instrumentRegistry = new InstrumentRegistry(this);
        this.marketDataHandler = new MarketDataHandler();
        this.tickAggregator = new TickAggregator(this.instrumentRegistry);
        this.supportResistanceAnalyzer = new SupportResistanceAnalyzer(this);
        this.marketSentimentAnalyzer = new MarketSentimentAnalyzer(this, this.top100USStocks, openingObservationMinutes, LocalTime.of(9, 30));
        this.circuitBreakerMonitor = new CircuitBreakerMonitor();
        this.darkPoolScanner = new DarkPoolScanner();
        this.liquidityMonitor = new LiquidityMonitor();
        this.vwapAnalyzer = new com.ibkr.indicators.VWAPAnalyzer();
        this.volumeAnalyzer = new com.ibkr.indicators.VolumeAnalyzer();
        this.sectorStrengthAnalyzer = new SectorStrengthAnalyzer(getSectorToStocks(), getSymbolToSector());

        // Level 1: Components that depend on Level 0
        this.riskManager = new com.ibkr.risk.RiskManager(this.liquidityMonitor, this.vwapAnalyzer);
        this.breakoutSignalGenerator = new com.ibkr.signal.BreakoutSignalGenerator(this.vwapAnalyzer, this.volumeAnalyzer, this.sectorStrengthAnalyzer, this.supportResistanceAnalyzer);
        this.portfolioManager = new PortfolioManager();
        this.ibOrderExecutor = new IBOrderExecutor(this.circuitBreakerMonitor, this.darkPoolScanner, this.instrumentRegistry, this.portfolioManager);
        OrderService orderService = new IbkrOrderService(this.ibOrderExecutor);

        if (!isBacktest) {
            // Level 2: Create services
            this.marketDataService = new BacktestMarketDataService("stockdata/MW-NIFTY-500-01-Nov-2024.csv");

            this.tradingEngine = new TradingEngine(this, orderService, this.marketDataService, portfolioManager, this.sectorStrengthAnalyzer);

            // TickProcessor is now simplified, it just needs to know about the engine to pass it bars.
            this.tickProcessor = new TickProcessor(this.breakoutSignalGenerator, this.riskManager, this.tradingEngine, orderService, this);
            // Now set the tick processor in the market data service
//            ((IbkrMarketDataService)this.marketDataService).setTickProcessor(this.tickProcessor);


            this.tickAggregator.setTradingEngine(this.tradingEngine);
            this.stockScreener = new StockScreener(this.marketDataService);


            // Level 3: Components that depend on IbkrMarketDataService
            this.clientSocket = null;
            this.readerSignal = null;
            this.ibOrderExecutor.setClientSocket(this.clientSocket);

            // Final Step: Initialize TradingEngine services that required the IbkrMarketDataService, breaking the circular dependency.
            this.tradingEngine.initializeServices(this.marketDataService, this.stockScreener);

            // Schedule tasks only in live mode
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

        } else {
            this.marketDataService = null;
            this.tradingEngine = null;
            this.tickProcessor = null;
            this.stockScreener = null;
            this.clientSocket = null;
            this.readerSignal = null;
        }

        logger.info("AppContext initialized successfully.");
    }

    private Set<String> loadTop100USStocks(Properties properties) {
        String stocksProperty = properties.getProperty("us.stocks.top100", "");
        return new HashSet<>(Arrays.asList(stocksProperty.split(",")));
    }

    private void loadSectorData(Properties properties) {
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith("sector.") && propertyName.endsWith(".stocks")) {
                String sectorName = propertyName.substring("sector.".length(), propertyName.length() - ".stocks".length());
                String[] stocks = properties.getProperty(propertyName).split(",");
                List<String> stockList = new ArrayList<>();
                for (String stock : stocks) {
                    String trimmedStock = stock.trim();
                    stockList.add(trimmedStock);
                    symbolToSector.put(trimmedStock, sectorName);
                }
                sectorToStocks.put(sectorName, stockList);
            }
        }
    }

    // --- Getters for major components ---
    public MarketDataService getMarketDataService() { return marketDataService; }
    public Set<String> getTop100USStocks() { return top100USStocks; }
    public InstrumentRegistry getInstrumentRegistry() { return instrumentRegistry; }
    public TickAggregator getTickAggregator() { return tickAggregator; }
    public TickProcessor getTickProcessor() { return tickProcessor; }
    public IBOrderExecutor getIbOrderExecutor() { return ibOrderExecutor; }
    public TradingEngine getTradingEngine() { return tradingEngine; }
    public SupportResistanceAnalyzer getSupportResistanceAnalyzer() { return supportResistanceAnalyzer; }
    public MarketSentimentAnalyzer getMarketSentimentAnalyzer() { return marketSentimentAnalyzer; }
    public StockScreener getStockScreener() { return stockScreener; }
    public PortfolioManager getPortfolioManager() { return portfolioManager; }
    public Map<String, List<String>> getSectorToStocks() { return sectorToStocks; }
    public Map<String, String> getSymbolToSector() { return symbolToSector; }
    public MeterRegistry getMeterRegistry() { return meterRegistry; }

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

    public Map<String, PreviousDayData> getPreviousDayDataMap() {
        return previousDayDataMap;
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
        if (this.marketDataService instanceof IbkrMarketDataService) {
            ((IbkrMarketDataService) this.marketDataService).shutdown();
        }
        if (this.marketDataHandler != null) {
            this.marketDataHandler.shutdown();
        }
        logger.info("AppContext shutdown process completed.");
    }
}
