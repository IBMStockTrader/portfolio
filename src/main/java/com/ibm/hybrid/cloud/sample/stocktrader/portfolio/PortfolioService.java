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

package com.ibm.hybrid.cloud.sample.stocktrader.portfolio;

import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.client.*;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Base64;
import java.util.UUID;

//Logging (JSR 47)
import java.util.logging.Level;
import java.util.logging.Logger;

//JDBC 4.0 (JSR 221)
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import javax.sql.DataSource;

//CDI 1.2
import javax.inject.Inject;
import javax.enterprise.context.ApplicationScoped;

//mpConfig 1.2
import org.eclipse.microprofile.config.inject.ConfigProperty;

//mpMetrics 1.1
import org.eclipse.microprofile.metrics.annotation.Counted;

//mpOpenTracing 1.0
import org.eclipse.microprofile.opentracing.Traced;

//mpRestClient 1.0
import org.eclipse.microprofile.rest.client.inject.RestClient;

//Transactions
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;

//JMS 2.0
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;

//JSON-P 1.0 (JSR 353).  This replaces my old usage of IBM's JSON4J (com.ibm.json.java.JSONObject)
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

//JNDI 1.0
import javax.naming.InitialContext;
import javax.naming.NamingException;

//Servlet 4.0
import javax.servlet.http.HttpServletRequest;

//JAX-RS 2.0 (JSR 339)
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.ApplicationPath;
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
@ApplicationScoped //enable interceptors like @Transactional (note you need a WEB-INF/beans.xml in your war)
/** This version stores the Portfolios via JDBC to DB2 (or whatever JDBC provider is defined in your server.xml).
 *  TODO: Should update to use PreparedStatements.
 */
public class PortfolioService extends Application {
	private static Logger logger = Logger.getLogger(PortfolioService.class.getName());

	private static final double ERROR            = -1.0;
	private static final int    CONFLICT         = 409; //odd that JAX-RS has no ConflictException

	private static final String NOTIFICATION_Q   = "jms/Portfolio/NotificationQueue";
	private static final String NOTIFICATION_QCF = "jms/Portfolio/NotificationQueueConnectionFactory";

	//Our ODM rule will return its own values for levels, generally in all caps
	private static final String BASIC    = "Basic";
	private static final String BRONZE   = "Bronze";
	private static final String SILVER   = "Silver";
	private static final String GOLD     = "Gold";
	private static final String PLATINUM = "Platinum";

	private boolean initialized = false;

	private InitialContext context = null;

	private Queue queue = null;
	private QueueConnectionFactory queueCF = null;

	private DataSource datasource = null;

	private static SimpleDateFormat dateFormatter = null;
	private static SimpleDateFormat timestampFormatter = null;

	private static EventStreamsProducer kafkaProducer = null;

	private @Inject @RestClient StockQuoteClient stockQuoteClient;
	private @Inject @RestClient ODMClient odmClient;
	private @Inject @RestClient WatsonClient watsonClient;

	private @Inject @ConfigProperty(name = "ODM_ID", defaultValue = "odmAdmin") String odmId;
	private @Inject @ConfigProperty(name = "ODM_PWD", defaultValue = "odmAdmin") String odmPwd;
	private @Inject @ConfigProperty(name = "WATSON_ID", defaultValue = "apikey") String watsonId;
	private @Inject @ConfigProperty(name = "WATSON_PWD") String watsonPwd;
	private @Inject @ConfigProperty(name = "KAFKA_TOPIC", defaultValue = "stocktrader") String kafkaTopic;
	private @Inject @ConfigProperty(name = "KAFKA_ADDRESS", defaultValue = "") String kafkaAddress;

	private HashMap<String, byte[]> memoryLeakTable = new HashMap<>();
	
