/*
       Copyright 2017, 2018 IBM Corp All Rights Reserved
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

package com.ibm.hybrid.cloud.sample.portfolio.data;

import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
@Schema(name="Portfolio")
public class PortfolioModel {
    private String owner;
    private double total;
    private String loyalty;
    private Map<String, StockModel> stocks;

    public PortfolioModel() {

    }

    public PortfolioModel(String owner, double total, String loyalty) {
        this.setOwner(owner);
        this.setTotal(total);
        this.setLoyalty(loyalty);
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public double getTotal() {
        return total;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    public String getLoyalty() {
        return loyalty;
    }

    public void setLoyalty(String loyalty) {
        this.loyalty = loyalty;
    }

    public Map<String, StockModel> getStocks() {
        return stocks;
    }

    public void setStocks(Map<String, StockModel> stocks) {
        this.stocks = stocks;
    }
}
