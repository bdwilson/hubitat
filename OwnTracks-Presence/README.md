OwnTracks Presence Updater for Hubitat
=======

[OwnTracks](https://owntracks.org/) is an app for iOS & Android that uses either device GPS or Bluetooth LE iBeacons to detect presence. This device driver and SmartApp will allow you to use Bluetooth iBeacons or GPS info on your mobile device to set presence in Hubitat. This means you can now get accurate presence with or without using GPS - depending on if you own an iBeacon or not - and without using a presence sensor. It also means you can have many different geofence locations that can trigger any number of virtual presense devices (because Hubitat app is limited to a single geofence currently). 

<img src="https://bdwilson.github.io/images/IMG_4808.jpg" width=300px>

This topic is covered in the Hubitat Community forums <a href="https://community.hubitat.com/t/release-owntracks-presence/53419">here</a>

Requirements
------------
To get started you'll need:
- [OwnTracks](https://owntracks.org/).  
- An iBeacon (Optional; or you can use GPS in the OwnTracks App). 
- Hubitat Hub
	- [A Virtual Presence OwnTracks Device](https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/virtual-mobile-presence-owntracks.groovy)
	- [My Hubitat app](https://raw.githubusercontent.com/bdwilson/hubitat/claude/owntracks-presence-quickstart/OwnTracks-Presence/owntracks-presence-app.groovy) assigned to your Virtual Presence Device(s) above

___NOTE: It's not my intention to duplicate what the OwnTracks MQTT server does with friend tracking, so if you have a requirement to use this, then you may need to look at other apps that can do different webhook URL's per region like [Geofency](https://github.com/bdwilson/hubitat/tree/master/Geofency-Presence) or use two apps to perform different tasks in your automations.___

Upgrading to 2.0
----------------
Version 2.0 adds a **Quick Setup** button that creates and configures your virtual presence device for you - see Installation below. If you're upgrading from a version before 2.0, nothing changes for devices you already have configured: just confirm they're still selected in the app's **Step 2** ("Select Additional Virtual Presence Devices"), skip Quick Setup entirely, and carry on as before.

Installation
--------------------
1. Install via [HPM](https://community.hubitat.com/t/beta-hubitat-package-manager/38016) (or go to Drivers Code and Apps Code and install [virtual-mobile-presence-owntracks.groovy](https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/virtual-mobile-presence-owntracks.groovy) and [owntracks-presence-app.groovy](https://raw.githubusercontent.com/bdwilson/hubitat/claude/owntracks-presence-quickstart/OwnTracks-Presence/owntracks-presence-app.groovy) manually. <b>Click Oauth</b> after saving the app.)
2. Install the User App (Apps -> Add User App -> OwnTracks Presence)
3. In **Step 1** of the app, enter a **Location/Region** and **User** (the same ones you'll use in OwnTracks) and click **Create Device** - this creates and configures the virtual presence device for you. It's usable by the app immediately - you don't need to select it anywhere. Repeat for each user/region pair you want to track. You can create as many as you like.
   * Prefer to do it yourself, or already have virtual presence devices from an older install? Go to _Devices -> Add Virtual Device_, create (or update an existing device to) type ___OwnTracks Virtual Mobile Presence Driver___, set its ___Location/Region___ and ___User___ preferences to match what you'll use in OwnTracks, then select it in **Step 2** ("Select Additional Virtual Presence Devices").
   * **Step 2 is only for devices Quick Setup didn't create.** Devices you created with Quick Setup in step 1 are already usable and don't need to be selected there. If you select a device in step 2 that isn't an ___OwnTracks Virtual Mobile Presence Driver___ device (or one of that type that's missing its Location/Region or User), it will not work - and step 3 will call it out in red so you know.
4. **Step 3** lists one foldable entry per device - "OwnTracks Presence Entry 1", "Entry 2", etc. Each one shows that device's Region/Location, User, and ready-to-paste webhook URL, followed by the exact steps to wire it up in the OwnTracks app on that person's phone (set HTTP mode, Username, and the URL; add the matching Region or iBeacon; then test). Any selected device that's missing a Region/Location or User shows up as its own entry too, in red, explaining why it isn't usable yet.

Configure OwnTracks
--------------------
1. Follow the steps in each entry from Step 3 above to configure that person's OwnTracks app - HTTP mode, Username, the webhook URL, and the matching Region/iBeacon.
   ___Keep in mind, if you replace your hub and restore from a backup your Hubitat cloud URL will change! Make sure you adjust all your automations should you restore from a backup to a new hub.___
2. Enable Debug Mode in the app (step 4), then in OwnTracks toggle between <b>significant</b> and <b>move</b> a couple of times. Don't be fooled by a successful-looking response in OwnTracks - check Hubitat's Logs and that device's events to confirm it actually updated.
3. You should now have a virtual presence sensor that you can tie to Hubitat actions. You can create as many virtual presence sensors as you have iBeacons or GPS locations in OwnTracks. You can also use the attributes (if available) to do things based on battery percentage, charging status, etc.

### Manual setup / versions before 2.0 (still supported)

If you'd rather not use the **Create Device** button, or you're maintaining an install from a version before 2.0, the original manual flow still works unchanged:

1. Go to Devices and create a new virtual device of type ___OwnTracks Virtual Mobile Presence Driver___ for each user and region/location who you wish to track (or go to Drivers Code and create the device using [virtual-mobile-presence-owntracks.groovy](https://raw.githubusercontent.com/bdwilson/hubitat/master/OwnTracks-Presence/virtual-mobile-presence-owntracks.groovy) first if it isn't installed yet).
2. Set:
  * ___Location/Region___ to be the name of the monitored region in OwnTracks (call it whatever you want in OwnTracks, just make sure it matches in HE)
  * ___User___ to be the name entered and saved in HE and added to the webhook.
3. In the app's Step 2 ("Select Additional Virtual Presence Devices"), select the device(s) you just created.
4. Copy the per-device URL shown in the app's Step 3 (or build it yourself: take the Endpoint URL and add your user's name after `/location/` and before `?access_token=`).
5. (Optional sanity check) Paste the URL into your browser - you should get a response like: <code>["This is the right URL! Add it directly into the OwnTracks URL field and make sure your virtual presence device is configured with the the location/region and user (Brian) within the device preferences."]</code>
6. In the OwnTracks app:
   * Click the __(i)__ icon on the main OwnTracks screen, then __Settings__.
   * Change the mode at the top to __HTTP__ - ___this will remove any regions/friends you've configured if you're using MQTT___.
   * If a UserID/Username field exists, set it to the same user configured in the URL - leaving it blank causes an error.
   * __Disable authentication__ (you'll authenticate using the access token in the Hubitat URL).
   * In the URL field, paste the URL from above. ___Make sure the name of your User matches the user from installation step 2 (within the device preferences), is added after the /location/ part in the URL.___<br><img src="https://bdwilson.github.io/images/IMG_4809.jpg" width=300px>
7. Still within the app, add your Regions or iBeacons, adjusting the radius if needed, and name them to match the Location/Region set on your device in step 2.
8. To test, make sure debug mode is enabled in the Hubitat app, then go back and forth between <b>significant</b> and <b>move</b> a few times in OwnTracks and review your logs & virtual device events.

Bugs/Contact Info
-----------------
Check the [Hubitat Community Forum](https://community.hubitat.com/t/release-owntracks-presence/53419) if you run into issues. 

Bug me on Twitter at [@brianwilson](http://twitter.com/brianwilson) or email me [here](http://cronological.com/comment.php?ref=bubba).
