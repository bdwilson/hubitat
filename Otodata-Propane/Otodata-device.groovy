/*
 * Virtual Mobile Presence for Otodata Propane Receiver  - based on original work by Austin Pritchett/ajpri
 * 
 */

metadata {
        definition (name: "Otodata Propane Receiver", 
					namespace: "brianwilson-hubitat", 
					author: "Brian Wilson",
					importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Otodata-Propane/Otodata-driver.groovy"
		) {
      
		capability "Sensor"
        attribute "NumberOfRestarts", "NUMBER"
	    attribute "propaneLevel", "NUMBER"
        attribute "LastUpdated", "DATE"
    }
}


def parse(String description) {
}


def installed () {
}

def updated() {

}

