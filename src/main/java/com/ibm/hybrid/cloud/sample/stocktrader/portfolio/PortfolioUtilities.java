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

import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.Portfolio;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.StockPurchase;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

//Logging (JSR 47)
import java.util.logging.Level;
import java.util.logging.Logger;

//mpOpenTracing 1.3
import org.eclipse.microprofile.opentracing.Traced;

//JSON-P 1.1 (JSR 353).  This replaces my old usage of IBM's JSON4J (com.ibm.json.java.JSONObject)
import javax.json.JsonObject;


public class PortfolioUtilities {
	private static Logger logger = Logger.getLogger(PortfolioUtilities.class.getName());

	private static SimpleDateFormat timestampFormatter = null;
	private static EventStreamsProducer kafkaProducer = null;

	/** Send a message to IBM Event Streams via the Kafka APIs */
	/*  TODO: Replace this with Emitter from mpReactiveMessaging 2.0 when it becomes available */
	@Traced
	void invokeKafka(Portfolio portfolio, String symbol, int shares, double commission, String kafkaAddress, String kafkaTopic) {
		if ((kafkaAddress == null) || kafkaAddress.isEmpty()) {
			logger.info("Kafka provider not configured, so not sending Kafka message about this stock trade");
			return; //only do the following if Kafka is configured
		}

		logger.fine("Preparing to send a Kafka message");

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
