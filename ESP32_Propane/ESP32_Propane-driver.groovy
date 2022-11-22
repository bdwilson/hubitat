/*
 * 
 *
 */

metadata {
        definition (name: "ESP32 Propane Sensor",
					namespace: "brianwilson-hubitat",
					author: "Brian Wilson",
					importUrl: "https://raw.githubusercontent.com/bdwilson/xxxx"
		) {
        capability "Battery"
        capability "VoltageMeasurement"
		capability "Sensor"
        attribute "NumberOfRestarts", "NUMBER"
        attribute "MinutesActive", "NUMBER"
	    attribute "propaneLevel", "NUMBER"
        attribute "LastUpdated", "DATE"
        attribute "LastRestart", "DATE"
    }
}


def parse(String description) {
}


def installed () {
}

def updated() {

}
