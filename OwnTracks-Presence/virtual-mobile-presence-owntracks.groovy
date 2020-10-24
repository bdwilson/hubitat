//		- based on original work by Austin Pritchett/ajpri

metadata {
        definition (
            name: "Virtual Mobile Presence for Owntracks", 
			namespace: "brianwilson-hubitat", 
            author: "Brian Wilson", 
            importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/virtual-mobile-presence-owntracks.groovy"
        ) {
        capability "Switch"
        capability "Refresh"
        capability "Presence Sensor"
	capability "Sensor"
        capability "Battery"
        attribute "ssid", "string"
        attribute "bssid", "string"    
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
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "presence", value: "present")
}

def departed () {
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "presence", value: "not present")
}

def installed () {
}

