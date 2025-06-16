package com.ibkr.core;

import com.ib.client.*;
import com.ib.client.OrderState; // Added for openOrder
import com.ib.client.Execution;
import com.ibkr.IBOrderExecutor;
import com.ibkr.data.InstrumentRegistry;
import com.ibkr.data.MarketDataHandler;
import com.ibkr.models.PreviousDayData; // Added
import com.ibkr.data.TickAggregator;
import com.ibkr.strategy.TickProcessor;
import com.zerodhatech.models.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList; // Added for TagValue in reqMktDepth
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap; // Added
import java.util.concurrent.CountDownLatch; // Added
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit; // Added
import java.time.LocalDateTime; // Added
import java.time.format.DateTimeFormatter; // Added

public class IBClient implements EWrapper {
    private static final Logger logger = LoggerFactory.getLogger(IBClient.class);
    private IBConnectionManager connectionManager;
    private final InstrumentRegistry instrumentRegistry;
    private final TickAggregator tickAggregator;
    private final TickProcessor tickProcessor;
    private final IBOrderExecutor orderExecutor;
    private final EReaderSignal readerSignal = new EJavaSignal();
    private final EClientSocket clientSocket = new EClientSocket(this, readerSignal);
    private final ExecutorService executor;
    private MarketDataHandler marketDataHandler;

    // For historical data fetching
    private final Map<Integer, PreviousDayData> historicalDataRequests = new ConcurrentHashMap<>();
    private final Map<String, PreviousDayData> successfullyFetchedPrevDayData = new ConcurrentHashMap<>();
    private CountDownLatch historicalDataLatch;
    private static final DateTimeFormatter IB_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss");


