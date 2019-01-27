# Honeywell Security

This is an App/Driver for Hubitat that supports controlling a Honeywell Vista
system via
[smartthings-nodeproxy](https://github.com/redloro/smartthings#envisalink-vista-tpi-plugin--alarmdecoder-ad2usb)
talking to a Envisalink 3/4 device.

# Requirements

1) SmartThings Node Proxy with Envisalink plugin configured and working. More
info on this here:
https://github.com/redloro/smartthings#envisalink-vista-tpi-plugin--alarmdecoder-ad2usb.
If you're moving from SmartThings to Hubitat then you already have this. You
may need to update your config.json to point to the IP address of your Hubitat
device, however the Honeywell Security app *should* do this for you. 

2) You need the MAC address of the device that is running your Node Proxy
server. This is a new requirement that wasn't needed for SmartThings. You can
either get this by running 'ifconfig' on your server, or you can enter a random
mac address in the config and attempt installation, then you'll see in the logs
that the connect was refused and the correct MAC address will be in the log
entry.

3) Have 1 Hubitat (or be OK using the first hubitat that you configured).  I wasn't sure how to do the
partition selections in the GUI. Feel free to fix this and adjust the other
functions. 

4) Only 1 partition on your Honeywell device. Only one device can have the MAC address
as the Device Network ID (DNI), so logic would have to be built in to add
another partition and if the partition is > 1, then perhaps the DNI could be
the MAC address|2. Logic would have to be adjusted in the updatePartitions
function and the create Partitions fuction. This probably isn't too much work
but I only have 1 so I can't test it. 

# Installation

1) Install the 3 zone drivers and 1 Partition driver into Drivers Code (click
on each, view raw file, copy and paste, save)
2) Install the Honewell Security into the Apps section (click
on each, view raw file, copy and paste, save)
3) Go to Apps and + Add User App. 
4) Click on the App once it's installed to configure it
5) Copy data from your SmartThings installation. You can look in the
config.json file for the password for your proxy and password for your panel.
You will need the MAC address of the server that your Node Proxy device is
running on.
6) Turn discover zones ON. 
7) Save and go to live logs and watch for errors. 

# To Do.

1) I have no idea if SHM works, or Hubitat's version works. 
2) No idea how to make buttons for partition. This might just work?
3) I realize native interfaces to Envisalink TPI for Vista/Ademco would be
better; but the node-proxy setup has been working so well with SmartThings, why reinvent the
wheel.
