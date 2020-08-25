/**
*   
*   File: Sendgrid.groovy
*   Requirements: Requires at least a free SendGrid account & validated email address.
*   Platform: Hubitat
*   Modification History:

*   Inspired by original work for SmartThings & Hubitat by: Dan Ogorchock for Pushover.
*
*  Copyright 2020 Brian Wilson
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
*
*/
preferences {
    input("apiKey", "text", title: "API Key:", description: "Sendgrid API key", required: true)
    input("from", "text", title: "From Address:", description: "From Address", required: true)
    input("to", "text", title: "To Address:", description: "To Address", required: true)
    input("subject", "text", title: "Subject:", description: "", required: true)
    input("debug", "bool", title: "Enable Debug Logging", required: true, defaultValue: false)
}

metadata {
    definition (name: "Sendgrid", namespace: "brianwilson-hubitat", author: "Brian Wilson") {
        capability "Notification"
        capability "Actuator"
        capability "Speech Synthesis"
    }
}

def installed() {
    initialize()
}

def updated() {
    initialize()   
}

def initialize() {
}

def speak(message) {
    deviceNotification(message)
}

def deviceNotification(message) {
  
    // Define the initial postBody keys and values for all messages

    def postBody = '{"personalizations": [{"to": [{"email": "' + to + '"}]}],"from": {"email": "' + from + '"},"subject": "' + subject + '","content": [{"type": "text/plain", "value": "' + message + '"}]}'
              
    ifDebug("Sending Message: $message [${postBody}]")

    // Prepare the package to be sent
    def params = [
        uri: "https://api.sendgrid.com",
        path: "/v3/mail/send",
        contentType: "application/json",
        headers: ["Authorization": "Bearer ${apiKey}", "Content-Type": "application/json"],
        requestContentType: "application/x-www-form-urlencoded",
        body: postBody
    ]

        httpPost(params){response ->
            if(response.status != 202) {
                sendPush("ERROR: 'Sendgrid' received HTTP error ${response.status}. Check your API key.")
                log.error "Received HTTP error ${response.status}. ${response}"
            }
            else {
                ifDebug("Message queued for sending by Sendgrid Server")
        }
    }
}

private ifDebug(msg) {
    if (settings?.debug || settings?.debug == null) {
        log.debug 'Sendgrid: ' + msg
    }
}