    public IBClient(InstrumentRegistry instrumentRegistry, TickAggregator tickAggregator,
                    TickProcessor tickProcessor, IBOrderExecutor orderExecutor,
                    MarketDataHandler marketDataHandler) {
        this.instrumentRegistry = instrumentRegistry;
        this.tickAggregator = tickAggregator;
        this.tickProcessor = tickProcessor;
        this.orderExecutor = orderExecutor;
        this.marketDataHandler = marketDataHandler;
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void setConnectionManager(IBConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public EClientSocket getClientSocket() { // Added getter
        return this.clientSocket;
    }

    public EReaderSignal getReaderSignal() { // Added getter
        return this.readerSignal;
    }

    public void connect(String host, int port, int clientId) {
        if (this.connectionManager == null) {
            logger.error("IBConnectionManager is not set. Cannot connect.");
            return;
        }
        // Assuming IBConnectionManager's connect method uses the EClientSocket it holds.
        this.connectionManager.connect(host, port, clientId);
    }

    public void subscribeToSymbol(String symbol, String exchange) {
        Contract contract = createStockContract(symbol, exchange);
        int tickerId = instrumentRegistry.registerInstrument(contract);

        // Use the internal clientSocket directly
        this.clientSocket.reqMarketDataType(1); // Live data
        logger.info("Requesting MktData for {}({})", symbol, tickerId);
        this.clientSocket.reqMktData(tickerId, contract, "", false, false, null);
        logger.info("Requesting TickByTick AllLast for {}({})", symbol, tickerId);
        this.clientSocket.reqTickByTickData(tickerId, contract, "AllLast", 0, false);
        logger.info("Requesting TickByTick BidAsk for {}({})", symbol, tickerId);
        this.clientSocket.reqTickByTickData(tickerId, contract, "BidAsk", 0, false);

        // Request 5-second real-time bars
        logger.info("Requesting 5-second RealTimeBars for {}({})", symbol, tickerId);
        this.clientSocket.reqRealTimeBars(tickerId, contract, 5, "TRADES", true, new ArrayList<TagValue>());

        // Request Market Depth
        int numRows = 20; // Number of depth lines
        boolean isSmartDepth = true; // For consolidated depth
        logger.info("Requesting Market Depth for {}({}), NumRows: {}, IsSmart: {}", symbol, tickerId, numRows, isSmartDepth);
        this.clientSocket.reqMktDepth(tickerId, contract, numRows, isSmartDepth, new ArrayList<TagValue>());
    }

    private Contract createStockContract(String symbol, String exchange) {
        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange(exchange);
        return contract;
    }

    public void startMessageProcessing() {
        // Start the EReader to process incoming messages
        // Uses internal clientSocket and readerSignal
        final EReader reader = new EReader(this.clientSocket, this.readerSignal);
        reader.start();

        // Use a separate thread to read messages
        executor.execute(() -> {
            logger.info("Message processing thread started.");

            while (this.clientSocket.isConnected()) {
                logger.debug("Client socket connected, waiting for signal.");
                this.readerSignal.waitForSignal();
                logger.debug("Signal received.");

                try {
                    logger.debug("Processing messages from EReader.");
                    reader.processMsgs();
                    logger.debug("Finished processing messages.");
                } catch (Exception e) {
                    logger.error("Error processing messages: {} - {}", e.getMessage(), e.getClass(), e);
                }
            }
            logger.info("Message processing thread stopped as client socket is disconnected.");
        });
    }

    @Override
    public void tickPrice(int tickerId, int field, double price, TickAttrib attribs) {
        logger.debug("tickPrice - tickerId: {} | field: {} | price: {}", tickerId, field, price);
        tickAggregator.processTickPrice(tickerId, field, price);
        processCompleteTick(tickerId);
    }

    @Override
    public void tickSize(int tickerId, int field, int size) {
        logger.debug("tickSize - tickerId: {} | field: {} | size: {}", tickerId, field, size);
        tickAggregator.processTickSize(tickerId, field, size);
        processCompleteTick(tickerId);
    }

    @Override
    public void tickByTickAllLast(int reqId, int tickType, long time, double price,
                                  int size, TickAttribLast tickAttribLast, String exchange, String specialConditions) {
        logger.debug("tickByTickAllLast - reqId: {} | tickType: {} | time: {} | price: {} | size: {}", reqId, tickType, time, price, size);
        tickAggregator.processTickByTickAllLast(reqId, tickType, time, price, size);
        processCompleteTick(reqId);
    }

    @Override
    public void tickByTickBidAsk(int reqId, long time, double bidPrice, double askPrice,
                                 int bidSize, int askSize, TickAttribBidAsk tickAttribBidAsk) {
        logger.debug("tickByTickBidAsk - reqId: {} | time: {} | bidPrice: {} | askPrice: {} | bidSize: {} | askSize: {}",
                reqId, time, bidPrice, askPrice, bidSize, askSize);
        tickAggregator.processTickByTickBidAsk(reqId, bidPrice, askPrice, bidSize, askSize);
        processCompleteTick(reqId);
    }

    private void processCompleteTick(int tickerId) {
        Tick tick = tickAggregator.getTick(tickerId);
        if (tick != null) {
            if (this.marketDataHandler != null) {
                this.marketDataHandler.persistTick(tick); // Added call
            } else {
                logger.warn("MarketDataHandler is null in IBClient. Cannot persist tick for tickerId: {}", tickerId);
            }
            tickProcessor.process(tick);
        }
    }

    // Implement all other required EWrapper methods...
    @Override public void error(int id, int errorCode, String errorMsg) {
        String enhancedErrorMsg = errorMsg;
        // Connectivity error codes from https://interactivebrokers.github.io/tws-api/message_codes.html
        // 502: Couldn't connect to TWS.
        // 504: Not connected to TWS.
        // 2103: Market data farm connection is broken.
        // 2104: Market data farm connection is OK.
        // 2105: HMDS data farm connection is broken.
        // 2106: HMDS data farm connection is OK.
        // 2107: Historical data farm connection is broken.
        // 2108: Historical data farm connection is OK.
        // 2157: Historic data farm is disconnected.
        // 2158: Historical data farm is connected but data is not available.
        // 2159: Historical market data Service error message.
        // 1100: Connectivity between IB and Trader Workstation has been lost.
        // 1101: Connectivity between IB and Trader Workstation has been restored.
        // 1102: Connectivity between IB and Trader Workstation has been lost.
        // 1300: Socket port has been reset as port conflicts with other applications.
        // Other groups: 300-399 (Order Errors), 10000+ (API System Msgs)

        List<Integer> connectivityErrorCodes = List.of(502, 504, 2103, 2105, 2107, 2157, 1100, 1102, 1300);
        List<Integer> connectivityRestoredCodes = List.of(2104, 2106, 2108, 2158, 1101);

        if (connectivityErrorCodes.contains(errorCode)) {
            enhancedErrorMsg = "Connectivity Issue: " + errorMsg;
            logger.error("TWS API Connectivity Error - ID: {}, Code: {}, Msg: {}", id, errorCode, enhancedErrorMsg);
            // Potentially trigger reconnection logic or update application's connection status here
        } else if (connectivityRestoredCodes.contains(errorCode)) {
            enhancedErrorMsg = "Connectivity Restored: " + errorMsg;
            logger.info("TWS API Connectivity Restored - ID: {}, Code: {}, Msg: {}", id, errorCode, enhancedErrorMsg);
        } else {
            logger.error("TWS API Error - ID: {}, Code: {}, Msg: {}", id, errorCode, errorMsg);
        }
    }

    // Overriding error(Exception e) and error(String s) to also use logger
    @Override
    public void error(Exception e) {
        logger.error("Error: {}", e.getMessage(), e);
    }

    @Override
    public void error(String str) {
        logger.error("Error: {}", str);
    }

    @Override
    public void tickOptionComputation(int i, int i1, double v, double v1, double v2, double v3, double v4, double v5, double v6, double v7) {

    }

    @Override
    public void tickGeneric(int i, int i1, double v) {

    }

    @Override
    public void tickString(int i, int i1, String s) {

    }

    @Override
    public void tickEFP(int i, int i1, double v, String s, double v1, int i2, String s1, double v2, double v3) {

    }

    @Override
    public void orderStatus(int orderId, String status, double filled, double remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
        logger.info("OrderStatus - OrderId: {}, Status: {}, Filled: {}, Remaining: {}, AvgFillPrice: {}, PermId: {}, ParentId: {}, LastFillPrice: {}, ClientId: {}, WhyHeld: {}, MktCapPrice: {}",
                orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld, mktCapPrice);
        if (orderExecutor != null) {
            // Pass relevant fields to IBOrderExecutor
            orderExecutor.updateOrderStatus(orderId, status, filled, remaining, avgFillPrice, lastFillPrice);
        } else {
            logger.warn("orderExecutor is null. Cannot update order status for OrderId: {}", orderId);
        }
    }

    @Override
    public void openOrder(int orderId, Contract contract, com.ib.client.Order ibOrder, com.ib.client.OrderState orderState) {
        logger.info("OpenOrder - OrderId: {}, Symbol: {}, Action: {}, Type: {}, Status: {}, LmtPrice: {}, AuxPrice: {}",
                orderId, contract.symbol(), ibOrder.action(), ibOrder.orderType(), orderState.getStatus(), ibOrder.lmtPrice(), ibOrder.auxPrice());
        if (orderExecutor != null) {
            orderExecutor.handleOpenOrder(orderId, contract, ibOrder, orderState);
        } else {
            logger.warn("orderExecutor is null. Cannot handle open order for OrderId: {}", orderId);
        }
    }

    @Override
    public void openOrderEnd() {
        logger.info("OpenOrderEnd received.");
        if (orderExecutor != null) {
            orderExecutor.handleOpenOrderEnd();
        } else {
            logger.warn("orderExecutor is null. Cannot handle openOrderEnd.");
        }
    }

    @Override
    public void updateAccountValue(String s, String s1, String s2, String s3) {

    }

    @Override
    public void updatePortfolio(Contract contract, double v, double v1, double v2, double v3, double v4, double v5, String s) {

    }

    @Override
    public void updateAccountTime(String s) {

    }

    @Override
    public void accountDownloadEnd(String s) {

    }

    @Override
    public void nextValidId(int orderId) {
        // Delegate to IBOrderExecutor
        if (orderExecutor != null) {
            orderExecutor.setNextValidOrderId(orderId);
        }
    }

    @Override
    public void contractDetails(int i, ContractDetails contractDetails) {

    }

    @Override
    public void bondContractDetails(int i, ContractDetails contractDetails) {

    }

    @Override
    public void contractDetailsEnd(int i) {

    }

    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {
        logger.info("ExecDetails - ReqId: {}, OrderId: {}, Symbol: {}, Side: {}, Shares: {}, Price: {}, Time: {}, ExecId: {}",
                reqId, execution.orderId(), contract.symbol(), execution.side(), execution.shares(), execution.price(), execution.time(), execution.execId());
        if (orderExecutor != null) {
            orderExecutor.handleExecutionDetails(reqId, contract, execution);
        } else {
            logger.warn("orderExecutor is null. Cannot handle execution details for ReqId: {}", reqId);
        }
    }

    @Override
    public void execDetailsEnd(int reqId) {
        logger.info("ExecDetailsEnd received for ReqId: {}", reqId);
        if (orderExecutor != null) {
            orderExecutor.handleExecutionDetailsEnd(reqId);
        } else {
            logger.warn("orderExecutor is null. Cannot handle execDetailsEnd for ReqId: {}", reqId);
        }
    }

    @Override
    public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size) {
        logger.debug("updateMktDepth - TickerId: {}, Position: {}, Operation: {}, Side: {}, Price: {}, Size: {}",
                tickerId, position, operation, side, price, size);

        if (tickAggregator != null) {
            tickAggregator.processMarketDepth(tickerId, position, operation, side, price, size);
            processCompleteTick(tickerId); // Notify TickProcessor after depth update
        } else {
            logger.warn("TickAggregator is null. Cannot process market depth for TickerId: {}", tickerId);
        }
    }

    @Override
    public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side, double price, int size, boolean isSmartDepth) {
        logger.debug("updateMktDepthL2 - TickerId: {}, Position: {}, MarketMaker: {}, Operation: {}, Side: {}, Price: {}, Size: {}, IsSmartDepth: {}",
                tickerId, position, marketMaker, operation, side, price, size, isSmartDepth);
        // For now, we can delegate to the same simplified processor if the structure is similar enough,
        // or implement a more specific L2 processor in TickAggregator later.
        // Choosing to call the same one for now, as it handles the core components (pos, op, side, price, size).
        if (tickAggregator != null) {
            tickAggregator.processMarketDepth(tickerId, position, operation, side, price, size); // Using existing L1 processor
            processCompleteTick(tickerId);
        } else {
            logger.warn("TickAggregator is null. Cannot process L2 market depth for TickerId: {}", tickerId);
        }
    }

