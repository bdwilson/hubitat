/**
 *  Envisalink Zone Contact Driver for Hubitat
 *
 *  Child device of Envisalink Connection driver.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

metadata {
    definition(name: "Envisalink Zone Contact", namespace: "bdwilson", author: "bdwilson") {
        capability "Contact Sensor"
        capability "Sensor"

        command "zone", [[name: "state*", type: "STRING"]]
    }
}

def installed() {
    sendEvent(name: "contact", value: "closed")
}

// Called by parent connection driver with state: "closed", "open", or "alarm"
def zone(String state) {
    def eventMap = [closed: "closed", open: "open", alarm: "open"]
    def descMap  = [closed: "Was Closed", open: "Was Opened", alarm: "Alarm Triggered"]
    sendEvent(name: "contact", value: eventMap[state] ?: state,
              descriptionText: descMap[state] ?: state)
}
