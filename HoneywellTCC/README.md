# Honeywell TCC IFTTT Driver

This is a Driver for Hubitat that supports controlling a Honeywell Total
Connect Comfort (TCC) Thermostat via IFTTT. Honeywell wants to be mean and not
support integrations with other home hubs, but they will integrate with IFTTT.
Thankfully, IFTTT supports webhooks so users can make their own calls. This
driver uses (abuses?) the ability to use webhooks to control devices which
you've integrated. This specific example works for my Honeywell Prestige
Thermostat, but it may work for others.

# Limitations

You're not going to get all thermostat functions. You won't be able to see what
the temp is, but perhaps you have another sensor on your Hubitat hub that will
give you that. All I really wanted to do was adjust temps, do permanent and
temporary holds, and then resume the schedule - so that's all this does.

Commands Supported:
setHeatingSetPoint (permanent or for X hours)
setCoolingSetPoint (permanent or for X hours)
setFollowSchedule
fanOn
fanAuto

So you'll essentially have to (well, you have to create the ones you want to
use) create 7 webhooks per thermostat you wish to control. 

# Installation

1) [IFTTT Account](https://ifttt.com). You need to connect your Honeywell
services to your account. Grab your API key from
[here](https://ifttt.com/services/maker_webhooks/settings) - it's the part
after the ''/use/'' in the URL.
2) Create a ''New Applet''. For ''This'' select Webhook. Click on the box ''Receive a web
request''.
3) For your event name, you'll make it be ''device''-''command''.  For
instance, I have two devices - upstairs and downstairs, and we have 7 commands
per device. So the first webhook I create will be ''downstairs-setCoolPerm''
4) For ''That'' select ''Honeywell Total Connect Comfort''. 
5) Then select the function you're creating - for this example, I'd select
''Set temperature to a permanent hold''. Then select the options for your
thermostat (which thermostat, cool or heat). For ''Target Temperature'', delete the field completely and click
on ''Add Ingredient'' and select ''Value1''. Then select ''Celsius'' or
''Fahrenheit''.  For your recipies that need two variables (the hold for
certain time), set ''Value2'' to be that variable. 
6) Click ''Create Action''. 

Optional: Enable notifications to verify things are working, but you can just
use the TCC app or website or thermstat to verify, because you'll eventually
turn them off.

Your events should look like this:
![IFTTT Configuration](https://github.com/bdwilson/bdwilson.github.io/blob/master/images/HoneywellTCC.png)

Next, install your driver.

1) Go to the raw version of the .groovy code on Github.
2) Go to Hubitat -> Drivers Code -> and paste the code in from above.
3) Go to Hubitat -> Devices -> Add Virtual Device
4) Scroll to the bottom of the list and select ''Honeywell TCC IFTTT Thermostat Driver''
5) Configure your command names to match what you configured above at IFTTT. It
should look similar to this:
![Hubitat Configuration](https://github.com/bdwilson/bdwilson.github.io/blob/master/images/HoneywellTCC2.png)
6) If you want to do perm holds unstead of holds for a # of hours, adjust your
''Hold#'' number, otherwise, all your changes will be permanent until you do a
''setFollowSchedule''.
7) Check the logs to see if things are working, if so, turn off debug.
Remember, not everything works just what's listed above at the top of the page.

# Questions
Q: What if I want to do permanent holds and temp holds?
A: Setup two drivers per thermostat, use the same webhooks, but change the driver options. 

# Questions/Comments/Pull Requests
Bug me on Twitter at [@brianwilson](http://twitter.com/brianwilson) or email me
[here](http://cronological.com/comment.php?ref=bubba) or on the [Hubitat
community](https://community.hubitat.com/)/@brianwilson.


