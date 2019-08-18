#include <ESP8266WiFi.h>
#include <WiFiClient.h>
#include <ESP8266WebServer.h>
#include <ESP8266mDNS.h>
#include <WebOTA.h>
#include <Timer.h>

const char* ssid = "ssid";
const char* password = "password";

ESP8266WebServer server(80);

boolean speedStates[] = { false, false, false, false };  // Initial state of each relay
int speeds[] = { 14, 12, 13, 16 };  // GPIO pins for each motor speed
//int relays[] = { 14, 12, 13, 16 }; //  4, 5, 15, 10}; // all relays
boolean otherStates[] = { false, false, false, false };  // Initial state of each relay
int other_relays[] = { 4, 5, 15, 10 };
int lights[] = { 4 }; // relay for lights
int stationTimer = -1; // 
int qcItem = 3; // 4th item in array is quickClean item.
Timer t;

void setup(void) {

  Serial.begin(115200);
  WiFi.begin(ssid, password);
  Serial.println("");



  // Wait for connection
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");

  Serial.print("Connected to ");
  Serial.println(ssid);

  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());

  Serial.print("MAC address: ");
  byte mac[6];
  WiFi.macAddress(mac);
  Serial.println(getMacString(mac));

  Serial.print("Gateway: ");
  Serial.println(WiFi.gatewayIP());

  if (MDNS.begin("esp8266")) {
    Serial.println("MDNS responder started");
  }

  // relay 6 (GPIO 4) = Lights
  server.on("/api/light", handleLight);
  
  server.on("/api/relay", handleRoot);

  server.on("/api/quickClean", handleClean);

  Serial.println("Initialize relays...");
  
  for (byte relay = 0; relay < sizeof(other_relays) / sizeof(int); relay++)  {
    pinMode(other_relays[relay], OUTPUT);
    digitalWrite(other_relays[relay], otherStates[relay] ? HIGH : LOW);
  }

  for (byte relay = 0; relay < sizeof(speeds) / sizeof(int); relay++)  {

    pinMode(speeds[relay], OUTPUT);
    digitalWrite(speeds[relay], speedStates[relay] ? HIGH : LOW);

    String request = "/api/relay/" + String(relay) + "/";
    Serial.println("Relay " + String(relay) + " url: " + request);
    server.on((request + "on").c_str(), [relay]() {
      if (relay == qcItem) {  // if quickClean relay. 
              Serial.println("TEST1 " + String(relay) + " url: " + String(qcItem));
              unsigned long time = 900000;
              String arg = server.arg("time");
              time = (arg.length() == 0 ? time : arg.toInt() * 60000);
              if (stationTimer >= 0) {
                 t.stop(stationTimer);
              }
              stationTimer = t.after (time,cleanOff);
              Serial.println("Clean for " + String(time) + "ms: ");
      }
      Serial.println("TEST2" + String(relay) + " url: " + String(qcItem));

      //setSpeed(relay, true);
    });
    server.on((request + "off").c_str(), [relay]() {
      setSpeed(relay, false);
    });
  }
  //server.on("/api/relay/all/on", []() {
  //  setAll(true);
  //});

  //server.on("/api/relay/all/off", []() {
  //  setAll(false);
  //});

  server.onNotFound(handleNotFound);
  server.begin();

  Serial.println("HTTP server started");
}

void loop(void) {
  webota.handle();
  server.handleClient();
  t.update();
}

void handleClean() {
  unsigned long time = 900000;

  String arg = server.arg("time");
  time = (arg.length() == 0 ? time : arg.toInt() * 60000);

  setSpeed(3,true);
  if (stationTimer >= 0) {
      t.stop(stationTimer);
  }
  stationTimer = t.after (time,cleanOff);
  Serial.println("Clean for " + String(time) + "ms: ");
}

void cleanOff() {
    Serial.println("Timer expired for for timer " + String(stationTimer));
    setSpeed(qcItem,false);
    stationTimer=-1;
}

// This method is called when a request is made to the root of the server. i.e. http://myserver
void handleRoot() {
  String response = "{ \"speeds\": [ ";
  String seperator = "";
  for (int relay = 0; relay < sizeof(speeds) / sizeof(int); relay++)  {
    response += seperator + String(speedStates[relay]);
    seperator = ", ";
  }
  response += " ] }";
  server.send(200, "application/json", response);
}

void handleLight() {
  server.send(400, "text/plain", "{ \"message\": \"Light\" }");
}
// This method is called when an undefined url is specified by the caller
void handleNotFound() {
  server.send(400, "text/plain", "{ \"message\": \"Invalid request\" }");
}

//void setAll(boolean state) {
//  for (byte relay = 0; relay < sizeof(relays) / sizeof(int); relay++)  {
//    digitalWrite(relays[relay], state ? HIGH : LOW);
//    speedStates[relay] = state;
//  }
//  String response = "All Relays = " + String(state ? "ON" : "OFF");
//  server.send(200, "application/json", "{ \"message\": \"" + response + "\" }");
//}

void setSpeed(int relay, boolean state) {
  for (byte r_relay = 0; r_relay < sizeof(speeds) / sizeof(int); r_relay++) {
    // if relay is not the one we just selected
    if (speeds[r_relay] != speeds[relay]) {
        // if the state of the other relays is the same as what
        // we want to set the current relay, we'll change it.
        if (speedStates[r_relay] == state) {
          int my_relay=speeds[r_relay];
          if (state == true) {
            // if on, turn off
            Serial.println("Other Speeds: " + String(my_relay) + " turned off " + String(stationTimer));
            digitalWrite(speeds[r_relay], false ? HIGH : LOW);
            speedStates[r_relay] = false;
            if (stationTimer >= 0 && r_relay == 3) { // if QuickClean relay and timer is on. 
              Serial.println("Turning off timer: " + String(relay));
              t.stop(stationTimer);
              stationTimer=-1;
            }
          //} else {
            // if off, turn on
          //  Serial.println("Other Relays: " + String(my_relay) + "turned on");
          //  digitalWrite(relays[r_relay], true ? HIGH : LOW);
          //  speedStates[r_relay] = true;
          }
        }  
      }                            
    }
  digitalWrite(speeds[relay], state ? HIGH : LOW);
  speedStates[relay] = state;
  String response = "Speed " + String(relay) + " " + (state ? "ON" : "OFF");
  server.send(200, "application/json", "{ \"message\": \"" + response + "\" }");
  Serial.println(response);
}

String getMacString(byte *mac) {
  String result = "";
  String seperator = "";
  for (int b = 0; b < 6; b++) {
    result += seperator + String(mac[b], HEX);
    seperator = ":";
  }
  return result;
}