	// Override ODM Client URL if secret is configured to provide URL
	static {
		String mpUrlPropName = ODMClient.class.getName() + "/mp-rest/url";
		String odmURL = System.getenv("ODM_URL");
		if ((odmURL != null) && !odmURL.isEmpty()) {
			logger.info("Using ODM URL from secret: " + odmURL);
			System.setProperty(mpUrlPropName, odmURL);
		} else {
			logger.info("ODM URL not found from env var from secret, so defaulting to value in jvm.options: " + System.getProperty(mpUrlPropName));
		}
	}

	@GET
	@Path("/")
	@Produces("application/json")
//	@RolesAllowed({"StockTrader", "StockViewer"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public Portfolio[] getPortfolios() throws SQLException {
		ArrayList<Portfolio> portfolioList = new ArrayList<Portfolio>();
		int count = 0;

		try {
			logger.fine("Running following SQL: SELECT * FROM Portfolio");
			ResultSet results = invokeJDBCWithResults("SELECT * FROM Portfolio");
	
			logger.fine("Iterating over results");
			while (results.next()) {
				Portfolio portfolio = new Portfolio();
				portfolio.setOwner(results.getString("owner"));
				portfolio.setTotal(results.getDouble("total"));
				portfolio.setLoyalty(results.getString("loyalty"));

				portfolioList.add(portfolio);
				count++;
			}
			releaseResults(results);
		} catch (SQLException sqle) {
			logException(sqle);
			throw sqle;
		}
	
		logger.info("Returning "+count+" portfolios");

		Portfolio[] portfolios = new Portfolio[count];
		portfolioList.toArray(portfolios);

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

		return portfolios;
	}

	@POST
	@Path("/{owner}")
	@Produces("application/json")
	@Counted(monotonic=true, name="portfolios", displayName="Stock Trader portfolios", description="Number of portfolios created in the Stock Trader applications")
//	@RolesAllowed({"StockTrader"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public Portfolio createPortfolio(@PathParam("owner") String owner) throws SQLException {
		Portfolio portfolio = null;
		if (owner != null) {
			logger.info("Creating portfolio for "+owner);

			//total=0.0, loyalty="Basic", balance=50.0, commissions=0.0, free=0, sentiment="Unknown", nextCommission=9.99
			portfolio = new Portfolio(owner, 0.0, "Basic", 50.0, 0.0, 0, "Unknown", 9.99);

			logger.fine("Running following SQL: INSERT INTO Portfolio VALUES ('"+owner+"', 0.0, 'Basic', 50.0, 0.0, 0, 'Unknown')");
			try {
				invokeJDBC("INSERT INTO Portfolio VALUES ('"+owner+"', 0.0, 'Basic', 50.0, 0.0, 0, 'Unknown')");
			} catch (SQLIntegrityConstraintViolationException dupKey) {
				logger.warning("Portfolio already exists for: "+owner);
				logException(dupKey);
				throw new WebApplicationException("Portfolio already exists for "+owner+"!", CONFLICT);
			}
			logger.info("Portfolio created successfully");
		}

		return portfolio;
	}

	@GET
	@Path("/{owner}")
	@Produces("application/json")
	@Transactional(TxType.REQUIRED) //two-phase commit (XA) across JDBC and JMS
