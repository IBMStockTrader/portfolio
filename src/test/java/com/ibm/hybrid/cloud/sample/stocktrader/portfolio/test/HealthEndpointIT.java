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

package com.ibm.hybrid.cloud.sample.stocktrader.portfolio.test;


import org.eclipse.microprofile.system.test.SharedContainerConfig;
import org.eclipse.microprofile.system.test.jupiter.MicroProfileTest;
import org.junit.jupiter.api.Test;

import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.ManualHealthProbes;
import com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json.HealthResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.inject.Inject;

@MicroProfileTest
@SharedContainerConfig(AppConfig.class)
public class HealthEndpointIT {

    @Inject
    public static ManualHealthProbes healthProbes;
    
    @Test
    public void testManualHealthLiveness() {
        HealthResponse livenessCheck = healthProbes.liveness();
        assertEquals("healthy", livenessCheck.getResponse());
    }
    
    @Test
    public void testManualHealthReadiness() {
        HealthResponse readinessCheck = healthProbes.readiness();
        assertEquals("ready", readinessCheck.getResponse());
    }

}
