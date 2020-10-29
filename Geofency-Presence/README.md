Geofency Multi-User Virtual Presence Updater for Hubitat
=======
<br>
Geofency is an app for iOS (sorry Android users - perhaps
[OwnTracks](https://github.com/bdwilson/hubitat/tree/master/OwnTracks-Presence)
will work for you?) that uses either device GPS or Bluetooth LE iBeacons. This device driver and SmartApp will allow you to use
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
	- [A Virtual Presence Device](https://raw.githubusercontent.com/bdwilson/hubitat/master/Geofency-Presence/virtual-mobile-presence.groovy)
	- [My Hubitat app](https://raw.githubusercontent.com/bdwilson/hubitat/master/Geofency-Presence/geofency-presence.groovy) assigned to your Virtual Presence Device(s) above

Installation
--------------------
1. Install via [HPM](https://community.hubitat.com/t/beta-hubitat-package-manager/38016) - search for keyword "presence"
2. Go to Devices and create a new virtual device of type ___Geofency Virtual Mobile Presence Device___ for each user and location who you wish to track
3. Set the ___Location___ and ___User___ to correspond to the location in Geofency you'll be monitoring and person who will be running Geofency

__OR__

1. Go to Drivers Code and create a new device using [virtual-mobile-presence.groovy](https://raw.githubusercontent.com/bdwilson/hubitat/master/Geofency-Presence/virtual-mobile-presence.groovy).
2. Go to Devices and create a new virtual device of type ___Geofency Virtual Mobile Presence Device___ for each user and location who you wish to track
3. Set the ___Location___ and ___User___ to correspond to the location in Geofency you'll be monitoring and person who will be running Geofency
4. Go to Apps Code and create a new App from code [Geofency Presence](https://raw.githubusercontent.com/bdwilson/hubitat/master/Geofency-Presence/geofency-presence.groovy).  Save it. <b>Click Oauth.</b>

Configure
---------
1. Install the User App (Apps -> Add User App) and select all the virtual presense devices you created that you want to control with Geofency
2. Copy the Endpoint URL. This will be your URL to configure in OwnTracks.  It should look something like this ___but you need to add the same user(s) as above after location when you use this URL below___.  For instance, mine looks
like this: https://cloud.hubitat.com/api/xxx-xxx-xx-xxx/apps/xx/location/Brian?access_token=adafae03-0330-4aeb-b15e-xxxxxxxxx.
3. You use the same URL for all of your users, just change the information after /location/ within the URL
4. Now paste this URL into your browser and make sure you get the following response: <code>["Yep, this is the right URL, just put it into Geofency Web Hook, set to POST and do a test. Make sure your Geofency location name matches the device '<location>-Brian'"]</code>
5. Install Geofency app and your iBeacon (optional).  In the app, setup your location or iBeacons and name them with the same location you used in installation step #2 above. Then when you're happy with your location and radius, click the 3 dots on the location and select "Webhook". Change HTTP method to JSON via Post, change the URL for entry and exit to be the url you created in #5 above. Use Enter/Exit buttons to test the settings. Don't be fooled by a successful message in Geofency - make sure debug logging is enabled in the app so you can see if it really worked. 
6. You should now have a virtual presence sensor that you can tie to Hubitat actions. You can create as many virtual presense sensors as you have iBeacons or GPS locations in Geofency.

Bugs/Contact Info
-----------------
Check the [Hubitat Community Forum](https://community.hubitat.com/t/release-geofency-presence/22788) if you run into issues. 

Bug me on Twitter at [@brianwilson](http://twitter.com/brianwilson) or email me [here](http://cronological.com/comment.php?ref=bubba).