//	@RolesAllowed({"StockTrader", "StockViewer"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public Portfolio getPortfolio(@PathParam("owner") String owner, @Context HttpServletRequest request) throws IOException, SQLException {
		Portfolio newPortfolio = null;

		createMemoryLeak(owner);
		
		Portfolio oldPortfolio = getPortfolioWithoutStocks(owner); //throws a 404 if not found
		if (oldPortfolio != null) {
			String oldLoyalty = oldPortfolio.getLoyalty();
			double overallTotal = 0;

			Portfolio portfolio = new Portfolio(owner);

			ArrayList<Stock> stocks = new ArrayList<Stock>();

			logger.fine("Running following SQL: SELECT * FROM Stock WHERE owner = '"+owner+"'");
			ResultSet results = invokeJDBCWithResults("SELECT * FROM Stock WHERE owner = '"+owner+"'");

			int count = 0;
			logger.fine("Iterating over results");
			while (results.next()) {
				count++;

				String symbol = results.getString("symbol");
				Stock stock = new Stock(symbol);

				int shares = results.getInt("shares");
				stock.setShares(shares);

				double commission = results.getDouble("commission");
				stock.setCommission(commission);

				String date = null;
				double price = 0;
				double total = 0;
				try {
					//call the StockQuote microservice to get the current price of this stock
					logger.info("Calling stock-quote microservice for "+symbol);

					String jwt = request.getHeader("Authorization");
					Quote quote = stockQuoteClient.getStockQuote(jwt, symbol);

					date = quote.getDate();
					price = quote.getPrice();

					total = shares * price;

					//TODO - is it OK to update rows (not adding or deleting) in the Stock table while iterating over its contents?
					logger.fine("Running following SQL: UPDATE Stock SET dateQuoted = '"+date+"', price = "+price+", total = "+total+" WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
					invokeJDBC("UPDATE Stock SET dateQuoted = '"+date+"', price = "+price+", total = "+total+" WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
					logger.info("Updated "+symbol+" entry for "+owner+" in Stock table");
				} catch (Throwable t) {
					logger.warning("Unable to get fresh stock quote.  Using cached values instead");
					logException(t);

					date = results.getString("dateQuoted");
					if (date == null) {
						Date now = new Date();
						if (dateFormatter == null) dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
						date = dateFormatter.format(now);
					}

					price = results.getDouble("price");
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

				if (price != -1) //-1 is the marker for not being able to get the stock quote.  But don't actually add that value
					overallTotal += total;

				logger.info("Adding "+symbol+" to portfolio for "+owner);
				portfolio.addStock(stock);
			}
			logger.info("Processed "+count+" stocks for "+owner);

			releaseResults(results);

			portfolio.setTotal(overallTotal);

			String loyalty = processLoyaltyLevel(request, owner, overallTotal, oldLoyalty);
			portfolio.setLoyalty(loyalty);

			int free = oldPortfolio.getFree();
			portfolio.setBalance(oldPortfolio.getBalance());
			portfolio.setCommissions(oldPortfolio.getCommissions());
			portfolio.setFree(free);
			portfolio.setSentiment(oldPortfolio.getSentiment());
			portfolio.setNextCommission(free>0 ? 0.0 : getCommission(loyalty));

			logger.fine("Running following SQL: UPDATE Portfolio SET total = "+overallTotal+", loyalty = '"+loyalty+"' WHERE owner = '"+owner+"'");
			invokeJDBC("UPDATE Portfolio SET total = "+overallTotal+", loyalty = '"+loyalty+"' WHERE owner = '"+owner+"'");

			logger.info("Returning "+portfolio.toString());
			newPortfolio = portfolio;
		} else {
			newPortfolio = new Portfolio(); //so we don't return null
			logger.warning("No portfolio found for "+owner); //shouldn't get here; an exception with a 404 should be thrown instead
		}

		return newPortfolio;
	}

	private Portfolio getPortfolioWithoutStocks(String owner) throws SQLException {
		logger.fine("Running following SQL: SELECT * FROM Portfolio WHERE owner = '"+owner+"'");
		ResultSet results = invokeJDBCWithResults("SELECT * FROM Portfolio WHERE owner = '"+owner+"'");

		Portfolio portfolio = null;
		if (results.next()) {
			logger.info("Found portfolio for "+owner);

			double total = results.getDouble("total");
			String loyalty = results.getString("loyalty");
			double balance = results.getDouble("balance");
			double commissions = results.getDouble("commissions");
			int free = results.getInt("free");
			String sentiment = results.getString("sentiment");

			releaseResults(results);

			double nextCommission = getCommission(loyalty);

			portfolio = new Portfolio(owner, total, loyalty, balance, commissions, free, sentiment, nextCommission);
		} else {
			releaseResults(results);

			throw new NotFoundException("No such portfolio: "+owner); //send back a 404
		}

		logger.fine("Returning "+((portfolio==null) ? "null" : portfolio.toString()));
		return portfolio;
	}

	@PUT
	@Path("/{owner}")
	@Produces("application/json")
	@Transactional(TxType.REQUIRED) //two-phase commit (XA) across JDBC and JMS
//	@RolesAllowed({"StockTrader"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public Portfolio updatePortfolio(@PathParam("owner") String owner, @QueryParam("symbol") String symbol, @QueryParam("shares") int shares, @Context HttpServletRequest request) throws IOException, SQLException {
		double commission = processCommission(owner); //throws a 404 if not found

		logger.fine("Running following SQL: SELECT * FROM Stock WHERE owner = '"+owner+"' and symbol = '"+symbol+"'");
		ResultSet results = invokeJDBCWithResults("SELECT * FROM Stock WHERE owner = '"+owner+"' and symbol = '"+symbol+"'");

		if (results.next()) { //row exists
			int oldShares = results.getInt("shares");
			double oldCommission = results.getDouble("commission");
			releaseResults(results);

			int newShares = oldShares+shares;
			double newCommission = oldCommission+commission;
			if (newShares > 0) {
				logger.fine("Running following SQL: UPDATE Stock SET shares = "+newShares+", commission = "+newCommission+" WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
				invokeJDBC("UPDATE Stock SET shares = "+newShares+", commission = "+newCommission+" WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
				//getPortfolio will fill in the price, date and total
			} else {
				logger.fine("Running following SQL: DELETE FROM Stock WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
				invokeJDBC("DELETE FROM Stock WHERE owner = '"+owner+"' AND symbol = '"+symbol+"'");
			}
		} else {
			logger.fine("Running following SQL: INSERT INTO Stock (owner, symbol, shares, commission) VALUES ('"+owner+"', '"+symbol+"', "+shares+", "+commission+")");
			invokeJDBC("INSERT INTO Stock (owner, symbol, shares, commission) VALUES ('"+owner+"', '"+symbol+"', "+shares+", "+commission+")");
			//getPortfolio will fill in the price, date and total
		}

		//getPortfolio will fill in the overall total and loyalty, and commit or rollback the transaction
		logger.info("Refreshing portfolio for "+owner);
		Portfolio portfolio = getPortfolio(owner, request);

		invokeKafka(portfolio, symbol, shares, commission);

		return portfolio;
	}

	@DELETE
	@Path("/{owner}")
	@Produces("application/json")
//	@RolesAllowed({"StockTrader"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public Portfolio deletePortfolio(@PathParam("owner") String owner) throws SQLException {
		Portfolio portfolio = getPortfolioWithoutStocks(owner); //throws a 404 if not found

		logger.fine("Running following SQL: DELETE FROM Portfolio WHERE owner = '"+owner+"'");
		invokeJDBC("DELETE FROM Portfolio WHERE owner = '"+owner+"'");
		logger.info("Successfully deleted portfolio for "+owner);

		return portfolio; //maybe this method should return void instead?
	}

	@POST
	@Path("/{owner}/feedback")
	@Consumes("application/json")
	@Produces("application/json")
//	@RolesAllowed({"StockTrader"}) //Couldn't get this to work; had to do it through the web.xml instead :(
	public Feedback submitFeedback(@PathParam("owner") String owner, WatsonInput input) throws IOException, SQLException {
		String sentiment = "Unknown";
		try {
			initialize();
		} catch (NamingException ne) {
			logger.warning("Error occurred during initialization");
		}

		Portfolio portfolio = getPortfolioWithoutStocks(owner); //throws a 404 if not found
		int freeTrades = portfolio.getFree();

		try {
			String credentials = watsonId + ":" + watsonPwd; //Watson accepts basic auth
			String authorization = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());

			logger.info("Calling Watson Tone Analyzer");

			WatsonOutput watson = watsonClient.getTone(authorization, input);
			sentiment = watson.determineSentiment();
		} catch (Throwable t) {
			logger.info("Error from Watson, with following input: "+input.toString());
			logException(t);
		}

		Feedback feedback = getFeedback(owner, sentiment);
		freeTrades += feedback.getFree();

		logger.fine("Running following JDBC command: UPDATE Portfolio SET sentiment='"+sentiment+"', free="+freeTrades+" WHERE owner='"+owner+"'");
		invokeJDBC("UPDATE Portfolio SET sentiment='"+sentiment+"', free="+freeTrades+" WHERE owner='"+owner+"'");

		logger.info("Returning feedback: "+feedback.toString());
		return feedback;
	}

	//Here's where we'll soon have a call to the predictive analytics service in ICP4Data
	private Feedback getFeedback(String owner, String sentiment) {
		int freeTrades = 1;
		String message = "Thanks for providing feedback.  Have a free trade on us!";

		if ("Anger".equalsIgnoreCase(sentiment)) {
			logger.info("Tone is angry");
			freeTrades = 3;
			message = "We're sorry you are upset.  Have three free trades on us!";
		} else if ("Unknown".equalsIgnoreCase(sentiment)) {
			logger.info("Tone is unknown");
			freeTrades = 0;
			message = "Error communicating with the Watson Tone Analyzer";
		}

		Feedback feedback = new Feedback(message, freeTrades, sentiment);
		return feedback;
	}

	private String processLoyaltyLevel(HttpServletRequest request, String owner, double overallTotal, String oldLoyalty) {
		String loyalty = null;
		ODMLoyaltyRule input = new ODMLoyaltyRule(overallTotal);
		try {
			String credentials = odmId+":"+odmPwd;
			String basicAuth = "Basic "+Base64.getEncoder().encode(credentials.getBytes());

			//call the LoyaltyLevel business rule to get the current loyalty level of this portfolio
			logger.info("Calling loyalty-level ODM business rule for "+owner);
			ODMLoyaltyRule result = odmClient.getLoyaltyLevel(basicAuth, input);

			loyalty = result.determineLoyalty();
			logger.info("New loyalty level for "+owner+" is "+loyalty);

			if (oldLoyalty == null) return loyalty;
			if (!oldLoyalty.equalsIgnoreCase(loyalty)) try {
				logger.info("Change in loyalty level detected.");

				LoyaltyChange message = new LoyaltyChange(owner, oldLoyalty, loyalty);
	
				String user = request.getRemoteUser(); //logged-in user
				if (user != null) message.setId(user);
	
				logger.info(message.toString());
	
				invokeJMS(message);
			} catch (JMSException jms) { //in case MQ is not configured, just log the exception and continue
				logger.warning("Unable to send message to JMS provider.  Continuing without notification of change in loyalty level.");
				logException(jms);
				Exception linked = jms.getLinkedException(); //get the nested exception from MQ
				if (linked != null) logException(linked);
			} catch (NamingException ne) { //in case MQ is not configured, just log the exception and continue
				logger.warning("Unable to lookup JMS managed resources from JNDI.  Continuing without notification of change in loyalty level.");
				logException(ne);
			} catch (Throwable t) { //in case MQ is not configured, just log the exception and continue
				logger.warning("An unexpected error occurred.  Continuing without notification of change in loyalty level.");
				logException(t);
			}
		} catch (Throwable t) {
			logger.warning("Unable to get loyalty level, via "+input.toString()+".  Using cached value instead");
			logException(t);
			loyalty = oldLoyalty;
		}
		return loyalty;
	}

	@Traced
	private void initialize() throws NamingException {
		if (!initialized) try {
			logger.info("Obtaining JDBC Datasource");

			context = new InitialContext();
			datasource = (DataSource) context.lookup("jdbc/Portfolio/PortfolioDB");

			logger.info("JDBC Datasource successfully obtained!"); //exception would have occurred otherwise

			//lookup our JMS objects
			logger.info("Looking up our JMS resources");
			queueCF = (QueueConnectionFactory) context.lookup(NOTIFICATION_QCF);
			queue = (Queue) context.lookup(NOTIFICATION_Q);

			logger.info("JMS Initialization completed successfully!"); //exception would have occurred otherwise

			if (watsonId != null) {
				logger.info("Watson initialization completed successfully!");
			} else {
				logger.warning("WATSON_ID config property is null");
			}

			if (odmId != null) {
				logger.info("Initialization complete");
			} else {
				logger.warning("ODM_ID config property is null");
			}
			initialized = true;
		} catch (NamingException ne) {
			logger.warning("JNDI lookup failed.  Initialization did NOT complete.  Expect severe failures!");
			logException(ne);
			throw ne;
		} catch (RuntimeException re) {
			logger.warning("Runtime exception.  Initialization did NOT complete.  Expect severe failures!");
			logException(re);
			throw re;
		}
	}

	@Traced
	private void invokeJDBC(String command) throws SQLException {
		try {
			initialize();
		} catch (NamingException ne) {
			if (datasource == null) throw new SQLException("Can't get datasource from JNDI lookup!", ne);
		}

		try {
			logger.fine("Running SQL executeUpdate command: "+command);
			Connection connection = datasource.getConnection();
			Statement statement = connection.createStatement();
	
			statement.executeUpdate(command);
	
			statement.close();
			connection.close();
	
			logger.info("SQL executeUpdate command completed successfully");
		} catch (SQLException sqle) {
			logException(sqle);
			throw sqle;
		}
	}

	@Traced
	private ResultSet invokeJDBCWithResults(String command) throws SQLException {
		ResultSet results = null;

		try {
			initialize();
		} catch (NamingException ne) {
			if (datasource == null) throw new SQLException("Can't get datasource from JNDI lookup!", ne);
		}

		try {
			logger.fine("Running SQL executeQuery command: "+command);
			Connection connection = datasource.getConnection();
			Statement statement = connection.createStatement();
	
			statement.executeQuery(command);
	
			results = statement.getResultSet();
			logger.info("SQL executeQuery command completed successfully - returning results");
		} catch (SQLException sqle) {
			logException(sqle);
			throw sqle;
		}
	
		return results; //caller needs to pass this to releaseResults when done
	}

	@Traced
	private void releaseResults(ResultSet results) throws SQLException {
		logger.info("Releasing JDBC resources");

		Statement statement = results.getStatement();
		Connection connection = statement.getConnection();

		results.close();
		statement.close();
		connection.close();

		logger.info("Released JDBC resources");
	}

	/** Send a JSON message to our notification queue.
	 */
	@Traced
	private void invokeJMS(Object json) throws JMSException, NamingException {
		if (!initialized) initialize(); //gets our JMS managed resources (Q and QCF)

		logger.info("Preparing to send a JMS message");

		QueueConnection connection = queueCF.createQueueConnection();
		QueueSession session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

		String contents = json.toString();
		TextMessage message = session.createTextMessage(contents);

		logger.info("Sending "+contents+" to "+queue.getQueueName());

		//"mqclient" group needs "put" authority on the queue for next two lines to work
		QueueSender sender = session.createSender(queue);
		sender.setDeliveryMode(DeliveryMode.PERSISTENT);
		sender.send(message);

		sender.close();
		session.close();
		connection.close();

		logger.info("JMS Message sent successfully!");
	}

	/** Send a message to IBM Event Streams via the Kafka APIs */
	private void invokeKafka(Portfolio portfolio, String symbol, int shares, double commission) {
		if (kafkaAddress == null || kafkaAddress.isEmpty()) {
			logger.info("IBM Event Streams not configured, so not sending Kafka message about this stock trade");
			return; //only do the following if Kafka is configured
		}

		logger.info("Preparing to send a Kafka message");

		try {
			if (kafkaProducer == null) kafkaProducer = new EventStreamsProducer(kafkaAddress, kafkaTopic);

			Date now = new Date();
			if (timestampFormatter == null) timestampFormatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS");
			String when = timestampFormatter.format(now);
	
			double price = -1;
			String owner = portfolio.getOwner();
			JsonObject stock = portfolio.getStocks().getJsonObject(symbol);
			if (stock != null) { //rather than calling stock-quote again, get it from the portfolio we just built
				price = stock.getJsonNumber("price").doubleValue();
			} else {
				logger.warning("Unable to get the stock price.  Skipping sending the StockPurchase to Kafka");
				return; //nothing to send if we can't look up the stock price
			}
	
			String tradeID = UUID.randomUUID().toString();
			StockPurchase purchase = new StockPurchase(tradeID, owner, symbol, shares, price, when, commission);
			String message = purchase.toString();
	
			kafkaProducer.produce(message); //publish the serialized JSON to our Kafka topic in IBM Event Streams
			logger.info("Delivered message to Kafka: "+message);
		} catch (Throwable t) {
			logger.warning("Failure sending message to Kafka");
			logException(t);
		} 
	}

	private double processCommission(String owner) throws SQLException {
		logger.info("Getting loyalty level for "+owner);
		Portfolio portfolio = getPortfolioWithoutStocks(owner); //throws a 404 if not found
		String loyalty = portfolio.getLoyalty();
	
		double commission = getCommission(loyalty);

		int free = portfolio.getFree();
		if (free > 0) { //use a free trade if available
			free--;
			commission = 0.0;

			logger.info("Using free trade for "+owner);
			invokeJDBC("UPDATE Portfolio SET free = "+free+" WHERE owner = '"+owner+"'");
		} else {
			double commissions = portfolio.getCommissions();
			commissions += commission;

			double balance = portfolio.getBalance();
			balance -= commission;

			logger.info("Charging commission of $"+commission+" for "+owner);
			invokeJDBC("UPDATE Portfolio SET commissions = "+commissions+", balance = "+balance+" WHERE owner = '"+owner+"'");
		}

		logger.info("Returning a commission of $"+commission);
		return commission;
	}

	private double getCommission(String loyalty) {
		//TODO: turn this into an ODM business rule
		double commission = 9.99;
		if (loyalty!= null) {
			if (loyalty.equalsIgnoreCase(BRONZE)) {
				commission = 8.99;
			} else if (loyalty.equalsIgnoreCase(SILVER)) {
				commission = 7.99;
			} else if (loyalty.equalsIgnoreCase(GOLD)) {
				commission = 6.99;
			} else if (loyalty.equalsIgnoreCase(PLATINUM)) {
				commission = 5.99;
			} 
		}

		return commission;
	}

	private static void logException(Throwable t) {
		logger.warning(t.getClass().getName()+": "+t.getMessage());

		//only log the stack trace if the level has been set to at least INFO
		if (logger.isLoggable(Level.INFO)) {
			StringWriter writer = new StringWriter();
			t.printStackTrace(new PrintWriter(writer));
			logger.info(writer.toString());
		}
	}
	
	private void createMemoryLeak(String owner) {
		
		int iterations = 0;
		
		if(owner != null) {
			if(owner.equalsIgnoreCase("RG"))
				iterations = 10;
			else if(owner.equalsIgnoreCase("ED"))
				iterations = 20;
			else if(owner.equalsIgnoreCase("JB")) {
				this.memoryLeakTable.clear(); // clear the leak
			}
		}
		
		
		if(iterations > 0) {
			// Create 10 MB leak
			long initFreeMemory = Runtime.getRuntime().freeMemory();
			
			for(int i = 0; i < iterations; i++) {
				byte[] leakObject = new byte[1000000];
				String key = "" + System.currentTimeMillis();
				this.memoryLeakTable.put(key, leakObject);
			}
			
			long currentFreeMemory = Runtime.getRuntime().freeMemory();
			
			logger.info("Owner: " + owner +  " leaked memory, initial free memory: " 
			            + initFreeMemory + " Current Free Memory: " + currentFreeMemory );
		}
	}
}
