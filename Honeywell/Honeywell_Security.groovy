/**
 *  Hubitat SmartApp: Honeywell Security
 *
 *  Original Author: redloro@gmail.com, updated for Hubitat by bubba@bubba.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 * 
 *  Version: 1.0.1
 */
import groovy.json.JsonSlurper

definition(
  name: "Honeywell Security",
  namespace: "brianwilson-hubitat",
  author: "bubba@bubba.org",
  description: "Honeywell Security SmartApp",
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
  dynamicPage(name: "page1", install: true, uninstall: true) {
	// Only support single hub
    //section("SmartThings Hub") {
    //  input "hostHub", "hub", title: "Select Hub", multiple: false, required: true
    //}
    section("SmartThings Node Proxy") {
      input "proxyAddress", "text", title: "Proxy Address", description: "(ie. 192.168.1.10)", required: true
      input "proxyPort", "text", title: "Proxy Port", description: "(ie. 8080)", required: true, defaultValue: "8080"
      input "authCode", "password", title: "Auth Code", description: "", required: true, defaultValue: "secret-key"
      input "macAddr", "text", title: "MacAddr of Proxy Server", description: "", required: true, defaultValue: "ABCA1234ABA"
    }
    section("Honeywell Panel") {
      input name: "pluginType", type: "enum", title: "Plugin Type", required: true, submitOnChange: true, options: ["envisalink", "ad2usb"]
      input "securityCode", "password", title: "Security Code", description: "User code to arm/disarm the security panel", required: false
      input "enableDiscovery", "bool", title: "Discover Zones (WARNING: All existing zones will be removed and recreated and mess up any existing rules that rely on zones.)", required: false, defaultValue: false
    }

    if (pluginType == "envisalink") {
      section("Envisalink Vista TPI") {
        input "evlAddress", "text", title: "Host Address", description: "(ie. 192.168.1.11)", required: false
        input "evlPort", "text", title: "Host Port", description: "(ie. 4025)", required: false
        input "evlPassword", "password", title: "Password", description: "", required: false
      }
    }

    section("Hubitat Safety Monitor") {
      input "enableHSM", "bool", title: "Integrate with Hubitat Safety Monitor", required: true, defaultValue: true
    }
    section("") {
       input "isDebug", "bool", title: "Enable Debug Logging", required: false, multiple: false, defaultValue: false, submitOnChange: true
    }
  }
}

def installed() {
	updated()
}

def subscribeToEvents() {
	subscribe(location, null, lanResponseHandler, [filterEvents:false])
  	subscribe(location, "hsmStatus", alarmHandler)
}

def uninstalled() {
 	removeChildDevices()
}

def updated() {
  unsubscribe()
  subscribeToEvents()
  if (settings.enableDiscovery) {
    //remove child devices as we will reload
    removeChildDevices()
  }

  state.alarmSystemStatus

  //subscribe to callback/notifications from STNP
  sendCommand('/subscribe/'+getNotifyAddress())

  //save envisalink settings to STNP config
  if (settings.pluginType == "envisalink" && settings.evlAddress && settings.evlPort && settings.evlPassword && settings.securityCode) {
    sendCommandPlugin('/config/'+settings.evlAddress+":"+settings.evlPort+":"+settings.evlPassword+":"+settings.securityCode)
  }

  //save ad2usb settings to STNP config
  if (settings.pluginType == "ad2usb" && settings.securityCode) {
    sendCommandPlugin('/config/'+settings.securityCode)
  }

  if (settings.enableDiscovery) {
    //delay discovery for 5 seconds
	def DNI=macAddr.replace(":","").toUpperCase()
	ifDebug("Creating Honeywell Security Child")
	addChildDevice("brianwilson-hubitat", "Honeywell Partition", DNI)
	state.installed = true
    runIn(5, discoverChildDevices)
    settings.enableDiscovery = false
  }
}

def lanResponseHandler(fromChildDev) {
	try {
    	def parsedEvent = parseLanMessage(fromChildDev).json
		def description = parsedEvent?.description
		def map = parseLanMessage(fromChildDev)
  		if (map.headers.'stnp-plugin' != settings.pluginType) {
      		return
  		}
  		processEvent(parsedEvent)
	} catch(MissingMethodException) {
		// these are events with description: null and data: null, so we'll just pass.
		pass
	}
}

private sendCommandPlugin(path) {
  sendCommand("/plugins/"+settings.pluginType+path)
}

private sendCommand(path) {
  ifDebug("send command: ${path}")

  if (settings.proxyAddress.length() == 0 ||
    settings.proxyPort.length() == 0) {
    ifDebug("SmartThings Node Proxy configuration not set!")
    return
  }

  def host = getProxyAddress()
  def headers = [:]
  headers.put("HOST", host)
  headers.put("Content-Type", "application/json")
  headers.put("stnp-auth", settings.authCode)

  def hubAction = new hubitat.device.HubAction(
      method: "GET",
      path: path,
      headers: headers
  )
  sendHubCommand(hubAction)
}

