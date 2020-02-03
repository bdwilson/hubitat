//******************************************************************************************
//  File: ST_Anything_LinkNodeR4_ESP8266WiFi.ino
//  Authors: Dan G Ogorchock & Daniel J Ogorchock (Father and Son)
//
//  Summary:  This Arduino Sketch, along with the ST_Anything library and the revised SmartThings 
//            library, demonstrates the ability of one LinkNodeR4 ESP8266 to 
//            implement a multi input/output custom device for integration into SmartThings.
//            The ST_Anything library takes care of all of the work to schedule device updates
//            as well as all communications with the LinkNodeR4 ESP8266's WiFi.
//
//            ST_Anything_LinkNodeR4 implements the following Capabilities as a demo of what 
//            is possible with a single LinkSprite LinkNode R4 board
//              - 4 x Switch devices (used to turn on a digital output (e.g. LED, relay, etc...)
//
//  Board Programming details available at: http://linksprite.com/wiki/index.php?title=File:211201004-GPIO.png
//    
//  Change History:
//
//    Date        Who            What
//    ----        ---            ----
//    2019-03-26  Dan Ogorchock  Original Creation
//
//******************************************************************************************
//******************************************************************************************
// SmartThings Library for ESP8266WiFi
//******************************************************************************************
#include <SmartThingsESP8266WiFi.h>

//******************************************************************************************
// ST_Anything Library 
//******************************************************************************************
#include <Constants.h>       //Constants.h is designed to be modified by the end user to adjust behavior of the ST_Anything library
#include <Device.h>          //Generic Device Class, inherited by Sensor and Executor classes
#include <Sensor.h>          //Generic Sensor Class, typically provides data to ST Cloud (e.g. Temperature, Motion, etc...)
#include <Executor.h>        //Generic Executor Class, typically receives data from ST Cloud (e.g. Switch)
#include <InterruptSensor.h> //Generic Interrupt "Sensor" Class, waits for change of state on digital input 
#include <PollingSensor.h>   //Generic Polling "Sensor" Class, polls Arduino pins periodically
#include <Everything.h>      //Master Brain of ST_Anything library that ties everything together and performs ST Shield communications
#include <PS_Illuminance.h>  //Implements a Polling Sensor (PS) to measure light levels via a photo resistor

#include <PS_TemperatureHumidity.h>  //Implements a Polling Sensor (PS) to measure Temperature and Humidity via DHT library
#include <PS_DS18B20_Temperature.h>  //Implements a Polling Sesnor (PS) to measure Temperature via DS18B20 libraries 
#include <PS_Water.h>        //Implements a Polling Sensor (PS) to measure presence of water (i.e. leak detector)
#include <IS_Motion.h>       //Implements an Interrupt Sensor (IS) to detect motion via a PIR sensor
#include <IS_Contact.h>      //Implements an Interrupt Sensor (IS) to monitor the status of a digital input pin
#include <IS_Smoke.h>        //Implements an Interrupt Sensor (IS) to monitor the status of a digital input pin
#include <IS_DoorControl.h>  //Implements an Interrupt Sensor (IS) and Executor to monitor the status of a digital input pin and control a digital output pin
#include <IS_Button.h>       //Implements an Interrupt Sensor (IS) to monitor the status of a digital input pin for button presses
#include <EX_Switch.h>       //Implements an Executor (EX) via a digital output to a relay
#include <EX_Alarm.h>        //Implements Executor (EX)as an Alarm Siren capability via a digital output to a relay
#include <S_TimedRelay.h>    //Implements a Sensor to control a digital output pin with timing capabilities
#include <WebOTA.h>

//******************************************************************************************
//Define which Arduino Pins will be used for each device
//******************************************************************************************
#define PIN_S2              D5  //wemos D1 device - D5 will trigger the relay

//******************************************************************************************
//ESP8266 WiFi Information
//******************************************************************************************
String str_ssid     = "YourSSID";                           //  <---You must edit this line!
String str_password = "YourPassword";                   //  <---You must edit this line!
// Make sure you reserve an IP on your router
//IPAddress ip(192, 168, 1, 228);       //Device IP Address       //  <---You must edit this line!
//IPAddress gateway(192, 168, 1, 1);    //Router gateway          //  <---You must edit this line!
//IPAddress subnet(255, 255, 255, 0);   //LAN subnet mask         //  <---You must edit this line!
//IPAddress dnsserver(192, 168, 1, 1);  //DNS server              //  <---You must edit this line!
const unsigned int serverPort = 8090; // port to run the http server on

// Smartthings Hub Information
//IPAddress hubIp(192, 168, 1, 149);  // smartthings hub ip       //  <---You must edit this line!
//const unsigned int hubPort = 39500; // smartthings hub port

// Hubitat Hub Information - Update this! 
IPAddress hubIp(192, 168, 1, 16);    // hubitat hub ip         //  <---You must edit this line!
const unsigned int hubPort = 39501;   // hubitat hub port

