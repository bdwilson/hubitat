/**
 *  Camect Connect
 *  Version: 1.3.0
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
  importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Camect/camect_connect-app.groovy",
  singleInstance: true
)

preferences {
	page(name: "page1")
    page(name: "page2")
    
}

def page1() {
    state.isDebug = isDebug
    //if(!state.accessToken){	
        //enable OAuth in the app settings or this call will fail
    //    createAccessToken()	
   // }
 	//def uri = getFullLocalApiServerUrl() + "/camect/?access_token=${state.accessToken}"
    return dynamicPage(name: "page1", install: false, uninstall: true, nextPage: "page2") {
    section("<h2>Camect Connect</h2>") {
      paragraph "<i>Please read the <a href=https://github.com/bdwilson/hubitat/blob/master/Camect/README.md>installation instructions</a> and check the <a href=https://community.hubitat.com/t/release-camect-connect/43837>Hubitat forum</a> if you run into issues.</a></i>"
      paragraph "Your Camect Home Code can be obtained by navigating to https://local.home.camect.com, then accepting the TOS. Once there, your home code is the first part of hostname - xxxxx.l.home.camect.com"
      paragraph "Your password is the first part of your email address that you used to register your Camect - for instance, bob@gmail.com would login as admin/bob."
      input "camectCode", "text", title: "Camect Home Code", description: "(ie. xxxxx.l.home.camect.com)", required: true
      input "user", "text", title: "Username", description: "(ie. admin)", required: true, defaultValue: "admin"
      input "pass", "password", title: "Password", description: "(ie. first part of email address", required: true, defaultValue: ""
      input "time", "text", title: "Time to keep a motion zone open (in seconds)", required: true, defaultValue: "20"
    }

    section("<h3><b>Optional</b>: Hubitat Safety Monitor</h3>") {
      input "enableHSM", "bool", title: "Integrate with Hubitat Safety Monitor", required: true, defaultValue: true
      paragraph("")
    }
    section("Enable Debug?") {
       input "isDebug", "bool", title: "Enable Debug Logging", required: false, multiple: false, defaultValue: false, submitOnChange: true
       paragraph("")
    }
        
    section("<h3><b>Optional</b>: Camect Virtual Motion Device Creation</h3>"){ 
        paragraph("Click the <b>Next</b> button to pick which devices you want to create virtual devices for. Even if you don't want to create virtual motion devices, you need to click Next, don't select any devices, then install. If you wish to remove previously created virtual devices, unselect them on the next page.")
        paragraph("")
    }
    section("<h3><b>Optional</b>: Motion Disabler</h3>") {            
        if (state.installed) {
            paragraph "Configure a motion disabler to disable motion detection for a certain period of time based on certain device criteria in your home. "
            paragraph "This could be any lock/motion/contact/presence device and you can have overlapping rules or different rules for leaving or arriving. "
            app(name: "CamectMotionDisabler", appName: "Camect Connect Child - Motion Disabler", namespace: "brianwilson-hubitat", title: "Add a Motion Disabler", multiple: true)
        } else {
            paragraph "Please click next to complete installation <b>THEN</b> come back here in order to configure a motion disabler app; this will allow you to disable motion for a given set of cameras, for a certain period of time, based on motion/contact/lock/presence events."
        }
    }
  }
}

def page2() {
    dynamicPage(name: "page2", title: "", install: true, uninstall: true, refreshInterval:0) {
        section("Instructions:", hideable: false, hidden: false) {
			paragraph "Select optional sensors/locks/contacts that will be used to disable notifications on the selected camera."
		}
        section("Select cameras that will be disabled when any of the below are triggered") {
            prefListDevices("Select which cameras to create virtual motion sensors for:") 
        }
	}
}

def installed() {
    updated()
}

def subscribeToEvents() {
  subscribe(location, "hsmStatus", alarmHandler)
}

def uninstalled() {
  removeChildDevices()
}

def updated() {
  unsubscribe()
  subscribeToEvents()

  def currentchild = getChildDevices()?.find { it.deviceNetworkId == "${settings.camectCode}"}
  if (currentchild == null) {
      ifDebug("Creating Camect Connect primary child")
	  addChildDevice("brianwilson-hubitat", "Camect Connect Driver", settings.camectCode)
    }
    createChildDevices(cameras)
    deleteChildDevices(cameras)
	state.installed = true
}

private sendCommand(command, params=[:]) {
    // Authorization https://community.hubitat.com/t/lametric-time/5000/3
    def apiEndpoint = "https://${settings.camectCode}.l.home.camect.com:443"
    def path = "/api${command}"
    def auth = "${settings.user}:${settings.pass}".bytes.encodeBase64()
    ifDebug("sendCommand: Endpoint: ${apiEndpoint} PATH: ${path} PARAMS: ${params}")

    def request = [
        uri:  apiEndpoint,
        path: path,
        headers: ["Authorization": "Basic ${auth}"],
        contentType: 'application/json',
        query: params
    ]
    //ifDebug("http get request: ${request}")
    try {
        httpGet(request) {resp ->
            //ifDebug("resp data: ${resp.data}")
            return resp.data
        }
    } catch (e) {  // } catch (groovyx.net.http.HttpResponseException e)  $e.response
        log.error "error: $e $request"
    }
}

private jsonEvent() {
    data = new groovy.json.JsonSlurper().parseText(request.body)
    if (data.type == "alert") {
        def child = getChildDevices()?.find { it.deviceNetworkId == "${data.cam_id}"}
        if (child) {
            child.updateStatus("open", settings.time, data)  // no close event yet.
            // auto close after time; do this in motion driver.  
        } 
    }
}


private removeChildDevices() {
  getAllChildDevices()?.each { deleteChildDevice(it.deviceNetworkId) }
}

def deleteChildDevices(cameras) {
    state.cameraList?.each { id, name ->
         if (!cameras?.contains(id)) {
             if(getChildDevice(id)) {
                 deleteChildDevice(id)
                 ifDebug("Removing unselected camera ${name} (${id})")
             }
         }

     }
}

def createChildDevices(cameras) {
  def hub = location.hubs[0]
  def response = sendCommand('/ListCameras')
    
  cameras?.each { camera -> 
      if (!getChildDevice(camera)) {
          ifDebug("Found new camera: ${state.cameraList[camera]}")
          addChildDevice("brianwilson-hubitat", "Camect Motion and Alerting", camera, hub.id, ["name": state.cameraList[camera], label: state.cameraList[camera], completedSetup: true])
          ifDebug("Added new Camect Motion and Alerting Device: ${state.cameraList[camera]} (Device ID: $camera)")
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

def prefListDevices(title) {  
    state.cameraList = [:]
    def response = sendCommand('/ListCameras')
    for (camera in response.camera) { 
        //ifDebug("Camera Found: ID ${camera.id} Name: ${camera.name}")
        state.cameraList[camera.id] = camera.name
    }
    if (state.cameraList) {
        section("${title}"){
            input(name: "cameras", type: "enum", required:false, multiple:true, options:state.cameraList)
        }
    } else {
        section("Error") {
            paragraph("ERROR: Make sure you check your password and your camect code")
        }
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
