/**
 *  Hubitat Device Handler: Honeywell Zone Contact
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
  definition (name: "Honeywell Zone Contact", namespace: "brianwilson-hubitat", author: "bubba@bubba.org") {
    capability "Contact Sensor"
    capability "Sensor"

    command "zone"
  }
}

def installed(){
    sendEvent(name: "contact", value: "closed")
}

def zone(String state) {
  def descMap = [
    'closed':"Was Closed",
    'open':"Was Opened",
    'alarm':"Alarm Triggered"
  ]
  def desc = descMap."${state}"

  sendEvent (name: "contact", value: "${state}", descriptionText: "${desc}")
}
