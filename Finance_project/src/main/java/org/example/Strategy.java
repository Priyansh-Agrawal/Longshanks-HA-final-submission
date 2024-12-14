package org.example;

import java.math.BigDecimal;
import java.util.List;


public class Strategy {

    public static class Glob {
        public BigDecimal entryPrice;     // Price at which the position was entered
        public BigDecimal trailingPrice; // Current trailing price (for trailing stop-loss logic)
        public BigDecimal takeProfit;    // Take profit level
        public BigDecimal stopLoss;      // Stop loss level
        public BigDecimal capital;       // Capital available
        public int currPosition;         // Current position: 1 for long, -1 for short, 0 for no position
    }


    // Long entry conditions (LSMA crosses above Gaussian Filter with relaxed thresholds)
    public static boolean checkLongEntry(StockData data, Glob glob, List<BigDecimal> closingPricesList, BigDecimal[] gaussianFilter, BigDecimal lsma) {
        if (glob.currPosition != 0) return false; // No long entry if already in a position

        // Get the latest value of the Gaussian filter
        BigDecimal latestGaussianFilter = gaussianFilter.length > 0 ? gaussianFilter[gaussianFilter.length - 1] : BigDecimal.ZERO;

        // Relaxed condition: LSMA crosses above Gaussian Filter (with relaxed conditions)
        if (lsma.compareTo(latestGaussianFilter) > 0) {
            glob.entryPrice = data.getClose();
            glob.trailingPrice = glob.entryPrice;
            glob.takeProfit = glob.entryPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(0.20))); // 20% max take profit
            glob.stopLoss = glob.entryPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.15))); // 15% stop loss

            glob.currPosition = 1; // Enter long position
            return true;
        }
        return false;
    }

    // Short entry conditions (Gaussian Filter crosses above LSMA with relaxed thresholds)
    public static boolean checkShortEntry(StockData data, Glob glob, List<BigDecimal> closingPricesList, BigDecimal[] gaussianFilter, BigDecimal lsma) {
        if (glob.currPosition != 0) return false; // No short entry if already in a position

        // Get the latest value of the Gaussian filter
        BigDecimal latestGaussianFilter = gaussianFilter.length > 0 ? gaussianFilter[gaussianFilter.length - 1] : BigDecimal.ZERO;

        // Relaxed condition: Gaussian Filter crosses above LSMA (with relaxed conditions)
        if (latestGaussianFilter.compareTo(lsma) > 0) {
            glob.entryPrice = data.getClose();
            glob.trailingPrice = glob.entryPrice;
            glob.takeProfit = glob.entryPrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(0.20))); // 20% max take profit
            glob.stopLoss = glob.entryPrice.multiply(BigDecimal.ONE.subtract(BigDecimal.valueOf(0.15))); // 15% stop loss

            glob.currPosition = -1; // Enter short position
            return true;
        }
        return false;
    }

    // Long exit conditions (price falls below entry price with more lenient thresholds)
    public static boolean checkLongExit(StockData data, Glob glob, List<BigDecimal> closingPricesList) {
        if (glob.currPosition != 1) return false; // No exit if not in a long position

        BigDecimal volatility = Indicators.calculateVolatility(closingPricesList, 14);
        BigDecimal volExitThreshold = Indicators.calculateVolatility(closingPricesList, 7); // Example for threshold
        BigDecimal rsi = Indicators.calculateRSI(closingPricesList, 14);
        BigDecimal currentTEMA = Indicators.calculateTEMA(closingPricesList, 14);
        BigDecimal previousTEMA = Indicators.calculatePreviousTEMA(closingPricesList, 14);

        // Exit conditions
        boolean volCondition = volatility.compareTo(volExitThreshold) < 0; // Volatility below exit threshold
        boolean rsiCondition = rsi.compareTo(BigDecimal.valueOf(40)) > 0;  // RSI > 40
        boolean temaCondition = currentTEMA.compareTo(previousTEMA) < 0;   // Current TEMA is decreasing

        if (volCondition || rsiCondition || temaCondition) {
            glob.currPosition = 0; // Reset position after exit
            // System.out.println("Long exit triggered: Price " + close +
            //         ", Volatility " + volatility +
            //         ", RSI " + rsi +
            //         ", Current TEMA " + currentTEMA +
            //         ", Previous TEMA " + previousTEMA);
            return true;
        }

        return false; // No exit condition met
    }



    // Short exit conditions (price rises above entry price with more lenient thresholds)
    public static boolean checkShortExit(StockData data, Glob glob, List<BigDecimal> closingPricesList) {
        if (glob.currPosition != -1) return false; // No exit if not in a short position
        BigDecimal close = data.getClose();
        BigDecimal volatility = Indicators.calculateVolatility(closingPricesList, 14);
        BigDecimal volEntryThreshold = Indicators.calculateVolatility(closingPricesList, 7); // Example for threshold
        BigDecimal rsi = Indicators.calculateRSI(closingPricesList, 14);
        // BigDecimal currentTEMA = Indicators.calculateTEMA(closingPricesList, 14);
        // BigDecimal previousTEMA = Indicators.calculatePreviousTEMA(closingPricesList, 14);

        // Exit conditions
        boolean rsiCondition = rsi.compareTo(BigDecimal.valueOf(40)) > 0;
        boolean volCondition = volatility.compareTo(volEntryThreshold) < 0;         // Volatility below threshold




        // Check for take profit or stop loss conditions
        boolean shouldExit = close.compareTo(glob.takeProfit) <= 0 || // Exit if price hits or drops below take profit
                close.compareTo(glob.stopLoss) >= 0;    // Exit if price hits or exceeds stop loss

        if (shouldExit || volCondition || rsiCondition) {
            glob.currPosition = 0; // Reset position after exit
            System.out.println("Short exit triggered: Price " + close +
                    ", Take Profit " + glob.takeProfit +
                    ", Stop Loss " + glob.stopLoss +
                    ", Volatility " + volatility);
            return true;
        }

        return false;
    }





}