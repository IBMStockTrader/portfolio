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

/** JSON-B POJO class representing a Watson Tone Analyzer tone JSON object */
public class WatsonTone {
    private double score;
    private String tone_id;
    private String tone_name;


    public WatsonTone() { //default constructor
    }

    public WatsonTone(double new_score, String new_tone_id, String new_tone_name) {
        setScore(new_score);
        setTone_id(new_tone_id);
        setTone_name(new_tone_name);
    }

    public double getScore() {
        return score;
    }

    public void setScore(double newScore) {
        score = newScore;
    }

    public String getTone_id() {
        return tone_id;
    }

    public void setTone_id(String newTone_id) {
        tone_id = newTone_id;
    }

    public String getTone_name() {
        return tone_name;
    }

    public void setTone_name(String newTone_name) {
        tone_name = newTone_name;
    }

    public boolean equals(Object obj) {
        boolean isEqual = false;
        if ((obj != null) && (obj instanceof WatsonTone)) isEqual = toString().equals(obj.toString());
        return isEqual;
   }

    public String toString() {
        return "{\"score\": "+score+", tone_id\": \""+tone_id+"\", \"tone_name\": \""+tone_name+"\"}";
    }
}
