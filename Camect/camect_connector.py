#!/usr/bin/python3
#
# need https://github.com/camect/camect-py 
# you can install these below packages via pip3
# 
# Further instructions can be found here
# https://github.com/bdwilson/hubitat/tree/master/Camect

import time
import camect
from pprint import pprint
import json
import requests
import sys
#import logging
#logging.basicConfig(level=logging.DEBUG)

# you will get this after you install the Camect Connect Hubitat App
hubitatOAUTHURL = 'http://192.168.x.x/apps/api/258/camect/?access_token=41b22633-xxx-xxx-xxx-xxxxxxxx'
# Your password is the first part of your email address that you used to register your Camect - for instance, bob@gmail.com would login as admin/bob.
camectUsername = "admin"
camectPassword = "bob"
# Your Camect Home Code can be obtained by navigating to https://local.home.camect.com, then accepting the TOS. 
# Once there, your home code is the first part of hostname - xxxxx.l.home.camect.com
# Don't forget the :443 port.
camectHost = "abc1345def.l.home.camect.com:443"

def sendEvent (string):
	headers = {'content-type': 'application/json'}
	try:
		r=requests.post(hubitatOAUTHURL, data=json.dumps(string), headers=headers, timeout=5)
		if (r.status_code != 200):
			print("Error sending event to Hubitat: %s" % r.status_code)
		else:
			print("Successfully sent event to hubitat")
	except (requests.exceptions.HTTPError, AttributeError) as err:
		print("Error sending event to Hubitat: %s" % err)

while(True):
	try:
		home = camect.Home(camectHost, camectUsername, camectPassword)
		print("Connected to %s" % home.get_name())
		loop = home.add_event_listener(lambda evt: sendEvent(evt))
		run=0
		while(1):
			time.sleep(5)
	except(KeyboardInterrupt):
		exit(0)
	except:
		e = sys.exc_info()[0]
		print("Unexpected exception: %s", e)
