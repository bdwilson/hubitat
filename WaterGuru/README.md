# WaterGuru Integration Driver
* You need a WaterGuru pool device.
* You need a system that can run a Python flask app -
[waterguru-api](https://github.com/bdwilson/waterguru-api).  There a dockerfile
so this can easily be done. 
* Install via HPM or manually using these: [this driver](https://raw.githubusercontent.com/bdwilson/hubitat/master/WaterGuru/WaterGuru-Driver.groovy) and [this app](https://raw.githubusercontent.com/bdwilson/hubitat/master/WaterGuru/WaterGuru-Integration.groovy).

# Caveats / Work in Progress
* This should really be a native Hubitat App, but I'm not investing the time to
figure out AWS Cognito + AWS4 Auth in Hubitat - knock yourself out and send me
pull requests. 
* This is intentionally limited to 2 polling times per day. I don't want this to get shut down by the vendor for abusing the API. I realize 2 polling times per day isn't great if you're tracking things like temperature, so I'd recommend you go [another route](https://github.com/bdwilson/hubitat/tree/master/Arduino-Pool#Requirements) and consider installing a pool sensor. 
* This doesn't include any notification options, but I can see many may be
there - if flow < certain threshold may mean your skimmer is full, if pH is
below or above a threshold, or free chlorine above or below, if cassette is
getting low, etc. If you come up with some notification pieces that can be
integrated in, send me the pull requests. 
* ~~This should really be a parent/child app setup and the backend API should respond with the statuses of all controllers/faucets/valves vs. one response per valve. I only have 1 faucet so this doesn't impact me as much as it does others. Feel free to fix and send me pull requests but I don't have cycles to spend on this currently.~~ 
