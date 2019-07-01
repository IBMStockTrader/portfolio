/*
       Copyright 2017 IBM Corp All Rights Reserved

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** JSON-B POJO class representing a Portfolio JSON object */
public class Portfolio {
    private String owner;
    private double total;
    private String loyalty;
    private double balance;
    private double commissions;
    private int free;
    private String sentiment;
    private double nextCommission;
    private Map<String,Stock> stocks = new HashMap<>();


    public Portfolio() { //default constructor
    }

    public Portfolio(String initialOwner) { //primary key constructor
        setOwner(initialOwner);
    }

    public Portfolio(String initialOwner, double initialTotal, String initialLoyalty, double initialBalance,
                     double initialCommissions, int initialFree, String initialSentiment, double initialNextCommission) {
        setOwner(initialOwner);
        setTotal(initialTotal);
        setLoyalty(initialLoyalty);
        setBalance(initialBalance);
        setCommissions(initialCommissions);
        setFree(initialFree);
        setSentiment(initialSentiment);
        setNextCommission(initialNextCommission);
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String newOwner) {
        owner = newOwner;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double newTotal) {
        total = newTotal;
    }

    public String getLoyalty() {
        return loyalty;
    }

    public void setLoyalty(String newLoyalty) {
        loyalty = newLoyalty;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double newBalance) {
        balance = newBalance;
    }

    public double getCommissions() {
        return commissions;
    }

    public void setCommissions(double newCommissions) {
        commissions = newCommissions;
    }

    public int getFree() {
        return free;
    }

    public void setFree(int newFree) {
        free = newFree;
    }

    public String getSentiment() {
        return sentiment;
    }

    public void setSentiment(String newSentiment) {
        sentiment = newSentiment;
    }

    public double getNextCommission() {
        return nextCommission;
    }

    public void setNextCommission(double newNextCommission) {
        nextCommission = newNextCommission;
    }

    public Map<String,Stock> getStocks() {
        return stocks;
    }

    public void setStocks(Map<String,Stock> newStocks) {
        stocks = newStocks;
    }

    public void addStock(Stock newStock) {
        Objects.requireNonNull(newStock);
        stocks.put(newStock.getSymbol(), newStock);
    }

    public boolean equals(Object other) {
        if (!(other instanceof Portfolio))
            return false;
        Portfolio o = (Portfolio) other;
        return Objects.equals(owner, o.owner) &&
                Objects.equals(total, o.total) &&
                Objects.equals(loyalty, o.loyalty) &&
                Objects.equals(balance, o.balance) &&
                Objects.equals(commissions, o.commissions) &&
                Objects.equals(free, o.free) &&
                Objects.equals(nextCommission, o.nextCommission) &&
                Objects.equals(sentiment, o.sentiment) &&
                Objects.equals(stocks, o.stocks);
   }

    public String toString() {
        return "{\"owner\": \""+owner+"\", \"total\": "+total+", \"loyalty\": \""+loyalty+"\", \"balance\": "+balance
               +", \"commissions\": "+commissions+", \"free\": "+free+", \"nextCommission\": "+nextCommission
               +", \"sentiment\": \""+sentiment+"\", \"stocks\": "+(stocks!=null?stocks.toString():"{}")+"}";
    }
}
