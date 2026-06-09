/**
 *  Envisalink Zone Motion Driver for Hubitat
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
    definition(name: "Envisalink Zone Motion", namespace: "bdwilson", author: "bdwilson",
               importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Envisalink/Envisalink_Zone_Motion.groovy") {
        capability "Motion Sensor"
        capability "Sensor"

        command "zone", [[name: "state*", type: "STRING"]]
    }

    preferences {
        input name: "txtEnable", type: "bool", title: "Enable description text logging", defaultValue: true
    }
}

def installed() {
    sendEvent(name: "motion", value: "inactive")
}

// Called by parent connection driver with state: "closed", "open", or "alarm"
def zone(String state) {
    def eventMap = [closed: "inactive", open: "active", alarm: "active"]
    def descMap  = [closed: "Motion Stopped", open: "Motion Detected", alarm: "Alarm Triggered"]
    def desc = descMap[state] ?: state
    sendEvent(name: "motion", value: eventMap[state] ?: state, descriptionText: "${device.displayName}: ${desc}")
    if (txtEnable) log.info "${device.displayName}: ${desc}"
}
