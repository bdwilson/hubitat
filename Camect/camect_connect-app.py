/**
 *  Camect Connect
 *  Version: 1.0.0
 */
import groovy.json.JsonSlurper

definition(
  name: "Camect Connect",
  namespace: "brianwilson-hubitat",
  author: "bubba@bubba.org",
  description: "Camect Connect",
  category: "My Apps",
  iconUrl: "https://raw.githubusercontent.com/redloro/smartthings/master/images/honeywell-security.png",
  iconX2Url: "https://raw.githubusercontent.com/redloro/smartthings/master/images/honeywell-security.png",
  iconX3Url: "https://raw.githubusercontent.com/redloro/smartthings/master/images/honeywell-security.png",
  singleInstance: true
)

preferences {
	page(name: "page1")
}

def page1() {
    state.isDebug = isDebug
    if(!state.accessToken){	
        //enable OAuth in the app settings or this call will fail
        createAccessToken()	
    }
 	def uri = getFullLocalApiServerUrl() + "/camect/?access_token=${state.accessToken}"
    return dynamicPage(name: "page1", install: true, uninstall: true) {
    section("Camect Connect") {
      paragraph "Your Camect Home Code can be obtained by navigating to https://local.home.camect.com, then accepting the TOS. Once there, your home code is the first part of hostname - xxxxx.l.home.camect.com"
      paragraph "Your password is the first part of your email address that you used to register your Camect - for instance, bob@gmail.com would login as admin/bob."
      input "camectCode", "text", title: "Camect Home Code", description: "(ie. xxxxx.l.home.camect.com)", required: true
      input "user", "text", title: "Username", description: "(ie. admin)", required: true, defaultValue: "admin"
      input "pass", "password", title: "Password", description: "(ie. first part of email address", required: true, defaultValue: ""
      input "time", "text", title: "Time to keep a motion zone open (in seconds)", required: true, defaultValue: "20"
    }
    section("Camect Motion Device Creation") {
       input "enableDiscovery", "bool", title: "Optionally discover Cameras (or add new ones) and Create virtual motion devices. Re-enabling this will only discover new devices, to remove devices, you need to remove the app.", required: false, defaultValue: false
    }

    section("Hubitat Safety Monitor") {
      input "enableHSM", "bool", title: "Integrate with Hubitat Safety Monitor", required: true, defaultValue: true
    }
    section("") {
       input "isDebug", "bool", title: "Enable Debug Logging", required: false, multiple: false, defaultValue: false, submitOnChange: true
    }
    section(){ 
        paragraph("Use the following URL for Camect Connector variable hubitatOAUTHURL if you have enabled Motion Device Creation: <a href='${uri}'>${uri}</a>")
    }
    section() {
            paragraph "Please save the above app before coming back and setting up a disabler"
            app(name: "CamectMotionDisabler", appName: "Camect Connect Child - Motion Disabler", namespace: "brianwilson-hubitat", title: "Add a Motion Disabler", multiple: true)
    }
  }
}

def installed() {
	updated()
}

def subscribeToEvents() {
  //subscribe(location, null, lanResponseHandler, [filterEvents:false])
  subscribe(location, "hsmStatus", alarmHandler)
}

def uninstalled() {
  removeChildDevices()
}

def updated() {
  unsubscribe()
  subscribeToEvents()
  //if (settings.enableDiscovery) {
    // remove this before publishing this. 
  //  removeChildDevices()
  //}

  if (settings.enableDiscovery) {
    //Dont have a reason to have a primary child at this point. may add later?
    //def currentchild = getChildDevices()?.find { it.deviceNetworkId == "${settings.camectCode}"}
    //if (currentchild == null) {
	//   ifDebug("Creating Camect Connect primary child")
	//   addChildDevice("brianwilson-hubitat", "Camect Connect Driver", settings.camectCode)
    //}
	state.installed = true
    runIn(5, discoverChildDevices)
    settings.enableDiscovery = false
  }
}


private sendCommand(command, params=[:]) {
    // Authorization https://community.hubitat.com/t/lametric-time/5000/3
    def apiEndpoint = "https://${settings.camectCode}.l.home.camect.com:443"
    def path = "/api${command}"
    def auth = "${settings.user}:${settings.pass}".bytes.encodeBase64()
    ifDebug("sendCommand: Endpoint: ${apiEndpoint} PATH: ${path} PARAMS: ${params} AUTH: $auth")

    def request = [
        uri:  apiEndpoint,
        path: path,
        headers: ["Authorization": "Basic ${auth}"],
        contentType: 'application/json',
        query: params
    ]
    try {
        httpGet(request) {resp ->
            ifDebug("resp data: ${resp.data}")
            return resp.data
        }
    } catch (e) {  // } catch (groovyx.net.http.HttpResponseException e)  $e.response
        log.error "error: $e"
    }
}

private jsonEvent() {
    data = new groovy.json.JsonSlurper().parseText(request.body)
    if (data.type == "alert") {
        ifDebug("data: ${data}")
        def child = getChildDevices()?.find { it.deviceNetworkId == "${data.cam_id}"}
        if (child) {
            child.updateStatus("open", settings.time, data)  // no close event yet.
            // auto close after time; do this in motion driver.  
        } 
    }
}


private removeChildDevices() {
  getAllChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

def discoverChildDevices() {
  def hub = location.hubs[0]
  def response = sendCommand('/ListCameras')
  for (camera in response.camera) {
      ifDebug("Camera Found: ID ${camera.id} Name: ${camera.name}")
      if (!getChildDevice(camera.id)) {
          ifDebug("Found new camera: ${camera.name}")
          addChildDevice("brianwilson-hubitat", "Camect Motion", camera.id, hub.id, ["name": camera.name, label: camera.name, completedSetup: true])
          ifDebug("Added new Camect Motion Device ${camera.name} (Device ID: ${camera.id})")
      }
  }  
}


def alarmHandler(evt) {
  if (!settings.enableHSM) {
    return
  }
  
  if ((state.alarmSystemStatus == "disarm") && (evt.value == "disarmed")) {
	return
  }
  if (state.alarmSystemStatus == evt.value) {
    return
  }

  ifDebug("Received HSM event: Value: ${evt.value} state.alarmSystemStatus: ${state.alarmSystemStatus}")
  state.alarmSystemStatus = evt.value

  if (evt.value == "disarmed") {
      ifDebug("Setting Camect to HOME mode")
      sendCommand('/SetOperationMode', [Mode:"HOME"])
      sendCommand('/GetHomeInfo')
  } else {
      ifDebug("Setting Camect to DEFAULT mode")
      sendCommand('/SetOperationMode', [Mode:"DEFAULT"])
      sendCommand('/GetHomeInfo')
  }
}

mappings {
	path("/camect/") {
		action: [
            POST: "jsonEvent",
		]
	}
}

private ifDebug(msg) {  
    if (msg && state.isDebug)  log.debug 'Camect Connect: ' + msg  
}
