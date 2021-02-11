/*
       Copyright 2017-2020 IBM Corp All Rights Reserved

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

import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.client.ODMClient;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.client.WatsonClient;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.Feedback;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.LoyaltyChange;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.ODMLoyaltyRule;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.Portfolio;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.StockPurchase;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.WatsonInput;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.WatsonOutput;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

//Logging (JSR 47)
import java.util.logging.Level;
import java.util.logging.Logger;

//JDBC 4.0 (JSR 221)
import java.sql.SQLException;

//mpOpenTracing 1.3
import org.eclipse.microprofile.opentracing.Traced;

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

//JSON-P 1.1 (JSR 353).  This replaces my old usage of IBM's JSON4J (com.ibm.json.java.JSONObject)
import javax.json.JsonObject;

//JNDI 1.0
import javax.naming.InitialContext;
import javax.naming.NamingException;

//Servlet 4.0
import javax.servlet.http.HttpServletRequest;


public class PortfolioUtilities {
	private static Logger logger = Logger.getLogger(PortfolioUtilities.class.getName());

	private static final String NOTIFICATION_Q   = "jms/Portfolio/NotificationQueue";
	private static final String NOTIFICATION_QCF = "jms/Portfolio/NotificationQueueConnectionFactory";

	private boolean initialized = false;

	//Our ODM rule will return its own values for levels, generally in all caps
	private static final String BASIC    = "Basic";
	private static final String BRONZE   = "Bronze";
	private static final String SILVER   = "Silver";
	private static final String GOLD     = "Gold";
	private static final String PLATINUM = "Platinum";

	private static Queue queue = null;
	private static QueueConnectionFactory queueCF = null;

	private static SimpleDateFormat timestampFormatter = null;

	private static EventStreamsProducer kafkaProducer = null;
	
	private static final String mqId = System.getenv("MQ_ID");
	private static final String mqPwd = System.getenv("MQ_PASSWORD");

	@Traced
	private void initialize() throws NamingException {
		if (!initialized) try {
			//lookup our JMS objects
			logger.info("Looking up our JMS resources");
			InitialContext context = new InitialContext();
			queueCF = (QueueConnectionFactory) context.lookup(NOTIFICATION_QCF);
			queue = (Queue) context.lookup(NOTIFICATION_Q);

			logger.info("JMS Initialization completed successfully!"); //exception would have occurred otherwise
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
	String invokeODM(ODMClient odmClient, String odmId, String odmPwd, String owner, double overallTotal, String oldLoyalty, HttpServletRequest request) {
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
	Feedback invokeWatson(WatsonClient watsonClient, String watsonId, String watsonPwd, WatsonInput input) {
		String sentiment = "Unknown";
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

	/** Send a JSON message to our notification queue. */
	@Traced
	void invokeJMS(Object json) throws JMSException, NamingException {
		if (!initialized) initialize(); //gets our JMS managed resources (Q and QCF)

		logger.info("Preparing to send a JMS message");

		QueueConnection connection = queueCF.createQueueConnection(mqId, mqPwd);
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
	/*  TODO: Replace this with mpReactiveMessaging */
	@Traced
	void invokeKafka(Portfolio portfolio, String symbol, int shares, double commission, String kafkaAddress, String kafkaTopic) {
		if ((kafkaAddress == null) || kafkaAddress.isEmpty()) {
			logger.info("Kafka provider not configured, so not sending Kafka message about this stock trade");
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
			JsonObject stocks = portfolio.getStocks();
			JsonObject stock = (stocks!=null) ? stocks.getJsonObject(symbol) : null;

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

	double getCommission(String loyalty) {
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

	static void logException(Throwable t) {
		logger.warning(t.getClass().getName()+": "+t.getMessage());

		//only log the stack trace if the level has been set to at least INFO
		if (logger.isLoggable(Level.INFO)) {
			StringWriter writer = new StringWriter();
			t.printStackTrace(new PrintWriter(writer));
			logger.info(writer.toString());
		}
	}
}
