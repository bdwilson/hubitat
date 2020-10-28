OwnTracks Presence Updater for Hubitat
=======

[OwnTracks](https://owntracks.org/) is an app for iOS & Android that uses either device GPS or Bluetooth LE iBeacons to detect presence. This device driver and SmartApp will allow you to use Bluetooth iBeacons or GPS info on your mobile device to set presence in Hubitat. This means you can now get accurate presence with or without using GPS - depending on if you own an iBeacon or not - and without using a presence sensor. It also means you can have many different geofence locations that can trigger any number of virtual presense devices (because Hubitat app is limited to a single geofence currently). 

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
2. Go to Devices and create a new virtual device of type OwnTracks Virtual Mobile Presence Device
3. Set the ___Location/Region___ and ___User___ to correspond to the location/region in OwnTracks you'll be monitoring and person who will be running OwnTracks 

__OR__

1. Go to Drivers Code and create a new device using [virtual-mobile-presence-owntracks.groovy](https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/virtual-mobile-presence-owntracks.groovy).
2. Go to Devices and create a new virtual device of type OwnTracks Virtual Mobile Presence Device
3. Set the ___Location/Region___ and ___User___ to correspond to the location/region in OwnTracks you'll be monitoring and person who will be running OwnTracks 
4. Go to Apps Code and import URL: [https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/owntracks-presence-app.groovy](https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/owntracks-presence-app.groovy).  Save it. <b>Click Oauth</b>

Configure
---------
1. Install the User App (Apps -> Add User App) and select all the virtual presense devices you created that you want to control with OwnTracks
2. Copy the Endpoint URL. This will be your URL to configure in OwnTracks.  It
should look something like this ___but you need to add the same user(s) as
above after location when you use this URL below___.  For instance, mine looks
like this:
https://cloud.hubitat.com/api/xxx-xxx-xx-xxx/apps/xx/location/Brian?access_token=adafae03-0330-4aeb-b15e-xxxxxxxxx.
3. You use the same URL for all of your users, just change the information after /location/ within the URL
4. Now paste this URL into your browser and make sure you get the following response: <code>["This is the right URL! Add it directly into the OwnTracks URL field and make sure your virtual presence device is configured with the the location/region and user ([User]) within the device preferences."]</code>
4. In the OwnTracks app
** Click the __(i)__ the main OwnTracks app on the top left, click __Settings__. 
** Change the mode at the top to method to __HTTP__ - ___this will remove any regions/friends you've configured___.   
** If UserID/Username field exists, you should make this the same as the user configured in the URL. If I leave this field blank, I get an error.
** __disable authentication__ (you'll authenticate using the access token in the hubitat URL).  
** In the URL field, copy the URL from above. ___Make sure the name of your User matches the user from installation Step 3 (within the device preferences), is added after the /location/ part in the URL.___<br><img src="https://bdwilson.github.io/images/IMG_4809.jpg" width=300px>
** Add your Regions or iBeacons in the app.  In the app, setup your regions and adjust the radius if needed or iBeacons and name them to match device preference location/region from installation step 3 above. 
5. To test your installation, make sure debug mode is enabled in the Hubitat App. Then go into the OwnTracks app and go back and forth between <b>significant</b> and <b>move</b> a few times and review your logs & virtual devices (you probably want to leave this on <b>significant</b> long-term because of battery life, but can review what these settings do <a href='https://owntracks.org/booklet/features/location'>here</a>). Device presence status should update to reflect correct presence for your devices. ___Note: Not all fields (battery, battery status, SSID, BSSID) will be available from all devices - this is a limitation with OwnTracks."___
6. You should now have a virtual presence sensor that you can tie to Hubitat actions. You can create as many virtual presense sensors as you have iBeacons or GPS locations in OwnTracks. You can also use the attributes (if available) to do things based on battery percentage, charging status, etc

Bugs/Contact Info
-----------------
Check the [Hubitat Community Forum](https://community.hubitat.com/t/release-owntracks-presence/53419) if you run into issues. 

Bug me on Twitter at [@brianwilson](http://twitter.com/brianwilson) or email me [here](http://cronological.com/comment.php?ref=bubba).
