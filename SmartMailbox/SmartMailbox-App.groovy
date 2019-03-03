/**
 *  Smart Mail Box
 *
 *  Original SmartThings author Edgar Santana, edited for Hubitat by Brian Wilson
 *  Date: 2015-1-11
 */
definition(
    name: "Smart Mail Box",
    namespace: "brianwilson-hubitat",
    author: "Brian Wilson",
    description: "Get notifications or text message when your mail arrives.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
	section("Choose one or more, when..."){
		input "contact", "capability.contactSensor", title: "Contact Opens", required: false, multiple: true
	}
	section("Send this message (optional, sends standard status message if not specified)"){
		input "messageText", "Your mail was delivered", title: "Mail Delivered?", required: false
		input "sendPushMessage", "capability.notification", title: "Send a Pushover notification?", multiple: true, required: true, submitOnChange: true
	}

	section("Minimum time between messages (optional, defaults to every message)") {
		input "frequency", "decimal", title: "Minutes", required: false
	}
    section("Only notify between these times of day") {
			input "starting", "time", title: "Starting", required: false 
			input "ending", "time", title: "Ending", required: false 
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	subscribeToEvents()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	subscribeToEvents()
}

def subscribeToEvents() {
	subscribe(contact, "contact.open", eventHandler)
	subscribe(contactClosed, "contact.closed", eventHandler)
}

def eventHandler(evt) {
	if (timeOk) {
    	if (frequency) {
			def lastTime = state[evt.deviceId]
			if (lastTime == null || now() - lastTime >= frequency * 60000) {
				sendMessage(evt)
			}
		}
		else {
			sendMessage(evt)
		}
    }
}

private sendMessage(evt) {
	def msg = "Mail is here!"
	if (messageText) {
		msg = messageText
	}
	log.debug "$evt.name:$evt.value '$msg'"
	sendPushMessage.deviceNotification(msg)

	if (frequency) {
		state[evt.deviceId] = now()
	}
}

private getTimeOk() {
	def result = true
	if (starting && ending) {
		def currTime = now()
		def start = timeToday(starting).time
		def stop = timeToday(ending).time
		result = start < stop ? currTime >= start && currTime <= stop : currTime <= stop || currTime >= start
	}
    
    else if (starting){
    	result = currTime >= start
    }
    else if (ending){
    	result = currTime <= stop
    }
    
	log.trace "timeOk = $result"
	result
}
