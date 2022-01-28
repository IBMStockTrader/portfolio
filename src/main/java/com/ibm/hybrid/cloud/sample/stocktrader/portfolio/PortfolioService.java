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

package com.ibm.hybrid.cloud.sample.stocktrader.portfolio;

import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.client.StockQuoteClient;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.Portfolio;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.Quote;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.Stock;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.dao.PortfolioDao;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.dao.StockDao;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

//Logging (JSR 47)
import java.util.logging.Level;
import java.util.logging.Logger;

//JDBC 4.0 (JSR 221)
import java.sql.SQLException;
import javax.sql.DataSource;

//CDI 2.0
import javax.inject.Inject;
import javax.enterprise.context.RequestScoped;

//mpConfig 1.3
import org.eclipse.microprofile.config.inject.ConfigProperty;

//mpHealth stuff moved to health subpackage

//mpJWT 1.1
import org.eclipse.microprofile.auth.LoginConfig;

//mpMetrics 2.0
import org.eclipse.microprofile.metrics.annotation.Counted;

//mpOpenTracing 1.3
import org.eclipse.microprofile.opentracing.Traced;

//mpRestClient 1.3
import org.eclipse.microprofile.rest.client.inject.RestClient;

//Transactions
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

//JNDI 1.0
import javax.naming.InitialContext;
import javax.naming.NamingException;

//Servlet 4.0
import javax.servlet.http.HttpServletRequest;

//JAX-RS 2.1 (JSR 339)
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.BadRequestException; //400 error
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;

@ApplicationPath("/")
@Path("/")
@LoginConfig(authMethod = "MP-JWT", realmName = "jwt-jaspi")
@RequestScoped //enable interceptors like @Transactional (note you need a WEB-INF/beans.xml in your war)
/** This version stores the Portfolios via JPA to DB2 (or whatever JDBC provider is defined in your server.xml).
 */
public class PortfolioService extends Application {
	private static Logger logger = Logger.getLogger(PortfolioService.class.getName());

	private static final double ERROR            = -1.0;
	private static final int    CONFLICT         = 409;         //odd that JAX-RS has no ConflictException
	private static final short  MAX_ERRORS       = 3;           //health check will fail if this threshold is met
	private static final String FAIL             = "FAIL";      //trying to create a portfolio with this name will always throw a 400

	private static boolean staticInitialized = false;
	public  static short   consecutiveErrors = 0; //used in health check

	private static DataSource datasource = null;

	private static SimpleDateFormat dateFormatter = null;

	private PortfolioUtilities utilities = new PortfolioUtilities();

	@Inject
	private PortfolioDao portfolioDAO;

	@Inject
	private StockDao stockDAO;

	private @Inject @RestClient StockQuoteClient stockQuoteClient;

	private @Inject @ConfigProperty(name = "KAFKA_HISTORY_TOPIC", defaultValue = "stocktrader") String kafkaTopic;
	private @Inject @ConfigProperty(name = "KAFKA_ADDRESS", defaultValue = "") String kafkaAddress;

	private static boolean publishToTradeHistoryTopic;
	
	// Override Stock Quote Client URL if secret is configured to provide URL
	static {
		publishToTradeHistoryTopic = Boolean.parseBoolean(System.getenv("TRADE_HISTORY_ENABLED"));
		logger.info("Publishing to Trade History topic enabled: "+publishToTradeHistoryTopic);

		String mpUrlPropName = StockQuoteClient.class.getName() + "/mp-rest/url";
		String urlFromEnv = System.getenv("STOCK_QUOTE_URL");
		if ((urlFromEnv != null) && !urlFromEnv.isEmpty()) {
			logger.info("Using Stock Quote URL from config map: " + urlFromEnv);
			System.setProperty(mpUrlPropName, urlFromEnv);
		} else {
			logger.info("Stock Quote URL not found from env var from config map, so defaulting to value in jvm.options: " + System.getProperty(mpUrlPropName));
		}
	}

