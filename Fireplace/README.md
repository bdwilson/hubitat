# Fireplace Controller for Hubitat

<i>Using this device could imact building codes. Check your local codes and use
caution when altering how your fireplace works - you've been
warned.</i><br><br>
The goal of this device is to spend ~$12 on a device that can connect to
Hubitat and Alexa to control my fireplace. My fireplace (Heat-N-Glo) has 2 wall
switches, one to control the fan, one to control the fireplace. Both of these
are dry switches and do not have MAINS voltage. This device sіts inbetween the
wall switch (in series) that turns on the fireplace (I leave the fan switch on
all the time as it only runs if the temp is > a certain amount). Being in series means that
the wall switch now is a master OFF connection (if off, this relay device won't
work). Also, the linked Arduino code linked below has a 2 hour shutoff (i.e. it
will turn off your fireplace after 2 hours) - this is a saftey precaution. 

Requirements
----------

- [ST_Anything](https://github.com/DanielOgorchock/ST_Anything) and [Hubduino](https://github.com/DanielOgorchock/ST_Anything/tree/master/HubDuino)
- [Wemos D1 Mini](https://www.amazon.com/dp/B076F53B6S/?coliid=I2FDTEHO69YGKH)
- [Wemos D1 Relay Shield](https://www.amazon.com/dp/B01NACU547/) - Note: I was unable to use the defined D1 pin to make this relay work. I had to [modify it to use D5](https://www.youtube.com/watch?v=GykA_7QmoXE) as this video can help you do this. 
- Fireplace that uses a dry contact device to turn it on (non-powered
  lightswitch) - you'll have to break this connection and wire your relay into
one side of this connection. You will wire this to the NO (normally open) and
middle (common) connection.<br><img src="https://bdwilson.github.io/images/IMG_2624.JPG" width=400px> <br> <img src="https://bdwilson.github.io/images/IMG_2629.JPG" width=400px> <br> <img src="https://bdwilson.github.io/images/IMG_2631.JPG" width=400px> 

## Installation
1. Connect your relay shield device and make sure it works by using [this test code](https://github.com/bdwilson/hubitat/blob/master/Fireplace/wemos_d1-relay-test.ino) - this test code assumes you're using D5 to control your relay, not D1. Adjust this if you think you can get D1 working on your device. This code will toggle the relay on/off - you will hear this. If you don't hear anything, it's not working. 
1. Follow the instructions with ST_Anything and get it and it's dependancies setup in Arduino. Connect your Wemos D1 via USB to your computer to program via Arduino.
1. Install [wemos_d1-hubduino-fireplace.ino](https://github.com/bdwilson/hubitat/blob/master/Fireplace/wemos_d1-hubduino-fireplace.ino) in Arduino and make sure you add the Wifi info for your network and your Hubitat IP.
1. Make sure you install all the [ST_Anything](https://github.com/DanielOgorchock/ST_Anything/tree/master/HubDuino) <b>Parent Ethernet device</b> and the <b>Child Relay Switch</b> device. If you're having issues, make sure your wall switch for the fireplace is turned on and that you've properly spliced and connected your relay into NO and middle connector on the relay. Also, make sure you've done step 1 to verify your relay works - if not, follow the video in the requirements to move D1 for the relay to be either D5 or D6.  
1. Optional: [Print a case](https://www.thingiverse.com/thing:2667568). If you don't have a case, make sure you protect all metal contacts on the device with electrical tape. 

Bugs/Contact Info
-----------------
Bug me on Twitter at [@brianwilson](http://twitter.com/brianwilson) or email me [here](http://cronological.com/comment.php?ref=bubba).
