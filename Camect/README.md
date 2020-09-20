# Camect Connect for Hubitat

This app will allow you to:
1. Sync HSM states to set Camect's Home/Default modes
2. Create virtual Motion devices within Hubitat to represent each camera.
Motion detected on a camera will be synced to Hubitat. You need a Linux system
to run the [Camect Connector](https://github.com/bdwilson/camect-connector). This connects to Camect and forwards events to
Hubitat (still, all communications remains local). 

Instructions
---
* Import these Drivers: [Camect Connect driver](https://raw.githubusercontent.com/bdwilson/hubitat/master/Camect/camect_connect-driver.groovy) and optionally the [motion & alerting driver](https://raw.githubusercontent.com/bdwilson/hubitat/master/Camect/camect_connect-motion-driver.groovy) assuming you want virtual motion devices in Hubitat to register when your cameras detect motion. 
* Import these Apps: [The main Camect Connect App](https://raw.githubusercontent.com/bdwilson/hubitat/master/Camect/camect_connect-app.groovy)
and optionally [Motion Disabler App]((https://raw.githubusercontent.com/bdwilson/hubitat/master/Camect/camect_connect-motion-disabler-child-app.groovy) if you wish to selectively disable Camect notifications based on some sensors (presence/contacts/locks/motion/etc)
* Install the User App Camect Connect; use the variables you'll need to install the app.
    1. You'll need to then navigate to [https://local.home.camect.com](https://local.home.camect.com) and accept the Terms of Service. You'll end up on your local server and the name will be **xxxxxx**.l.home.camect.com. This beginning part is considered your **Camect Code**. 
    2. You'll then need to determine your username and password - the username in the default case is **admin** and the password is the first part of your email address that you used to register your camect device - for instance, bob@gmail.com would give you the password "bob".
* Optionally go back after installation and add a Motion Disabler (requires the child app above). This will let you suppress camect notification events based on hubitat devices 

# Work In Progress
* ~~Requires middleman to listen to events and forward those to Hubitat.~~  This has been fixed - native support to connect to the Camect webservice has been implemented.
* The Camect API is extremely limited, so help me out by voting for these:
    * ~~**CAMECT HAS SAID THIS IS IN PROGRESS** Would be good if there was an API command to [temporarily suppress notitifications](https://groups.google.com/a/camect.com/forum/?oldui=1#!category-topic/forum/feature-request/1MnFjSAdPUI).  This way, if you are coming/going and not a visitor, you could inform camect suppress notifications (Telegram, Email, etc) based on some Hubitat event. This is somethign I'd really like to see.~~ This has been added to the API. 
    * [Suppressed Events are not exposed via API](https://groups.google.com/a/camect.com/forum/?oldui=1#!category-topic/forum/feature-request/A0K0YgHQizQ) so go vote for this as well if you need this and want to use Hubitat to determine if a notification gets sent.
    * [Animated GIF is not exposed via API](https://groups.google.com/a/camect.com/forum/?oldui=1#!category-topic/forum/feature-request/_PLRDMPR02Q) thus you can't send that URL to a device of your pleasing (Hubitat, or Pushover for instance).  