	public static boolean isReady() { //determines answer to readiness probe
		if (!staticInitialized) try {
			staticInitialize();
		} catch (Throwable t) {
			PortfolioUtilities.logException(t);
		}

		return (datasource!=null); //the only hard prereq for Portfolio is that JDBC is configured properly
	}

	public static boolean isHealthy() { //determines answer to livenesss probe
		return consecutiveErrors<MAX_ERRORS;
	}

	@GET
	@Path("/")
	@Produces(MediaType.APPLICATION_JSON)
	@Transactional
//	@RolesAllowed({"StockTrader", "StockViewer"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public Portfolio[] getPortfolios() throws SQLException {
		int count = 0;
		Portfolio[] portfolios = null;

		try {
			logger.fine("Running following SQL: SELECT * FROM Portfolio");
			List<Portfolio> portfolioList = portfolioDAO.readAllPortfolios();
			count = portfolioList.size();

			portfolios = new Portfolio[count];
			portfolioList.toArray(portfolios);

			consecutiveErrors = 0;
		} catch (Throwable t) {
			consecutiveErrors++;
			logger.warning("Failed getting portfolios");
			PortfolioUtilities.logException(t);
		}

		if (count == 0) {
			logger.info("No portfolios to return");
		} else {
			logger.fine("Returning "+count+" portfolios");

			if (logger.isLoggable(Level.FINE)) {
				StringBuffer json = new StringBuffer("[");
				for (int index=0; index<count; index++) {
					Portfolio portfolio = portfolios[index];
					json.append(portfolio.toString());
					if (index != count-1) json.append(", ");
				}
				json.append("]");
				logger.fine(json.toString());
			}
		}

		return portfolios;
	}

	@POST
	@Path("/{owner}")
	@Produces(MediaType.APPLICATION_JSON)
	@Counted(name="portfolios", displayName="Stock Trader portfolios", description="Number of portfolios created in the Stock Trader application")
	@Transactional
	//	@RolesAllowed({"StockTrader"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public Portfolio createPortfolio(@PathParam("owner") String owner, @QueryParam("accountID") String accountID) throws SQLException {
		Portfolio portfolio = null;
		if (owner != null) try {
			if (owner.equalsIgnoreCase(FAIL)) {
				logger.warning("Throwing a 400 error for owner: "+owner);
				consecutiveErrors++;
				throw new BadRequestException("Invalid value for portfolio owner: "+owner);
			}

			logger.fine("Creating portfolio for "+owner+ "with accountID = "+accountID);

			portfolio = new Portfolio(owner, 0.0, accountID);

			logger.fine("Running following SQL: INSERT INTO Portfolio VALUES ('"+owner+"', 0.0, "+accountID+")");
			
			if (portfolioDAO.readEvent(owner) == null) {
				portfolioDAO.createPortfolio(portfolio);
			} else {
				logger.warning("Portfolio already exists for: "+owner);
				throw new WebApplicationException("Portfolio already exists for "+owner+"!", CONFLICT);			
			}

			logger.fine("Portfolio created successfully");
			consecutiveErrors = 0;
		} catch (Throwable t) {
			consecutiveErrors++;
			logger.warning("Failed creating portfolio for "+owner);
			PortfolioUtilities.logException(t);
		}

		return portfolio;
	}

	@GET
	@Path("/{owner}")
	@Produces(MediaType.APPLICATION_JSON)
	@Transactional(TxType.REQUIRED) //two-phase commit (XA) across JDBC and JMS
