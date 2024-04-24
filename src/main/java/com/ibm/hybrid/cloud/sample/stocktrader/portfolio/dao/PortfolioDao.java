/*
       Copyright 2019-2021 IBM Corp, All Rights Reserved
       Copyright 2023-2024 Kyndryl, All Rights Reserved

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

import jakarta.enterprise.context.RequestScoped;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.Portfolio;

@RequestScoped
public class PortfolioDao {

    @PersistenceContext(name = "jpa-unit")
    private EntityManager em;

    public void createPortfolio(Portfolio portfolio) {
        em.persist(portfolio);
    }

    public Portfolio readEvent(String owner) {
        return em.find(Portfolio.class, owner);
    }

    public void updatePortfolio(Portfolio portfolio) {
        em.merge(portfolio);
        em.flush();
    }

    public void deletePortfolio(Portfolio portfolio) {
        em.remove(em.merge(portfolio));
    }

    public List<Portfolio> readAllPortfolios() {
        return em.createNamedQuery("Portfolio.findAll", Portfolio.class).getResultList();
    }

}