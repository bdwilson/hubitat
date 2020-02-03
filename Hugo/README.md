# Hugo Button Remote + Hubitat Integration

Hugo is a <i>fantastic</i> button remote availble on
[Tindie](https://www.tindie.com/products/nicethings/hugo-esp8266-4-button-wifi-remote/)
that uses an ESP8266, Arduino compatibale firmware, a Battery and 4 buttons (which offers 7 button
combinations). You can [print your own
cases](https://www.thingiverse.com/thing:3641618), [upload your own
firmware](https://github.com/mcer12/Hugo-ESP8266), you can even [solder an IR
transmitter to it and use it as a real remote - and other hardware
mods](https://github.com/mcer12/Hugo-ESP8266/wiki). I use my device to control
my blinds. I'm going to detail how to integrate your Hugo device into Hubitat.

<img src="https://bdwilson.github.io/images/hubitat-hugo4.png" width=200px>

##Prerequisites

* [Buy a
Hugo](https://www.tindie.com/products/nicethings/hugo-esp8266-4-button-wifi-remote/)
* [Buy a Hubitat](https://hubitat.com/)

## Installation
1. Create a new Virtual Button within Hubitat -> Devices.  Call it Hugo (or Hugo1 if you have more than one)
<img src="https://bdwilson.github.io/images/hubitat-hugo1.png" width=800px>
2. Install the Hubitat (if not already installed) Maker API Built-In app. Add your new Hugo device to Maker API. 
3. Use the <b>Get All Devices</b> link within Maker API Hubitat App to get the info for all devices. Copy this URL; it should look similar to this: http://192.168.1.xx/apps/api/8/devices?access_token=xxxx-xxx-xxx-xxxxxx
4. Look through the list to determine the Device ID of your Hugo device you created above. For my example, it will be device # 1825
5. Use the URL above to create your Button URL's. Replace 1825 with your Hugo
device number and the # sign with your button (1-7).
http://192.168.1.xx/apps/api/8/devices/1825/#?access_token=xxxx-xxx-xxx-xxxxxx  (HINT: if you leave off the button number, you will see the capabilities of the device - this is a good way to determine if you have the right URL). 
<img src="https://bdwilson.github.io/images/hubitat-hugo0.png" width=800px>
6. Go into Hubitat Apps -> Rule Machine -> Create New -> Name it "Button - Hugo 1" or whatever you wish.  Select Trigger Events -> Button Device -> Select you virtual Hugo Device you created in step 1 above.
7. Select Pre-fill all button actions with "pushed".  Then go through each button and map it to what command you wish to run.
<img src="https://bdwilson.github.io/images/hubitat-hugo2.png" width=800px>
8. Verify using the URL's in #4 above, to make sure your buttons do what you want. 
9. Once you're happy, [follow the Hugo instructions](https://github.com/mcer12/Hugo-ESP8266/wiki/Sketch:-HTTP-(Basic-URL-trigger)
to add each URL from above, adjusting the number from 1 to 7.  
<img src="https://bdwilson.github.io/images/hubitat-hugo3.png" width=800px>
