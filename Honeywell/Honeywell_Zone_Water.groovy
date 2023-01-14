/**
 *  Hubitat Device Handler: Honeywell Zone Water
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
 */
metadata {
  definition (name: "Honeywell Zone Water", namespace: "brianwilson-hubitat", author: "bubba@bubba.org") {
    capability "Water Sensor"
    capability "Sensor"

    command "zone"
  }
}

def installed(){
    sendEvent(name:"water", value:"dry")
}

def zone(String state) {
  // need to convert open to wet and closed to dry
  def eventMap = [
    'closed':"dry",
    'open':"wet",
    'alarm':"alarm"
  ]
  def newState = eventMap."${state}"

  def descMap = [
    'closed':"No Water Detected",
    'open':"Water Detected",
    'alarm':"Alarm Triggered"
  ]
  def desc = descMap."${state}"

  sendEvent (name: "water", value: "${newState}", descriptionText: "${desc}")
}
