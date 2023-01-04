/*
 * Virtual Mobile Presence for Owntracks - based on original work by Austin Pritchett/ajpri
 *
 * Version 1.1.3.6
 * 
 */
metadata {
	definition (
		name: "OwnTracks Virtual Mobile Presence Driver", 
		namespace: "brianwilson-hubitat", 
		author: "Brian Wilson", 
		importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/virtual-mobile-presence-owntracks.groovy"
	) {
		capability "Switch"
		capability "Refresh"
		capability "Presence Sensor"
		capability "Sensor"
		capability "Battery"
        
		attribute "ssid", "STRING"
		attribute "bssid", "STRING"
		attribute "batteryStatus", "STRING"
		attribute "region", "STRING"
		attribute "user", "STRING"
		attribute "lat", "NUMBER"
		attribute "lon", "NUMBER"
        attribute "lastUpdated", "DATE"

        command "arrived"
        command "departed"
	}
	preferences { 
		input name: "region", type: "text", title: "Location/Region to Track", required: true
		input name: "user", type: "text", title: "User to Track", required: true
	}
}

def parse(String description) {
}

def on() {
	sendEvent(name: "switch", value: "on")
	sendEvent(name: "presence", value: "present")
}

def refresh() { }

def off() {
	sendEvent(name: "switch", value: "off")
	sendEvent(name: "presence", value: "not present")
}

def arrived() {
	on()
}

def departed () {
	off()
}

def installed () {
}

def updated() {
	state.clear()
	sendEvent(name: "user", value: "${user}") 
	sendEvent(name: "region", value: "${region}")
}