    @Override
    public void updateNewsBulletin(int i, int i1, String s, String s1) {

    }

    @Override
    public void managedAccounts(String s) {

    }

    @Override
    public void receiveFA(int i, String s) {

    }

    @Override
    public void historicalData(int reqId, Bar bar) {
        logger.debug("HistoricalData - ReqId: {}, Date: {}, O: {}, H: {}, L: {}, C: {}, V: {}, WAP: {}",
                reqId, bar.time(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume(), bar.wap());

        PreviousDayData data = historicalDataRequests.get(reqId);
        if (data != null) {
            data.setPreviousHigh(bar.high());
            data.setPreviousClose(bar.close());
            // Assuming only one bar is expected for "1 D" duration
            successfullyFetchedPrevDayData.put(data.getSymbol(), data);
            // No need to remove from historicalDataRequests immediately, historicalDataEnd will handle latch
        } else {
            logger.warn("Received historical data for unknown reqId: {}", reqId);
        }
    }

    @Override
    public void historicalDataEnd(int reqId, String startDateStr, String endDateStr) {
        logger.info("HistoricalDataEnd - ReqId: {}, StartDate: {}, EndDate: {}", reqId, startDateStr, endDateStr);
        PreviousDayData data = historicalDataRequests.get(reqId);
        if (data != null) {
            if (!successfullyFetchedPrevDayData.containsKey(data.getSymbol())) {
                // This means historicalData callback was not called, or no bar was returned.
                logger.warn("HistoricalDataEnd received for reqId: {}, but no bar data was processed for symbol: {}. Storing with 0 values.", reqId, data.getSymbol());
                // Store it with 0s to mark it as processed and prevent latch timeout if this is acceptable.
                // Or handle as an error. For now, storing with 0s.
                successfullyFetchedPrevDayData.put(data.getSymbol(), data);
            }
        } else {
             logger.warn("HistoricalDataEnd received for unknown reqId: {}", reqId);
        }
        if (historicalDataLatch != null) {
            historicalDataLatch.countDown();
            logger.debug("Historical data latch countDown. Remaining: {}", historicalDataLatch.getCount());
        }
    }


    public Map<String, PreviousDayData> fetchPreviousDayDataForAllStocks(Set<String> symbols) throws InterruptedException {
        if (symbols == null || symbols.isEmpty()) {
            logger.warn("Symbol list is empty. Cannot fetch previous day data.");
            return new ConcurrentHashMap<>(); // Return empty map
        }
        if (!clientSocket.isConnected()) {
            logger.error("Cannot fetch historical data. IBClient not connected.");
            return new ConcurrentHashMap<>();
        }

        logger.info("Requesting previous day OHLC for {} symbols.", symbols.size());
        successfullyFetchedPrevDayData.clear();
        historicalDataRequests.clear();
        historicalDataLatch = new CountDownLatch(symbols.size());

        // Using an empty string for endDateTime. TWS provides data for the previous trading day.
        String endDateTime = "";
        String durationStr = "1 D"; // 1 day
        String barSizeSetting = "1 day";
        String whatToShow = "TRADES";
        int useRTH = 1; // Regular trading hours only
        int formatDate = 1; // yyyyMMdd HH:mm:ss

        // Use a separate request ID sequence for historical data to avoid collision with market data subscriptions
        // For simplicity, reusing instrumentRegistry for now, but this is not ideal.
        // A dedicated AtomicInteger for historicalReqId would be better.
        // int nextHistReqId = instrumentRegistry.getNextReqId(); // Assuming such a method or manage IDs differently

        int currentHistReqId = 50000; // Starting base for historical data reqIds to avoid collision with mkt data

        for (String symbol : symbols) {
            Contract contract = createStockContract(symbol, "SMART"); // Assuming SMART for all US stocks
            // int reqId = instrumentRegistry.registerInstrument(contract, true); // Need a way to get unique reqId
            int reqId = currentHistReqId++;

            PreviousDayData placeholder = new PreviousDayData(symbol, 0, 0);
            historicalDataRequests.put(reqId, placeholder);

            logger.debug("Requesting historical data for {}: ReqId={}, Contract={}, EndDT={}, Duration={}, BarSize={}, WhatToShow={}, UseRTH={}, Format={}",
                    symbol, reqId, contract.symbol(), endDateTime, durationStr, barSizeSetting, whatToShow, useRTH, formatDate);
            clientSocket.reqHistoricalData(reqId, contract, endDateTime, durationStr, barSizeSetting, whatToShow, useRTH, formatDate, false, null);
        }

        logger.info("All historical data requests dispatched. Waiting for responses...");
        boolean allDataReceived = historicalDataLatch.await(60, TimeUnit.SECONDS); // Timeout after 60 seconds

        if (!allDataReceived) {
            logger.warn("Timed out waiting for all historical data. Received {} of {} responses.",
                        successfullyFetchedPrevDayData.size(), symbols.size());
        } else {
            logger.info("Successfully received all historical data responses (or marked as complete).");
        }

        // Clean up requests map for symbols that might not have triggered historicalDataEnd (e.g., due to errors not caught by latch)
        // This ensures that if a symbol had an error and did not call countDown, we don't wait indefinitely if await timed out.
        // The successfullyFetchedPrevDayData map is the source of truth.
        historicalDataRequests.clear();

        return new ConcurrentHashMap<>(successfullyFetchedPrevDayData); // Return a copy
    }

    @Override
    public void scannerParameters(String s) {

    }

    @Override
    public void scannerData(int i, int i1, ContractDetails contractDetails, String s, String s1, String s2, String s3) {

    }

    @Override
    public void scannerDataEnd(int i) {

    }

    @Override
    public void realtimeBar(int reqId, long time, double open, double high, double low, double close, long volume, double wap, int count) {
        logger.debug("RealTimeBar - ReqId: {}, Time: {}, O: {}, H: {}, L: {}, C: {}, V: {}, WAP: {}, Count: {}",
                reqId, time, open, high, low, close, volume, wap, count);

        if (tickAggregator != null) {
            tickAggregator.processRealTimeBar(reqId, time, open, high, low, close, volume, wap, count);
            processCompleteTick(reqId); // Notify TickProcessor after bar update
        } else {
            logger.warn("TickAggregator is null. Cannot process realTimeBar for ReqId: {}", reqId);
        }
    }

    @Override
    public void currentTime(long l) {

    }

    @Override
    public void fundamentalData(int i, String s) {

    }

    @Override
    public void deltaNeutralValidation(int i, DeltaNeutralContract deltaNeutralContract) {

    }

    @Override
    public void tickSnapshotEnd(int i) {

    }

    @Override
    public void marketDataType(int i, int i1) {

    }

    @Override
    public void commissionReport(CommissionReport commissionReport) {

    }

    @Override
    public void position(String s, Contract contract, double v, double v1) {

    }

    @Override
    public void positionEnd() {

    }

    @Override
    public void accountSummary(int i, String s, String s1, String s2, String s3) {

    }

    @Override
    public void accountSummaryEnd(int i) {

    }

    @Override
    public void verifyMessageAPI(String s) {

    }

    @Override
    public void verifyCompleted(boolean b, String s) {

    }

    @Override
    public void verifyAndAuthMessageAPI(String s, String s1) {

    }

    @Override
    public void verifyAndAuthCompleted(boolean b, String s) {

    }

    @Override
    public void displayGroupList(int i, String s) {

    }

    @Override
    public void displayGroupUpdated(int i, String s) {

    }

    @Override
    public void tickByTickMidPoint(int i, long l, double v) {

    }

    @Override
    public void orderBound(long l, int i, int i1) {

    }

    @Override
    public void completedOrder(Contract contract, Order order, OrderState orderState) {

    }

    @Override
    public void completedOrdersEnd() {

    }



    @Override
    public void connectionClosed() {
        logger.info("Connection closed.");
    }

    @Override
    public void connectAck() {
        logger.info("Connect ACK received.");
        // This is a good place to confirm connection success if further actions depend on it.
    }

    @Override
    public void positionMulti(int i, String s, String s1, Contract contract, double v, double v1) {

    }

    @Override
    public void positionMultiEnd(int i) {

    }

    @Override
    public void accountUpdateMulti(int i, String s, String s1, String s2, String s3, String s4) {

    }

    @Override
    public void accountUpdateMultiEnd(int i) {

    }

    @Override
    public void securityDefinitionOptionalParameter(int i, String s, int i1, String s1, String s2, Set<String> set, Set<Double> set1) {

    }

    @Override
    public void securityDefinitionOptionalParameterEnd(int i) {

    }

    @Override
    public void softDollarTiers(int i, SoftDollarTier[] softDollarTiers) {

    }

    @Override
    public void familyCodes(FamilyCode[] familyCodes) {

    }

    @Override
    public void symbolSamples(int i, ContractDescription[] contractDescriptions) {

    }

    @Override
    public void historicalDataEnd(int i, String s, String s1) {

    }

    @Override
    public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {

    }

    @Override
    public void tickNews(int i, long l, String s, String s1, String s2, String s3) {

    }

    @Override
    public void smartComponents(int i, Map<Integer, Map.Entry<String, Character>> map) {

    }

    @Override
    public void tickReqParams(int i, double v, String s, int i1) {

    }

    @Override
    public void newsProviders(NewsProvider[] newsProviders) {

    }

    @Override
    public void newsArticle(int i, int i1, String s) {

    }

    @Override
    public void historicalNews(int i, String s, String s1, String s2, String s3) {

    }

    @Override
    public void historicalNewsEnd(int i, boolean b) {

    }

    @Override
    public void headTimestamp(int i, String s) {

    }

    @Override
    public void histogramData(int i, List<HistogramEntry> list) {

    }

    @Override
    public void historicalDataUpdate(int i, Bar bar) {

    }

    @Override
    public void rerouteMktDataReq(int i, int i1, String s) {

    }

    @Override
    public void rerouteMktDepthReq(int i, int i1, String s) {

    }

    @Override
    public void marketRule(int i, PriceIncrement[] priceIncrements) {

    }

    @Override
    public void pnl(int i, double v, double v1, double v2) {

    }

    @Override
    public void pnlSingle(int i, int i1, double v, double v1, double v2, double v3) {

    }

    @Override
    public void historicalTicks(int i, List<HistoricalTick> list, boolean b) {

    }

    @Override
    public void historicalTicksBidAsk(int i, List<HistoricalTickBidAsk> list, boolean b) {

    }

    @Override
    public void historicalTicksLast(int i, List<HistoricalTickLast> list, boolean b) {

    }

    // ... other EWrapper methods
}