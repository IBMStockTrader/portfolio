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

package com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json;

/** JSON-B POJO class representing a Kubernetes health probe response JSON object */
public class HealthResponse {
    private String type;
    private String response;


    public HealthResponse() { //default constructor
    }

    public HealthResponse(String initialType, String initialResponse) { //convenience constructor
        setType(initialType);
        setResponse(initialResponse);
    }

    public String getType() {
        return type;
    }

    public void setType(String newType) {
        type = newType;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String newResponse) {
        response = newResponse;
    }

    public String toString() {
        return "{\"type\": \""+type+"\", \"response\": \""+response+"\"}";
    }
}
