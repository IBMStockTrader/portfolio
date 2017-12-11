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

//Standard HTTP request classes.  Maybe replace these with use of JAX-RS 2.0 client package instead...
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;

//Logging (JSR 47)
import java.util.logging.Level;
import java.util.logging.Logger;

//JDBC 4.0 (JSR 221)
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Enumeration;

import javax.annotation.Resource;
import javax.annotation.Resource.AuthenticationType;
import javax.sql.DataSource;

//JSON-P 1.0 (JSR 353).  The replaces my old usage of IBM's JSON4J (com.ibm.json.java.JSONObject)
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

//JNDI 1.0
import javax.naming.InitialContext;
import javax.servlet.http.HttpServletRequest;

//JAX-RS 2.0 (JSR 339)
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Path;


@ApplicationPath("/")
@Path("/")
/** This version stores the Portfolios via JDBC to DB2 (or whatever JDBC provider is defined in your server.xml).
 *  TODO: Should update to use PreparedStatements.
 */
public class Portfolio extends Application {
	private static Logger logger = Logger.getLogger(Portfolio.class.getName());

	private static final String   QUOTE_SERVICE = "http://stock-quote-service:9080/stock-quote";
	private static final String LOYALTY_SERVICE = "http://loyalty-level-service:9080/loyalty-level";

	@Resource(name = "PortfolioDB", authenticationType = AuthenticationType.CONTAINER) private DataSource datasource = null;


	@GET
	@Path("/")
	@Produces("application/json")
//	@RolesAllowed({"StockTrader", "StockViewer"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public JsonArray getPortfolios() throws IOException, SQLException {
		JsonArrayBuilder builder = Json.createArrayBuilder();

		logger.fine("Running following SQL: SELECT * FROM Portfolio");
		ResultSet results = invokeJDBCWithResults("SELECT * FROM Portfolio");

		int count = 0;
		logger.fine("Iterating over results");
		while (results.next()) {
			String owner = results.getString("owner");
			double total = results.getDouble("total");
			String loyalty = results.getString("loyalty");

			JsonObjectBuilder portfolio = Json.createObjectBuilder();
			portfolio.add("owner", owner);
			portfolio.add("total", total);
			portfolio.add("loyalty", loyalty);

			builder.add(portfolio);
			count++;
		}
		releaseResults(results);

		logger.fine("Returning "+count+" portfolios");
		return builder.build();
	}

	@POST
	@Path("/{owner}")
	@Produces("application/json")
//	@RolesAllowed({"StockTrader"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public JsonObject createPortfolio(@PathParam("owner") String owner) throws SQLException {
		JsonObject portfolio = null;
		if (owner != null) {
			logger.fine("Creating portfolio for "+owner);

			JsonObjectBuilder portfolioBuilder = Json.createObjectBuilder();
			portfolioBuilder.add("owner", owner);
			portfolioBuilder.add("loyalty", "Basic");
			portfolioBuilder.add("total", 0.0);

			JsonObjectBuilder stocksBuilder = Json.createObjectBuilder();
			portfolioBuilder.add("stocks", stocksBuilder);

			portfolio = portfolioBuilder.build();

			logger.fine("Running following SQL: INSERT INTO Portfolio VALUES ('"+owner+"', 0.0, 'Basic')");
			invokeJDBC("INSERT INTO Portfolio VALUES ('"+owner+"', 0.0, 'Basic')");
			logger.fine("Portfolio created successfully");
		}

		return portfolio;
	}

	@GET
	@Path("/{owner}")
	@Produces("application/json")
