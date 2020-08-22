BTLE Multi-User Virtual Presence Updater for Hubitat
=======
<br>
The BTLE Multi-User presence app will allow you to get information from BT
devices into Hubitat in order to set presence for users. This will work with
Tile Mate devices, and any other device that transmits BT presence every few
seconds (configurable for what your threshold is). Wherever you run the python
script from is your "location" and you can assign different "users" to be
looked for within the script, thus it's multi-user. This idea was <a
href="https://www.domoticz.com/wiki/Presence_detection_%28Bluetooth_4.0_Low_energy_Beacon%29">borrowed
from here.</a>
<br><br>
The added benefit of using this is that you don't need to invest in expensive
presence devices, assuming you have a Linux device with bluetooth LE
capabilities and it's in a location where it can see the BTLE devices you wish
to track.
<br><br>
There is a <a href="https://community.hubitat.com/t/release-btle-presence-sensor/48332">thread on this topic</a> in the hubitat forums.

Requirements
------------
To get started you'll need:
- A Linux device with BTLE capabilities - cheapest is [Pi Zero W](https://www.adafruit.com/product/3400), but a beefier Pi works too. I would not recommend virtualizing this with Docker as you need access to the bluetooth stack.
- Patience - you'll have to figure out what bluetooth device is actually the one you care about - this is a little trial and error and you can use RSSI information to determine how close/far away things are. 
- Hubitat Hub
	- [A Virtual Presence Device](https://github.com/ajpri/STApps/blob/master/devicetypes/ajpri/virtual-mobile-presence.src/virtual-mobile-presence.groovy)
	- [My Hubitat app](https://raw.githubusercontent.com/bdwilson/hubitat/master/BTLE-Presence/BTLE-Presence-app.groovy) assigned to your Virtual Presence Device(s) above

PI installation
---------------
1. Before you invest time in getting hubitat setup for this, make sure you can
successfully see your beacon and have the right software installed. I'm not
going to go into details here, but they are losely based on
[this](https://www.domoticz.com/wiki/Presence_detection_%28Bluetooth_4.0_Low_energy_Beacon%29). 
2. I do know that Raspberry Pi wireless devices support this out of the box.  **You also have to run this as root**
3. Login to your Pi
<pre>
sudo apt-get update
sudo apt-get install -y libusb-dev libdbus-1-dev libglib2.0-dev libudev-dev libical-dev libreadline-dev python-bluez python-requests
</pre>
4. Make sure you can see your BT interface
<pre>
$ hciconfig
hci0:	Type: Primary  Bus: UART
	BD Address: DC:A6:32:0B:0A:A7  ACL MTU: 1021:8  SCO MTU: 64:1
	UP RUNNING
	RX bytes:869075428 acl:0 sco:0 events:24957693 errors:0
	TX bytes:275256 acl:1 sco:0 commands:30480 errors:0
</pre>
5. Make sure your test script runs. If not, you may need additional modules.
Again, check the above install instructions in #1 in case it is helpful. 
<pre>
$ sudo ./test_beacon.py
</pre>
6. If you see a bunch of MAC addresses scrolling by, this all the bluetooth
devices your system can see. Good luck finding out which one is the BT device
you care about. I know the Tile Mate devices broadcast once every 15-20
seconds, so look for an entry with "back after 16 secs" and those will be your
Tile Mate devices.  Grab the mac addresses you care about.
7. Update your mac addresses in TAG_DATA array in check_beacon_presence.py.
Here is my example. I named my tiles "Brian" and "Amy" (ties to part of the
device name in Hubitat). I have a timeout of 30
seconds for them (if not seen for 30 secs, they get flagged as AWAY), and the
area І'm running my script from is "Home" (ties to the device location in
Hubitat), and SWITCH_MODE means it only reports changes to Hubitat.  We don't
have the URL yet, so move on to the next step to do that.
<pre>
TAG_DATA = [
            ["Brian","fe:31:0b:b0:c6:12",30,0,"Home",SWITCH_MODE],
            ["Amy","c8:ae:c6:e0:dc:12",30,0,"Home",SWITCH_MODE],
           ]
</pre>

Hubitat Installation
--------------------
1. Go to Drivers Code and create a new device using [virtual-mobile-presence.groovy](https://github.com/ajpri/STApps/blob/master/devicetypes/ajpri/virtual-mobile-presence.src/virtual-mobile-presence.groovy).
2. Go to Devices and create a new virtual device '''name it whatever the location name will be called in
your script with a - and a USER after it'''. Mine are called "Home-Brian" and "Home-Spawn1".  Network Device ID can be
'''HOME_VIRTUAL_PRESENCE_1''' for the first. Device Type will be
the type you configured in step 1. For this example, mine is named "Home-Brian" and my
 location name above is "Home". You can create multiple
virtual devices and give them different names after the "-" then you can
reference them after the location below in #5 and use that new webhook in #8.
<b>There should be no space before and after the "-" in the device name!</b>
3. Go to Apps Code and create a new App from code
[BTLE-Presence-app.groovy](https://raw.githubusercontent.com/bdwilson/hubitat/master/BTLE-Presence/BTLE-Presence-app.groovy).  Save it. **Click Oauth.**
4. Install the User App and select all the virtual presense devices you created
that you want to control. 
5. Copy the Endpoint URL (I use the internal URL), and use that in the
URL_HUBITAT entry for the python script above. It should look something like
this: http://192.168.1.30/apps/api/414/location/?user=PARAM_NAME&location=PARAM_IDX&cmd=PARAM_CMD&access_token=3523535-1356-235623465-xxxxx-xxxxx
6. Now paste this URL into your browser and make sure you get the following
response:
<code>["Yep, this is the right URL, just put it into check_beacon_presence.py
URL_HUBITAT. Make sure your location name (next to last element of each array
in TAG_DATA) in the script matches the device 'location-name'"]</code>
7. Install the url in the python script, then run it as root.  Also you'll want
to set it up to run as root (see Autorun as Service section
[here](https://www.domoticz.com/wiki/Presence_detection_%28Bluetooth_4.0_Low_energy_Beacon%29).
<pre>
sudo ./check_beacon_presence.py
</pre>
8. You should now have a virtual presence sensor that you can tie to
Hubitat actions. You can create as many virtual presense sensors as you have
tags. 
9.
<pre>
$ sudo ./check_beacon_presence.py
Brian - HOME - http://192.168.1.30/apps/api/414/location/?user=Brian&location=Home&cmd=HOME&access_token=806621fc-6d62-42e8-a07c-xxxxxxxxxxxxxx
Amy - AWAY - http://192.168.1.30/apps/api/414/location/?user=Amy&location=Home&cmd=AWAY&access_token=806621fc-6d62-42e8-a07c-xxxxxxxxxxxxxx
Amy - HOME - http://192.168.1.30/apps/api/414/location/?user=Amy&location=Home&cmd=HOME&access_token=806621fc-6d62-42e8-a07c-xxxxxxxxxxxxxx
</pre>

Bugs/Contact Info
-----------------
There is a [thread on this topic](https://community.hubitat.com/t/release-btle-presence-sensor/48332) in the Hubitat forums. Please check here for help.
<br><br>
Bug me on Twitter at [@brianwilson](http://twitter.com/brianwilson) or email me [here](http://cronological.com/comment.php?ref=bubba).
