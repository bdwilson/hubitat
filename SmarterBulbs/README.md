SmarterBulbs / Detecting Powerloss with Hubitat
=======

One of the downfalls of some Zwave and Zigbee bulbs is that when power is lost
then restored, they will turn on and usually they will fail to report their
status to Hubitat without a forced refresh of the bulb. Below will discuss how
to detect a powerloss and potentially mitigate the effects of the lights
turning on when power is restored.  I currently use all 3 of these methods
today but the most accurate method appears to be tying into my apcupsd daemon
that is monitoring my APC UPS via USB. If you're just starting out and trying
to deal with this issue AND you have a UPS that can be monitored, I'd suggest
you go that route first as this can easily be done via a Raspberry Pi Device
and a USB cable. These methods also assume that your Hubitat device is also
connected to a UPS - it doesn't have to be the same one you are monitoring, but
you don't want your Hubitat to go down and have to boot back up after power
restoration as the bootup process will take longer than it will for these
methods to refresh your bulbs and it just won't work. All of these methods also
rely on the "SmarterBulbs" hubitat app as well as a "spare" (or canary) bulb that you can
put somewhere that won't interrupt people if it comes on in the middle of the
night AND the bulb can't be used for other purposes.  


Options
---
In order of most accurate/quick/reliable:
1. <b>Method 1</b>: Modifying apcupsd 
This method involves Using an APC UPS, and running apcupsd on Linux to monitor
the UPS status. This method issues a refresh to your "canary" bulb via Maker
API.

Modify /etc/apcupsd/apccontrol and look for "mainsback" and add the curl
command below using the URL from #1 below. You can also add additional refresh
calls by putting a <code>sleep 1</code> inbetween the curl calls:
<pre>
    mainsback)
    if [ -f /etc/apcupsd/powerfail ] ; then
       printf "Continuing with shutdown."  | ${WALL}
    fi
    curl -s http://192.168.1.xx/apps/api/8/devices/xxx/refresh?access_token=xxxxxx-xxxxxx-xxxx-xxxxxx
</pre>

2. <b>Method 2</b>: Using Homemade Temperature, humidity, pressure and light sensor. 
This method requires a [Homemade
Temperature](https://community.hubitat.com/t/hubitat-with-homemade-temperature-humidity-pressure-and-light-sensor/1816/484)
sensor, resistor, and battery. You need to connect this as per the link above
and install the proper drivers. You'd then create a RM action if voltage drops
below a certain threshold, refresh your canary bulb via MakerAPI.

3. <b>Method 3</b>: Using an Arduino device to refresh your bulbs when power is
restored to the device

This method requires an ESP-01 device with custom code included here that will
call a refresh to your "canary" device when power is restored. This takes more
than just a "blib" as the capacitors in your USB powersupply will likely not
get low enough on a blip to cause the device to power off. 

It takes lessthan 1 second for the ESP-01 device to drain the USB power supply when power dies and less than 1 second for this device to connect to your wireless network (assuming your network gear and
hubitat is on a UPS), and make the HTTP call to the Hubitat Maker API. In
reality you could use this code to do any sort of action when the power comes
back on - send notification, make sure doors are locked, etc. Here is a video (https://bubba.d.pr/9MdpJs) of the device in action with Hubitat.

Here is the hardware/software needed for this method:
- [ESP8266 ESP12 USB device](https://www.amazon.com/IZOKEE-Wireless-Transceiver-Mega2560-Raspberry/dp/B07D49MSTM) - this one works well and is super cheap.
- [Arduino](https://arduino-esp8266.readthedocs.io/en/latest/installing.html)

Requirements
------------

All methods above require you to go install SmarterBulbs and enable MakerAPI
for your canary bulb. Again, method 1 above is most reliable so I'd go this
route first.  Method 3 is cheapest option and will work for big power outages
but not blips - so you could start with this if you don't have a UPS. 

Installation
--------------------

1. Determine which device you wish to control with Hubitat, go into the Maker
API app and get the internal URL to use to do a "refresh". It should be
something similar to this:
http://192.168.1.xx/apps/api/8/devices/xxx/refresh?access_token=xxxxxx-xxxxxx-xxxx-xxxxxx.
Save this URL as you'll need it later.

<b>Steps 2 - 7 are if you're following Method #3 only. If you're going #1 or
#2, proceed to step 8.</b>

2. Follow the steps here to download and [install
Arduino](https://arduino-esp8266.readthedocs.io/en/latest/installing.html) -
Make sure you follow the Instruction steps about adding the Additional Board
Manager URL: http://arduino.esp8266.com/stable/package_esp8266com_index.json,
then open Boards Manager and install version 2.5.0 or greater for ESP8266.
https://arduino-esp8266.readthedocs.io/en/latest/installing.html

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

8. Configure a RM rule to turn all bulbs out when your "canary" bulb turns on,
or (optionally) Install Smarter Bulbs from this repo in Apps Code in Hubitat and activate it. It
will check all selected bulbs every 10 minutes and save their state. In the
case of a power outage, your bulbs will be reset to their state. 

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
