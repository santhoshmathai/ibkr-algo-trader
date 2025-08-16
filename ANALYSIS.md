# Trading Application Logic and Data Flow Analysis

### High-Level Overview

This project is a sophisticated trading bot designed to trade on the Interactive Brokers (IB) platform. Although the project is structured within a "Kite Connect" (a trading API for the Indian broker Zerodha) client, the core trading logic, data handling, and execution are all geared towards Interactive Brokers. The bot uses IB for market data and order execution, but it internally uses the Zerodha Kite Connect data models (like `Tick`, `Order`) as a standardized format for market data.

The application follows a modular design, separating concerns into distinct packages:
*   `com.ibkr.data`: Handles market data ingestion, aggregation, and persistence.
*   `com.ibkr.analysis`: Performs market analysis, such as calculating market sentiment, sector strength, and support/resistance levels.
*   `com.ibkr.risk`: Manages risk by assessing volatility, liquidity, and validating trades against pre-defined risk rules.
*   `com.ibkr.strategy`: Contains the specific trading strategies that generate buy and sell signals (e.g., the "Opening Range Breakout" or `OrbStrategy`).
*   `com.ibkr.execution`: Manages order execution.

### Logic of Each Package

#### 1. `com.ibkr.data` (Data Handling)

This package is the foundation of the application, responsible for managing the flow of market data.

*   **Data Flow:** Raw market data (like prices, volume, and order book depth) is received from the Interactive Brokers TWS API.
*   **`TickAggregator.java`**: This is the most critical class in this package. It acts as a data processor that receives the raw, low-level data from IB and "aggregates" it into a standardized, richer data object: `com.zerodhatech.models.Tick`. It also performs the crucial task of creating 1-minute OHLC (Open, High, Low, Close) bars from the incoming data, which are essential for many trading strategies.
*   **`MarketDataHandler.java`**: This class has an archival role. It takes the standardized `Tick` objects and saves them to CSV files on disk. This is useful for debugging, backtesting, and post-trade analysis but is not involved in real-time decision-making.
*   **`InstrumentRegistry.java`**: This is a utility that keeps track of the instruments being traded, mapping the instrument's symbol (e.g., "AAPL") to the internal ID used by the IB API.

#### 2. `com.ibkr.analysis` (Market Analysis)

This package contains the tools to analyze market conditions, providing the trading strategies with a broader context to improve decision-making.

