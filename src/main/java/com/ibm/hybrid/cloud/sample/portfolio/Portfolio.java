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
import java.net.HttpURLConnection;
import java.net.URL;

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
	private static final String   QUOTE_SERVICE = "http://stock-quote-service:9080/stock-quote";
	private static final String LOYALTY_SERVICE = "http://loyalty-level-service:9080/loyalty-level";
	@Resource(name = "PortfolioDB", authenticationType = AuthenticationType.CONTAINER) private DataSource datasource = null;

	@GET
	@Path("/")
	@Produces("application/json")
//	@RolesAllowed({"StockTrader", "StockViewer"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public JsonArray getPortfolios() throws IOException, SQLException {
		JsonArrayBuilder builder = Json.createArrayBuilder();

		ResultSet results = invokeJDBCWithResults("SELECT * FROM Portfolio");
		while (results.next()) {
			String owner = results.getString("owner");
			double total = results.getDouble("total");
			String loyalty = results.getString("loyalty");

			JsonObjectBuilder portfolio = Json.createObjectBuilder();
			portfolio.add("owner", owner);
			portfolio.add("total", total);
			portfolio.add("loyalty", loyalty);

			builder.add(portfolio);
		}
		releaseResults(results);

		return builder.build();
	}

	@POST
	@Path("/{owner}")
	@Produces("application/json")
//	@RolesAllowed({"StockTrader"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public JsonObject createPortfolio(@PathParam("owner") String owner) throws SQLException {
		JsonObject portfolio = null;
		if (owner != null) {
			JsonObjectBuilder portfolioBuilder = Json.createObjectBuilder();
			portfolioBuilder.add("owner", owner);
			portfolioBuilder.add("loyalty", "Basic");
			portfolioBuilder.add("total", 0.0);

			JsonObjectBuilder stocksBuilder = Json.createObjectBuilder();
			portfolioBuilder.add("stocks", stocksBuilder);

			portfolio = portfolioBuilder.build();

			invokeJDBC("INSERT INTO Portfolio VALUES ('"+owner+"', 0.0, 'Basic')");
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

			ResultSet results = invokeJDBCWithResults("SELECT * FROM Stock WHERE owner = '"+owner+"'");

			while (results.next()) {
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
					JsonObject quote = invokeREST(request, "GET", QUOTE_SERVICE+"/"+symbol);
					date = quote.getString("date");

					price = quote.getJsonNumber("price").doubleValue();

					total = shares * price;

					//TODO - is it OK to update rows (not adding or deleting) in the Stock table while iterating over its contents?
					invokeJDBC("UPDATE Stock SET dateQuoted = '"+date+"', price = "+price+", total = "+total+" WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
				} catch (IOException ioe) {
					ioe.printStackTrace();
					System.out.println("Unable to get fresh stock quote.  Using cached values instead");

					date = results.getString("dateQuoted");
					price = results.getDouble("price");
					total = shares * price;
				}

				stock.add("date", date);
				stock.add("price", price);
				stock.add("total", total);

				if (total != -1) //-1 is the marker for not being able to get the stock quote.  But don't actually add that value
					overallTotal += total;

				stocks.add(symbol, stock);
			}

			releaseResults(results);

			portfolio.add("stocks", stocks);
			portfolio.add("total", overallTotal);

			String loyalty = null;
			try {
				//call the LoyaltyLevel microservice to get the current loyalty level of this portfolio
				JsonObject loyaltyLevel = invokeREST(request, "GET", LOYALTY_SERVICE+"?owner="+owner+"&loyalty="+oldLoyalty+"&total="+overallTotal);
				loyalty = loyaltyLevel.getString("loyalty");
			} catch (IOException ioe) {
				ioe.printStackTrace();
				System.out.println("Unable to get loyalty level.  Using cached value instead");
				loyalty = oldLoyalty;
			}
			portfolio.add("loyalty", loyalty);

			invokeJDBC("UPDATE Portfolio SET total = "+overallTotal+", loyalty = '"+loyalty+"' WHERE owner = '"+owner+"'");

			newPortfolio = portfolio.build();
		} else {
			newPortfolio = Json.createObjectBuilder().build(); //so we don't return null
		}

		return newPortfolio;
	}

	private JsonObject getPortfolioWithoutStocks(String owner) throws SQLException {
		ResultSet results = invokeJDBCWithResults("SELECT * FROM Portfolio WHERE owner = '"+owner+"'");

		JsonObject portfolio = null;
		if (results.next()) {
			double total = results.getDouble("total");
			String loyalty = results.getString("loyalty");

			JsonObjectBuilder builder = Json.createObjectBuilder();
			builder.add("owner", owner);
			builder.add("total", total);
			builder.add("loyalty", loyalty);

			portfolio = builder.build();
		}

		releaseResults(results);

		return portfolio;
	}

	@PUT
	@Path("/{owner}")
	@Produces("application/json")
