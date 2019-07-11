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

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.Id;
import javax.persistence.Column;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;

import org.eclipse.persistence.annotations.CascadeOnDelete;

@Entity
@Table
@NamedQuery(name = "Portfolio.findAll", query = "SELECT p FROM Portfolio p")
/** JSON-B POJO class representing a Portfolio JSON object */
public class Portfolio {

    @Id
    @Column(nullable = false, length = 32)
    private String owner;
    private double total;
    @Column(length = 8)
    private String loyalty;
    private double balance;
    private double commissions;
    private int free;
    private String sentiment;
    @Transient
    private double nextCommission;

    @OneToMany(mappedBy = "portfolio")
    @CascadeOnDelete
    private List<Stock> stocks = new ArrayList<Stock>();

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

    public List<Stock> getStocks() {
        return stocks;
    }

    public void setStocks(List<Stock> newStocks) {
        stocks = newStocks;
    }

    public void addStock(Stock newStock) {
        if (newStock != null) {
            stocks.add(newStock);
        }
    }

    public boolean equals(Object obj) {
        boolean isEqual = false;
        if ((obj != null) && (obj instanceof Portfolio)) isEqual = toString().equals(obj.toString());
        return isEqual;
   }

    public String toString() {
        return "{\"owner\": \""+owner+"\", \"total\": "+total+", \"loyalty\": \""+loyalty+"\", \"balance\": "+balance
               +", \"commissions\": "+commissions+", \"free\": "+free+", \"nextCommission\": "+nextCommission
               +", \"sentiment\": \""+sentiment+"\", \"stocks\": "+(stocks!=null?stocks.toString():"{}")+"}";
    }
}