//******************************************************************************************
//st::Everything::callOnMsgSend() optional callback routine.  This is a sniffer to monitor 
//    data being sent to ST.  This allows a user to act on data changes locally within the 
//    Arduino sktech.
//******************************************************************************************
void callback(const String &msg)
{
//  Serial.print(F("ST_Anything Callback: Sniffed data = "));
//  Serial.println(msg);
  
  //TODO:  Add local logic here to take action when a device's value/state is changed
  
  //Masquerade as the ThingShield to send data to the Arduino, as if from the ST Cloud (uncomment and edit following line)
  //st::receiveSmartString("Put your command here!");  //use same strings that the Device Handler would send
}

//******************************************************************************************
//Arduino Setup() routine
//******************************************************************************************
void setup()
{
  //******************************************************************************************
  //Declare each Device that is attached to the Arduino
  //  Notes: - For each device, there is typically a corresponding "tile" defined in your 
  //           SmartThings Device Hanlder Groovy code, except when using new COMPOSITE Device Handler
  //         - For details on each device's constructor arguments below, please refer to the 
  //           corresponding header (.h) and program (.cpp) files.
  //         - The name assigned to each device (1st argument below) must match the Groovy
  //           Device Handler names.  (Note: "temphumid" below is the exception to this rule
  //           as the DHT sensors produce both "temperature" and "humidity".  Data from that
  //           particular sensor is sent to the ST Hub in two separate updates, one for 
  //           "temperature" and one for "humidity")
  //         - The new Composite Device Handler is comprised of a Parent DH and various Child
  //           DH's.  The names used below MUST not be changed for the Automatic Creation of
  //           child devices to work properly.  Simply increment the number by +1 for each duplicate
  //           device (e.g. contact1, contact2, contact3, etc...)  You can rename the Child Devices
  //           to match your specific use case in the ST Phone Application.
  //******************************************************************************************
  //Polling Sensors
  
  //Interrupt Sensors 

  //Special sensors/executors (uses portions of both polling and executor classes)
  
  //Executors
  static st::S_TimedRelay executor1(F("relaySwitch1"), PIN_S2, LOW, false, 7200000, 0, 1, 0);  // turn relay on for max of 120 minutes. 
  //static st::EX_Switch executor2(F("switch2"), PIN_S3, LOW, false);  //Non-Inverted logic for "Active High" Relay Board
  //static st::EX_Switch executor3(F("switch3"), PIN_S4, LOW, false);  //Non-Inverted logic for "Active High" Relay Board
  //static st::EX_Switch executor4(F("switch4"), PIN_S5, LOW, false);  //Non-Inverted logic for "Active High" Relay Board
  
  //*****************************************************************************
  //  Configure debug print output from each main class 
  //  -Note: Set these to "false" if using Hardware Serial on pins 0 & 1
  //         to prevent communication conflicts with the ST Shield communications
  //*****************************************************************************
  st::Everything::debug=true;
  st::Executor::debug=true;
  st::Device::debug=true;
  st::PollingSensor::debug=true;
  st::InterruptSensor::debug=true;

  //*****************************************************************************
  //Initialize the "Everything" Class
  //*****************************************************************************

  //Initialize the optional local callback routine (safe to comment out if not desired)
  st::Everything::callOnMsgSend = callback;
  
  //Create the SmartThings ESP8266WiFi Communications Object
    //STATIC IP Assignment - Recommended
    //st::Everything::SmartThing = new st::SmartThingsESP8266WiFi(str_ssid, str_password, ip, gateway, subnet, dnsserver, serverPort, hubIp, hubPort, st::receiveSmartString, "OfficeESP");
 
    //DHCP IP Assigment - Must set your router's DHCP server to provice a static IP address for this device's MAC address
    st::Everything::SmartThing = new st::SmartThingsESP8266WiFi(str_ssid, str_password, serverPort, hubIp, hubPort, st::receiveSmartString, "HubDuino-D1-Fireplace");

  //Run the Everything class' init() routine which establishes WiFi communications with SmartThings Hub
  st::Everything::init();
  
  //*****************************************************************************
  //Add each sensor to the "Everything" Class
  //*****************************************************************************
      
  //*****************************************************************************
  //Add each executor to the "Everything" Class
  //*****************************************************************************
  st::Everything::addSensor(&executor1);
  //st::Everything::addExecutor(&executor2);
  //st::Everything::addExecutor(&executor3);
  //st::Everything::addExecutor(&executor4);
    
  //*****************************************************************************
  //Initialize each of the devices which were added to the Everything Class
  //*****************************************************************************
  st::Everything::initDevices();
}

//******************************************************************************************
//Arduino Loop() routine
//******************************************************************************************
void loop()
{
  webota.handle();
  //*****************************************************************************
  //Execute the Everything run method which takes care of "Everything"
  //*****************************************************************************
  st::Everything::run();
}
