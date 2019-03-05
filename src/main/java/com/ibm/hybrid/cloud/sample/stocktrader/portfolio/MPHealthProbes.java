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

package com.ibm.hybrid.cloud.sample.stocktrader.portfolio;

//Standard I/O classes
import java.io.PrintWriter;
import java.io.StringWriter;

//Logging (JSR 47)
import java.util.logging.Level;
import java.util.logging.Logger;

//CDI 2.0
import javax.inject.Inject;
import javax.enterprise.context.ApplicationScoped;
//import javax.enterprise.context.control.ActivateRequestContext;
import javax.enterprise.context.control.RequestContextController;

//Servlet 4.0
import javax.servlet.http.HttpServletRequest;

//mpHealth 1.0
import org.eclipse.microprofile.health.Health;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;


@Health
@ApplicationScoped
/** Use mpHealth for both readiness and liveness probes. */
public class MPHealthProbes implements HealthCheck {
	private static Logger logger = Logger.getLogger(MPHealthProbes.class.getName());

	private static final String READINESS        = "readiness"; //header value from yaml for readiness probe
	private static final String LIVENESS         = "liveness";  //header value from yaml for liveness probe

	private @Inject RequestContextController requestContextController;
	private @Inject HttpServletRequest request;

	//mpHealth probe
	@Override
//	@ActivateRequestContext
	public HealthCheckResponse call() {
		logger.info("Entering mpHealth call() - activating RequestContextController");
		requestContextController.activate();

		HealthCheckResponseBuilder builder = HealthCheckResponse.named("Portfolio");

		String probeType = null;
		if (request!=null) { //determine if this is a readiness or liveness probe
			try {
				logger.info("getting ProbeType header");
				probeType = request.getHeader("ProbeType");
				logger.info("got ProbeType header: "+probeType);
			} catch (Throwable t) {
				logger.info("failed getting ProbeType header");
				logException(t);
			}
		} else {
			logger.warning("Failure injecting HttpServletRequest");
		}

		if (probeType!=null) {
			if (probeType.equals(READINESS)) { //this is a readiness probe
				if (PortfolioService.isReady()) {
					builder = builder.up();
					logger.fine("Returning ready!");
				} else {
					builder = builder.down();
					logger.warning("Returning NOT ready!");
				}
			} else if (probeType.equals(LIVENESS)) { //this is a liveness probe
				builder = builder.withData("consecutiveErrors", PortfolioService.consecutiveErrors);
				if (PortfolioService.isHealthy()) {
					builder = builder.up();
					logger.fine("Returning healthy!");
				} else {
					builder = builder.down();
					logger.warning("Returning NOT healthy!");
				}
			} else {
				logger.warning("Unable to determine Kubernetes probe type: "+probeType);
				builder = builder.down();
			}
		} else {
			logger.warning("ProbeType http header not set - defaulting to healthy");
			builder = builder.up();
		}

		logger.info("Exiting mpHealth call() - deactivating RequestContextController");
		requestContextController.deactivate();

		return builder.build();
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
}
