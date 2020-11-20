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

package com.ibm.hybrid.cloud.sample.stocktrader.portfolio.json;

/** JSON-B POJO class representing an ODM business rule for determining the loyalty level of a portfolio */
public class LoyaltyChange {
    private String fOwner;
    private String fOld;
    private String fNew;
    private String fId;


    public LoyaltyChange() { //default constructor
    }

    public LoyaltyChange(String initialOwner, String initialOldLoyalty, String initialNewLoyalty) { //convenience constructor
        setOwner(initialOwner);
        setOld(initialOldLoyalty);
        setNew(initialNewLoyalty);
    }

    public String getOwner() {
        return fOwner;
    }

    public void setOwner(String initialOwner) {
        fOwner = initialOwner;
    }

    public String getOld() {
        return fOld;
    }

    public void setOld(String initialOldLoyalty) {
        fOld = initialOldLoyalty;
    }

    public String getNew() {
        return fNew;
    }

    public void setNew(String initialNewLoyalty) {
        fNew = initialNewLoyalty;
    }

    public String getId() {
        return fId;
    }

    public void setId(String initialId) {
        fId = initialId;
    }

    public boolean equals(Object obj) {
        boolean isEqual = false;
        if ((obj != null) && (obj instanceof LoyaltyChange)) isEqual = toString().equals(obj.toString());
        return isEqual;
   }

    public String toString() {
        return "{\"owner\": \""+fOwner+"\", \"old\": \""+fOld+"\", \"new\": \""+fNew+"\", \"id\": \""+fId+"\"}";
    }
}
