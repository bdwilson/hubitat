/**
 *  Scrypted Connect Child - Motion Disabler
 *  Version: 0.2.0
 *
 *  Mutes Scrypted app notifications for the selected cameras for N seconds
 *  whenever one of the chosen contact / lock / motion / presence events happens
 *  (e.g. unlocking the front door mutes the Doorbell and Front Porch for 2 minutes).
 *  Recording continues. Repeated triggers extend the mute; Scrypted itself
 *  re-enables notifications when the time is up, even if Hubitat is offline.
 *
 *  Licensed under the Apache License, Version 2.0
 */
definition(
    name: "Scrypted Connect Child - Motion Disabler",
    namespace: "brianwilson-hubitat",
    author: "Brian Wilson",
    description: "Temporarily mute Scrypted notifications for cameras based on Hubitat sensors",
    category: "Convenience",
    parent: "brianwilson-hubitat:Scrypted Connect",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/refs/heads/scrypted-connect/Scrypted/scrypted_connect-motion-disabler-child-app.groovy"
)

preferences {
    page(name: "pageConfig")
}

def pageConfig() {
    dynamicPage(name: "pageConfig", title: "", install: true, uninstall: true) {
        Map cameraOptions = parent.cameraOptions()
        if (!cameraOptions) {
            section("Error") {
                paragraph "No Scrypted Connect camera devices exist yet. Select cameras in the Scrypted Connect app first."
            }
            return
        }
        section("Instructions") {
            paragraph "When any selected event happens, Scrypted notifications for the selected cameras are muted for the chosen time. " +
                "Avoid using the same cameras' Scrypted Connect motion sensors as triggers here, or a detection would mute itself."
        }
        section("Cameras to mute") {
            input "cameras", "enum", title: "Cameras", options: cameraOptions, multiple: true, required: true
        }
        section("Contact sensors") {
            input "contacts", "capability.contactSensor", title: "Contact sensors", required: false, multiple: true
            input "contactOpen", "bool", title: "On open", defaultValue: true
            input "contactClose", "bool", title: "On close", defaultValue: true
        }
        section("Locks") {
            input "locks", "capability.lock", title: "Locks", required: false, multiple: true
            input "lockUnlock", "bool", title: "On unlocked", defaultValue: true
            input "lockLock", "bool", title: "On locked", defaultValue: true
        }
        section("Motion sensors") {
            input "motions", "capability.motionSensor", title: "Motion sensors", required: false, multiple: true
            input "motionActive", "bool", title: "On active", defaultValue: true
            input "motionInactive", "bool", title: "On inactive", defaultValue: true
        }
        section("Presence sensors") {
            input "presence", "capability.presenceSensor", title: "Presence sensors", required: false, multiple: true
            input "onPresent", "bool", title: "On arrival (present)", defaultValue: true
            input "onNotPresent", "bool", title: "On departure (not present)", defaultValue: true
        }
        section("Duration") {
            input "seconds", "number", title: "Mute notifications for this many seconds", required: true, defaultValue: 120, range: "10..86400"
        }
        section("Name") {
            label title: "Name this Motion Disabler", required: true
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
    if (contacts) {
        if (settings.contactOpen) subscribe(contacts, "contact.open", triggerHandler)
        if (settings.contactClose) subscribe(contacts, "contact.closed", triggerHandler)
    }
    if (locks) {
        if (settings.lockUnlock) subscribe(locks, "lock.unlocked", triggerHandler)
        if (settings.lockLock) subscribe(locks, "lock.locked", triggerHandler)
    }
    if (motions) {
        if (settings.motionActive) subscribe(motions, "motion.active", triggerHandler)
        if (settings.motionInactive) subscribe(motions, "motion.inactive", triggerHandler)
    }
    if (presence) {
        if (settings.onPresent) subscribe(presence, "presence.present", triggerHandler)
        if (settings.onNotPresent) subscribe(presence, "presence.not present", triggerHandler)
    }
    parent.ifDebug("Motion Disabler '${app.label}' watching for ${settings.seconds}s mutes of ${settings.cameras}")
}

def triggerHandler(evt) {
    parent.ifDebug("Motion Disabler '${app.label}': ${evt.displayName} ${evt.value} -> muting ${settings.cameras} for ${settings.seconds}s")
    parent.muteCameras(settings.cameras as List, settings.seconds as Integer,
        "Motion Disabler '${app.label}': ${evt.displayName} ${evt.value}")
}
