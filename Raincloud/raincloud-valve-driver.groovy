/*
 * Rain Cloud Valve Driver
 * 
 * 1.1.0 - Brian Wilson / bubba@bubba.org
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
    definition(name: "Raincloud Valve", namespace: "brianwilson-hubitat", author: "Brian Wilson", importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Raincloud/Raincloud.groovy") {
        capability "Valve"
		capability "Actuator"
        capability "Switch"
		
		command "battery"
		command "refresh"
        command "auto"
        command "raindelay", ["number"]
        command "open", ["number"]
        
        attribute "AutoWatering", "BOOLEAN"
        attribute "LastUpdated", "DATE"
        attribute "WateringTime", "NUMBER"
        attribute "RainDelay", "NUMBER"
        attribute "Battery", "NUMBER"
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

def on() {
    open()
}

def off() {
    close()
}

def close() {
    parent.childClose(device.deviceNetworkId)
}

def open(mins) { 
    parent.childOpen(device.deviceNetworkId, mins)
}

def battery() {
    refresh()
}

def auto() {
    parent.childAuto(device.deviceNetworkId, device.currentValue('AutoWatering'))
}

def raindelay(days) {
    parent.childRain(device.deviceNetworkId, days)
}

