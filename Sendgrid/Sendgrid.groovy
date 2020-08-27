/**
*   
*   File: Sendgrid.groovy
*   Requirements: Requires at least a free SendGrid account & validated email address.
*   Platform: Hubitat
*   Modification History:
*     26-AUG-2020 - Added options for multiple recipient addresses, option to BCC
*                   recipients, option to send with HIGH priority.
*
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
    input("apiKey", "text", title: "SendGrid API Key:", description: "", required: true)
    input("from", "text", title: "Sender Address:", description: "An approved address or domain configured within SendGrid", required: true)
    input("to", "text", title: "Recipient Address(es):", description: "Separate multiple emails with a space", required: true)
    input("subject", "text", title: "Subject:", description: "", required: true)
    input("urgent", "bool", title: "Send with high priority", description: "", required: true)
    input("bcconly", "bool", title: "BCC recipient(s) - will show as sent to your FROM address", description: "", required: true)
    input("debug", "bool", title: "Enable Debug Logging", required: true, defaultValue: false)
}

metadata {
    definition (name: "Sendgrid", namespace: "brianwilson-hubitat", author: "Brian Wilson",
               importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Sendgrid/Sendgrid.groovy") {
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
    def postBody = ""
    def headers = ""
    def bcc = ""
    def values = settings.to.split(' ')
    def newTo = ""
    for (i in values) {
        newTo += '{"email": "' + i + '"},'
    }
    newTo = newTo.substring(0, newTo.length()-1)

    if (settings.urgent) {
         headers = '"headers": {"Priority": "Urgent", "Importance": "high", "X-Priority": "1"},'
    }
    if (settings.bcconly) {
        bcc = ',"bcc": [' + newTo + ']'
        newTo = '{"email": "' + settings.from + '"}'
    }
         
    postBody = '{"personalizations": [{"to": [' + newTo + ']' + bcc + '}],"from": {"email": "' + settings.from + '"},' + headers + '"subject": "' + settings.subject + '","content": [{"type": "text/plain", "value": "' + message + '"}]}'
         
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
    try {
       httpPost(params){response ->
            if(response.status != 202) {
                sendPush("ERROR: 'Sendgrid' received HTTP error ${response.status}. Check your API key.")
                log.error "Received HTTP error ${response.status}. ${response}"
            }
            else {
                ifDebug("Message queued for sending by Sendgrid Server.")
       }
      }
    } catch (e) {
        log.error "SendGrid: Error making httpPost: $e"
    }
}

private ifDebug(msg) {
    if (settings?.debug || settings?.debug == null) {
        log.debug 'SendGrid: ' + msg
    }
}
