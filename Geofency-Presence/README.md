Geofency Multi-User Virtual Presence Updater for Hubitat
=======
<br>
Geofency is an app for iOS (sorry Android users) that uses either device GPS or Bluetooth LE iBeacons. This device driver and SmartApp will allow you to use
Bluetooth iBeacons or GPS info on your mobile device to set presence in
Hubitat. This means you can now get accurate presence with or without using GPS
- depending on if you own an iBeacon or not - and without using a presence
sensor. It also means you can have many different geofence locations that can
trigger any number of virtual presense devices (because Hubitat app is limited
to a single geofence currently). 

The added benefit of using Geofency is you can keep track of how long you stay
in places that you frequent. 

<img src="https://bdwilson.github.io/images/IMG_0BDCE0D2F6F9-1.jpeg" width=300px>

This topic is covered in the Hubitat Community forums <a href="https://community.hubitat.com/t/release-geofency-presence/22788">here</a>

Requirements
------------
To get started you'll need:
- [Geofency](https://www.geofency.com/).
- An iBeacon (Optional; or you can use GPS in the Geofency App). 
- Hubitat Hub
	- [A Virtual Presence Device](https://github.com/ajpri/STApps/blob/master/devicetypes/ajpri/virtual-mobile-presence.src/virtual-mobile-presence.groovy)
	- [My Hubitat app](https://raw.githubusercontent.com/bdwilson/hubitat/master/Geofency-Presence/geofency-presence.groovy) assigned to your Virtual Presence Device(s) above

Installation
--------------------
1. Go to Drivers Code and create a new device using [virtual-mobile-presence.groovy](https://github.com/ajpri/STApps/blob/master/devicetypes/ajpri/virtual-mobile-presence.src/virtual-mobile-presence.groovy).
2. Go to Devices and create a new virtual device '''name it whatever the location name will be called in
Geofency with a - and a USER after it'''. Mine are called "Virtual Presence-Brian" and "Virtual Presence-Spawn1".  Network Device ID can be
'''VIRTUAL_PRESENCE_1''' for the first. Device Type will be
the type you configured in step 1. For this example, mine is named "Virtual Presence-Brian" and my
Geofency location name in #8 below is "Virtual Presence". You can create multiple
virtual devices and give them different names after the "-" then you can
reference them after the location below in #5 and use that new webhook in #8.
<b>There should be no space before and after the "-" in the device name!</b>
3. Go to Apps Code and create a new App from code geofency-smartapp.groovy. Save it. Click Oauth.
4. Install the User App and select all the virtual presense devices you created
that you want to control. 
5. Copy the Endpoint URL and alter it to have your name after /location/. This will be your URL to configure in Geofency Webhook.  It should look something like this (without the []): https://cloud.hubitat.com/api/xxx-xxx-xx-xxx/apps/xx/location/[your user]?access_token=adafae03-0330-4aeb-b15e-xxxxxxxxx.  For the example in #2
above, [your user] is Brian. <b>If  have "Brian?access_token=xxxx</b>
6. Now paste this URL into your browser and make sure you get the following
response:
<code>["Yep, this is the right URL, just put it into Geofency Web Hook, set to POST and do a test. Make sure your Geofency location name matches the device '<location>-Brian'"]</code>
7. Install Geofency app and your iBeacon (optional).  In the app, setup your
location or iBeacons and name them like #2 above (minus the "-Name" part since that will get
passed on your webhook URL). Then when you're happy with your location and radius,
click the 3 dots on the location and select "Webhook". Change HTTP method to
JSON via Post, change the URL for entry and exit to be the url you created in
#5 above. Use Enter/Exit buttons to test the settings. Don't be fooled by a
successful message in Geofency - make sure debug logging is enabled in the app
so you can see if it really worked. 
8. You should now have a virtual presence sensor that you can tie to
Hubitat actions. You can create as many virtual presense sensors as you have
iBeacons or GPS locations in Geofency.

Bugs/Contact Info
-----------------
Check the [Hubitat Community Forum](https://community.hubitat.com/t/release-geofency-presence/22788) if you run into issues. 

Bug me on Twitter at [@brianwilson](http://twitter.com/brianwilson) or email me [here](http://cronological.com/comment.php?ref=bubba).
