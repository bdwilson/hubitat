# Melnor Raincloud Driver
* You need a Raincloud watering device connected to WifiAquaTimer website.
* You need a system that can run a Python flask app -
[rainycloud-flask](https://github.com/bdwilson/raincloudy-flask)
* Import [this
driver](https://raw.githubusercontent.com/bdwilson/hubitat/master/Raincloud/raincloud-valve-driver.groovy)
and [this
app](https://raw.githubusercontent.com/bdwilson/hubitat/master/Raincloud/raincloud-connect-app.groovy).


# Work In Progresss
* ~~You need to create __one virtual device per valve__ - its up to you to not schedule the valves to overlap.~~
* The default time to run needs to be > whatever time you schedule via an app like Simple Irrigation.
* Be courteous with your scheduling to check status updates. If you have 4 valves checking every 5 minutes, it could be seen as putting considerable load on Melnor's servers. I would not go lower than 5 minutes while valves are open and would consider setting the refresh time to manual or 3 hours while closed. 
* ~~This should really be a parent/child app setup and the backend API should respond with the statuses of all controllers/faucets/valves vs. one response per valve. I only have 1 faucet so this doesn't impact me as much as it does others. Feel free to fix and send me pull requests but I don't have cycles to spend on this currently.~~ 