//	@RolesAllowed({"StockTrader"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public JsonObject updatePortfolio(@PathParam("owner") String owner, @QueryParam("symbol") String symbol, @QueryParam("shares") int shares, @Context HttpServletRequest request) throws IOException, SQLException {
		ResultSet results = invokeJDBCWithResults("SELECT shares FROM Stock WHERE owner = '"+owner+"' and symbol = '"+symbol+"'");

		if (results.next()) { //row exists
			int oldShares = results.getInt("shares");
			releaseResults(results);

			shares += oldShares;
			if (shares > 0) {
				invokeJDBC("UPDATE Stock SET shares = "+shares+" WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
				//getPortfolio will fill in the price, date and total
			} else {
				invokeJDBC("DELETE FROM Stock WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
			}
		} else {
			invokeJDBC("INSERT INTO Stock (owner, symbol, shares) VALUES ('"+owner+"', '"+symbol+"', "+shares+")");
			//getPortfolio will fill in the price, date and total
		}

		//getPortfolio will fill in the overall total and loyalty

		return getPortfolio(owner, request);
	}

	@DELETE
	@Path("/{owner}")
	@Produces("application/json")
//	@RolesAllowed({"StockTrader"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public JsonObject deletePortfolio(@PathParam("owner") String owner) throws SQLException {
		JsonObject portfolio = getPortfolioWithoutStocks(owner);

		invokeJDBC("DELETE FROM Portfolio WHERE owner = '"+owner+"'");

		return portfolio; //maybe this method should return void instead?
	}

	private static JsonObject invokeREST(HttpServletRequest request, String verb, String uri) throws IOException {
		URL url = new URL(uri);

		HttpURLConnection conn = (HttpURLConnection) url.openConnection();

		copyFromRequest(conn, request); //forward headers (including cookies) from inbound request

		conn.setRequestMethod(verb);
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setDoOutput(true);
		InputStream stream = conn.getInputStream();
//		JSONObject json = JSONObject.parse(stream); //JSON4J
		JsonObject json = Json.createReader(stream).readObject();

		stream.close();

		return json;
	}

	//forward headers (including cookies) from inbound request
	private static void copyFromRequest(HttpURLConnection conn, HttpServletRequest request) {
		System.out.println("portfolio: copyFromRequest");
		Enumeration<String> headers = request.getHeaderNames();
		if (headers != null) {
			int count = 0;
			while (headers.hasMoreElements()) {
				String headerName = headers.nextElement(); //"Authorization" and "Cookie" are especially important headers
				String headerValue = request.getHeader(headerName);
				System.out.println(headerName+": "+headerValue);
				conn.setRequestProperty(headerName, headerValue); //odd it's called request property here, rather than header...
				count++;
			}
			if (count == 0) System.out.println("headers is empty");
		} else {
			System.out.println("headers is null");
		}
	}

	private Throwable initialize() {
		Throwable cause = null;
		if (datasource == null) try {
			System.out.println("Obtaining JDBC Datasource");

			InitialContext ctx = new InitialContext();
			datasource = (DataSource) ctx.lookup("jdbc/Portfolio/PortfolioDB");

			System.out.println("JDBC Datasource successfully obtained!");
		} catch (Throwable t) {
			t.printStackTrace();
			cause = t;
		}
		return cause;
	}

	private void invokeJDBC(String command) throws SQLException {
		Throwable cause = initialize();
		if (datasource == null) throw new SQLException("Can't get datasource from JNDI lookup!", cause);

		Connection connection = datasource.getConnection();
		Statement statement = connection.createStatement();

		statement.executeUpdate(command);

		statement.close();
		connection.close();
	}

	private ResultSet invokeJDBCWithResults(String command) throws SQLException {
		Throwable cause = initialize();
		if (datasource == null) throw new SQLException("Can't get datasource from JNDI lookup!", cause);

		Connection connection = datasource.getConnection();
		Statement statement = connection.createStatement();

		statement.executeQuery(command);

		ResultSet results = statement.getResultSet();

		return results; //caller needs to pass this to releaseResults when done
	}

	private void releaseResults(ResultSet results) throws SQLException {
		Statement statement = results.getStatement();
		Connection connection = statement.getConnection();

		results.close();
		statement.close();
		connection.close();
	}
}
