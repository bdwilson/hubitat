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
	- [My Hubitat app](https://raw.githubusercontent.com/bdwilson/hubitat/claude/geofency-presence-install-42ldx6/Geofency-Presence/geofency-presence.groovy) assigned to your Virtual Presence Device(s) above

Installation
--------------------
1. Install via [HPM](https://community.hubitat.com/t/beta-hubitat-package-manager/38016) - search for keyword "presence" (or go to Drivers Code and Apps Code and install [virtual-mobile-presence.groovy](https://raw.githubusercontent.com/bdwilson/hubitat/master/Geofency-Presence/virtual-mobile-presence.groovy) and [geofency-presence.groovy](https://raw.githubusercontent.com/bdwilson/hubitat/claude/geofency-presence-install-42ldx6/Geofency-Presence/geofency-presence.groovy) manually. <b>Click Oauth</b> after saving the app.)
2. Install the User App (Apps -> Add User App -> Geofency Multi-User API Presence App)
3. In **Step 1** of the app, enter a **Location** and **User** (the same ones you'll use in Geofency) and click **Create Device** - this creates and configures the virtual presence device for you. It's usable by the app immediately - you don't need to select it anywhere. Repeat for each user/location pair you want to track. You can create as many as you like.
   * Prefer to do it yourself, or already have virtual presence devices from an older install? Go to _Devices -> Add Virtual Device_, create (or update an existing device to) type ___Geofency Virtual Mobile Presence Device___, set its ___Location___ and ___User___ preferences to match what you'll use in Geofency, then select it in **Step 2** ("Select Additional Virtual Presence Devices").
   * **Step 2 is only for devices Quick Setup didn't create.** Devices you created with Quick Setup in step 1 are already usable and don't need to be selected there. If you select a device in step 2 that isn't a ___Geofency Virtual Mobile Presence Device___ (or one of that type that's missing its Location/User), it will not work - and step 3 will call it out in red so you know.
4. **Step 3** lists one foldable entry per device - "Geofency Presence Entry 1", "Entry 2", etc. Each one shows that device's Location, User, and ready-to-paste webhook URL, followed by the exact steps to wire it up in the Geofency app (create the Location, set the Entry/Exit webhook URLs, HTTP method, and test it). Any selected device that's missing a Location/User shows up as its own entry too, in red, explaining why it isn't usable yet.

Configure Geofency
------------------
1. For each user's URL from Step 3 above, paste it into Geofency's webhook settings (Settings -> Webhook), with HTTP Method set to **POST (JSON)**.
   ___Keep in mind, if you replace your hub and restore from a backup your Hubitat cloud URL will change! Make sure you adjust all your automations should you restore from a backup to a new hub.___
2. Paste the same URL into your browser and make sure you get a response like: <code>["Yep, this is the right URL, just put it into Geofency Web Hook, set to POST and do a test. Make sure your Geofency location name matches the device location and user (Brian) configured in the preferences"]</code>
3. Install the Geofency app and your iBeacon (optional). In the app, set up your location or iBeacons and name them with the same location you used above. Then click the 3 dots on the location and select "Webhook". Change the HTTP method to JSON via Post, and set the URL for entry and exit to the URL from Step 3. Use the Enter/Exit buttons to test the settings. Don't be fooled by a successful message in Geofency - make sure debug logging is enabled in the app so you can see if it really worked.
4. You should now have a virtual presence sensor that you can tie to Hubitat actions. You can create as many virtual presence sensors as you have iBeacons or GPS locations in Geofency.

### Manual/legacy setup (still supported)

If you'd rather not use the **Create Device** button, or you're maintaining an existing install, the original manual flow still works unchanged:

1. Go to Devices and create a new virtual device of type ___Geofency Virtual Mobile Presence Device___ for each user and location who you wish to track (or go to Drivers Code and create the device using [virtual-mobile-presence.groovy](https://raw.githubusercontent.com/bdwilson/hubitat/master/Geofency-Presence/virtual-mobile-presence.groovy) first if it isn't installed yet).
2. Set:
  * ___Location___ to be the name of the monitored location in Geofency (call it whatever you want in Geofency, just make sure it matches in HE)
  * ___User___ to be the name entered and saved in HE and added to the webhook.
3. In the app's Step 2 ("Select Additional Virtual Presence Devices"), select the device(s) you just created.
4. Copy the per-device URL shown in the app's Step 3 (or build it yourself: take the Endpoint URL and add your user's name after `/location/` and before `?access_token=`).

Bugs/Contact Info
-----------------
Check the [Hubitat Community Forum](https://community.hubitat.com/t/release-geofency-presence/22788) if you run into issues. 

Bug me on Twitter at [@brianwilson](http://twitter.com/brianwilson) or email me [here](http://cronological.com/comment.php?ref=bubba).
