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

import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.HealthResponse;

import java.util.logging.Logger;

//JAX-RS 2.0 (JSR 339)
import javax.ws.rs.core.MediaType;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.Path;
import javax.ws.rs.ServiceUnavailableException; //http 503 code


@Path("health")
/** Implement the Kubernetes readiness and liveness probes */
public class ManualHealthProbes {
	private static Logger logger = Logger.getLogger(ManualHealthProbes.class.getName());

	@GET
	@Path("/readiness")
	@Produces(MediaType.APPLICATION_JSON)
    public HealthResponse readiness() {
        HealthResponse response = null;
        if (PortfolioService.isReady()) {
            logger.info("Readiness probe succeeded");
            response = new HealthResponse("readiness", "ready");
        } else {
            logger.warning("Readiness probe failed");
            throw new ServiceUnavailableException(); //503
        }
        return response;
    }

	@GET
	@Path("/liveness")
	@Produces(MediaType.APPLICATION_JSON)
    public HealthResponse liveness() {
        HealthResponse response = null;
        if (PortfolioService.isHealthy()) {
            logger.fine("Liveness probe succeeded");
            response = new HealthResponse("liveness", "healthy");
        } else {
            logger.warning("Liveness probe failed");
            throw new ServiceUnavailableException(); //503
        }
        return response;
    }
}
