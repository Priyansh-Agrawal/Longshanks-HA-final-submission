package org.example;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;

public class StrategyTester {
    private static final double INITIAL_CAPITAL = 1_000_000.0;

    public static double calculateMaxDrawdown(List<BigDecimal> portfolioValues) {
        if (portfolioValues == null || portfolioValues.isEmpty()) {
            return 0.0;
        }

        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal peak = portfolioValues.get(0);

        for (BigDecimal value : portfolioValues) {
            peak = peak.max(value);
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentDrawdown = peak.subtract(value).divide(peak, MathContext.DECIMAL128);
                maxDrawdown = maxDrawdown.max(currentDrawdown);
            }
        }

        return maxDrawdown.doubleValue();
    }

    private static void simulate() {
        System.out.println("Simulation started.");

        StockDataManager dataManager = new StockDataManager();
        dataManager.loadHistoricalDataFromCSV("/Users/priyanshagrawal/IdeaProjects/Demo-Robert/Finance_project/stock_data/consolidated_stock_data.csv");
        System.out.println("Data loaded successfully.");

        List<String> stocks = dataManager.getStocks();
        System.out.printf("Total stocks to process: %d%n", stocks.size());

        Map<String, BigDecimal> cashBalance = new HashMap<>();  // Cash available for each stock
        Map<String, Long> sharesHeld = new HashMap<>();        // Number of shares held for each stock
        Map<String, Strategy.Glob> globMap = new HashMap<>();  // Separate glob for each stock
        Map<String, List<BigDecimal>> stockPortfolioValues = new HashMap<>(); // Track values per stock
        BigDecimal initialStockAllocation = BigDecimal.valueOf(INITIAL_CAPITAL / stocks.size());

        // Initialize maps for each stock
        for (String stock : stocks) {
            cashBalance.put(stock, initialStockAllocation);
            sharesHeld.put(stock, 0L);
            globMap.put(stock, new Strategy.Glob());
            globMap.get(stock).capital = initialStockAllocation;
            stockPortfolioValues.put(stock, new ArrayList<>());
        }

        // Find the maximum number of trading days across all stocks
        int maxTradingDays = stocks.stream()
                .mapToInt(stock -> dataManager.getHistoricalData(stock).size())
                .max()
                .orElse(0);

        // Process each day for all stocks
        for (int day = 14; day < maxTradingDays; day++) {
            for (String stock : stocks) {
                List<StockData> stockData = dataManager.getHistoricalData(stock);
                if (day >= stockData.size()) continue;

                System.out.printf("Processing stock: %s for day %d%n", stock, day);

                List<BigDecimal> closingPricesList = new ArrayList<>();
                List<BigDecimal> highs = new ArrayList<>();
                List<BigDecimal> lows = new ArrayList<>();

                // Get historical data up to current day
                for (int j = Math.max(0, day - 14); j <= day; j++) {
                    StockData historicalData = stockData.get(j);
                    closingPricesList.add(historicalData.getClose());
                    highs.add(historicalData.getHigh());
                    lows.add(historicalData.getLow());
                }

                StockData data = stockData.get(day);
                Strategy.Glob glob = globMap.get(stock);

                System.out.println("  Calculating indicators...");
                BigDecimal lsma = Indicators.calculateLSMA(closingPricesList, 14);
                BigDecimal[] gaussianFilter = Indicators.getGaussianFilter(closingPricesList, 14, 2);
                System.out.println("  Indicators calculated.");

                BigDecimal closingPrice = data.getAdjClose();
                if (closingPrice.compareTo(BigDecimal.ZERO) <= 0) continue;

                // Long Entry
                if (Strategy.checkLongEntry(data, glob, closingPricesList, gaussianFilter, lsma)) {
                    System.out.println("  Long entry triggered.");
                    long bought = Math.min(cashBalance.get(stock).divide(closingPrice, MathContext.DECIMAL128).longValue(), data.getVolume());
                    sharesHeld.put(stock, sharesHeld.get(stock) + bought);
                    cashBalance.put(stock, cashBalance.get(stock).subtract(closingPrice.multiply(BigDecimal.valueOf(bought))));
                    System.out.printf("  Bought %d shares of %s at $%.2f%n", bought, stock, closingPrice.doubleValue());
                }

                // Short Entry
                if (Strategy.checkShortEntry(data, glob, closingPricesList, gaussianFilter, lsma)) {
                    System.out.println("  Short entry triggered.");
                    long sold = Math.min(sharesHeld.get(stock), data.getVolume());
                    sharesHeld.put(stock, sharesHeld.get(stock) - sold);
                    cashBalance.put(stock, cashBalance.get(stock).add(closingPrice.multiply(BigDecimal.valueOf(sold))));
                    System.out.printf("  Short sold %d shares of %s at $%.2f%n", sold, stock, closingPrice.doubleValue());
                }

                // Long Exit
                if (glob.currPosition == 1 && Strategy.checkLongExit(data, glob, closingPricesList)) {
                    System.out.println("  Long exit triggered.");
                    long sold = sharesHeld.get(stock);
                    sharesHeld.put(stock, 0L);
                    cashBalance.put(stock, cashBalance.get(stock).add(closingPrice.multiply(BigDecimal.valueOf(sold))));
                    System.out.printf("  Sold %d shares of %s at $%.2f%n", sold, stock, closingPrice.doubleValue());
                }

                // Short Exit
                if (glob.currPosition == -1 && Strategy.checkShortExit(data, glob, closingPricesList)) {
                    System.out.println("  Short exit triggered.");
                    long bought = Math.min(cashBalance.get(stock).divide(closingPrice, MathContext.DECIMAL128).longValue(), data.getVolume());
                    sharesHeld.put(stock, sharesHeld.get(stock) + bought);
                    cashBalance.put(stock, cashBalance.get(stock).subtract(closingPrice.multiply(BigDecimal.valueOf(bought))));
                    System.out.printf("  Covered %d shares of %s at $%.2f%n", bought, stock, closingPrice.doubleValue());
                }

                // Calculate current position value (cash + market value of shares)
                BigDecimal positionValue = cashBalance.get(stock).add(closingPrice.multiply(BigDecimal.valueOf(sharesHeld.get(stock))));
                stockPortfolioValues.get(stock).add(positionValue);
                System.out.printf("  Position value: $%.2f%n", positionValue.doubleValue());
            }
        }

        // Combine all stock portfolio values into daily portfolio values
        List<BigDecimal> dailyPortfolioValues = new ArrayList<>();
        for (int i = 0; i < maxTradingDays - 14; i++) {
            BigDecimal dailyTotal = BigDecimal.ZERO;
            for (String stock : stocks) {
                List<BigDecimal> stockValues = stockPortfolioValues.get(stock);
                if (i < stockValues.size()) {
                    dailyTotal = dailyTotal.add(stockValues.get(i));
                }
            }
            dailyPortfolioValues.add(dailyTotal);
        }

        // Calculate Sharpe ratio
        List<BigDecimal> dailyReturns = new ArrayList<>();
        for (int i = 1; i < dailyPortfolioValues.size(); i++) {
            BigDecimal dailyReturn = dailyPortfolioValues.get(i).subtract(dailyPortfolioValues.get(i - 1))
                    .divide(dailyPortfolioValues.get(i - 1), MathContext.DECIMAL128);
            dailyReturns.add(dailyReturn);
        }

        BigDecimal averageReturn = dailyReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size()), MathContext.DECIMAL128);
        BigDecimal variance = dailyReturns.stream()
                .map(r -> r.subtract(averageReturn).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(dailyReturns.size()), MathContext.DECIMAL128);
        BigDecimal standardDeviation = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
        BigDecimal sharpeRatio = averageReturn.divide(standardDeviation, MathContext.DECIMAL128);

        // Calculate final performance metrics
        double maxDrawdown = calculateMaxDrawdown(dailyPortfolioValues);
        BigDecimal totalPnL = !dailyPortfolioValues.isEmpty() ?
                dailyPortfolioValues.get(dailyPortfolioValues.size() - 1)
                        .subtract(BigDecimal.valueOf(INITIAL_CAPITAL)) :
                BigDecimal.ZERO;
        BigDecimal finalCapital = BigDecimal.valueOf(INITIAL_CAPITAL).add(totalPnL);

        System.out.println("\nBacktest Results:");
        System.out.printf("Initial Capital: $%.2f%n", INITIAL_CAPITAL);
        System.out.printf("Total P&L: $%.2f%n", totalPnL.doubleValue());
        System.out.printf("Final Capital: $%.2f%n", finalCapital.doubleValue());
        System.out.printf("Return: %.2f%%%n", (totalPnL.divide(BigDecimal.valueOf(INITIAL_CAPITAL), MathContext.DECIMAL128).multiply(BigDecimal.valueOf(100))).doubleValue());
        System.out.printf("Max Drawdown: %.2f%%%n", maxDrawdown * 100);
        System.out.printf("Annualized Sharpe Ratio: %.6f%n%n", sharpeRatio.doubleValue() * Math.sqrt(252));

        System.out.println("Simulation complete.");
    }

    public static void main(String[] args) {
        simulate();
    }
}