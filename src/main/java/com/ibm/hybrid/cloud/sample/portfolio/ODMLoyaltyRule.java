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

/** JSON-B POJO class representing an ODM business rule for determining the loyalty level of a portfolio */
public class ODMLoyaltyRule {
    private LoyaltyDecision theLoyaltyDecision = null;


    public ODMLoyaltyRule() { //default constructor
    }

    public ODMLoyaltyRule(double tradeTotal) { //convenience constructor
        LoyaltyDecision newLoyaltyDecision = new LoyaltyDecision(tradeTotal);
        setTheLoyaltyDecision(newLoyaltyDecision);
    }

    public LoyaltyDecision getTheLoyaltyDecision() {
        return theLoyaltyDecision;
    }

    public void setTheLoyaltyDecision(LoyaltyDecision newLoyaltyDecision) {
        theLoyaltyDecision = newLoyaltyDecision;
    }

    public String determineLoyalty() {
        String loyalty = "Unknown";
        if (theLoyaltyDecision != null) loyalty = theLoyaltyDecision.getLoyalty();
        return loyalty;
    }

    public boolean equals(Object obj) {
        boolean isEqual = false;
        if ((obj != null) && (obj instanceof ODMLoyaltyRule)) isEqual = toString().equals(obj.toString());
        return isEqual;
   }

    public String toString() {
        return "{\"theLoyaltyDecision\": "+theLoyaltyDecision.toString()+"}";
    }
}
