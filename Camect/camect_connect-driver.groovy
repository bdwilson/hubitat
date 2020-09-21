/**
 *  Hubitat Device Handler: Camect Connect
 *  Version: 1.3.0
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
metadata {
  definition (name: "Camect Connect Driver", 
              namespace: "brianwilson-hubitat", 
              author: "bubba@bubba.org",
              importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Camect/camect_connect-driver.groovy"
    ) {
        capability "Switch"
        capability "Refresh"
        capability "Presence Sensor"
		capability "Sensor"
        capability "Initialize"

    
        attribute "camectStatus", "String"
  }
}

def installed() {
    log.info "installed() called"
    updated()
}

def updated() {
    log.info "updated() called"
    //Unschedule any existing schedules
    initialize()
}

def initialize() {
    
  unschedule(initialize)
    
  interfaces.webSocket.close()
  pauseExecution(1000)
  
  def auth = "${parent.getSetting('user')}:${parent.getSetting('pass')}".bytes.encodeBase64()
 
  try {
        // connect webSocket to weatherflow
        interfaces.webSocket.connect("wss://${parent.getSetting('camectCode')}.l.home.camect.com/api/event_ws", headers: [ "Authorization": "Basic ${auth}" ])
    } 
    catch (e) {
        log.error "webSocket.connect failed: ${e.message}"
  }
}

def parse(String description) {
    // {"type":"alert","desc":"xxx just saw a person.","url":"https://home.camect.com/home/xxx/camera?id=xxx\u0026ts=1600388757797","cam_id":"xxxx","cam_name":"xxx","detected_obj":["person"]}]
    // {"type":"mode","desc":"HOME"}
    parent.ifDebug "parsed: $description"
      try {
        def response = null;
        response = new groovy.json.JsonSlurper().parseText(description)
        if (response == null){
            log.warn 'String description not parsed'
            return
        }

        switch (response.type) {
            case 'alert':
                parent.ifDebug("Received an alert from camect")
                def child = parent.getChildDevices()?.find { it.deviceNetworkId == "${response.cam_id}"}               
                if (child) {
                    parent.ifDebug("Sending to child: ${response.cam_id}")
                    child.updateStatus("open", parent.getSetting('time'), response)  // no close event yet.
                    // auto close after time; do this in motion driver.
                }
                break
            case 'mode':
                //if ((response.desc == "HOME") && (parent.currentValue('hsmSetArm') != "disarm")) {
                //    parent.ifDebug("Would have sent HSM Event to DISARM hsmSetArm: current val ${state.alarmSystemStatus}")
                    // sendLocationEvent(name: "hsmSetArm", value: "disarm")
                //} else if ((response.desc == "DEFAULT") && (parent.currentValue('hsmSetArm') == "disarm")) {
                //    parent.ifDebug("Would have sent HSM Event to go to DEFAULT/ARM hsmSetArm: current val  ${state.alarmSystemStatus}")
                //}
                break
            default:
                log.warn "Unhandled event: ${response}"
                break
        }
    }  
    catch(e) {
        log.error "Failed to parse json e = ${e}"
        return
    }  
}

def on() {
    // workaround for bug in API. Should be updated with the commented out code below.
    parent.cameras?.each { camera ->
        def params  = [ Enable:1, Reason:"All Cameras", CamId:camera]
        parent.sendCommand('/EnableAlert', params) 
        parent.ifDebug("Enabling alerts (All Cameras) for ${camera}")
    }
    // def params  = [ Enable:1, Reason:"All Cameras"]
    // parent.sendCommand('/EnableAlert', params) 
    sendEvent(name: "switch", value: "on", descriptionText: "Enabling alerts for All Cameras")
}
def off() {
    // workaround for bug in API. Should be updated with the commented out code below.
    parent.cameras?.each { camera ->
        def params  = [ Reason:"All Cameras", CamId:camera]
        parent.sendCommand('/EnableAlert', params) 
        parent.ifDebug("Disabling alerts (All Cameras) for ${camera}")
    }
    //def params  = [ Reason:"All Cameras"]
    //parent.sendCommand('/EnableAlert', params) 
    sendEvent(name: "switch", value: "off", descriptionText: "Disabling alerts for All Cameras")
}

void webSocketStatus(String status){
    parent.ifDebug "webSocketStatus: ${status}"

    if (status.startsWith('failure: ')) {
        log.warn "failure message from web socket ${status}"
        state.connection = 'disconnected'
        reconnectWebSocket()
    } 
    else if (status == 'status: open') {        
        log.info 'webSocket is open'
        state.connection = 'connected'

        //requestData()

        // success! reset reconnect delay
        pauseExecution(1000)
        state.reconnectDelay = 1
    } 
    else if (status == 'status: closing'){
        log.warn 'webSocket connection closing.'
        state.connection = 'closing'
    } 
    else {
        log.warn "webSocket error: ${status}"
        state.connection = 'disconnected'
        reconnectWebSocket()
    }
}

void reconnectWebSocket() {
    // first delay is 2 seconds, doubles every time
    state.reconnectDelay = (state.reconnectDelay ?: 1) * 2

    // don't let delay get too crazy, max it out at 10 minutes
    if(state.reconnectDelay > 600) state.reconnectDelay = 600

    // if the station is offline, give it some time before trying to reconnect
    log.info "Reconnecting WebSocket in ${state.reconnectDelay} seconds."
    runIn(state.reconnectDelay, initialize, [overwrite: false])
}
