/*
       Copyright 2017-2021 IBM Corp, All Rights Reserved
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

package com.ibm.hybrid.cloud.sample.stocktrader.portfolio.client;

import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.Quote;

import jakarta.enterprise.context.ApplicationScoped;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Path;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@ApplicationPath("/")
@Path("/")
@ApplicationScoped
@RegisterRestClient
/** mpRestClient "remote" interface for the stock quote microservice */
public interface StockQuoteClient {
	@GET
	@Path("/")
	@Produces("application/json")
	public Quote[] getAllCachedQuotes(@HeaderParam("Authorization") String jwt);

	@GET
	@Path("/{symbol}")
	@Produces("application/json")
	public Quote getStockQuote(@HeaderParam("Authorization") String jwt, @PathParam("symbol") String symbol);
}
