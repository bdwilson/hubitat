/**
 *  Smart Mailbox
 *
 *  Original SmartThings author Edgar Santana, edited for Hubitat by Brian Wilson
 *  Date: 2015-1-11
 */
definition(
    name: "Smart Mailbox",
    namespace: "brianwilson-hubitat",
    author: "Brian Wilson",
    description: "Get notifications or text message when your mail arrives.",
    category: "Convenience",
	importURL: "https://raw.githubusercontent.com/bdwilson/hubitat/master/SmartMailbox/SmartMailbox-App.groovy",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
	state.isDebug = isDebug
	section("Choose one or more, when..."){
		input "contact", "capability.contactSensor", title: "Contact Opens", required: false, multiple: true
	}
	section("Send this message (optional, sends standard status message if not specified)"){
		input "messageText", "Your mail was delivered", title: "Mail Delivered?", required: false
		input "sendPushMessage", "capability.notification", title: "Send a Pushover notification?", multiple: true, required: true, submitOnChange: true
	}
	
	section("Only notify between these times of day") {
			input "starting", "time", title: "Starting", required: false 
			input "ending", "time", title: "Ending", required: false 
	}
	section("Minimum time between messages (optional, defaults to every message) - don't use if using 'Notify every other' below.") {
		input "frequency", "decimal", title: "Minutes", required: false
	}
	section("Notify every other time mailbox is open (resets nightly; uses the above 'between these times' schedule if set)") {
       input "notifyEveryOther", "bool", title: "Notify every other time", required: false, multiple: false, defaultValue: false, submitOnChange: true
       input "notifyFirstTime", "bool", title: "Notify First Time? (otherwise second)", required: false, multiple: false, defaultValue: false, submitOnChange: true

	}
	section("") {
       		input "isDebug", "bool", title: "Enable Debug Logging", required: false, multiple: false, defaultValue: false, submitOnChange: true
    }
 
}

def installed() {
	ifDebug("Installed with settings: ${settings}")
	subscribeToEvents()
}

def updated() {
	ifDebug("Updated with settings: ${settings}")
	unsubscribe()
	subscribeToEvents()
}

def uninstall() {
	unschedule()
}

def subscribeToEvents() {
	state.isDebug=false
	state.lastTime=NULL
	subscribe(contact, "contact.open", eventHandler)
	subscribe(contactClosed, "contact.closed", eventHandler)
	//contact.each {
	//	log.debug("ID: ${it.deviceId}")
	//	state[it.deviceId]=NULL
	//}
	if (settings.notifyEveryOther) {
		ifDebug("Setting notifyEveryOther active")
			resetNotify()
		    unschedule()
			schedule("0 0 0 ? * * *", resetNotify)
	}
	if (settings.isDebug) {
		state.isDebug=true
	}
}

def resetNotify() {
	if (settings.notifyFirstTime) {
		state.notifyEveryOther = 0
	} else {
		state.notifyEveryOther = 1
	}	
}

def eventHandler(evt) {
	if (timeOk) {
		if ((settings.notifyEveryOther) && (state.notifyEveryOther % 2 == 0)) {
			ifDebug("notifyEveryOther= ${state.notifyEveryOther}") 
			sendMessage(evt)
		} else if (frequency) {
			//def lastTime = state[evt.deviceId]
			lastTime = state.lastTime
			ifDebug("lastTime = ${lastTime} ${state.lastTime}")
			if (lastTime != null) {
			    def curDiff = now() - lastTime
			    def freq = frequency * 60000
			    ifDebug("Last Time: ${lastTime}, currDiff: ${currDiff}, frequency: ${frequency}, freq: ${freq}")
			    if (now() - lastTime >= frequency * 60000) {
				    sendMessage(evt)
				}	
			} else {
					ifDebug("lastTime=null")
					sendMessage(evt)
			}
		} else if (!settings.notifyEveryOther) {
			sendMessage(evt)
		}
    }
	if (settings.notifyEveryOther) {
		state.notifyEveryOther+=1
	}
}

private sendMessage(evt) {
	state.lastTime = now()
	def msg = "Mail is here!"
	if (messageText) {
		msg = messageText
	}
	ifDebug("$evt.name:$evt.value:$evt.deviceId:${now()} '$msg'")
	sendPushMessage.deviceNotification(msg)
	//state[evt.deviceId] = now()
	state.lastTime = now()

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
    
	ifDebug("timeOk = $result")
	result
}

private ifDebug(msg) {  
    if (msg && state.isDebug)  log.debug 'SmartMailbox: ' + msg  
}
