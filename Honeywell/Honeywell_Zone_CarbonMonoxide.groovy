/**
 *  Hubitat Device Handler: Honeywell Zone Carbon Monoxide
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
  definition (name: "Honeywell Zone CarbonMonoxide", namespace: "brianwilson-hubitat", author: "bubba@bubba.org") {
    capability "Carbon Monoxide Detector"
    capability "Sensor"

    command "zone"
  }
}

def installed(){
    sendEvent(name:"carbonMonoxide", value:"clear")
}

def zone(String state) {
  // need to convert open to detected and closed to clear
  def eventMap = [
    'closed':"clear",
    'open':"detected",
    'alarm':"detected",
    'tested':"tested"
  ]
  def newState = eventMap."${state}"

  def descMap = [
    'closed':"Was Cleared",
    'open':"Was Detected",
    'alarm':"Was Detected",
    'tested':"Was Tested"
  ]
  def desc = descMap."${state}"

  sendEvent (name: "carbonMonoxide", value: "${newState}", descriptionText: "${desc}")
}