//	@RolesAllowed({"StockTrader", "StockViewer"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public Portfolio getPortfolio(@PathParam("owner") String owner, @QueryParam("immutable") boolean immutable, @Context HttpServletRequest request) throws IOException, SQLException {
		logger.fine("Getting portfolio for "+owner);

		Portfolio portfolio = getPortfolioWithoutStocks(owner); //throws a 404 if not found
		if (!immutable && (portfolio != null)) try {
			double overallTotal = 0;

			logger.fine("Running following SQL: SELECT * FROM Stock WHERE owner = '"+owner+"'");
			List<Stock> results = stockDAO.readStockByOwner(owner);

			int count = 0;
			logger.fine("Iterating over results");
			for (Stock stock : results) {
				count++;

				String symbol = stock.getSymbol();
				int shares = stock.getShares();

				String date = null;
				double price = 0;
				double total = 0;
				Quote quote = null;
				try {
					//call the StockQuote microservice to get the current price of this stock
					logger.fine("Calling stock-quote microservice for "+symbol);

					String jwt = request.getHeader("Authorization");
					quote = stockQuoteClient.getStockQuote(jwt, symbol);

					if (quote != null) {
						date = quote.getDate();
						price = quote.getPrice();

						total = shares * price;

						//TODO - is it OK to update rows (not adding or deleting) in the Stock table while iterating over its contents?
						logger.fine("Updated "+symbol+" entry for "+owner+" in Stock table");
						stock.setDate(date);
						stock.setPrice(price);
						stock.setTotal(total);
						stock.setPortfolio(portfolio);

						stockDAO.updateStock(stock);
						stockDAO.detachStock(stock);
					} else {
						logger.warning("Received null from StockQuote microservice.  Using cached values instead");
					}
				} catch (Throwable t) {
					logger.warning("Unable to get fresh stock quote.  Using cached values instead");
					utilities.logException(t);
				}

				if (quote == null) {
					date = stock.getDate();
					if (date == null) {
						Date now = new Date();
						if (dateFormatter == null) dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
						date = dateFormatter.format(now);
					}

					price = stock.getPrice();
					if (price == 0) { //SQL returns 0 for a double if the column was null
						price = ERROR;
						total = ERROR;
					} else {
						total = shares * price;
					}
				}

				stock.setDate(date);
				stock.setPrice(price);
				stock.setTotal(total);

				if (price != ERROR) //-1 is the marker for not being able to get the stock quote.  But don't actually add that value
					overallTotal += total;

				logger.fine("Adding "+symbol+" to portfolio for "+owner);
				portfolio.addStock(stock);
			}
			logger.fine("Processed "+count+" stocks for "+owner);

			portfolio.setTotal(overallTotal);

			portfolioDAO.updatePortfolio(portfolio);

			logger.fine("Returning "+portfolio.toString());
			consecutiveErrors = 0;
		} catch (Throwable t) {
			consecutiveErrors++;
			logger.warning("Failure refreshing portfolio for "+owner);
			PortfolioUtilities.logException(t);
		} else {
			if (portfolio == null) {
				logger.warning("No portfolio found for "+owner); //shouldn't get here; an exception with a 404 should be thrown instead
			}
		}

		return portfolio;
	}

	@Traced
	private Portfolio getPortfolioWithoutStocks(String owner) throws SQLException {
		Portfolio portfolio = null;

		try {
			logger.fine("Running following SQL: SELECT * FROM Portfolio WHERE owner = '"+owner+"'");

			portfolio = portfolioDAO.readEvent(owner);
		} catch (Throwable t) {
			consecutiveErrors++;
			logger.warning("Problem retrieving portfolio for "+owner);
			PortfolioUtilities.logException(t);
		}

		if (portfolio != null) {
			logger.fine("Found portfolio for "+owner);
			consecutiveErrors = 0;
		} else {
			logger.warning("No such portfolio: "+owner); //this remains info level since this means something could be wrong
			throw new NotFoundException("No such portfolio: "+owner); //send back a 404
		}

		logger.fine("Returning "+((portfolio==null) ? "null" : portfolio.toString()));
		return portfolio;
	}
    
	@PUT
	@Path("/{owner}")
	@Produces(MediaType.APPLICATION_JSON)
	@Transactional(TxType.REQUIRED) //two-phase commit (XA) across JDBC and JMS