//	@RolesAllowed({"StockTrader", "StockViewer"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public JsonObject getPortfolio(@PathParam("owner") String owner, @Context HttpServletRequest request) throws IOException, SQLException {
		JsonObject newPortfolio = null;
		JsonObject oldPortfolio = getPortfolioWithoutStocks(owner);
		if (oldPortfolio != null) {
			String oldLoyalty = oldPortfolio.getString("loyalty");
			double overallTotal = 0;

			JsonObjectBuilder portfolio = Json.createObjectBuilder();
			portfolio.add("owner", owner);

			JsonObjectBuilder stocks = Json.createObjectBuilder();

			logger.fine("Running following SQL: SELECT * FROM Stock WHERE owner = '"+owner+"'");
			ResultSet results = invokeJDBCWithResults("SELECT * FROM Stock WHERE owner = '"+owner+"'");

			int count = 0;
			logger.fine("Iterating over results");
			while (results.next()) {
				count++;
				JsonObjectBuilder stock = Json.createObjectBuilder();

				String symbol = results.getString("symbol");
				stock.add("symbol", symbol);

				int shares = results.getInt("shares");
				stock.add("shares", shares);

				String date = null;
				double price = 0;
				double total = 0;
				try {
					//call the StockQuote microservice to get the current price of this stock
					logger.fine("Calling stock-quote microservice for "+symbol);
					JsonObject quote = invokeREST(request, "GET", QUOTE_SERVICE+"/"+symbol);

					date = quote.getString("date");
					price = quote.getJsonNumber("price").doubleValue();

					total = shares * price;

					//TODO - is it OK to update rows (not adding or deleting) in the Stock table while iterating over its contents?
					logger.fine("Running following SQL: UPDATE Stock SET dateQuoted = '"+date+"', price = "+price+", total = "+total+" WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
					invokeJDBC("UPDATE Stock SET dateQuoted = '"+date+"', price = "+price+", total = "+total+" WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
					logger.fine("Updated "+symbol+" entry for "+owner+" in Stock table");
				} catch (IOException ioe) {
					logger.warning("Unable to get fresh stock quote.  Using cached values instead");
					logException(ioe);

					date = results.getString("dateQuoted");
					price = results.getDouble("price");
					total = shares * price;
				}

				stock.add("date", date);
				stock.add("price", price);
				stock.add("total", total);

				if (price != -1) //-1 is the marker for not being able to get the stock quote.  But don't actually add that value
					overallTotal += total;

				logger.fine("Adding "+symbol+" to portfolio for "+owner);
				stocks.add(symbol, stock);
			}
			logger.fine("Processed "+count+" stocks for "+owner);

			releaseResults(results);

			portfolio.add("stocks", stocks);
			portfolio.add("total", overallTotal);

			String loyalty = null;
			try {
				//call the LoyaltyLevel microservice to get the current loyalty level of this portfolio
				logger.fine("Calling loyalty-level microservice for "+owner);
				JsonObject loyaltyLevel = invokeREST(request, "GET", LOYALTY_SERVICE+"?owner="+owner+"&loyalty="+oldLoyalty+"&total="+overallTotal);
				loyalty = loyaltyLevel.getString("loyalty");
				logger.fine("New loyalty level for "+owner+" is "+loyalty);
			} catch (IOException ioe) {
				logger.warning("Unable to get loyalty level.  Using cached value instead");
				logException(ioe);
				loyalty = oldLoyalty;
			}
			portfolio.add("loyalty", loyalty);

			logger.fine("Running following SQL: UPDATE Portfolio SET total = "+overallTotal+", loyalty = '"+loyalty+"' WHERE owner = '"+owner+"'");
			invokeJDBC("UPDATE Portfolio SET total = "+overallTotal+", loyalty = '"+loyalty+"' WHERE owner = '"+owner+"'");

			logger.fine("Building portfolio JSON object for "+owner);
			newPortfolio = portfolio.build();
		} else {
			newPortfolio = Json.createObjectBuilder().build(); //so we don't return null
			logger.warning("No portfolio found for "+owner);
		}

		return newPortfolio;
	}

	private JsonObject getPortfolioWithoutStocks(String owner) throws SQLException {
		logger.fine("Running following SQL: SELECT * FROM Portfolio WHERE owner = '"+owner+"'");
		ResultSet results = invokeJDBCWithResults("SELECT * FROM Portfolio WHERE owner = '"+owner+"'");

		JsonObject portfolio = null;
		if (results.next()) {
			logger.fine("Found portfolio for "+owner);

			double total = results.getDouble("total");
			String loyalty = results.getString("loyalty");

			JsonObjectBuilder builder = Json.createObjectBuilder();
			builder.add("owner", owner);
			builder.add("total", total);
			builder.add("loyalty", loyalty);

			logger.fine("Building portfolio JSON object for "+owner);
			portfolio = builder.build();
		}

		releaseResults(results);

		logger.finer("Returning "+portfolio.toString());
		return portfolio;
	}

	@PUT
	@Path("/{owner}")
	@Produces("application/json")
//	@RolesAllowed({"StockTrader"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public JsonObject updatePortfolio(@PathParam("owner") String owner, @QueryParam("symbol") String symbol, @QueryParam("shares") int shares, @Context HttpServletRequest request) throws IOException, SQLException {
		logger.fine("Running following SQL: SELECT shares FROM Stock WHERE owner = '"+owner+"' and symbol = '"+symbol+"'");
		ResultSet results = invokeJDBCWithResults("SELECT shares FROM Stock WHERE owner = '"+owner+"' and symbol = '"+symbol+"'");

		if (results.next()) { //row exists
			int oldShares = results.getInt("shares");
			releaseResults(results);

			shares += oldShares;
			if (shares > 0) {
				logger.fine("Running following SQL: UPDATE Stock SET shares = "+shares+" WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
				invokeJDBC("UPDATE Stock SET shares = "+shares+" WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
				//getPortfolio will fill in the price, date and total
			} else {
				logger.fine("Running following SQL: DELETE FROM Stock WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
				invokeJDBC("DELETE FROM Stock WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
			}
		} else {
			logger.fine("Running following SQL: INSERT INTO Stock (owner, symbol, shares) VALUES ('"+owner+"', '"+symbol+"', "+shares+")");
			invokeJDBC("INSERT INTO Stock (owner, symbol, shares) VALUES ('"+owner+"', '"+symbol+"', "+shares+")");
			//getPortfolio will fill in the price, date and total
		}

		//getPortfolio will fill in the overall total and loyalty

		logger.fine("Refreshing portfolio for "+owner);
		return getPortfolio(owner, request);
	}

	@DELETE
	@Path("/{owner}")
	@Produces("application/json")
//	@RolesAllowed({"StockTrader"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public JsonObject deletePortfolio(@PathParam("owner") String owner) throws SQLException {
		JsonObject portfolio = getPortfolioWithoutStocks(owner);

		logger.fine("Running following SQL: DELETE FROM Portfolio WHERE owner = '"+owner+"'");
		invokeJDBC("DELETE FROM Portfolio WHERE owner = '"+owner+"'");
		logger.fine("Successfully deleted portfolio for "+owner);

		return portfolio; //maybe this method should return void instead?
	}

	private static JsonObject invokeREST(HttpServletRequest request, String verb, String uri) throws IOException {
		logger.fine("Preparing call to "+verb+" "+uri);
		URL url = new URL(uri);

		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		copyFromRequest(conn, request); //forward headers (including cookies) from inbound request

		conn.setRequestMethod(verb);
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setDoOutput(true);

		logger.fine("Invoking REST URL");
		InputStream stream = conn.getInputStream();

		logger.fine("Parsing results as JSON");
//		JSONObject json = JSONObject.parse(stream); //JSON4J
		JsonObject json = Json.createReader(stream).readObject();

		stream.close();

		logger.finer("Returning "+json.toString());
		return json;
	}

	//forward headers (including cookies) from inbound request
	private static void copyFromRequest(HttpURLConnection conn, HttpServletRequest request) {
		logger.fine("Copying headers (and cookies) from request to response");
		Enumeration<String> headers = request.getHeaderNames();
		if (headers != null) {
			int count = 0;
			while (headers.hasMoreElements()) {
				String headerName = headers.nextElement(); //"Authorization" and "Cookie" are especially important headers
				String headerValue = request.getHeader(headerName);
				logger.finer(headerName+": "+headerValue);
				conn.setRequestProperty(headerName, headerValue); //odd it's called request property here, rather than header...
				count++;
			}
			if (count == 0) logger.info("headers is empty");
		} else {
			logger.info("headers is null");
		}
	}

	private Throwable initialize() {
		Throwable cause = null;
		if (datasource == null) try {
			logger.fine("Obtaining JDBC Datasource");

			InitialContext ctx = new InitialContext();
			datasource = (DataSource) ctx.lookup("jdbc/Portfolio/PortfolioDB");

			logger.fine("JDBC Datasource successfully obtained!");
		} catch (Throwable t) {
			logException(t);
			cause = t;
		}
		return cause;
	}

	private void invokeJDBC(String command) throws SQLException {
		Throwable cause = initialize();
		if (datasource == null) throw new SQLException("Can't get datasource from JNDI lookup!", cause);

		logger.fine("Running SQL executeUpdate command");
		Connection connection = datasource.getConnection();
		Statement statement = connection.createStatement();

		statement.executeUpdate(command);

		statement.close();
		connection.close();

		logger.fine("SQL executeUpdate command completed successfully");
	}

	private ResultSet invokeJDBCWithResults(String command) throws SQLException {
		Throwable cause = initialize();
		if (datasource == null) throw new SQLException("Can't get datasource from JNDI lookup!", cause);

		logger.fine("Running SQL executeQuery command");
		Connection connection = datasource.getConnection();
		Statement statement = connection.createStatement();

		statement.executeQuery(command);

		ResultSet results = statement.getResultSet();
		logger.fine("SQL executeQuery command completed successfully - returning results");

		return results; //caller needs to pass this to releaseResults when done
	}

	private void releaseResults(ResultSet results) throws SQLException {
		logger.fine("Releasing JDBC resources");

		Statement statement = results.getStatement();
		Connection connection = statement.getConnection();

		results.close();
		statement.close();
		connection.close();

		logger.fine("Released JDBC resources");
	}

	private static void logException(Throwable t) {
		logger.warning(t.getClass().getName()+": "+t.getMessage());

		//only log the stack trace if the level has been set to at least FINE
		if (logger.isLoggable(Level.FINE)) {
			StringWriter writer = new StringWriter();
			t.printStackTrace(new PrintWriter(writer));
			logger.fine(writer.toString());
		}
	}
}
