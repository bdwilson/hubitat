#include <Arduino.h>
#include <ESP8266WiFi.h>
#include <ESP8266WiFiMulti.h>
#include <ESP8266HTTPClient.h>
#include <WiFiClient.h>

ESP8266WiFiMulti WiFiMulti;

#define WIFINAME "YourSSID"
#define WIFIPASS "YourWIFIPassword"
#define URL "http://192.168.1.xx/apps/api/8/devices/xxx/refresh?access_token=xxx-xxxxx-xxxx-xxxxx-xxxxx"
uint8_t refresh_num = 3; // number of times to call refresh - in case this device is faster than your bulb getting power back
uint8_t count = 0;

void blink(void) {
  digitalWrite(2, LOW);  // Turn On LED
  delay(500);           // Wait for a second
  digitalWrite(2, HIGH); // Turn Off LED
  delay(500);
}

void setup() {
  // put your setup code here, to run once:
  Serial.begin(115200);
  pinMode(2, OUTPUT);
  digitalWrite(2, HIGH);

  Serial.println();
  Serial.println();
  Serial.println();

  for (uint8_t t = 2; t > 0; t--) {
    Serial.printf("[SETUP] WAIT %d...\n", t);
    Serial.flush();
    blink();
  }

  WiFi.mode(WIFI_STA);
  WiFiMulti.addAP(WIFINAME, WIFIPASS);
}

void loop() {
  // put your main code here, to run repeatedly:
  if ((WiFiMulti.run() == WL_CONNECTED)) {
    digitalWrite(2, LOW); // LED ON indicates connected.
    if (count < refresh_num) {
      WiFiClient client;
      HTTPClient http;
      if (http.begin(client, URL)) {
        Serial.print("[HTTP] GET...\n");
        // start connection and send HTTP header
        int httpCode = http.GET();
        // httpCode will be negative on error
        if (httpCode == HTTP_CODE_OK) {
          // HTTP header has been send and Server response header has been handled
          Serial.printf("[HTTP] GET Success: %d\n", httpCode);
          count++;
          blink();
        } else {
          Serial.printf("[HTTP] GET failed, error: %s\n", http.errorToString(httpCode).c_str());
        }
        http.end();
      } else {
        Serial.printf("[HTTP} Unable to connect\n");
      }
    } else {
      Serial.print("[HTTP] DONE...\n");
      digitalWrite(2, LOW);
      delay(5000);
      sleep();
    }
  } else {
    delay(10000);
  }
  delay(1000);
}

void sleep(void) {
  cli();
  while (1) {
    ESP.wdtFeed();
    ESP.wdtEnable(0);
    Serial.print("Doing nothing...\n");
    digitalWrite(2, HIGH);
    delay(2500);
  }
}
