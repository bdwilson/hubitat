/**
 *  Envisalink Partition Driver for Hubitat
 *
 *  Child device of Envisalink Connection driver.
 *  Commands are forwarded to the parent connection driver which
 *  sends them to the panel via TPI.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 *  except in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Version: 2.0.3
 */

metadata {
    definition(name: "Envisalink Partition", namespace: "bdwilson", author: "bdwilson",
               importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Envisalink/Envisalink_Partition.groovy") {
        capability "Alarm"
        capability "Sensor"
        capability "Actuator"

        command "armAway"
        command "armStay"
        command "armInstant"
        command "disarm"
        command "chime"
        command "trigger1"
        command "trigger2"
        command "bypass"
        command "partition", [
            [name: "state*", type: "STRING"],
            [name: "alpha*", type: "STRING"]
        ]

        attribute "dscpartition", "String"
        attribute "panelStatus",  "String"
    }

    preferences {
        input name: "bypassZones", type: "text",
              title: "Default Bypass Zones",
              description: "Comma-separated zone numbers used by the Bypass button",
              required: false
    }
}

def installed() {
    sendEvent(name: "dscpartition", value: "notready")
}

// Called by parent connection driver when partition state changes
def partition(String partState, String alpha) {
    sendEvent(name: "dscpartition", value: partState, descriptionText: alpha)
    sendEvent(name: "panelStatus",  value: alpha, displayed: false)
}

// ─── Commands → forwarded to parent connection driver ─────────────────────────

def armAway()    { parent.armAway() }
def armStay()    { parent.armStay() }
def armInstant() { parent.armInstant() }
def disarm()     { parent.disarm() }
def off()        { parent.disarm() }
def chime()      { parent.chime() }
def trigger1()   { parent.trigger1() }
def trigger2()   { parent.trigger2() }

// Called from the device UI button — uses the bypassZones preference
def bypass() {
    if (settings.bypassZones) parent.bypass(settings.bypassZones)
}

// Called programmatically (e.g., from Rule Machine) with explicit zones
def bypass(String zones) {
    parent.bypass(zones)
}