*   **`MarketSentimentAnalyzer.java`**: This analyzer determines the overall market direction. It does this by monitoring a predefined list of stocks (e.g., the NIFTY 500) and checking how many are trading above or below their opening price.
    *   **Opening Trend**: Calculated once, a few minutes after the market opens. If >70% of stocks are up, the trend is "UP". If >70% are down, the trend is "DOWN". Otherwise, it's "NEUTRAL".
    *   **Market Sentiment**: Calculated over the first 30 minutes. It uses a stricter criteria (e.g., price must be above both the open and the previous day's close) to classify sentiment as "STRONG_UP", "STRONG_DOWN", or "NEUTRAL".
*   **`SectorStrengthAnalyzer.java`**: This class groups stocks into sectors (e.g., "Technology", "Finance") and calculates the average performance of each sector. This allows a strategy to check if a stock is in a sector that is outperforming the market.
*   **`SupportResistanceAnalyzer.java`**: This identifies key price levels for a stock. In its current form, it simply uses the previous day's high as a **resistance** level and the previous day's low as a **support** level.
*   **`VolumeSpikeAnalyzer.java`**: This analyzer detects significant, real-time spikes in trading volume. It works by:
    1.  Aggregating the traded volume within a short, configurable time interval (e.g., 15 minutes).
    2.  Projecting what the total volume for the day would be if this pace continues.
    3.  Comparing this projected daily volume to the stock's historical average daily volume.
    4.  If the projected volume is significantly higher (e.g., more than double the average), it flags the stock as having a "volume spike". This is a powerful indicator that can be used by strategies to confirm the strength of a price movement or as a primary trading signal itself.

#### 3. `com.ibkr.risk` (Risk Management)

This package acts as the safety layer for the application, ensuring that the bot does not take on excessive risk. A trading signal is only acted upon if it passes the checks in this package.

*   **`VolatilityAnalyzer.java`**: It calculates price volatility using the standard deviation of the last 20 price ticks. If the volatility becomes too high (e.g., the standard deviation is more than 5% of the current price), it flags the instrument as "too volatile" to trade.
*   **`LiquidityMonitor.java`**: This class assesses whether there is enough liquidity to trade an instrument easily. It uses the size of the order book to calculate a liquidity score. It also has a check to see if the bid-ask spread is too wide, preventing trades in illiquid market conditions.
*   **`RiskManager.java`**: This is the central risk controller. It uses the `VolatilityAnalyzer` and `LiquidityMonitor` to validate a trade *before* it is placed. It also contains the logic for **position sizing**—dynamically calculating how many shares to buy or sell based on the current volatility and liquidity. Higher volatility or lower liquidity results in a smaller position size.
*   **`AdvancedRiskManager.java`**: This provides specialized checks, particularly for short selling. It checks if a stock is at risk of a "short squeeze" and validates if the conditions are appropriate for a short sale.

#### 4. `com.ibkr.strategy` (Trading Strategies)

This is where the actual buy and sell signals are generated. The `README-LOGIC.md` file specifically details the data flow for an **`OrbStrategy`** (Opening Range Breakout).

*   **`OrbStrategy` Logic**:
    1.  **Define Opening Range**: The strategy first identifies the highest high and lowest low of an instrument within a specific time window after the market opens (e.g., the first 15 minutes). This high and low define the "opening range".
    2.  **Breakout Signals**:
        *   A **BUY signal** is generated if the price breaks *above* the high of the opening range.
        *   A **SELL (short) signal** is generated if the price breaks *below* the low of the opening range.
    3.  **Volume Confirmation**: A simple breakout is not enough. The strategy also checks if the breakout is accompanied by a significant increase in trading volume. This helps to filter out "false breakouts".
    4.  **Market Depth**: The strategy also looks at the market depth (the list of buy and sell orders in the order book) to ensure there isn't a large wall of opposing orders that could prevent the price from moving further after the breakout.

### Overall Data Flow and Decision-Making Process

Here is the step-by-step data and decision flow for a typical trade:

1.  **Data Ingestion**: The `IBKRClient` connects to the Interactive Brokers TWS API and subscribes to market data for the selected instruments.
2.  **Data Aggregation**: The raw data flows into the `TickAggregator`, which processes it into standardized `Tick` objects and 1-minute OHLC bars.
3.  **Analysis Loop**: A central `TradingEngine` (likely in `com.ibkr.core`) orchestrates the process. On every new 1-minute bar, it does the following:
    *   It updates all the analyzers in the `com.ibkr.analysis` package with the new data.
    *   It passes the new bar, along with volume and market depth data, to the active trading strategy (e.g., `OrbStrategy`).
4.  **Signal Generation**: The `OrbStrategy` analyzes the new data. If its conditions for a breakout are met (price breaks the opening range + high volume + favorable market depth), it generates a `TradingSignal` object (e.g., `BUY GOLDBEES@NSE`).
5.  **Pre-Trade Risk Assessment**: The `TradingSignal` is sent to the `RiskManager`.
    *   The `RiskManager` checks if the instrument is currently too volatile or illiquid. If so, it **vetoes the trade**.
    *   If it's a short-sell signal, the `AdvancedRiskManager` performs additional checks for short-squeeze risk. If the risk is high, it **vetoes the trade**.
6.  **Position Sizing**: If the trade is approved by the risk managers, the `RiskManager` calculates the appropriate number of shares to trade based on its position sizing logic.
7.  **Order Execution**: Finally, the validated signal and calculated position size are passed to the `IBOrderExecutor`, which constructs and sends the final order to the Interactive Brokers TWS API for execution.

### Major Decision Points (Buy/Sell Calls)

The decision to place a trade is not a single "if-then" statement but a cascade of checks, creating a robust decision-making process:

1.  **The Primary Trigger (The Strategy)**: The `OrbStrategy` makes the initial call. The core decision is the **Opening Range Breakout**. This is the "offensive" part of the system that actively looks for trading opportunities.
2.  **The Contextual Filter (The Analyzers)**: While not explicitly stated as a hard filter in the `README-LOGIC.md`, a sophisticated implementation (likely in the `TradingEngine`) would use the analysis components as a filter. For example, it might decide to:
    *   Only take **BUY** signals if the `MarketSentimentAnalyzer` reports a "STRONG_UP" or "NEUTRAL" sentiment.
    *   Only trade stocks that are in a sector that is outperforming the market, according to the `SectorStrengthAnalyzer`.
3.  **The Final Veto (The Risk Managers)**: This is the critical defensive layer. The `RiskManager` has the final say. A potentially profitable signal from the strategy will be **rejected** if the current market conditions for that specific instrument are deemed too risky (e.g., spread is too wide, volatility is too high, or there's a risk of a short squeeze). This is the system's primary mechanism for capital preservation.

This multi-layered approach ensures that trades are only placed when a high-probability pattern (the strategy) aligns with favorable broader market conditions (the analysis) and an acceptable level of risk (the risk management).