private processEvent(evt) {
  ifDebug("Running Process Event ${evt}")
  if (evt.type == "discover") {
    addChildDevices(evt.partitions, evt.zones)
  }
  if (evt.type == "zone") {
    updateZoneDevices(evt.zone, evt.state)
  }
  if (evt.type == "partition") {
    updatePartitions(evt.partition, evt.state, evt.alpha)
    updateAlarmSystemStatus(evt.state,evt.alpha)
  }
}

private addChildDevices(partitions, zones) {
  zones.each {
    def deviceId = 'honeywell|zone'+it.zone
	def hub = location.hubs[0]
    ifDebug("Adding Child Device (zone): ${deviceId}")
    if (!getChildDevice(deviceId)) {
      it.type = it.type.capitalize()
      addChildDevice("brianwilson-hubitat", "Honeywell Zone "+it.type, deviceId, hub.id, ["name": it.name, label: it.name, completedSetup: true])
		ifDebug("Added zone device: ${deviceId} ${hub.id} ${hub.name}")
    }
  }
}

private removeChildDevices() {
  getAllChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

def discoverChildDevices() {
  sendCommandPlugin('/discover')
}

private updateZoneDevices(zonenum,zonestatus) {
  ifDebug("updateZoneDevices: ${zonenum} is ${zonestatus}")
  def zonedevice = getChildDevice("honeywell|zone${zonenum}")
  if (zonedevice) {
    zonedevice.zone("${zonestatus}")
  }
}

private updatePartitions(partitionnum, partitionstatus, panelalpha) {
  // since our main partition is based on MAC address of the SmartThings Node Proxy
  // we already know which partition to update. Again, only supports single partition.
  def DNI=macAddr.replace(":","").toUpperCase()
  ifDebug("updatePartitions: ${DNI} is ${partitionstatus}")
  def partitionDevice = getChildDevice(DNI)
  ifDebug("Partition Device: ${partitionDevice}")
  if (partitionDevice) {
    partitionDevice.partition("${partitionstatus}", "${panelalpha}")
  }
}

def alarmHandler(evt) {
  if (!settings.enableHSM) {
    return
  }

  // deal with when you have just set hsmSetArm=disarm  
  // and received the event hsmStatus=disarmed 
  if ((state.alarmSystemStatus == "disarm") && (evt.value == "disarmed")) {
	return
  }
  if (state.alarmSystemStatus == evt.value) {
    return
  }

  ifDebug("Received HSM event: Value: ${evt.value} state.alarmSystemStatus: ${state.alarmSystemStatus}")
  state.alarmSystemStatus = evt.value
  if (evt.value == "armedHome") {
    sendCommandPlugin('/armStay')
  }
  if (evt.value == "armedNight") {
    sendCommandPlugin('/armStay')
  }
  if (evt.value == "armedAway") {
    sendCommandPlugin('/armAway')
  }
  if (evt.value == "disarmed") {
    sendCommandPlugin('/disarm')
  }
}

private updateAlarmSystemStatus(partitionstatus,alpha) {
  if (!settings.enableHSM || partitionstatus == "arming" || alpha.contains("May Exit")) {
    return
  }

  def lastAlarmSystemStatus = state.alarmSystemStatus
  if (partitionstatus == "armedstay" || partitionstatus == "armedinstant") {
    state.alarmSystemStatus = "armHome"
  }
  if (partitionstatus == "armedaway" || partitionstatus == "armedmax") {
    state.alarmSystemStatus = "armAway"
  }
  if (partitionstatus == "ready") {
    state.alarmSystemStatus = "disarm"
  }

  if (lastAlarmSystemStatus != state.alarmSystemStatus) {
	ifDebug("Sending HSM Event to hsmSetArm: ${state.alarmSystemStatus} (partition status: ${partitionstatus})")
    sendLocationEvent(name: "hsmSetArm", value: state.alarmSystemStatus)
  }
}

private getProxyAddress() {
  return settings.proxyAddress + ":" + settings.proxyPort
}

private getNotifyAddress() {
  // only support single hub.
  def hub = location.hubs[0] 
  ifDebug("Hubitat IP: ${hub.getDataValue("localIP")}")
  ifDebug("Hubitat LAN Port: ${hub.getDataValue("localSrvPortTCP")}")
  return hub.getDataValue("localIP") + ":" + hub.getDataValue("localSrvPortTCP")
}

private ifDebug(msg) {  
    if (msg && state.isDebug)  log.debug 'Honeywell Security: ' + msg  
}
