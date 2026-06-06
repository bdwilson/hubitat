/**
 *  Envisalink Zone Carbon Monoxide Driver for Hubitat
 *
 *  Child device of Envisalink Connection driver.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Version: 2.0.1
 */

metadata {
    definition(name: "Envisalink Zone CO", namespace: "bdwilson", author: "bdwilson",
               importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Envisalink/Envisalink_Zone_CO.groovy") {
        capability "Carbon Monoxide Detector"
        capability "Sensor"

        command "zone", [[name: "state*", type: "STRING"]]
    }
}

def installed() {
    sendEvent(name: "carbonMonoxide", value: "clear")
}

// Called by parent connection driver with state: "closed", "open", "alarm", or "tested"
def zone(String state) {
    def eventMap = [closed: "clear", open: "detected", alarm: "detected", tested: "tested"]
    def descMap  = [closed: "Was Cleared", open: "CO Detected", alarm: "CO Detected", tested: "Was Tested"]
    sendEvent(name: "carbonMonoxide", value: eventMap[state] ?: state,
              descriptionText: descMap[state] ?: state)
}
