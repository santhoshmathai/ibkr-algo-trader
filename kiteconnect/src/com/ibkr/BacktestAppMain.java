package com.ibkr;

import com.ibkr.core.TradingEngine;
import com.ibkr.models.PortfolioManager;
import com.ibkr.service.BacktestMarketDataService;
import com.ibkr.service.BacktestOrderService;
import com.ibkr.service.MarketDataService;
import com.ibkr.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;

public class BacktestAppMain {

    private static final Logger logger = LoggerFactory.getLogger(BacktestAppMain.class);

    public static void main(String[] args) {
        logger.info("Starting backtest...");

        // Create the backtesting services
        MarketDataService marketDataService = new BacktestMarketDataService("stockdata/MW-NIFTY-500-01-Nov-2024.csv");
        OrderService orderService = new BacktestOrderService();

        // Create the AppContext
        AppContext appContext = new AppContext(true);

        // Create the TradingEngine
        TradingEngine tradingEngine = new TradingEngine(appContext, orderService, marketDataService, new PortfolioManager(), null);

        // Initialize the TradingEngine services
        tradingEngine.initializeServices(marketDataService, new com.ibkr.screener.StockScreener(marketDataService));

        // Run the backtest
        // For now, we will just run the pre-market screen as a test
        tradingEngine.runPreMarketScreen();

        logger.info("Backtest finished.");
    }
}
