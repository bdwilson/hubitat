/**
 *  Hubitat Device Handler: Camect Motion Driver
 *  Version: 1.1.0 
 */
metadata {
  definition (name: "Camect Motion", 
	namespace: "brianwilson-hubitat", 
	author: "bubba@bubba.org",
	importURL: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Camect/camect_connect-motion-driver.groovy"
   ) {
    capability "Motion Sensor"
    capability "Sensor"

    attribute "LastMessage", "string"
    attribute "Objects", "string"
    attribute "LastURL", "string"
  }
}

def updateStatus(state, time, json) {
  // need to convert open to active and closed to inactive
  def eventMap = [
    'closed':"inactive",
    'open':"active",
  ]
  def newState = eventMap."${state}"

  def descMap = [
    'closed':"Motion Has Stopped",
    'open':"Detected Motion",
  ]
  def desc = descMap."${state}"

  if (json.desc) {
        desc = json.desc
  }
  // need to parse out objects
  def name = device.name
  parent.ifDebug("Scheduling close of ${name} in ${time}")
  unschedule(inactive)
  time = time.toInteger() * 1000
  runInMillis(time,inactive) 
  sendEvent (name: "Objects", value: "${json.detected_obj}")
  sendEvent (name: "LastMessage", value: "${desc}")
  sendEvent (name: "LastURL", value: "${json.url}") 
  sendEvent (name: "motion", value: "${newState}", descriptionText: "${desc}")
  
}

def inactive() {
      sendEvent (name: "motion", value: "inactive", descriptionText: "Motion Has Stopped")
}
