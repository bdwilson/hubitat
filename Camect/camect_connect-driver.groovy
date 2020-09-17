/**
 *  Hubitat Device Handler: Camect Connect
 *  Version: 1.2.0
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
metadata {
  definition (name: "Camect Connect Driver", 
              namespace: "brianwilson-hubitat", 
              author: "bubba@bubba.org",
              importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Camect/camect_connect-driver.groovy"
    ) {
        capability "Switch"
        capability "Refresh"
        capability "Presence Sensor"
		capability "Sensor"
    
        attribute "camectStatus", "String"
  }
}

def on() {
    // workaround for bug in API. Should be updated with the commented out code below.
    parent.cameras?.each { camera ->
        def params  = [ Enable:1, Reason:"All Cameras", CamId:camera]
        parent.sendCommand('/EnableAlert', params) 
        parent.ifDebug("Enabling alerts (All Cameras) for ${camera}")
    }
    // def params  = [ Enable:1, Reason:"All Cameras"]
    // parent.sendCommand('/EnableAlert', params) 
    sendEvent(name: "switch", value: "on", descriptionText: "Enabling alerts for All Cameras")
}
def off() {
    // workaround for bug in API. Should be updated with the commented out code below.
    parent.cameras?.each { camera ->
        def params  = [ Reason:"All Cameras", CamId:camera]
        parent.sendCommand('/EnableAlert', params) 
        parent.ifDebug("Disabling alerts (All Cameras) for ${camera}")
    }
    //def params  = [ Reason:"All Cameras"]
    //parent.sendCommand('/EnableAlert', params) 
    sendEvent(name: "switch", value: "off", descriptionText: "Disabling alerts for All Cameras")
}

