package com.ibkr.util;

import com.ib.client.Contract;
import com.ibkr.AppContext;
import com.ibkr.service.MarketDataService;
import com.zerodhatech.models.HistoricalData;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class IbkrHistoricalDataDownloader {

    private static final Logger logger = LoggerFactory.getLogger(IbkrHistoricalDataDownloader.class);

    public static void main(String[] args) {
        if (args.length < 5) {
            System.out.println("Usage: IbkrHistoricalDataDownloader <symbol> <date YYYYMMDD> <duration> <barSize> <outputFile>");
            System.out.println("Example: IbkrHistoricalDataDownloader AAPL 20250820 \"60 Mins\" \"1 min\" aapl_data.csv");
            return;
        }

        String symbol = args[0];
        String date = args[1];
        String duration = args[2];
        String barSize = args[3];
        String outputFilePath = args[4];

        String endDateTime = date + " 16:00:00";

        AppContext appContext = new AppContext();
        MarketDataService marketDataService = appContext.getMarketDataService();

        try {
            // 1. Connect to TWS/Gateway
            logger.info("Connecting to TWS/Gateway...");
            marketDataService.connect(appContext.getTwsHost(), appContext.getTwsPort(), appContext.getTwsClientId());
            // In a real app, you'd wait for connectAck here. For this simple downloader, we'll proceed.
            Thread.sleep(2000); // Simple wait for connection

            // 2. Fetch data from API
            logger.info("Requesting historical data for {}...", symbol);
            Contract contract = new Contract();
            contract.symbol(symbol);
            contract.secType("STK");
            contract.exchange("SMART");
            contract.currency("USD");

            List<HistoricalData> data = marketDataService.requestHistoricalData(contract, endDateTime, duration, barSize, "TRADES", 1, 2).get();

            // 3. Write the data to CSV
            writeToCsv(data, outputFilePath);

            logger.info("Successfully downloaded and saved historical data for '{}' to: {}", symbol, outputFilePath);

        } catch (IOException | InterruptedException | ExecutionException e) {
            logger.error("An error occurred while fetching or writing data: ", e);
        } finally {
            logger.info("Disconnecting from TWS/Gateway...");
            marketDataService.disconnect();
        }
    }

    private static void writeToCsv(List<HistoricalData> dataList, String filePath) throws IOException {
        String[] headers = {"Date", "Open", "High", "Low", "Close", "Volume"};

        try (Writer writer = new FileWriter(filePath);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(headers))) {

            for (HistoricalData data : dataList) {
                csvPrinter.printRecord(data.timeStamp, data.open, data.high, data.low, data.close, data.volume);
            }

            csvPrinter.flush();
        }
    }
}
