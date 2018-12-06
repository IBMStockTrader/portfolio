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

/** JSON-B POJO class representing a Watson Tone Analyzer document JSON object */
public class WatsonDocument {
    private WatsonTone[] tones;


    public WatsonDocument() { //default constructor
    }

    public WatsonDocument(WatsonTone[] newTones) { //primary key constructor
        setTones(newTones);
    }

    public WatsonTone[] getTones() {
        return tones;
    }

    public void setTones(WatsonTone[] newTones) {
        tones = newTones;
    }

    public boolean equals(Object obj) {
        boolean isEqual = false;
        if ((obj != null) && (obj instanceof WatsonDocument)) isEqual = toString().equals(obj.toString());
        return isEqual;
    }

    public String toString() {
        StringBuffer json = new StringBuffer("{\"tones\": ");
        if (tones != null) {
            for (int index=0; index<tones.length; index++) {
                WatsonTone tone = tones[index];
                json.append(tone.toString());
                if (index != tones.length-1) json.append(", ");
            }
        }
        json.append("}");
        return json.toString();
    }
}
