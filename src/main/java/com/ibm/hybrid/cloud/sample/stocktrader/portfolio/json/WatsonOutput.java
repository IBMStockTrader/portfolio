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

/** JSON-B POJO class representing a Watson Tone Analyzer output JSON object */
public class WatsonOutput {
    private WatsonDocument document_tone;


    public WatsonOutput() { //default constructor
    }

    public WatsonOutput(WatsonDocument newDocument_tone) { //primary key constructor
        setDocument_tone(newDocument_tone);
    }

    public WatsonDocument getDocument_tone() {
        return document_tone;
    }

    public void setDocument_tone(WatsonDocument newDocument_tone) {
        document_tone = newDocument_tone;
    }

    public String determineSentiment() {
        String sentiment = "Unknown";

        if (document_tone != null) {
            WatsonTone[] tones = document_tone.getTones();
            double score = 0.0;
            for (int index=0; index<tones.length; index++) {
                WatsonTone tone = tones[index];
                double newScore = tone.getScore();
                if (newScore > score) { //we might get back multiple tones; if so, go with the one with the highest score
                    sentiment = tone.getTone_name();
                    score = newScore;
                }
            }
        }
    
        return sentiment;
    }

    public boolean equals(Object obj) {
        boolean isEqual = false;
        if ((obj != null) && (obj instanceof WatsonOutput)) isEqual = toString().equals(obj.toString());
        return isEqual;
   }

    public String toString() {
        StringBuffer json = new StringBuffer("{\"document_tone\": ");
        if (document_tone != null) {
            json.append(document_tone.toString());
         } else {
            json.append("{}");
         }
         json.append("}");
         return json.toString();
    }
}
