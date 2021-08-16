/*
 * Water Guru Integration Driver
 * 
 * 1.0.1 - Brian Wilson / bubba@bubba.org
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 * 
 */

metadata {
    definition(name: "WaterGuru Integration Driver", namespace: "brianwilson-hubitat", author: "Brian Wilson", importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/WaterGuru/WaterGuru-Driver.groovy") {
        capability "Battery"
        capability "Consumable"
		capability "LiquidFlowRate"
        capability "Refresh"
        capability "SignalStrength"
        capability "TemperatureMeasurement"
        capability "pHMeasurement"

		command "battery"
		command "refresh"
        
        attribute "freeChlorine", "NUMBER"
        attribute "LastUpdated", "DATE"
        attribute "LastMeasurementHuman", "STRING"
        attribute "LastMeasurement", "DATE"
        attribute "CassettePercent", "NUMBER"
        attribute "CassetteChecksLeft", "NUMBER"
        attribute "CassetteTimeLeft", "STRING"
        attribute "CassetteStatus", "ENUM", ["RED", "YELLOW", "GREEN"]   
        attribute "batteryStatus", "ENUM", ["RED", "YELLOW", "GREEN"]   
        attribute "Status", "ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "statusMsg", "STRING"
    }
}

def installed() {
    refresh()    
}    

def refresh() {
    parent.updateStatus()
}

def updated() {
    refresh()
}

def battery() {
    refresh()
}
