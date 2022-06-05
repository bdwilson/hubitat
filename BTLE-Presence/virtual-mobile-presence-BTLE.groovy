//Release History
//		1.0 Oct. 12, 2015
//			Initial Release


metadata {
        definition (name: "Virtual Mobile Presence - BTLE", namespace: "brianwilson-hubitat", author: "Brian Wilson") {
        capability "Switch"
        capability "Refresh"
        capability "Presence Sensor"
		capability "Sensor"
		capability "SignalStrength"

        command "arrived"
        command "departed"
            
    }

	// simulator metadata
	simulator {
	}

	// UI tile definitions
	tiles {
		standardTile("button", "device.switch", width: 2, height: 2, canChangeIcon: false,  canChangeBackground: true) {
			state "off", label: 'Away', action: "switch.on", icon: "st.Kids.kid10", backgroundColor: "#ffffff", nextState: "on"
			state "on", label: 'Present', action: "switch.off", icon: "st.Kids.kid10", backgroundColor: "#53a7c0", nextState: "off"
		}
		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat") {
			state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        standardTile("presence", "device.presence", width: 1, height: 1, canChangeBackground: true) {
			state("present", labelIcon:"st.presence.tile.mobile-present", backgroundColor:"#53a7c0")
			state("not present", labelIcon:"st.presence.tile.mobile-not-present", backgroundColor:"#ffffff")
		}
		main (["button", "presence"])
		details(["button", "presence", "refresh"])
	}
}

def parse(String description) {
}

def arrived(rssi=null) {
     if (rssi != null) {
        sendEvent(name: "rssi", value: "${rssi}")
    }
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "presence", value: "present")
}

def departed() {
	off()
}

def on(rssi=null) {
     if (rssi != null) {
        sendEvent(name: "rssi", value: "${rssi}")
    }
	sendEvent(name: "switch", value: "on")
    sendEvent(name: "presence", value: "present")
}

def off() {
	sendEvent(name: "switch", value: "off")
    sendEvent(name: "presence", value: "not present")

}
