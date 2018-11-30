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

package com.ibm.hybrid.cloud.sample.portfolio;

/** JSON-B POJO class representing a Stock Purchase JSON object */
public class StockPurchase {
    private String id; //each trade will have its own UUID
    private String owner;
    private String symbol;
    private int shares;
    private double price;
    private String when;
    private double commission;


    public StockPurchase() { //default constructor
    }

    public StockPurchase(String initialId, String initialOwner, String initialSymbol, int initialShares,
            double initialPrice, String initialWhen, double initialCommission) {
        setOwner(initialOwner);
        setSymbol(initialSymbol);
        setShares(initialShares);
        setPrice(initialPrice);
        setWhen(initialWhen);
        setCommission(initialCommission);
    }

    public String getId() {
        return id;
    }

    public void setId(String newId) {
        id = newId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String newOwner) {
        owner = newOwner;
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

    public double getPrice() {
        return price;
    }

    public void setPrice(double newPrice) {
        price = newPrice;
    }

    public String getWhen() {
        return when;
    }

    public void setWhen(String newWhen) {
        when = newWhen;
    }

    public double getCommission() {
        return commission;
    }

    public void setCommission(double newCommission) {
        commission = newCommission;
    }

    public boolean equals(Object obj) {
        boolean isEqual = false;
        if ((obj != null) && (obj instanceof StockPurchase)) isEqual = toString().equals(obj.toString());
        return isEqual;
   }

    public String toString() {
        return "{\"owner\": \""+owner+"\", \"symbol\": \""+symbol+"\", \"shares\": "+shares+
               ", \"price\": "+price+", \"when\": \""+when+"\", \"commission\": "+commission+"}";
    }
}
