OwnTracks Presence Updater for Hubitat
=======

[OwnTracks](https://owntracks.org/) is an app for iOS & Android that uses
either device GPS or Bluetooth LE iBeacons to detect presence. This device driver and SmartApp will allow you to use
Bluetooth iBeacons or GPS info on your mobile device to set presence in
Hubitat. This means you can now get accurate presence with or without using GPS
- depending on if you own an iBeacon or not - and without using a presence
sensor. It also means you can have many different geofence locations that can
trigger any number of virtual presense devices (because Hubitat app is limited
to a single geofence currently). 

<img src="https://bdwilson.github.io/images/IMG_4808.jpg" width=300px>

This topic is covered in the Hubitat Community forums <a href="https://community.hubitat.com/t/release-geofency-presence/22788">here</a>

Requirements
------------
To get started you'll need:
- [OwnTracks](https://owntracks.org/).  
- An iBeacon (Optional; or you can use GPS in the OwnTracks App). 
- Hubitat Hub
	- [A Virtual Presence OwnTracks Device](https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/virtual-mobile-presence-owntracks.groovy)
	- [My Hubitat app](https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/owntracks-presence-app.groovy) assigned to your Virtual Presence Device(s) above

Installation
--------------------
1. Install via [HPM](https://community.hubitat.com/t/beta-hubitat-package-manager/38016)
2. Go to Devices and create a new virtual device '''name it whatever the name will be called in OwnTracks with a - and a USER after it'''. Mine are called "Home-Brian" and "Home-Spawn1".  Network Device ID can be
'''HOME_PRESENCE_BRIAN''' for the first. Device Type will be the type you configured in step 1. For this example, mine is named "Home-Brian" and my
OwnTracks location name in #8 below is "Home". You can create multiple virtual devices and give them different names after the "-" then you can
reference them after the location below in #5 and use that new url in #8.  <b>There should be no space before and after the "-" in the device name!</b>

___NOTE: If you install via HPM and wish to uninstall later, you will need to change the device type of your virtual presence devices to something other than Virtual Mobile Presence for OwnTracks - or remove the devices prior to uninstalling!___

__OR__

1. Go to Drivers Code and create a new device using [virtual-mobile-presence-owntracks.groovy](https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/virtual-mobile-presence-owntracks.groovy).
2. Go to Devices and create a new virtual device '''name it whatever the name will be called in OwnTracks with a - and a USER after it'''. Mine are called "Home-Brian" and "Home-Spawn1".  Network Device ID can be
'''HOME_PRESENCE_BRIAN''' for the first. Device Type will be the type you configured in step 1. For this example, mine is named "Home-Brian" and my
OwnTracks location name in #8 below is "Home". You can create multiple virtual devices and give them different names after the "-" then you can
reference them after the location below in #5 and use that new url in #8.  <b>There should be no space before and after the "-" in the device name!</b>
3. Go to Apps Code and import URL:
[https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/owntracks-presence-app.groovy](https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/owntracks-presence-app.groovy). Save it. Click Oauth.

Configure
---------
1. Install the User App (Apps -> Add User App) and select all the virtual presense devices you created no
that you want to control. 
2. Copy the Endpoint URL. This will be your URL to configure in OwnTracks.  It should look something like this ___but you need to add the same user as above after location when you use this URL below___.  For instance, mine looks like this:
https://cloud.hubitat.com/api/xxx-xxx-xx-xxx/apps/xx/location/Brian?access_token=adafae03-0330-4aeb-b15e-xxxxxxxxx.
3. Now paste this URL into your browser and make sure you get the following response: <code>["This is the right URL! Add it directly into the OwnTracks URL field and make sure your Hubitat device name matches the format: '[Region Name in OwnTracks]-Brian'"]</code>
4. In the OwnTracks app
* Click the __(i)__ the main OwnTracks app on the top left, click __Settings__. 
* Change the mode at the top to method to __HTTP__ - ___this will remove any regions/friends you've configured___.   
* If UserID/Username field exists, you should make this the same as the user configured in the URL. If I leave this field blank, I get an error.
* __disable authentication__ (you'll authenticate using the access token in the hubitat URL).  
* In the URL field, copy the URL from above. ___Make sure the name of your Person from Installation Step 2 (Brian in my case below), is added after the /location/ part in the URL.___
<img src="https://bdwilson.github.io/images/IMG_4809.jpg" width=300px>
5. Add your Regions or iBeacons (optional) in the app.  In the app, setup your regions and adjust the radius if needed or iBeacons and name them like #2 above (minus the "-Name" part since that will get passed on your URL). 
6. You should now have a virtual presence sensor that you can tie to Hubitat actions. You can create as many virtual presense sensors as you have iBeacons or GPS locations in OwnTracks.
7. The best way to test this is to have debug logging turned on in the app, set a small radius, then go for a walk. Unfortunately, there is no "test URL" function. 

Bugs/Contact Info
-----------------
Check the [Hubitat Community Forum](https://community.hubitat.com/t/release-owntracks-presence/53419) if you run into issues. 

Bug me on Twitter at [@brianwilson](http://twitter.com/brianwilson) or email me [here](http://cronological.com/comment.php?ref=bubba).
