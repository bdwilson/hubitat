# Camect Connect for Hubitat

This app will allow you to:
1. Sync HSM states to set Camect's Home/Default modes
2. Create virtual Motion devices within Hubitat to represent each camera.
Motion detected on a camera will be synced to Hubitat. You need a Linux system
to run the [camect_connector.py](https://github.com/bdwilson/camect-connector/camect_connector.py). This connects to Camect and forwards events to
Hubitat (still, all communications remains local). 

Instructions
---
* Import [this
driver](https://raw.githubusercontent.com/bdwilson/hubitat/master/Camect/camect_connect-motion-driver.py)
and [this
app](https://raw.githubusercontent.com/bdwilson/hubitat/master/Camect/camect_connect-app.py). 
* Optionally install
[camect_connector.py](https://github.com/bdwilson/hubitat/master/Camect/camect_connector.py)
if you wish to sync Camera events to Hubitat. 

# Work In Progress
* Requires middleman to listen to events and forward those to Hubitat. 
* The Camect API is extremely limited, so help me out by voting for these:
** Would be good if there was an API command to [temporarily suppress
notitifications](https://groups.google.com/a/camect.com/forum/?oldui=1#!category-topic/forum/feature-request/1MnFjSAdPUI).
This way, if you are coming/going and not a visitor, you could inform camect to
suppress based on some Hubitat event. Go vote for this if you see value.
** [Suppressed Events are not exposed via
API](https://groups.google.com/a/camect.com/forum/?oldui=1#!category-topic/forum/feature-request/A0K0YgHQizQ)
so go vote for this as well if you need this and want to use Hubitat to
determine if a notification gets sent.
** [Animated GIF is not exposed via
API](https://groups.google.com/a/camect.com/forum/?oldui=1#!category-topic/forum/feature-request/_PLRDMPR02Q)
thus you can't send that URL to a device of your pleasing (Hubitat, or Pushover
for instance).  

