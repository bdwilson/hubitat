/**
 *  Hubitat Device Handler: Camect Motion Driver
 *  Version: 1.3.2
 */
metadata {
  definition (name: "Camect Motion and Alerting", 
              namespace: "brianwilson-hubitat", 
              author: "bubba@bubba.org", 
              importURL: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Camect/camect_connect-motion-driver.groovy"
  ) {
    capability "Motion Sensor"
    capability "Sensor"
    capability "Switch"

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

  TimeZone.getTimeZone('UTC')
  Date date = new Date()
  String newdate = date.format("YYYY-MM-dd HH:mm:ss")
    
  //parent.ifDebug("Scheduling close of ${name} in ${time}")
  unschedule(inactive)
  time = time.toInteger() * 1000
  runInMillis(time,inactive) 
  parent.ifDebug("Motion detected at ${name} (${device.deviceNetworkId})")
  sendEvent (name: "Objects", value: "${json.detected_obj}")
  sendEvent (name: "LastMessage", value: "${desc}")
  sendEvent (name: "LastURL", value: "${json.url}")
  sendEvent (name: "motion", value: "${newState}", descriptionText: "${desc}")
  sendEvent (name: "timestamp", value: "${newdate}")
}

def on() {
        def params  = [ Enable:1, Reason:"${device.name}", CamId:device.deviceNetworkId]
        parent.sendCommand('/EnableAlert', params) 
        sendEvent(name: "switch", value: "on", descriptionText: "Enabling alerts for ${device.name}")
}
def off() {
        def params  = [ Reason:"${device.name}", CamId:device.deviceNetworkId]
        parent.sendCommand('/EnableAlert', params) 
        sendEvent(name: "switch", value: "off", descriptionText: "Enabling alerts for ${device.name}")
}

def inactive() {
      parent.ifDebug("Motion stopped for ${device.name} (${device.deviceNetworkId})")
      sendEvent (name: "motion", value: "inactive", descriptionText: "Motion Has Stopped")

      String newdate = date.format("YYYY-MM-dd HH:mm:ss")    
      sendEvent (name: "timestamp", value: "${newdate}")    
}

