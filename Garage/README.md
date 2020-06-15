Arduino ST_Anything Garage Controller for Hubitat
=======
The goal of this device is to control my Garage openers - which are MyQ
compatible with something that gives me local control and does not rely on the
MyQ Cloud. I was previously using MyQ Lite for Hubitat (which at one point I
was helping to keep working with they changed things), but I'd had enough.

<img src="https://bdwilson.github.io/images/IMG_8BC0D8160AE5-1.jpeg" width=400px>

Requirements
------------
To get started you'll need:
- [Wemos D1
  Mini](https://www.amazon.com/Development-MicroPython-Nodemcu-Arduino-Compatible/dp/B07H22CDQ8) - you only need one, however if you have other projects, get a few as they are cheaper in bulk
- [Relay Shields for Wemos](https://www.amazon.com/HiLetgo-Relay-Shield-Module-WeMos/dp/B01NACU547) - you'll need as many as you have doors to control
- [Arduino](https://arduino-esp8266.readthedocs.io/en/latest/installing.html)
- [ST_Anything](https://github.com/DanielOgorchock/ST_Anything) and
  [Hubduino](https://github.com/DanielOgorchock/ST_Anything/tree/master/HubDuino)
- Remote Control that will work on your garage doors - I got one from eBay for
  $10 that looked like this <img src="https://bdwilson.github.io/images/garage_remote.png" width=400px>
- [Project Box](https://www.thingiverse.com/thing:4460677) - This is one I
  made, you could come up with something on your own.

Installation
--------------------

1. Follow the instructions with ST_Anything and get it and it's dependancies
setup in Arduino. Connect your R8 or R4 device an FT232 device or similar that
can be used to program the ESP8266 on the R4 or R8.

2. Install [garage_controller-hubduino-wemos-d1-mini.ino](garage_controller-hubduino-wemos-d1-mini.ino) into Arduino and modify the settings for
your wireless network, Hub IP. I've set mine up for DHCP and have the IP
reserved, you should do this. 

3. Wire it up:
- Wemos D1 Mini will use D6 and D7 to control the two relays - connect D6 to D1
  of one of the relays, and D7 to D1 of the other relay. <img src="https://bdwilson.github.io/images/IMG_4029.JPG" width=400px>
- Get 5v and Ground from Wemos D1 Mini to each relay - I daisy chained them in
  my pictures. <img src="https://bdwilson.github.io/images/IMG_4030.JPG" width=400px>
- Use a multi-tester to figure out how your buttons are wired - the remote I
  got was pretty easy as the buttons had only 2 terminals and not 4 like some.
You'll notice I only wired one side on one button - this is because that
connection is shared with another button, thus I daisy chain one side as to not
have to solder another connection to a button. <img src="https://bdwilson.github.io/images/IMG_4030.JPG" width=400px>
You'll need to connect 3v3 and Ground to the remote as well. <img src="https://bdwilson.github.io/images/IMG_4028.JPG" width=400px>
<img src="https://bdwilson.github.io/images/IMG_4027.JPG" width=400px>

4. Install the rest of
[Hubduino](https://github.com/DanielOgorchock/ST_Anything/tree/master/HubDuino)
in Hubitat and install the [ethernet
driver](https://github.com/bdwilson/ST_Anything/blob/master/HubDuino/Drivers/hubduino-parent-ethernet.groovy)
and the [relay switch
driver](https://github.com/bdwilson/ST_Anything/blob/master/HubDuino/Drivers/child-relay-switch.groovy)
it should discover your relayswitch devices. If everything works, put it in
your box: <img src="https://bdwilson.github.io/images/IMG_4013.jpg" width=400px>
<img src="https://bdwilson.github.io/images/IMG_4014.jpg" width=400px>

Bugs/Contact Info
-----------------
Bug me on Twitter at [@brianwilson](http://twitter.com/brianwilson) or email me [here](http://cronological.com/comment.php?ref=bubba).



