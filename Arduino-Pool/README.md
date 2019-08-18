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
- [LinkNode R8](https://www.amazon.com/ESP8266-ESP-01S-Wireless-Development-PlayStation-4/dp/B07FBNZ79T)
- [Arduino](https://arduino-esp8266.readthedocs.io/en/latest/installing.html)
- [ST_Anything](https://github.com/DanielOgorchock/ST_Anything)
- [SuperFlo VS Communication Cable](https://www.polytecpools.com/Pentair-SuperFlo-VS-Communication-Cable_p_9177.html)

Installation
--------------------

1. Determine which device you wish to control with Hubitat, go into the Maker
API app and get the internal URL to use to do a "refresh". It should be
something similar to this:
http://192.168.1.xx/apps/api/8/devices/xxx/refresh?access_token=xxxxxx-xxxxxx-xxxx-xxxxxx.
Save this URL as you'll need it later.

2. Follow the steps here to download and [install
Arduino](https://arduino-esp8266.readthedocs.io/en/latest/installing.html).
Make sure you follow the Instruction steps about adding the Additional Board
Manager URL: http://arduino.esp8266.com/stable/package_esp8266com_index.json,
then open Boards Manager and install version 2.5.0 or greater for ESP8266.
https://arduino-esp8266.readthedocs.io/en/latest/installing.html

3. Go to Tools -> Board and select NodeMCU 0.9 (ESP-12 Module)

4. Create a new Sketch and copy and paste in the code from hubitat.ino and change the variables to set
your Wifi Name, Wifi Password, and the URL from #1. 

5. Make sure the code compiles: Arduino -> Sketch -> Verify/Compile

Changes
-------

Bugs/Contact Info
-----------------
Bug me on Twitter at [@brianwilson](http://twitter.com/brianwilson) or email me [here](http://cronological.com/comment.php?ref=bubba).


