/**
 *  Hubitat Device Handler: Honeywell Zone Motion
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
  definition (name: "Honeywell Zone Motion", namespace: "brianwilson-hubitat", author: "bubba@bubba.org") {
    capability "Motion Sensor"
    capability "Sensor"

    command "zone"
  }
}

def zone(String state) {
  // need to convert open to active and closed to inactive
  def eventMap = [
    'closed':"inactive",
    'open':"active",
    'alarm':"alarm"
  ]
  def newState = eventMap."${state}"

  def descMap = [
    'closed':"Motion Has Stopped",
    'open':"Detected Motion",
    'alarm':"Alarm Triggered"
  ]
  def desc = descMap."${state}"

  sendEvent (name: "motion", value: "${newState}", descriptionText: "${desc}")
}
