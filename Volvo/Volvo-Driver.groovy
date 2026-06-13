/**
 * Volvo Vehicle Driver
 *
 * 1.0.0 - Brian Wilson / bubba@bubba.org
 *
 * Child driver for the Volvo Connect App.
 * Exposes lock/unlock, fuel level, battery/EV level, range, and GPS location.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at:
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

metadata {
    definition(
        name: "Volvo Vehicle Driver",
        namespace: "brianwilson-hubitat",
        author: "Brian Wilson",
        importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Volvo/Volvo-Driver.groovy"
    ) {
        capability "Lock"
        capability "Battery"
        capability "Refresh"

        command "lock"
        command "unlock"
        command "refresh"

        // Fuel (ICE / hybrid)
        attribute "fuelLevel",  "NUMBER"   // percent
        attribute "fuelRange",  "NUMBER"   // km

        // EV / hybrid battery
        attribute "batteryRange",    "NUMBER"   // km
        attribute "chargingStatus",  "STRING"

        // Location
        attribute "latitude",     "STRING"
        attribute "longitude",    "STRING"
        attribute "lastLocation", "STRING"

        attribute "lastRefresh",  "STRING"
    }

    preferences {
        input "isDebug", "bool", title: "Enable Debug Logging", defaultValue: false
    }
}

def installed() {
    refresh()
}

def updated() {
    if (isDebug) runIn(3600, logsOff)
}

def lock() {
    ifDebug("lock() called")
    sendEvent(name: "lock", value: "locking")
    def vin = device.deviceNetworkId
    parent.lockVehicle(vin)
}

def unlock() {
    ifDebug("unlock() called")
    sendEvent(name: "lock", value: "unlocking")
    def vin = device.deviceNetworkId
    parent.unlockVehicle(vin)
}

def refresh() {
    ifDebug("refresh() called")
    def vin = device.deviceNetworkId
    parent.refreshVehicle(vin)
}

def logsOff() {
    log.warn "Volvo Driver: debug logging disabled"
    device.updateSetting("isDebug", [value: "false", type: "bool"])
}

private void ifDebug(String msg) {
    if (isDebug) log.debug "Volvo Driver [${device.displayName}]: ${msg}"
}
