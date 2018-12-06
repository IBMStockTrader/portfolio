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

/** JSON-B POJO class representing an ODM business rule for determining the loyalty level of a portfolio */
public class LoyaltyDecision {
    private double tradeTotal = 0.0;
    private String loyalty = "UNKNOWN";


    public LoyaltyDecision() { //default constructor
    }

    public LoyaltyDecision(double initialTradeTotal) { //primary key constructor
        setTradeTotal(initialTradeTotal);
    }

    public double getTradeTotal() {
        return tradeTotal;
    }

    public void setTradeTotal(double newTradeTotal) {
        tradeTotal = newTradeTotal;
    }

    public String getLoyalty() {
        return loyalty;
    }

    public void setLoyalty(String newLoyalty) {
        loyalty = newLoyalty;
    }

    public boolean equals(Object obj) {
        boolean isEqual = false;
        if ((obj != null) && (obj instanceof LoyaltyDecision)) isEqual = toString().equals(obj.toString());
        return isEqual;
   }

    public String toString() {
        return "{\"tradeTotal\": "+tradeTotal+", \"loyalty\": \""+loyalty+"\"}";
    }
}
