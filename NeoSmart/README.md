# hubitatDrivers
A collection of drivers for the Hubitat Elevation.

# NeoSmart Blind Controller

Benefits of using Hubitat & Alexa to control your NeoSmart blinds is you can
have them go up/down to certain levels (50%, 25%, etc).  You cannot do this
with the [native NeoSmart Alexa
integration](http://neosmartblinds.com/smartcontroller-integrations/#scAlexaCommands). 

Requirements 
---

* [NeoSmart Controller](http://neosmartblinds.com/smartcontroller/)
* Optional Amazon Alexa device to control percentages via voice.

Setup
---

Since the NeoSmart blinds don't have a way to tell the controller where they are, we have to use time as a way to determine how far to open/close them. In
order for this to work, you must time how long it takes your blinds to open.
Because of this, you might want to setup each individual blind as their own
virtual device, then you can group them within Alexa. Otherwise, you can just
use your best estimate on how long it takes to open/close the blinds - some
will be faster, some will be slower, and depending on battery life, they may
all work a little differently. Unfortunately, you'll have to live with that
since Neo doesn't give us a way to have two-way control of the blinds. 

1) Add the driver to your drivers code. 
2) Create a new Virtual driver and give it the name of your blind or set of
blinds.  
3) Open the NeoSmart app and locate the Smart Controller option. Put the
contents of **ID** and **IP** into the settings of your virtual device.
4) Stil in the app, locate the blind you wish to control under your rooms,
click the arrow on the right of the room.  You should see both **Room Code**
and **Blind Code**. You need whichever code you wish to control - the room if
you wish to control the whole room, or blind code if you only wish to control
the single blind. 
5) While still in the NeoSmart app, use a stopwatch to close the blinds and
keep track of how many seconds it takes to close. Put this in settings.  Open
the blinds again then do the same thing with the Favorite button and put how
long it takes to get to the favorite setting from open.  
6) Add your blinds to your dashboard by selecting the new devices and using
"shade" as the device type. 

Optional Alexa Integration (probably same with Google Home)
---

* Share out your blinds to the Hubitat Amazon Echo App. Ask Alexa to discover new devices.  
* Within the Alexa App, you should see the devices show up as dimmer bulbs - thats ok; Alexa is dumb and doesn't know what a blind is. If you're OK telling Alexa to turn on/off blinds, then you don't need to proceed any further. I prefer to use the terms "open/close" vs. "on/off". Unfortunately this doesn't work out of the box with Alexa, however it **DOES** work if you say "Alexa, open the living room blind to 50%".  The word "open" works fine if you also give the blind a percentage to open. 
* If you go into Alexa -> Routines and create a routine that says "open the xxx blinds" and link to the proper device, then you don't have to say on/off. You'll have to setup both open and close routines per each blind, and perhaps create groups for "blinds" if you want them to all open/close.    

