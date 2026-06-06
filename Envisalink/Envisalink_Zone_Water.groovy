/**
 *  Envisalink Zone Water Driver for Hubitat
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
    definition(name: "Envisalink Zone Water", namespace: "bdwilson", author: "bdwilson",
               importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Envisalink/Envisalink_Zone_Water.groovy") {
        capability "Water Sensor"
        capability "Sensor"

        command "zone", [[name: "state*", type: "STRING"]]
    }
}

def installed() {
    sendEvent(name: "water", value: "dry")
}

// Called by parent connection driver with state: "closed", "open", or "alarm"
def zone(String state) {
    def eventMap = [closed: "dry", open: "wet",   alarm: "wet"]
    def descMap  = [closed: "No Water Detected", open: "Water Detected", alarm: "Alarm Triggered"]
    sendEvent(name: "water", value: eventMap[state] ?: state,
              descriptionText: descMap[state] ?: state)
}
