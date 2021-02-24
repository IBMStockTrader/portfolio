/*
       Copyright 2017-2021 IBM Corp All Rights Reserved

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
import java.util.Iterator;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.Id;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.json.JsonObject;
import javax.json.bind.annotation.JsonbTransient;

//JSON-P 1.0 (JSR 353).  This replaces my old usage of IBM's JSON4J (com.ibm.json.java.JSONObject)
import javax.json.Json;
import javax.json.JsonObjectBuilder;

@Entity
@Table
@NamedQuery(name = "Portfolio.findAll", query = "SELECT p FROM Portfolio p")
/** JSON-B POJO class representing a Portfolio JSON object */
public class Portfolio {

    @Id
    @Column(nullable = false, length = 32)
    private String owner;
    private double total;
    @Transient
    JsonObject stocks;

    @JsonbTransient
    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL)
    private List<Stock> stockList = new ArrayList<Stock>();

    public Portfolio() { //default constructor
    }

    public Portfolio(String initialOwner) { //primary key constructor
        setOwner(initialOwner);
    }

    public Portfolio(String initialOwner, double initialTotal) {
        setOwner(initialOwner);
        setTotal(initialTotal);
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

    public JsonObject getStocks() {
        return stocks;
    }

    public void setStocks(JsonObject newStocks) {
        stocks = newStocks;
    }
   
    public void addStock(Stock newStock) {
        if (newStock != null) {
            String symbol = newStock.getSymbol();
            if (symbol != null) {
                JsonObjectBuilder stocksBuilder = Json.createObjectBuilder();
            
                if (stocks != null) { //JsonObject is immutable, so copy current "stocks" into new builder
                    Iterator<String> iter = stocks.keySet().iterator();
                    while (iter.hasNext()) {
                        String key = iter.next();
                        JsonObject obj = stocks.getJsonObject(key);
                        stocksBuilder.add(key, obj);
                    }
                }

                //can only add a JSON-P object to a JSON-P object; can't add a JSON-B object.  So converting...
                JsonObjectBuilder builder = Json.createObjectBuilder();

                builder.add("symbol", symbol);
                builder.add("shares", newStock.getShares());
                builder.add("commission", newStock.getCommission());
                builder.add("price", newStock.getPrice());
                builder.add("total", newStock.getTotal());
                builder.add("date", newStock.getDate());

                JsonObject stock = builder.build();

                stocksBuilder.add(symbol, stock); //might be replacing an item; caller needs to do any merge (like updatePortfolio does)
                stocks = stocksBuilder.build();
            }
        }
    }

    public boolean equals(Object obj) {
        boolean isEqual = false;
        if ((obj != null) && (obj instanceof Portfolio)) isEqual = toString().equals(obj.toString());
        return isEqual;
   }

    public String toString() {
        return "{\"owner\": \""+owner+"\", \"total\": "+total+", \"stocks\": "+(stocks!=null?stocks.toString():"{}")+"}";
    }
}
