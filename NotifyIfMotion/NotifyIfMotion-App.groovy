/**
 *  Notify if motion
 */
definition(
    name: "Notify if motion",
    namespace: "brianwilson-hubitat",
    author: "Brian Wilson",
    description: "Notify API if motion",
    category: "Family",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Which motion sensors?") {
		input "motions", "capability.motionSensor", title: "Motion(s):", required: true, multiple: true
	}
    section ("Endpoint URL:") {
        input "endpointURL", "text", title: "Endpoint URL to notify of motion:", required:true
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
	subscribe(motions, "motion.active", motionHandler)
    subscribe(motions, "motion.inactive", motionHandler)
}

def motionHandler(evt) {
	
	 def deviceName = evt.displayName.replaceAll("\\s","")
	
	 log.debug("Motion Detected Date: ${evt.date} Value: ${evt.value} DisplayName: ${evt.displayName}  deviceName: ${deviceName} State: ${state}")
    
	httpPost(uri: "${settings.endpointURL}", body: [name: "${evt.name}", value: "${evt.value}", displayName: "${deviceName}", stateChanged: "$evt.isStateChange()"])
		{response -> log.debug "HTTP Response from Homer: ${response.data}"}
}

def startingTime() {
	state.checkMotion = true
}

def endingTime() {
	state.checkMotion = false
}
