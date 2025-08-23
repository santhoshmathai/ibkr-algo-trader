package com.ibkr;

import com.ib.client.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class IBKRApiService implements EWrapper {

    private final EClientSocket client;
    private final EReaderSignal signal;
    private int nextReqId = 1;

    private final CompletableFuture<Void> connectionFuture = new CompletableFuture<>();
    private final Map<Integer, CompletableFuture<Double>> historicalDataFutures = new ConcurrentHashMap<>();
    private final Map<Integer, List<Bar>> historicalDataBars = new ConcurrentHashMap<>();

    public IBKRApiService() {
        this.signal = new EJavaSignal();
        this.client = new EClientSocket(this, signal);
    }

    public void connect(String host, int port, int clientId) {
        client.eConnect(host, port, clientId);
        final EReader reader = new EReader(client, signal);
        reader.start();

        new Thread(() -> {
            while (client.isConnected()) {
                signal.waitForSignal();
                try {
                    reader.processMsgs();
                } catch (Exception e) {
                    System.out.println("Exception: " + e.getMessage());
                }
            }
        }).start();
    }

    public void awaitConnection() throws ExecutionException, InterruptedException {
        connectionFuture.get();
    }

    public void disconnect() {
        client.eDisconnect();
    }

    public CompletableFuture<Double> getAverageDailyVolume(String symbol) {
        CompletableFuture<Double> future = new CompletableFuture<>();
        int reqId = nextReqId++;
        historicalDataFutures.put(reqId, future);
        historicalDataBars.put(reqId, new ArrayList<>());

        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.secType("STK");
        contract.exchange("SMART");
        contract.currency("USD");

        client.reqHistoricalData(
                reqId,
                contract,
                "",
                "5 D",
                "1 day",
                "TRADES",
                1,
                1,
                false,
                null);

        return future;
    }

    @Override
    public void historicalData(int reqId, Bar bar) {
        List<Bar> bars = historicalDataBars.get(reqId);
        if (bars != null) {
            bars.add(bar);
        }
    }

    @Override
    public void historicalDataEnd(int reqId, String startDateStr, String endDateStr) {
        CompletableFuture<Double> future = historicalDataFutures.remove(reqId);
        List<Bar> bars = historicalDataBars.remove(reqId);

        if (future != null) {
            if (bars != null && !bars.isEmpty()) {
                double totalVolume = 0;
                for (Bar bar : bars) {
                    totalVolume += bar.volume();
                }
                future.complete(totalVolume / bars.size());
            } else {
                future.complete(0.0);
            }
        }
    }

    @Override
    public void error(int id, int errorCode, String errorMsg) {
        if (!connectionFuture.isDone()) {
            // Error codes that indicate a connection problem, see https://interactivebrokers.github.io/tws-api/message_codes.html
            if (errorCode == 502 || errorCode == 504 || errorCode == 509) {
                connectionFuture.completeExceptionally(new RuntimeException("IBKR Connection Error: " + errorMsg));
            }
        }

        CompletableFuture<Double> future = historicalDataFutures.remove(id);
        if (future != null) {
            future.completeExceptionally(new RuntimeException("IBKR API Error. Id: " + id + ", Code: " + errorCode + ", Msg: " + errorMsg));
        }
    }

    @Override
    public void tickPrice(int tickerId, int field, double price, TickAttrib attrib) {
    }

    @Override
    public void tickSize(int tickerId, int field, int size) {
    }

    @Override
    public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta, double optPrice,
            double pvDividend, double gamma, double vega, double theta, double undPrice) {
    }

    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
    }

    @Override
    public void tickString(int tickerId, int tickType, String value) {
    }

    @Override
    public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints,
            double impliedFuture, int holdDays, String futureLastTradeDate, double dividendImpact,
            double dividendsToLastTradeDate) {
    }

    @Override
    public void orderStatus(int orderId, String status, double filled, double remaining, double avgFillPrice,
            int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
    }

    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
    }

    @Override
    public void openOrderEnd() {
    }

    @Override
    public void updateAccountValue(String key, String value, String currency, String accountName) {
    }

    @Override
    public void updatePortfolio(Contract contract, double position, double marketPrice, double marketValue,
            double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {
    }

    @Override
    public void updateAccountTime(String timeStamp) {
    }

    @Override
    public void accountDownloadEnd(String accountName) {
    }

    @Override
    public void nextValidId(int orderId) {
        connectionFuture.complete(null);
    }

    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
    }

    @Override
    public void bondContractDetails(int reqId, ContractDetails contractDetails) {
    }

    @Override
    public void contractDetailsEnd(int reqId) {
    }

    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {
    }

    @Override
    public void execDetailsEnd(int reqId) {
    }

    @Override
    public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size) {
    }

    @Override
    public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side,
            double price, int size, boolean isSmartDepth) {
    }

    @Override
    public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {
    }

    @Override
    public void managedAccounts(String accountsList) {
    }

    @Override
    public void receiveFA(int faDataType, String xml) {
    }

    @Override
    public void historicalDataUpdate(int reqId, Bar bar) {
    }

    @Override
    public void commissionReport(CommissionReport commissionReport) {
    }

    @Override
    public void position(String account, Contract contract, double pos, double avgCost) {
    }

    @Override
    public void positionEnd() {
    }

    @Override
    public void accountSummary(int reqId, String account, String tag, String value, String currency) {
    }

    @Override
    public void accountSummaryEnd(int reqId) {
    }

    @Override
    public void verifyMessageAPI(String apiData) {
    }

    @Override
    public void verifyCompleted(boolean isSuccessful, String errorText) {
    }

    @Override
    public void verifyAndAuthMessageAPI(String apiData, String xyzChallenge) {
    }

    @Override
    public void verifyAndAuthCompleted(boolean isSuccessful, String errorText) {
    }

    @Override
    public void displayGroupList(int reqId, String groups) {
    }

    @Override
    public void displayGroupUpdated(int reqId, String contractInfo) {
    }

    @Override
    public void error(Exception e) {
    }

    @Override
    public void error(String str) {
    }

    @Override
    public void connectionClosed() {
    }

    @Override
    public void connectAck() {
    }

    @Override
    public void positionMulti(int reqId, String account, String modelCode, Contract contract, double pos,
            double avgCost) {
    }

    @Override
    public void positionMultiEnd(int reqId) {
    }

    @Override
    public void accountUpdateMulti(int reqId, String account, String modelCode, String key, String value,
            String currency) {
    }

    @Override
    public void accountUpdateMultiEnd(int reqId) {
    }

    @Override
    public void securityDefinitionOptionalParameter(int reqId, String exchange, int underlyingConId,
            String tradingClass, String multiplier, Set<String> expirations, Set<Double> strikes) {
    }

    @Override
    public void securityDefinitionOptionalParameterEnd(int reqId) {
    }

    @Override
    public void softDollarTiers(int reqId, SoftDollarTier[] tiers) {
    }

    @Override
    public void familyCodes(FamilyCode[] familyCodes) {
    }

    @Override
    public void symbolSamples(int reqId, ContractDescription[] contractDescriptions) {
    }

    @Override
    public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {
    }

    @Override
    public void tickNews(int tickerId, long timeStamp, String providerCode, String articleId, String headline,
            String extraData) {
    }

    @Override
    public void smartComponents(int reqId, Map<Integer, Map.Entry<String, Character>> theMap) {
    }

    @Override
    public void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) {
    }

    @Override
    public void newsProviders(NewsProvider[] newsProviders) {
    }

    @Override
    public void newsArticle(int requestId, int articleType, String articleText) {
    }

    @Override
    public void historicalNews(int requestId, String time, String providerCode, String articleId, String headline) {
    }

    @Override
    public void historicalNewsEnd(int requestId, boolean hasMore) {
    }

    @Override
    public void headTimestamp(int reqId, String headTimestamp) {
    }

    @Override
    public void histogramData(int reqId, List<HistogramEntry> items) {
    }

    @Override
    public void rerouteMktDataReq(int reqId, int conId, String exchange) {
    }

    @Override
    public void rerouteMktDepthReq(int reqId, int conId, String exchange) {
    }

    @Override
    public void marketRule(int marketRuleId, PriceIncrement[] priceIncrements) {
    }

    @Override
    public void pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) {
    }

    @Override
    public void pnlSingle(int reqId, int pos, double dailyPnL, double unrealizedPnL, double realizedPnL,
            double value) {
    }

    @Override
    public void historicalTicks(int reqId, List<HistoricalTick> ticks, boolean done) {
    }

    @Override
    public void historicalTicksBidAsk(int reqId, List<HistoricalTickBidAsk> ticks, boolean done) {
    }

    @Override
    public void historicalTicksLast(int reqId, List<HistoricalTickLast> ticks, boolean done) {
    }

    @Override
    public void tickByTickAllLast(int reqId, int tickType, long time, double price, int size,
            TickAttribLast tickAttribLast, String exchange, String specialConditions) {
    }

    @Override
    public void tickByTickBidAsk(int reqId, long time, double bidPrice, double askPrice, int bidSize, int askSize,
            TickAttribBidAsk tickAttribBidAsk) {
    }

    @Override
    public void tickByTickMidPoint(int reqId, long time, double midPoint) {
    }

    @Override
    public void orderBound(long orderId, int apiClientId, int apiOrderId) {
    }

    @Override
    public void completedOrder(Contract contract, Order order, OrderState orderState) {
    }

    @Override
    public void completedOrdersEnd() {
    }

    @Override
    public void tickSnapshotEnd(int reqId) {
    }

    @Override
    public void marketDataType(int reqId, int marketDataType) {
    }

    @Override
    public void fundamentalData(int reqId, String data) {
    }

    @Override
    public void realtimeBar(int reqId, long time, double open, double high, double low, double close, long volume,
            double wap, int count) {
    }

    @Override
    public void scannerParameters(String xml) {
    }

    @Override
    public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance, String benchmark,
            String projection, String legsStr) {
    }

    @Override
    public void scannerDataEnd(int reqId) {
    }

    @Override
    public void currentTime(long time) {
    }

    @Override
    public void deltaNeutralValidation(int reqId, DeltaNeutralContract deltaNeutralContract) {
    }
}
