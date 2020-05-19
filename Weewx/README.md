# Weewx Local Capabilities

Please see the [Hubitat Community](https://community.hubitat.com/t/release-weewx-local-capabilities/17709) for more info.


# Notes:

You'll need to search through the .groovy and make sure you're pulling the
correct variables.  This verison is pulling outTemp and rainSum from Weewx -
rainSum can be updated in the driver to however much rain you want it to
register in order for the conditions to go from dry to wet - this also turns
on/off a switch (whatever this device is called).  You can use this for
Irrigation apps like Simple Irrigation.