//	@RolesAllowed({"StockTrader"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public Portfolio updatePortfolio(@PathParam("owner") String owner, @QueryParam("symbol") String symbol, @QueryParam("shares") int shares, @QueryParam("commission") double commission, @Context HttpServletRequest request) throws IOException, SQLException {
		logger.fine("Updating portfolio for "+owner);

		Stock stock = new Stock();
		stock.setCommission(commission);
		stock.setSymbol(symbol);
		stock.setShares(shares);

		Portfolio portfolio = getPortfolioWithoutStocks(owner);
		try {
			if (portfolio != null) {
				stock.setPortfolio(portfolio);
			}

			logger.fine("Running following SQL: SELECT * FROM Stock WHERE owner = '"+owner+"' and symbol = '"+symbol+"'");
			List<Stock> results = stockDAO.readStockByOwnerAndSymbol(owner, symbol);

			boolean deleteStock = false;
			if (!results.isEmpty()) { //row exists
				stock = results.get(0);
				int oldShares = stock.getShares();
				double oldCommission = stock.getCommission();

				int newShares = oldShares+shares;
				double newCommission = oldCommission+commission;
				if (newShares > 0) {
					logger.fine("Running following SQL: UPDATE Stock SET shares = "+newShares+", commission = "+newCommission+" WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
					stock.setShares(newShares);
					stock.setCommission(newCommission);
					//getPortfolio will fill in the price, date and total
				} else {
					logger.fine("Running following SQL: DELETE FROM Stock WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
					deleteStock = true;
				}
			} else {
				logger.fine("Running following SQL: INSERT INTO Stock (owner, symbol, shares, commission) VALUES ('"+owner+"', '"+symbol+"', "+shares+", "+commission+")");
				stockDAO.createStock(stock);
				//getPortfolio will fill in the price, date and total
			}

			//getPortfolio will fill in the overall total and loyalty, and commit or rollback the transaction
			logger.fine("Refreshing portfolio for "+owner);
			portfolio = getPortfolio(owner, false, request);

			if (publishToTradeHistoryTopic) utilities.invokeKafka(portfolio, symbol, shares, commission, kafkaAddress, kafkaTopic);

			if (deleteStock) stockDAO.deleteStock(stock); //delay deleting until after invoking Kafka, which needs the quote info that would be gone after the delete
			consecutiveErrors = 0;
		} catch (Throwable t) {
			consecutiveErrors++;
			logger.warning("Unable to update portfolio for "+owner);
			PortfolioUtilities.logException(t);
		}

		return portfolio;
	}

	@DELETE
	@Path("/{owner}")
	@Produces(MediaType.APPLICATION_JSON)
	@Transactional
//	@RolesAllowed({"StockTrader"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public Portfolio deletePortfolio(@PathParam("owner") String owner) throws SQLException {
		logger.fine("Delete portfolio for "+owner);

		Portfolio portfolio = getPortfolioWithoutStocks(owner);

		try {
			logger.fine("Running following SQL: DELETE FROM Portfolio WHERE owner = '"+owner+"'");
			portfolioDAO.deletePortfolio(portfolio);
			logger.fine("Successfully deleted portfolio for "+owner);
		} catch (Throwable t) {
			logger.warning("Failure deleting portfolio for "+owner);
			PortfolioUtilities.logException(t);
		}

		return portfolio; //maybe this method should return void instead?
	}

	private static void staticInitialize() throws NamingException {
		if (!staticInitialized) try {
			logger.info("Obtaining JDBC Datasource");

			InitialContext context = new InitialContext();
			datasource = (DataSource) context.lookup("jdbc/Portfolio/PortfolioDB");

			logger.info("JDBC Datasource successfully obtained!"); //exception would have occurred otherwise

			staticInitialized = true;
		} catch (NamingException ne) {
			logger.warning("JNDI lookup failed.  Initialization did NOT complete.  Expect severe failures!");
			PortfolioUtilities.logException(ne);
			throw ne;
		} catch (RuntimeException re) {
			logger.warning("Runtime exception.  Initialization did NOT complete.  Expect severe failures!");
			PortfolioUtilities.logException(re);
			throw re;
		}
	}
}
