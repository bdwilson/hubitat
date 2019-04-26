Arduino ESP12/ESP8266 WiFi Maker API Device
=======
<br>
The goal of this device is to call a device refresh AFTER a power failure. My
smart bulbs (Sengled) are pretty dumb - in the event of a power failure, they turn on,
even if they were off before the power outage. To combat this, I have a
"canary" bulb in my attic that I never turn on - the only time it comes on is
during a power outage, so my goal was to use this as the trigger to turn all
the other lights off after an outage. The issue I ran into is that Hubitat
doesn't know that the status of the bulb changed after a power outage (not sure
why because this worked fine on SmartThings), anyhow, the point of this device
is to trigger an API call after the power outage to tell Hubitat to refresh my
''canary'' lightbulb so that Hubitat sees that the light is now on, thus turning whatever lights are using this ''canary'' as a trigger
within Rule Machine. Since we experience most of our power failures at night, the goal is to have the lights
turned off as soon as the power comes back. It takes about 1`second for this
device to connect to your wireless network (assuming your network gear and
hubitat is on a UPS), and make the HTTP call to the Hubitat Maker API. In
reality you could use this code to do any sort of action when the power comes
back on - send notification, make sure doors are locked, etc. 

Requirements
------------
To get started you'll need:
- [ESP8266 ESP12 USB device](https://www.amazon.com/ESP8266-ESP-01S-Wireless-Development-PlayStation-4/dp/B07FBNZ79T/) - this one works well and is super cheap.
- [Arduino](https://arduino-esp8266.readthedocs.io/en/latest/installing.html)

Installation
--------------------

1. Determine which device you wish to control with Hubitat, go into the Maker
API app and get the internal URL to use to do a "refresh". It should be
something similar to this:
http://192.168.1.xx/apps/api/8/devices/xxx/refresh?access_token=xxxxxx-xxxxxx-xxxx-xxxxxx.
Save this URL as you'll need it later.

2. Follow the steps here to download and [install
Arduino](https://arduino-esp8266.readthedocs.io/en/latest/installing.html).
Make sure you follow the Instruction steps about adding the Additional Board
Manager URL: http://arduino.esp8266.com/stable/package_esp8266com_index.json,
then open Boards Manager and install version 2.5.0 or greater for ESP8266.
https://arduino-esp8266.readthedocs.io/en/latest/installing.html (get version

3. Go to Tools -> Board and select NodeMCU 0.9 (ESP-12 Module)

4. Create a new Sketch and copy and paste in the code from hubitat.ino and change the variables to set
your Wifi Name, Wifi Password, and the URL from #1. 

5. Make sure the code compiles: Arduino -> Sketch -> Verify/Compile

6. Plug in your ESP12 USB device and do Arduino -> Sketch -> Upload.  Open up
Tools -> Serial Monitor to see what is going on once the upload completes.
Also open up your Hubitat -> Logs and see if you see the connections coming in
(You need to make sure Maker API has logging turned on). 

7. Once you have confirmed this works, use an old USB power adapter and plug
the ESP12 Device into an outlet that is not protected by a UPS so it can make
calls to refresh your bulb once power has been restored.  Your WiFi network and
Hubitat should be on a UPS in order for this all to work.

Changes
-------
If you make changes to the code, keep in mind that both the software & hardware
watchdog processes will reset the device if something isn't done for a period
of time. This is why the sleep function is actually printing something out to
serial. Otherwise, the device will reset and start doing things over again,
when in reality you only want your device to be refreshed when the power comes
back on. 

Bugs/Contact Info
-----------------
Bug me on Twitter at [@brianwilson](http://twitter.com/brianwilson) or email me [here](http://cronological.com/comment.php?ref=bubba).


