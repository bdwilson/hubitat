/**
 *  Envisalink Zone Smoke Driver for Hubitat
 *
 *  Child device of Envisalink Connection driver.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Version: 2.0.2
 */

metadata {
    definition(name: "Envisalink Zone Smoke", namespace: "bdwilson", author: "bdwilson",
               importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Envisalink/Envisalink_Zone_Smoke.groovy") {
        capability "Smoke Detector"
        capability "Sensor"

        command "zone", [[name: "state*", type: "STRING"]]
    }
}

def installed() {
    sendEvent(name: "smoke", value: "clear")
}

// Called by parent connection driver with state: "closed", "open", "alarm", or "tested"
def zone(String state) {
    def eventMap = [closed: "clear", open: "detected", alarm: "detected", tested: "tested"]
    def descMap  = [closed: "Was Cleared", open: "Smoke Detected", alarm: "Smoke Detected", tested: "Was Tested"]
    sendEvent(name: "smoke", value: eventMap[state] ?: state,
              descriptionText: descMap[state] ?: state)
}
