Arduino ST_Anything Pool Controller
=======
<br>
The goal of this device is to control my Pool Pump (Pentair Superflow VS) + my
[$S.R. Smith Kelo pool
lights](https://srsmith.com/en-us/products/pool-lighting/kelo-led-pool-light/). 

***Need to Finish documentation***


Requirements
------------
To get started you'll need:
- [LinkNode
  R8](https://www.amazon.com/ESP8266-ESP-01S-Wireless-Development-PlayStation-4/dp/B07FBNZ79T).
You'll also need a power supply, or be comfortable dissecting an old USB cable
and hardwiring it to your R4 or R8 device. 
- [Arduino](https://arduino-esp8266.readthedocs.io/en/latest/installing.html)
- [ST_Anything](https://github.com/DanielOgorchock/ST_Anything)
- [SuperFlo VS Communication Cable](https://www.polytecpools.com/Pentair-SuperFlo-VS-Communication-Cable_p_9177.html)
- [Pool Lights](https://srsmith.com/media/178718/2018-kelo_manual_0318.pdf) (Optional) - Any model that is controlled with quick on/off/on to cycle through the lights.

Installation
--------------------

1. Follow the instructions with ST_Anything and get it and it's dependancies
setup in Arduino. Connect your R8 or R4 device an FT232 device to program it. 

2. Install ST_Anything-LinkNodeR8.ino into Arduino and modify the settings for
your wireless network, Hub IP, and config settings below

3. The configuration in LinkNodeR8 code is setup this way:
	- Switch 1 = Speed 1, Switch 2 = Speed 2, Switch 3 = Speed 3. Switch 4 =
	  Quick Clean
    - If you turn on any speed, it turns the other speeds off.
    - Relay Switch 9 is 15 minutes of Quick Clean, Relay Switch 10 is 30
      minutes of Quick Clean; it will turn both of these off when complete but won't turn on any other speeds when done. 
	- Relay Switch 1-8 are configured as timed relay switches to turn the
	  switch on/off/on within 1 second for a certain number of times which in
turn, changes the colors. This is a common design for pool lighting systems.
**If you're not going to wire your lights to this only only want to control
your pool speeds, comment out those relay switches and executors for the
sensors within the code.**
![](ħttps://bdwilson.github.io/images/ledmanual.png){:width="400px"}

4. Splice your SuperFlow VS communication wire and wire it according to the
docs below and speeds mentioned above. 
![](ħttps://bdwilson.github.io/images/superflovs.png){:width="400px"}
Example here is a LinkNode R4 
![](ħttps://bdwilson.github.io/images/IMG_0882.JPG){:width="400px"}

Note: by default on a SuperFlow VS will use external controls if any of the
inputs have signal, otherwise, the pump will follow the schedule on the device.
This worked best for me. If you want only External controls to work, follow
this information. You cannot power on/off the pump via either method. 
![](ħttps://bdwilson.github.io/images/superflovs.png){:width="400px"}

5. If you are wiring lights, you'll need to tap into your existing power for
your low voltage lights and essentially put a relay inbetween those
connections. Be very careful if you're messing in your pool control or lighting
control box and insure that mains are off at the breaker.
![](ħttps://bdwilson.github.io/images/IMG_0885.JPG){:width="400px"}

6. Install the rest of ST_Anything within Hubitat and it should discover all of
your switches and relayswitches. If everything works, put everything in a
[water-tight box](https://www.amazon.com/gp/product/B07BQD3SZV).

Bugs/Contact Info
-----------------
Bug me on Twitter at [@brianwilson](http://twitter.com/brianwilson) or email me [here](http://cronological.com/comment.php?ref=bubba).


