package com.ibkr;

import com.ib.client.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IBKRClient implements EWrapper {
    private EClientSocket clientSocket;
    private int nextOrderId = 0;
    private EReaderSignal signal;
    private ExecutorService executor;

    public IBKRClient() {
        signal = new EJavaSignal();
        clientSocket = new EClientSocket(this, signal);
        executor = Executors.newSingleThreadExecutor();
    }

    public static void main(String[] args) {
        IBKRClient client = new IBKRClient();
        client.connect("127.0.0.1", 7496, 0); // Localhost, Paper Trading port, Client ID
        client.startMessageProcessing();
        //client.requestMarketData();
        // Request market data for all S&P 500 companies
        //List<String> sp500Tickers = client.getSP500Tickers();
        client.requestMarketDataForSymbols();

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

    public void connect(String host, int port, int clientId) {
        clientSocket.eConnect(host, port, clientId);
        if (clientSocket.isConnected()) {
            System.out.println("Connected to IBKR");
        }
    }

    public List<String> getSP500Tickers() {
        // Placeholder: Replace with the actual logic to retrieve S&P 500 tickers
        return Arrays.asList("AAPL", "MSFT", "GOOGL", "AMZN", "TSLA"); // Add full list or fetch from an API
        //return Arrays.asList("AAPL"); // Add full list or fetch from an API

    }

    public void requestMarketDataForTickers(List<String> tickers) {
        int tickerId = 1;
        for (String ticker : tickers) {
            Contract contract = createStockContract(ticker);
            clientSocket.reqHistoricalData(tickerId++, contract, "", "5 D", "1 min", "MIDPOINT", 1, 1, false, null);

           // clientSocket.reqHistoricalData(1, contract, "", "5 D", "1 min", "MIDPOINT", 1, 1, false, null);
            System.out.printf("Requested historical market data for %s%n", ticker);
        }
    }

    private Contract createStockContract(String symbol) {
        String value = symbol ;
        Contract contract = new Contract();
        contract.symbol(value);
        contract.secType("STK");
        contract.currency("USD");
        contract.exchange("SMART");
        return contract;
    }

    public void requestMarketDataForSymbols() {
        List<String> stockSymbols = Arrays.asList(
                "MMM", "AOS", "ABT", "ABBV", "ACN", "ADBE", "AMD", "AES", "AFL", "A", "APD", "ABNB",
                "AKAM", "ALB", "ARE", "ALGN", "ALLE", "LNT", "ALL", "GOOGL", "GOOG", "MO", "AMZN", "AMCR", "AEE", "AEP",
                "AXP", "AIG", "AMT", "AWK", "AMP", "AME", "AMGN", "APH", "ADI", "ANSS", "AON", "APA", "APO", "AAPL", "AMAT", "APTV",
                "ACGL", "ADM", "ANET", "AJG", "AIZ", "T", "ATO", "ADSK", "ADP", "AZO", "AVB", "AVY", "AXON", "BKR", "BALL",
                "AVGO", "BR", "BRO", "BF.B", "BLDR", "BG", "BXP", "CHRW", "CDNS", "CZR", "CPT", "CPB", "COF", "CAH", "KMX",
                "CCL", "CARR", "CAT", "CBOE", "CBRE", "CDW", "CE", "COR", "CNC", "CNP", "CF", "CRL", "SCHW", "CHTR", "CVX", "CMG",
                "CB", "CHD", "CI", "CINF", "CTAS", "CSCO", "C", "CFG", "CLX", "CME", "CMS", "KO", "CTSH", "CL", "CMCSA", "CAG",
                "COP", "ED", "STZ", "CEG", "COO", "CPRT", "GLW", "CPAY", "CTVA", "CSGP", "COST", "CTRA", "CRWD", "CCI", "CSX",
                "CMI", "CVS", "DHR", "DRI", "DVA", "DAY", "DECK", "DE", "DELL", "DAL", "DVN", "DXCM", "FANG", "DLR", "DFS",
                "DG", "DLTR", "D", "DPZ", "DOV", "DOW", "DHI", "DTE", "DUK", "DD", "EMN", "ETN", "EBAY", "ECL", "EIX",
                "EW", "EA", "ELV", "EMR", "ENPH", "ETR", "EOG", "EPAM", "EQT", "EFX", "EQIX", "EQR", "ERIE", "ESS", "EL",
                "EG", "EVRG", "ES", "EXC", "EXPE", "EXPD", "EXR", "XOM", "FFIV", "FDS", "FICO", "FAST", "FRT", "FDX", "FIS",
                "FITB", "FSLR", "FE", "FI", "FMC", "F", "FTNT", "FTV", "FOXA", "FOX", "BEN", "FCX", "GRMN", "IT", "GE",
                "GEHC", "GEV", "GEN", "GNRC", "GD", "GIS", "GM", "GPC", "GILD", "GPN", "GL", "GDDY", "GS", "HAL", "HIG",
                "HAS", "HCA", "DOC", "HSIC", "HSY", "HES", "HPE", "HLT", "HOLX", "HD", "HON", "HRL", "HST", "HWM", "HPQ",
                "HUBB", "HUM", "HBAN", "HII", "IBM", "IEX", "IDXX", "ITW", "INCY", "IR", "PODD", "INTC", "ICE", "IFF", "IP",
                "IPG", "INTU", "ISRG", "IVZ", "INVH", "IQV", "IRM", "JBHT", "JBL", "JKHY", "J", "JNJ", "JCI", "JPM", "JNPR",
                "K", "KVUE", "KDP", "KEY", "KEYS", "KMB", "KIM", "KMI", "KKR", "KLAC", "KHC", "KR", "LHX", "LH", "LRCX",
                "LW", "LVS", "LDOS", "LEN", "LII", "LLY", "LIN", "LYV", "LKQ", "LMT", "L", "LOW", "LULU", "LYB", "MTB",
                "MPC", "MKTX", "MAR", "MMC", "MLM", "MAS", "MA", "MTCH", "MKC", "MCD", "MCK", "MDT", "MRK", "META", "MET",
                "MTD", "MGM", "MCHP", "MU", "MSFT", "MAA", "MRNA", "MHK", "MOH", "TAP", "MDLZ", "MPWR", "MNST", "MCO", "MS",
                "MOS", "MSI", "MSCI", "NDAQ", "NTAP", "NFLX", "NEM", "NWSA", "NWS", "NEE", "NKE", "NI", "NDSN", "NSC",
                "NTRS", "NOC", "NCLH", "NRG", "NUE", "NVDA", "NVR", "NXPI", "ORLY", "OXY", "ODFL", "OMC", "ON", "OKE",
                "ORCL", "OTIS", "PCAR", "PKG", "PLTR", "PANW", "PARA", "PH", "PAYX", "PAYC", "PYPL", "PNR", "PEP", "PFE",
                "PCG", "PM", "PSX", "PNW", "PNC", "POOL", "PPG", "PPL", "PFG", "PG", "PGR", "PLD", "PRU", "PEG", "PTC",
                "PSA", "PHM", "PWR", "QCOM", "DGX", "RL", "RJF", "RTX", "O", "REG", "REGN", "RF", "RSG", "RMD", "RVTY",
                "ROK", "ROL", "ROP", "ROST", "RCL", "SPGI", "CRM", "SBAC", "SLB", "STX", "SRE", "NOW", "SHW", "SPG",
                "SWKS", "SJM", "SW", "SNA", "SOLV", "SO", "LUV", "SWK", "SBUX", "STT", "STLD", "STE", "SYK", "SMCI",
                "SYF", "SNPS", "SYY", "TMUS", "TROW", "TTWO", "TPR", "TRGP", "TGT", "TEL", "TDY", "TFX", "TER", "TSLA",
                "TXN", "TPL", "TXT", "TMO", "TJX", "TSCO", "TT", "TDG", "TRV", "TRMB", "TFC", "TYL", "TSN", "USB",
                "UBER", "UDR", "ULTA", "UNP", "UAL", "UPS", "URI", "UNH", "UHS", "VLO", "VTR", "VLTO", "VRSN", "VRSK",
                "VZ", "VRTX", "VTRS", "VICI", "V", "VST", "VMC", "WRB", "GWW", "WAB", "WBA", "WMT", "DIS", "WBD",
                "WM", "WAT", "WEC", "WFC", "WELL", "WST", "WDC", "WY", "WMB", "WTW", "WDAY", "WYNN", "XEL", "XYL",
                "YUM", "ZBRA", "ZBH", "ZTS"
        );
        //List<Contract> contracts = new ArrayList<>();
        int tickerId = 1; // Unique ID for each request

        
        for (String symbol : stockSymbols) {
            Contract contract = createStockContract(symbol);

            //contracts.add(contract);
            clientSocket.reqMarketDataType(3);
            clientSocket.reqMktData(tickerId++, contract, "",false, false, null);

            System.out.println("Requested market data for: " + symbol);
        }

        /*for (String symbol : stockSymbols) {
            Contract contract = createStockContract(symbol);

            // Get the date for last Friday in the required format (yyyyMMdd HH:mm:ss)
            String lastFriday = getLastFriday();

            clientSocket.reqHistoricalData(
                    tickerId++,
                    contract,
                    lastFriday,  // End date/time
                    "1 D",       // Duration (1 day)
                    "1 min",     // Bar size
                    "TRADES",    // Data type
                    1,           // Regular Trading Hours only
                    1,           // Keep up to date
                    false,       // UseRTH (false means include all trading hours)
                    new ArrayList<>() // Empty list of TagValue instead of int
            );

            System.out.println("Requested historical data for: " + symbol);
        } */

       // return contracts;
    }

    public void requestMarketData() {
        // Define the contract (e.g., AAPL stock)
        Contract contract = new Contract();
        contract.symbol("AAPL");
        contract.secType("STK"); // Stock
        contract.currency("USD");
        contract.exchange("SMART");

        // Request market data (tick)
        //clientSocket.reqMktData(1, contract, "", false, false, null);
        clientSocket.reqHistoricalData(3, contract, "", "5 D", "1 min", "MIDPOINT", 1, 1, false, null);

        System.out.println("Requested Apple Market data");

    }

    public static String getLastFriday() {
        Calendar cal = Calendar.getInstance();

        // Go back to last Friday
        do {
            cal.add(Calendar.DAY_OF_MONTH, -1);
        } while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        return sdf.format(cal.getTime());
    }

    // Callback for real-time market data
    @Override
    public void tickPrice(int tickerId, int field, double price, TickAttrib attribs) {
        System.out.printf("Tick Price. Ticker ID: %d, Field: %d, Price: %.2f%n", tickerId, field, price);
    }

    @Override
    public void tickSize(int tickerId, int field, int size) {
        System.out.printf("Tick Size. Ticker ID: %d, Field: %d, Size: %d%n", tickerId, field, size);
    }

    @Override
    public void tickOptionComputation(int i, int i1, double v, double v1, double v2, double v3, double v4, double v5, double v6, double v7) {

    }

    @Override
    public void tickString(int tickerId, int tickType, String value) {
        System.out.printf("Tick String. Ticker ID: %d, Tick Type: %d, Value: %s%n", tickerId, tickType, value);
    }

    @Override
    public void tickEFP(int i, int i1, double v, String s, double v1, int i2, String s1, double v2, double v3) {

    }

    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
        System.out.printf("Tick Generic. Ticker ID: %d, Tick Type: %d, Value: %.2f%n", tickerId, tickType, value);
    }

    @Override
    public void nextValidId(int orderId) {
        nextOrderId = orderId;
        System.out.println("Next Valid Order ID: " + orderId);
    }

    @Override
    public void connectionClosed() {
        System.out.println("Connection closed.");
    }

    @Override
    public void error(Exception e) {
        System.err.println("Error: " + e.getMessage());
    }

    @Override
    public void error(int id, int errorCode, String errorMsg) {
        System.err.printf("Error. ID: %d, Code: %d, Message: %s%n", id, errorCode, errorMsg);
    }

    @Override
    public void error(String str) {
        System.err.println("Error: " + str);
    }

    @Override
    public void connectAck() {
        System.out.println("Connection acknowledged.");
    }

    // Empty implementations for other EWrapper methods
    @Override
    public void currentTime(long time) {
    }

    @Override
    public void managedAccounts(String accountsList) {
    }

    @Override
    public void receiveFA(int i, String s) {

    }

    @Override
    public void accountSummary(int reqId, String account, String tag, String value, String currency) {
    }

    @Override
    public void accountSummaryEnd(int reqId) {
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
    public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size) {
    }

    @Override
    public void updateMktDepthL2(int i, int i1, String s, int i2, int i3, double v, int i4, boolean b) {

    }

    @Override
    public void updateNewsBulletin(int i, int i1, String s, String s1) {

    }

    public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side, double price, int size) {
    }

    @Override
    public void historicalData(int reqId, Bar bar) {
        System.out.printf(
                "Historical Data. ReqId: %d, Date: %s, Open: %.2f, High: %.2f, Low: %.2f, Close: %.2f, Volume: %d%n",
                reqId, bar.time(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume()
        );
    }

    @Override
    public void historicalDataEnd(int reqId, String startDate, String endDate) {
        System.out.printf("Historical Data End. ReqId: %d, Start: %s, End: %s%n", reqId, startDate, endDate);
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
    public void realtimeBar(int i, long l, double v, double v1, double v2, double v3, long l1, double v4, int i1) {

    }

    @Override
    public void deltaNeutralValidation(int reqId, DeltaNeutralContract deltaNeutralContract) {
    }

    @Override
    public void fundamentalData(int reqId, String data) {
    }

    @Override
    public void bondContractDetails(int reqId, ContractDetails contractDetails) {
    }

    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
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
    public void position(String account, Contract contract, double pos, double avgCost) {
    }

    @Override
    public void positionEnd() {
    }

    public void accountSummaryEnd() {
    }

    @Override
    public void tickSnapshotEnd(int reqId) {
    }

    @Override
    public void marketDataType(int reqId, int marketDataType) {
    }

    @Override
    public void commissionReport(CommissionReport commissionReport) {
    }

    @Override
    public void positionMulti(int reqId, String account, String modelCode, Contract contract, double pos, double avgCost) {
    }

    @Override
    public void positionMultiEnd(int reqId) {
    }

    @Override
    public void accountUpdateMulti(int reqId, String account, String modelCode, String key, String value, String currency) {
    }

    @Override
    public void accountUpdateMultiEnd(int reqId) {
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
    public void symbolSamples(int reqId, ContractDescription[] contractDescriptions) {
    }

    public void historicalTicks(int reqId, HistoricalTick[] ticks, boolean done) {
    }

    public void historicalTicksBidAsk(int reqId, HistoricalTickBidAsk[] ticks, boolean done) {
    }

    public void historicalTicksLast(int reqId, HistoricalTickLast[] ticks, boolean done) {
    }

    @Override
    public void tickByTickAllLast(int reqId, int tickType, long time, double price, int size,
                                  TickAttribLast tickAttribLast, String exchange, String specialConditions) {
    }

    @Override
    public void tickByTickBidAsk(int reqId, long time, double bidPrice, double askPrice, int bidSize,
                                 int askSize, TickAttribBidAsk tickAttribBidAsk) {
    }

    @Override
    public void tickByTickMidPoint(int reqId, long time, double midPoint) {
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
}