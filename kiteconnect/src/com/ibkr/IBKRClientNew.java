package com.ibkr;

import com.ib.client.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IBKRClientNew implements EWrapper {
    private EClientSocket clientSocket;
    private int nextOrderId = 0;
    private EReaderSignal signal;
    private ExecutorService executor;

    public IBKRClientNew() {
        signal = new EJavaSignal();
        clientSocket = new EClientSocket(this, signal);
        executor = Executors.newSingleThreadExecutor();
    }

    public static void main(String[] args) {
        IBKRClientNew client = new IBKRClientNew();
        client.connect("127.0.0.1", 7496, 0);
        client.startMessageProcessing();
        client.requestTickByTickDataForSymbols();
    }

    public void connect(String host, int port, int clientId) {
        clientSocket.eConnect(host, port, clientId);
        if (clientSocket.isConnected()) {
            System.out.println("Connected to IBKR");
        }
    }

    public void startMessageProcessing() {
        final EReader reader = new EReader(clientSocket, signal);
        reader.start();

        executor.execute(() -> {
            while (clientSocket.isConnected()) {
                signal.waitForSignal();
                try {
                    reader.processMsgs();
                } catch (Exception e) {
                    System.err.println("Error processing messages: " + e.getMessage());
                }
            }
        });
    }

    private Contract createStockContract(String symbol) {
        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange("SMART");
        return contract;
    }

    public void requestTickByTickDataForSymbols() {
        List<String> stockSymbols = Arrays.asList("AAPL", "MSFT", "GOOGL", "AMZN", "TSLA"); // Extend as needed
        int tickerId = 1000;

        for (String symbol : stockSymbols) {
            Contract contract = createStockContract(symbol);
            clientSocket.reqTickByTickData(tickerId++, contract, "Last", 0, false);
            System.out.println("Requested tick-by-tick data for: " + symbol);
        }
    }

    // --- EWrapper Implementation for Tick-by-Tick Data ---

    @Override
    public void tickByTickAllLast(int reqId, int tickType, long time, double price, int size, TickAttribLast tickAttribLast, String exchange, String specialConditions) {
        System.out.printf("TickByTick - ID: %d | Type: %s | Time: %d | Price: %.2f | Size: %d | Exchange: %s%n",
                reqId, (tickType == 1 ? "Ask" : "Bid"), time, price, size, exchange);
    }

    @Override
    public void tickByTickBidAsk(int reqId, long time, double bidPrice, double askPrice, int bidSize, int askSize, TickAttribBidAsk tickAttribBidAsk) {
        System.out.printf("TickByTick Bid/Ask - ID: %d | Time: %d | Bid: %.2f (%d) | Ask: %.2f (%d)%n",
                reqId, time, bidPrice, bidSize, askPrice, askSize);
    }

    @Override
    public void tickByTickMidPoint(int reqId, long time, double midPoint) {
        System.out.printf("TickByTick MidPoint - ID: %d | Time: %d | MidPoint: %.2f%n",
                reqId, time, midPoint);
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

    // --- Required Empty Implementations for EWrapper ---

    @Override public void tickPrice(int tickerId, int field, double price, TickAttrib attribs) {}
    @Override public void tickSize(int tickerId, int field, int size) {}
    @Override public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta,
                                                double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {}
    @Override public void tickGeneric(int tickerId, int tickType, double value) {}
    @Override public void tickString(int tickerId, int tickType, String value) {}
    @Override public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints,
                                  double impliedFuture, int holdDays, String futureLastTradeDate, double dividendImpact, double dividendsToLastTradeDate) {}
    @Override public void orderStatus(int orderId, String status, double filled, double remaining,
                                      double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {}
    @Override public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {}
    @Override public void openOrderEnd() {}
    @Override public void updateAccountValue(String key, String value, String currency, String accountName) {}
    @Override public void updatePortfolio(Contract contract, double position, double marketPrice, double marketValue,
                                          double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {}
    @Override public void updateAccountTime(String timeStamp) {}
    @Override public void accountDownloadEnd(String accountName) {}
    @Override public void nextValidId(int orderId) { nextOrderId = orderId; }
    @Override public void contractDetails(int reqId, ContractDetails contractDetails) {}
    @Override public void bondContractDetails(int reqId, ContractDetails contractDetails) {}
    @Override public void contractDetailsEnd(int reqId) {}
    @Override public void execDetails(int reqId, Contract contract, Execution execution) {}
    @Override public void execDetailsEnd(int reqId) {}
    @Override public void commissionReport(CommissionReport commissionReport) {}
    @Override public void fundamentalData(int reqId, String data) {}

    @Override
    public void deltaNeutralValidation(int i, DeltaNeutralContract deltaNeutralContract) {

    }

    @Override public void historicalData(int reqId, Bar bar) {}
    @Override public void historicalDataEnd(int reqId, String startDateStr, String endDateStr) {}
    @Override public void marketDataType(int reqId, int marketDataType) {}
    @Override public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size) {}
    @Override public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation,
                                           int side, double price, int size, boolean isSmartDepth) {}
    @Override public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {}

    @Override
    public void managedAccounts(String s) {

    }

    @Override public void position(String account, Contract contract, double pos, double avgCost) {}
    @Override public void positionEnd() {}
    @Override public void accountSummary(int reqId, String account, String tag, String value, String currency) {}
    @Override public void accountSummaryEnd(int reqId) {}
    @Override public void verifyMessageAPI(String apiData) {}
    @Override public void verifyCompleted(boolean isSuccessful, String errorText) {}
    @Override public void verifyAndAuthMessageAPI(String apiData, String xyzChallange) {}
    @Override public void verifyAndAuthCompleted(boolean isSuccessful, String errorText) {}
    @Override public void displayGroupList(int reqId, String groups) {}
    @Override public void displayGroupUpdated(int reqId, String contractInfo) {}
    @Override public void positionMulti(int reqId, String account, String modelCode, Contract contract, double pos, double avgCost) {}
    @Override public void positionMultiEnd(int reqId) {}
    @Override public void accountUpdateMulti(int reqId, String account, String modelCode, String key, String value, String currency) {}
    @Override public void accountUpdateMultiEnd(int reqId) {}
    @Override public void securityDefinitionOptionalParameter(int reqId, String exchange, int underlyingConId,
                                                              String tradingClass, String multiplier, Set<String> expirations, Set<Double> strikes) {}
    @Override public void securityDefinitionOptionalParameterEnd(int reqId) {}
    @Override public void softDollarTiers(int reqId, SoftDollarTier[] tiers) {}
    @Override public void familyCodes(FamilyCode[] familyCodes) {}
    @Override public void symbolSamples(int reqId, ContractDescription[] contractDescriptions) {}
    @Override public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {}
    @Override public void tickNews(int tickerId, long timeStamp, String providerCode, String articleId, String headline, String extraData) {}
    @Override public void smartComponents(int reqId, Map<Integer, Map.Entry<String, Character>> theMap) {}
    @Override public void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) {}
    @Override public void newsProviders(NewsProvider[] newsProviders) {}
    @Override public void newsArticle(int requestId, int articleType, String articleText) {}
    @Override public void historicalNews(int requestId, String time, String providerCode, String articleId, String headline) {}
    @Override public void historicalNewsEnd(int requestId, boolean hasMore) {}
    @Override public void headTimestamp(int reqId, String headTimestamp) {}
    @Override public void histogramData(int reqId, List<HistogramEntry> items) {}
    @Override public void rerouteMktDataReq(int reqId, int conid, String exchange) {}
    @Override public void rerouteMktDepthReq(int reqId, int conid, String exchange) {}
    @Override public void marketRule(int marketRuleId, PriceIncrement[] priceIncrements) {}
    @Override public void pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) {}
    @Override public void pnlSingle(int reqId, int pos, double dailyPnL, double unrealizedPnL, double realizedPnL, double value) {}
    @Override public void historicalTicks(int reqId, List<HistoricalTick> ticks, boolean done) {}
    @Override public void historicalTicksBidAsk(int reqId, List<HistoricalTickBidAsk> ticks, boolean done) {}
    @Override public void historicalTicksLast(int reqId, List<HistoricalTickLast> ticks, boolean done) {}
    @Override public void tickSnapshotEnd(int reqId) {}
    @Override public void connectAck() {
        if (clientSocket.isAsyncEConnect())
            clientSocket.startAPI();
    }
    @Override public void error(Exception e) { System.err.println("Exception: " + e.getMessage()); }
    @Override public void error(String str) { System.err.println("Error STR: " + str); }
    @Override public void error(int id, int errorCode, String errorMsg) {
        System.err.printf("Error - ID: %d | Code: %d | Msg: %s%n", id, errorCode, errorMsg);
    }
    @Override public void connectionClosed() {
        System.out.println("Connection closed.");
    }
    @Override public void currentTime(long time) {}
    @Override public void realtimeBar(int reqId, long time, double open, double high, double low, double close,
                                      long volume, double wap, int count) {}
    @Override public void scannerParameters(String xml) {}
    @Override public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance,
                                      String benchmark, String projection, String legsStr) {}
    @Override public void scannerDataEnd(int reqId) {}
    @Override public void receiveFA(int faDataType, String xml) {}
    @Override public void historicalDataUpdate(int reqId, Bar bar) {}
}
