/**
 * Volvo Vehicle Driver
 *
 * 1.2.1 - Brian Wilson / bubba@bubba.org
 *
 * Child driver for the Volvo Connect App.
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

        // Engine start/stop (requires conve:engine_start_stop — pending API approval for most users)
        command "startEngine", [[name: "runtimeMinutes", type: "NUMBER", description: "Runtime in minutes (1–15, default 15)"]]
        command "stopEngine"

        // Climatization (requires conve:climatization_start_stop — pending API approval)
        command "startClimatization"
        command "stopClimatization"

        // Horn / lights (requires conve:honk_flash — pending API approval)
        command "honk"
        command "flash"
        command "honkAndFlash"

        // Fuel (ICE / hybrid)
        attribute "fuelLevel",  "NUMBER"   // percent
        attribute "fuelRange",  "NUMBER"   // km or miles depending on unit preference

        // EV / hybrid battery
        attribute "batteryRange",       "NUMBER"
        attribute "chargingStatus",     "STRING"
        attribute "chargingConnection", "STRING"
        attribute "chargeLimit",        "NUMBER"

        // Engine
        attribute "engineStatus", "STRING"   // RUNNING / STOPPED

        // Doors
        attribute "doorFrontLeft",  "STRING"
        attribute "doorFrontRight", "STRING"
        attribute "doorRearLeft",   "STRING"
        attribute "doorRearRight",  "STRING"
        attribute "hood",           "STRING"
        attribute "tailgate",       "STRING"
        attribute "tankLid",        "STRING"

        // Windows
        attribute "windowFrontLeft",  "STRING"
        attribute "windowFrontRight", "STRING"
        attribute "windowRearLeft",   "STRING"
        attribute "windowRearRight",  "STRING"
        attribute "sunroof",          "STRING"

        // Tyres
        attribute "tyreFrontLeft",  "STRING"
        attribute "tyreFrontRight", "STRING"
        attribute "tyreRearLeft",   "STRING"
        attribute "tyreRearRight",  "STRING"

        // Odometer
        attribute "odometer", "NUMBER"

        // Warnings (key safety indicators)
        attribute "warningBrakeLight",     "STRING"
        attribute "warningFuelLow",        "STRING"
        attribute "warningEngineLight",    "STRING"
        attribute "warningOilLow",         "STRING"
        attribute "warningWasherFluid",    "STRING"
        attribute "warningBrakeFluid",     "STRING"

        // Vehicle info (populated once on first poll)
        attribute "vehicleModel",   "STRING"
        attribute "modelYear",      "STRING"
        attribute "fuelType",       "STRING"
        attribute "externalColor",  "STRING"
        attribute "gearbox",        "STRING"

        // Location
        attribute "latitude",          "STRING"
        attribute "longitude",         "STRING"
        attribute "heading",           "STRING"
        attribute "lastLocation",      "STRING"
        attribute "locationTimestamp", "STRING"

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
    parent.lockVehicle(device.deviceNetworkId)
}

def unlock() {
    ifDebug("unlock() called")
    sendEvent(name: "lock", value: "unlocking")
    parent.unlockVehicle(device.deviceNetworkId)
}

def refresh() {
    ifDebug("refresh() called")
    parent.refreshVehicle(device.deviceNetworkId)
}

def startEngine(minutes = null) {
    ifDebug("startEngine(${minutes}) called")
    parent.startEngineVehicle(device.deviceNetworkId, minutes)
}

def stopEngine() {
    ifDebug("stopEngine() called")
    parent.stopEngineVehicle(device.deviceNetworkId)
}

def startClimatization() {
    ifDebug("startClimatization() called")
    parent.startClimatizationVehicle(device.deviceNetworkId)
}

def stopClimatization() {
    ifDebug("stopClimatization() called")
    parent.stopClimatizationVehicle(device.deviceNetworkId)
}

def honk() {
    ifDebug("honk() called")
    parent.honkVehicle(device.deviceNetworkId)
}

def flash() {
    ifDebug("flash() called")
    parent.flashVehicle(device.deviceNetworkId)
}

def honkAndFlash() {
    ifDebug("honkAndFlash() called")
    parent.honkFlashVehicle(device.deviceNetworkId)
}

def logsOff() {
    log.warn "Volvo Driver: debug logging disabled"
    device.updateSetting("isDebug", [value: "false", type: "bool"])
}

private void ifDebug(String msg) {
    if (isDebug) log.debug "Volvo Driver [${device.displayName}]: ${msg}"
}
