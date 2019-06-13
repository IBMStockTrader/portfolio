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

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQuery;

import javax.persistence.Column;
import javax.persistence.ManyToOne;

@Entity
@Table
@NamedQuery(name = "Stock.findAll", query = "SELECT s FROM Stock WHERE s.owner = :owner")
/** JSON-B POJO class representing a Stock JSON object */
public class Stock {

    @Id
    @Column(nullable = false, length = 8)
    private String symbol;
    private int shares;
    private double commission;
    private double price;
    private double total;
    private String date;

    @ManyToOne
    @JoinColumn(name = "owner")
    private Portfolio portfolio;

    public Stock() { //default constructor
    }

    public Stock(String initialSymbol) { //primary key constructor
        setSymbol(initialSymbol);
    }

    public Stock(String initialSymbol, int initialShares, double initialCommission,
                 double initialPrice, double initialTotal, String initialDate) {
        setSymbol(initialSymbol);
        setShares(initialShares);
        setCommission(initialCommission);
        setPrice(initialPrice);
        setTotal(initialTotal);
        setDate(initialDate);
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String newSymbol) {
        symbol = newSymbol;
    }

    public int getShares() {
        return shares;
    }

    public void setShares(int newShares) {
        shares = newShares;
    }

    public double getCommission() {
        return commission;
    }

    public void setCommission(double newCommission) {
        commission = newCommission;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double newPrice) {
        price = newPrice;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double newTotal) {
        total = newTotal;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String newDate) {
        date = newDate;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public void setPortfolio(Portfolio newPortfolio) {
        portfolio = newPortfolio;
    }

    public String toString() {
        return "{\"symbol\": \""+symbol+"\", \"shares\": "+shares+", \"commission\": "+commission
               +", \"price\": "+price+", \"total\": "+total+", \"date\": \""+date+"\"}";
    }
}
