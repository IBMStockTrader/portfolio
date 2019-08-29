/*
       Copyright 2019 IBM Corp All Rights Reserved

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

package com.ibm.hybrid.cloud.sample.stocktrader.portfolio.dao;

import javax.enterprise.context.RequestScoped;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.Stock;

@RequestScoped
public class StockDao {

    @PersistenceContext(name = "jpa-unit")
    private EntityManager em;

    public void createStock(Stock stock) {
        em.persist(stock);
    }

    public Stock readEvent(String symbol) {
        return em.find(Stock.class, symbol);
    }

    public void updateStock(Stock stock) {
        em.merge(stock);
        em.flush();
    }

    public void deleteStock(Stock stock) {
        em.remove(em.merge((stock)));
    }

    public void detachStock(Stock stock) {
        em.detach(stock);
    }

    public List<Stock> readStockByOwner(String owner) {
        return em.createNamedQuery("Stock.findByOwner", Stock.class)
            .setParameter("owner", owner).getResultList();
    }
    public List<Stock> readStockByOwnerAndSymbol(String owner, String symbol) {
        return em.createNamedQuery("Stock.findByOwnerAndSymbol", Stock.class)
            .setParameter("owner", owner)
            .setParameter("symbol", symbol).getResultList();
    }
}