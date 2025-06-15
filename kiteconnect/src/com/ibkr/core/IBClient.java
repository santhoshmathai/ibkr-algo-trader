package com.ibkr.core;

import com.ib.client.*;
import com.ibkr.data.InstrumentRegistry;
import com.ibkr.data.TickAggregator;
import com.ibkr.strategy.TickProcessor;
import com.zerodhatech.models.Tick;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IBClient implements EWrapper {
    private final IBConnectionManager connectionManager;
    private final InstrumentRegistry instrumentRegistry;
    private final TickAggregator tickAggregator;
    private final TickProcessor tickProcessor;
    private EClientSocket clientSocket;
    private int nextOrderId = 0;
    private EReaderSignal signal;
    private ExecutorService executor;
    public IBClient() {
        this.instrumentRegistry = new InstrumentRegistry();
        this.tickAggregator = new TickAggregator(instrumentRegistry);
        this.tickProcessor = new TickProcessor();
        this.connectionManager = new IBConnectionManager(this);
        signal = new EJavaSignal();
        clientSocket = new EClientSocket(this, signal);
        executor = Executors.newSingleThreadExecutor();
    }

    public void connect(String host, int port, int clientId) {
        connectionManager.connect(host, port, clientId);
    }

    public void subscribeToSymbol(String symbol, String exchange) {
        Contract contract = createStockContract(symbol, exchange);
        int tickerId = instrumentRegistry.registerInstrument(contract);

        EClientSocket client = connectionManager.getClientSocket();
        client.reqMarketDataType(1); // Live data
        client.reqMktData(tickerId, contract, "", false, false, null);
        client.reqTickByTickData(tickerId, contract, "AllLast", 0, false);
        client.reqTickByTickData(tickerId, contract, "BidAsk", 0, false);
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
        final EReader reader = new EReader(clientSocket, signal);
        reader.start();

        // Use a separate thread to read messages
        executor.execute(() -> {
            System.out.println("execute thread start");

            while (clientSocket.isConnected()) {
                System.out.println("clientSocket.isConnected() is true");
                System.out.println("signal.waitForSignal() - before");

                signal.waitForSignal();
                System.out.println("signal.waitForSignal() - after");

                try {
                    System.out.println(" reader.processMsgs() - before");

                    reader.processMsgs();
                    System.out.println(" reader.processMsgs() - after");

                    System.out.println("processMsgs");
                } catch (Exception e) {
                    System.err.println("Error processing messages: " + e.getMessage() + e.getClass());
                }
            }
        });
    }

    @Override
    public void tickPrice(int tickerId, int field, double price, TickAttrib attribs) {
        System.err.printf("tickPrice - tickerId: %d | field: %d | price: %f%n", tickerId, field, price);
        tickAggregator.processTickPrice(tickerId, field, price);
        processCompleteTick(tickerId);
    }

    @Override
    public void tickSize(int tickerId, int field, int size) {
        System.out.printf("tickSize - tickerId: %d | field: %d | price: %d%n", tickerId, field, size);

        tickAggregator.processTickSize(tickerId, field, size);
        processCompleteTick(tickerId);
    }

    @Override
    public void tickByTickAllLast(int reqId, int tickType, long time, double price,
                                  int size, TickAttribLast tickAttribLast, String exchange, String specialConditions) {
        System.out.printf("tickByTickAllLast - reqId: %d | tickType: %d | time: %d | price : %f%n", reqId, tickType, time,  price);

        tickAggregator.processTickByTickAllLast(reqId, tickType, time, price, size);
        processCompleteTick(reqId);
    }

    @Override
    public void tickByTickBidAsk(int reqId, long time, double bidPrice, double askPrice,
                                 int bidSize, int askSize, TickAttribBidAsk tickAttribBidAsk) {
        System.out.printf("tickByTickBidAsk - reqId: %d | time: %d | bidPrice: %f | askPrice : %f | bidSize : %d | askSize: %d%n", reqId, time, bidPrice,  askPrice, bidSize, askSize);

        tickAggregator.processTickByTickBidAsk(reqId, bidPrice, askPrice, bidSize, askSize);
        processCompleteTick(reqId);
    }

    private void processCompleteTick(int tickerId) {
        Tick tick = tickAggregator.getTick(tickerId);
        if (tick != null) {
            tickProcessor.process(tick);
        }
    }

    // Implement all other required EWrapper methods...
    @Override public void error(int id, int errorCode, String errorMsg) {
        System.err.printf("Error - ID: %d | Code: %d | Msg: %s%n", id, errorCode, errorMsg);
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
    public void orderStatus(int i, String s, double v, double v1, double v2, int i1, int i2, double v3, int i3, String s1, double v4) {

    }

    @Override
    public void openOrder(int i, Contract contract, Order order, OrderState orderState) {

    }

    @Override
    public void openOrderEnd() {

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
    public void nextValidId(int i) {

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
    public void execDetails(int i, Contract contract, Execution execution) {

    }

    @Override
    public void execDetailsEnd(int i) {

    }

    @Override
    public void updateMktDepth(int i, int i1, int i2, int i3, double v, int i4) {

    }

    @Override
    public void updateMktDepthL2(int i, int i1, String s, int i2, int i3, double v, int i4, boolean b) {

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
    public void historicalData(int i, Bar bar) {

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
    public void realtimeBar(int i, long l, double v, double v1, double v2, double v3, long l1, double v4, int i1) {

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
    public void error(Exception e) {

    }

    @Override
    public void error(String s) {

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

    }

    @Override
    public void connectAck() {

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