/**
 * Volvo Connect App
 *
 * 1.2.3 - Brian Wilson / bubba@bubba.org
 *
 * Native Hubitat integration for Volvo vehicles via the Volvo Connected Vehicle API.
 * Supports lock/unlock, fuel/battery level, range, GPS location, doors, windows,
 * tyres, odometer, warnings, engine start/stop, climatization, and honk/flash.
 *
 * Setup:
 *  1. Register a developer account at https://developer.volvocars.com
 *  2. Create an application and note your VCC API Key, Client ID, and Client Secret
 *  3. Add your Hubitat callback URL as an allowed redirect URI in your Volvo app settings
 *     (the callback URL is shown on the Authorization page of this app)
 *  4. Install this app, enter your credentials, click Authorize, complete the browser flow
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at:
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

definition(
    name: "Volvo Connect",
    namespace: "brianwilson-hubitat",
    author: "bubba@bubba.org",
    description: "Volvo Connected Vehicle integration — lock, fuel/battery, range, location, doors, windows, tyres, engine",
    category: "My Apps",
    importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Volvo/Volvo-Connect-App.groovy",
    iconUrl: "",
    iconX2Url: "",
    singleInstance: true,
    oauth: true
)

preferences {
    page(name: "mainPage")
    page(name: "authPage")
}

mappings {
    path("/callback") {
        action: [GET: "oauthCallback"]
    }
}

// =================== Pages ===================

def mainPage() {
    if (!state.accessToken) createAccessToken()

    def callbackUrl = getCallbackUrl()

    return dynamicPage(name: "mainPage", install: false, uninstall: true, nextPage: "authPage") {
        section("<b>Step 1 — Create your Volvo app with this Redirect URI</b>") {
            paragraph "When you create an application at <a href='https://developer.volvocars.com' target='_blank'>developer.volvocars.com</a>, " +
                      "it will ask for a <b>Redirect URI</b>. Use this exact URL:"
            paragraph "<code>${callbackUrl}</code>"
            paragraph "<i>Volvo requires a publicly reachable HTTPS URI — this is your hub's cloud callback URL.</i>"
        }

        section("<b>Step 2 — Enter your Volvo Developer Credentials</b>") {
            paragraph "After creating the app, copy its credentials here."
            input "vccApiKey",     "text",     title: "VCC API Key",     required: true,  submitOnChange: true
            input "clientId",      "text",     title: "Client ID",       required: true,  submitOnChange: true
            input "clientSecret",  "password", title: "Client Secret",   required: false, submitOnChange: true
        }

        section("<b>Step 3 — Which optional APIs did you subscribe to?</b>") {
            paragraph "Check only the APIs you subscribed to in the Volvo developer portal. " +
                      "Requesting scopes from an unsubscribed API will cause authorization to fail.\n\n" +
                      "<b>Connected Vehicle API</b> is always included (lock/unlock, fuel, doors, windows, tyres, odometer, warnings)."
            input "hasEnergyApi",   "bool", title: "Energy API — EV battery level, charging status, electric range",  defaultValue: false, submitOnChange: true
            input "hasLocationApi", "bool", title: "Location API — GPS latitude/longitude",                            defaultValue: false, submitOnChange: true
        }

        section("<b>Step 4</b>") {
            paragraph "Click Next to authorize with Volvo and select your vehicle."
        }
    }
}

// Volvo requires a publicly reachable HTTPS redirect URI — use the hub cloud URL.
private String getCallbackUrl() {
    if (!state.accessToken) createAccessToken()
    return "${getFullApiServerUrl()}/callback?access_token=${state.accessToken}"
}

def authPage() {
    if (!state.accessToken) createAccessToken()

    def callbackUrl = getCallbackUrl()
    def isAuthorized = state.volvoRefreshToken != null

    return dynamicPage(name: "authPage", install: isAuthorized, uninstall: true) {

        if (!isAuthorized) {
            section("<b>Step 1 — Register Redirect URI</b>") {
                paragraph "In your Volvo developer app settings, add this exact URL as an allowed redirect URI:\n\n<code>${callbackUrl}</code>"
            }

            section("<b>Step 2 — Authorize</b>") {
                if (settings.clientId && settings.vccApiKey) {
                    buildPkce()
                    def authUrl = buildAuthUrl(callbackUrl)
                    paragraph "Click the link below to authorize Hubitat with your Volvo account:\n\n<a href='${authUrl}' target='_blank'><b>Authorize with Volvo &rarr;</b></a>"
                    paragraph "<i>After authorizing, you will be redirected back to this app automatically. Then refresh this page.</i>"
                    if (state.authError) {
                        paragraph "<font color='red'>Authorization error: ${state.authError}</font>"
                        state.authError = null
                    }
                } else {
                    paragraph "<i>Enter credentials on the previous page first.</i>"
                }
            }
        } else {
            section("<b>Authorization</b>") {
                paragraph "<font color='green'>&#10003; Authorized with Volvo</font>"
                input "btnReauth", "button", title: "Re-authorize", width: 3
            }

            section("<b>Vehicle Selection</b>") {
                input "btnDiscover", "button", title: "Discover Vehicles", width: 3
                if (state.discoveryError) paragraph "<font color='red'>${state.discoveryError}</font>"
                if (state.discoveredVehicles) {
                    input "selectedVin", "enum",
                        title: "Select Vehicle",
                        options: state.discoveredVehicles,
                        required: true, submitOnChange: true
                }
            }

            if (settings.selectedVin) {
                section("<b>Polling</b>") {
                    input "pollInterval", "enum",
                        title: "Poll Interval",
                        options: ["5": "Every 5 min", "10": "Every 10 min", "15": "Every 15 min",
                                  "30": "Every 30 min", "60": "Every hour"],
                        defaultValue: "15", required: true
                }

                section("<b>Units</b>") {
                    input "useImperial", "bool", title: "Use imperial units (miles) instead of metric (km)", defaultValue: false, submitOnChange: true
                }

                section("<b>Notifications</b>") {
                    paragraph "Notifications are change-driven — a poll by itself never sends anything."
                    input "notifyDevices", "capability.notification",
                        title: "Send notifications to", multiple: true, required: false, submitOnChange: true
                    if (settings.notifyDevices) {
                        input "notifyChargingStarted",  "bool", title: "Notify when charging starts",                           defaultValue: true,  required: false
                        input "notifyChargingStopped",  "bool", title: "Notify when charging stops or disconnects",             defaultValue: true,  required: false
                        input "notifyChargingComplete", "bool", title: "Notify when fully charged",                             defaultValue: true,  required: false
                        input "notifyBatterySteps",     "bool", title: "Notify at every 10% battery increment while charging",  defaultValue: false, required: false
                    }
                }

                section("<b>Options</b>") {
                    input "isDebug", "bool", title: "Enable Debug Logging", defaultValue: false, submitOnChange: true
                }
            }
        }
    }
}

def appButtonHandler(btn) {
    if (btn == "btnDiscover") {
        state.discoveryError = null
        discoverVehicles()
    } else if (btn == "btnReauth") {
        state.volvoAccessToken  = null
        state.volvoRefreshToken = null
        state.volvoTokenExpiry  = null
    }
}

// =================== OAuth2 / PKCE ===================

private void buildPkce() {
    // Regenerate on every page view so the challenge always matches the current link
    if (true) {
        state.codeVerifier = randomToken(64)
        // CSRF guard: a one-time value echoed back by Volvo and verified in the callback.
        // Prevents an attacker from replaying our callback URL with their own auth code.
        state.oauthState = randomToken(32)
        ifDebug("Generated new PKCE code_verifier and oauth state")
    }
}

private String randomToken(int len) {
    def chars = (('A'..'Z') + ('a'..'z') + ('0'..'9') + ['-', '_', '.', '~'])
    def sb = new StringBuilder(len)
    len.times { sb.append(chars[(int)(Math.random() * chars.size())]) }
    return sb.toString()
}

private String codeChallenge(String verifier) {
    def digest = java.security.MessageDigest.getInstance("SHA-256").digest(verifier.getBytes("UTF-8"))
    return digest.encodeBase64().toString().tr('+/', '-_').replaceAll('=+$', '')
}

private String buildAuthUrl(String callbackUrl) {
    def challenge = codeChallenge(state.codeVerifier)

    // Core: Connected Vehicle API scopes (always required).
    // Extended scopes (odometer, windows, tyres, warnings, engine, climatization, honk/flash)
    // are requested here; endpoints silently return null if not yet approved.
    def scopeList = [
        "openid",
        "conve:vehicle_relation",
        "conve:fuel_status",
        "conve:lock_status",
        "conve:lock",
        "conve:unlock",
        "conve:command_accessibility",
        "conve:commands",
        "conve:doors_status",
        "conve:odometer_status",
        "conve:windows_status",
        "conve:tyre_status",
        "conve:warnings",
        "conve:engine_start_stop",
        "conve:climatization_start_stop",
        "conve:honk_flash",
        "conve:trip_statistics",
        "conve:environment"
    ]
    if (settings.hasEnergyApi)   scopeList += ["energy:state:read"]
    if (settings.hasLocationApi) scopeList += ["location:read"]
    def scopes = scopeList.join("%20")

    return "https://volvoid.eu.volvocars.com/as/authorization.oauth2" +
        "?response_type=code" +
        "&client_id=${java.net.URLEncoder.encode(settings.clientId, 'UTF-8')}" +
        "&redirect_uri=${java.net.URLEncoder.encode(callbackUrl, 'UTF-8')}" +
        "&scope=${scopes}" +
        "&state=${java.net.URLEncoder.encode(state.oauthState, 'UTF-8')}" +
        "&code_challenge=${challenge}" +
        "&code_challenge_method=S256"
}

def oauthCallback() {
    ifDebug("oauthCallback: params=${params}")
    def code = params.code
    def error = params.error

    if (error) {
        state.authError = "${error}: ${params.error_description ?: 'unknown'}"
        log.error "Volvo OAuth error: ${state.authError}"
        return render(contentType: "text/html", data: "<h2>Authorization failed: ${state.authError}</h2><p>Return to the Hubitat app and try again.</p>")
    }
    // CSRF guard: reject callbacks whose state doesn't match the one we issued.
    if (!params.state || params.state != state.oauthState) {
        state.authError = "State mismatch — ignoring unsolicited callback"
        log.warn "Volvo OAuth: state mismatch (got '${params.state}'), rejecting callback"
        return render(contentType: "text/html", data: "<h2>Authorization rejected: state mismatch.</h2><p>Start the authorization again from the Hubitat app.</p>")
    }
    state.oauthState = null  // single-use
    if (!code) {
        state.authError = "No code received in callback"
        return render(contentType: "text/html", data: "<h2>Authorization failed: no code received.</h2>")
    }

    def callbackUrl = getCallbackUrl()
    def ok = exchangeCode(code, callbackUrl)
    if (ok) {
        discoverVehicles()
        return render(contentType: "text/html",
            data: "<h2>&#10003; Volvo authorization successful!</h2><p>Return to the Hubitat app to select your vehicle and complete setup.</p>")
    } else {
        return render(contentType: "text/html",
            data: "<h2>Token exchange failed.</h2><p>Check Hubitat logs and try again.</p>")
    }
}

private boolean exchangeCode(String code, String redirectUri) {
    def body = "grant_type=authorization_code" +
        "&code=${java.net.URLEncoder.encode(code, 'UTF-8')}" +
        "&redirect_uri=${java.net.URLEncoder.encode(redirectUri, 'UTF-8')}" +
        "&client_id=${java.net.URLEncoder.encode(settings.clientId, 'UTF-8')}" +
        "&code_verifier=${java.net.URLEncoder.encode(state.codeVerifier, 'UTF-8')}"

    if (settings.clientSecret) {
        body += "&client_secret=${java.net.URLEncoder.encode(settings.clientSecret, 'UTF-8')}"
    }

    try {
        def result = [:]
        httpPost([
            uri: "https://volvoid.eu.volvocars.com",
            path: "/as/token.oauth2",
            requestContentType: "application/x-www-form-urlencoded",
            body: body,
            timeout: 30
        ]) { resp ->
            result = resp.data instanceof Map ? resp.data : new JsonSlurper().parseText(resp.data.text)
        }
        storeTokens(result)
        state.codeVerifier = null  // PKCE verifier is single-use
        return true
    } catch (e) {
        log.error "Volvo token exchange failed: ${e}"
        return false
    }
}

private boolean refreshAccessToken() {
    if (!state.volvoRefreshToken) return false
    ifDebug("Refreshing Volvo access token")
    def body = "grant_type=refresh_token" +
        "&refresh_token=${java.net.URLEncoder.encode(state.volvoRefreshToken, 'UTF-8')}" +
        "&client_id=${java.net.URLEncoder.encode(settings.clientId, 'UTF-8')}"

    if (settings.clientSecret) {
        body += "&client_secret=${java.net.URLEncoder.encode(settings.clientSecret, 'UTF-8')}"
    }

    try {
        def result = [:]
        httpPost([
            uri: "https://volvoid.eu.volvocars.com",
            path: "/as/token.oauth2",
            requestContentType: "application/x-www-form-urlencoded",
            body: body,
            timeout: 30
        ]) { resp ->
            result = resp.data instanceof Map ? resp.data : new JsonSlurper().parseText(resp.data.text)
        }
        storeTokens(result)
        return true
    } catch (e) {
        log.error "Volvo token refresh failed: ${e}"
        state.volvoAccessToken = null
        return false
    }
}

private void storeTokens(Map result) {
    if (!result?.access_token) { log.error "Volvo: no access_token in response: ${result}"; return }
    state.volvoAccessToken  = result.access_token
    state.volvoTokenExpiry  = now() + ((result.expires_in ?: 1800) * 1000L) - 60000L
    if (result.refresh_token) state.volvoRefreshToken = result.refresh_token
    ifDebug("Volvo tokens stored, expires in ${result.expires_in ?: '?'} seconds")
}

private String getVolvoToken() {
    if (state.volvoAccessToken && state.volvoTokenExpiry && now() < state.volvoTokenExpiry) {
        return state.volvoAccessToken
    }
    if (!refreshAccessToken()) {
        log.error "Volvo: could not obtain a valid access token"
        return null
    }
    return state.volvoAccessToken
}

// =================== Volvo API ===================

private Map volvoGet(String path) {
    def token = getVolvoToken()
    if (!token) return null
    def result = [:]
    try {
        httpGet([
            uri: "https://api.volvocars.com",
            path: path,
            headers: [
                "Authorization": "Bearer ${token}",
                "vcc-api-key"  : settings.vccApiKey,
                "Accept"       : "application/json"
            ],
            timeout: 30
        ]) { resp ->
            result = resp.data instanceof Map ? resp.data : new JsonSlurper().parseText(resp.data.text)
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        // 403 on new endpoints is expected while Volvo API approval is pending
        if (e.statusCode == 403) {
            log.warn "Volvo GET ${path} not yet approved (403) — will work after Volvo API approval"
        } else {
            log.error "Volvo GET ${path} failed (${e.statusCode}): ${e.message}"
        }
        return null
    } catch (e) {
        log.error "Volvo GET ${path} error: ${e}"
        return null
    }
    return result
}

private Map volvoPost(String path, Map body = [:]) {
    def token = getVolvoToken()
    if (!token) return null
    def result = [:]
    try {
        httpPost([
            uri: "https://api.volvocars.com",
            path: path,
            requestContentType: "application/json",
            headers: [
                "Authorization": "Bearer ${token}",
                "vcc-api-key"  : settings.vccApiKey,
                "Accept"       : "application/json"
            ],
            body: JsonOutput.toJson(body),
            timeout: 30
        ]) { resp ->
            result = resp.data instanceof Map ? resp.data : new JsonSlurper().parseText(resp.data.text)
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        // 403 on commands is expected while Volvo API approval is pending
        if (e.statusCode == 403) {
            log.warn "Volvo POST ${path} not yet approved (403) — will work after Volvo API approval"
        } else {
            log.error "Volvo POST ${path} failed (${e.statusCode}): ${e.message}"
        }
        return null
    } catch (e) {
        log.error "Volvo POST ${path} error: ${e}"
        return null
    }
    return result
}

// =================== Vehicle Discovery ===================

private void discoverVehicles() {
    def resp = volvoGet("/connected-vehicle/v2/vehicles")
    if (!resp?.data) { state.discoveryError = "No vehicles found or API error. Check logs."; return }
    def found = [:]
    resp.data.each { v ->
        def vin = v.vin
        if (vin) found[vin] = vin
    }
    if (!found) { state.discoveryError = "No vehicles found in your account."; return }
    state.discoveredVehicles = found
    state.discoveryError = null
    ifDebug("Discovered vehicles: ${found}")
}

// =================== Lifecycle ===================

def installed() { updated() }

def uninstalled() {
    unschedule()
    getAllChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

def updated() {
    unschedule()
    if (settings.isDebug) runIn(3600, logsOff)

    if (state.volvoRefreshToken && settings.selectedVin) {
        ensureChildDevice(settings.selectedVin)
        runIn(10, pollVehicle)
        def cron = pollCron(settings.pollInterval ?: "15")
        schedule(cron, pollVehicle)
    }
}

private String pollCron(String minutes) {
    switch (minutes) {
        case "5":  return "0 0/5 * * * ?"
        case "10": return "0 0/10 * * * ?"
        case "15": return "0 0/15 * * * ?"
        case "30": return "0 0/30 * * * ?"
        case "60": return "0 0 * * * ?"
        default:   return "0 0/15 * * * ?"
    }
}

private void ensureChildDevice(String vin) {
    def d = getChildDevice(vin)
    if (!d) {
        log.info "Volvo: creating child device for VIN ${vin}"
        try {
            addChildDevice("brianwilson-hubitat", "Volvo Vehicle Driver", vin, null,
                [name: "Volvo ${vin}", label: "Volvo ${vin}", completedSetup: true])
        } catch (e) {
            log.error "Volvo: failed to create child device: ${e.message}. Ensure 'Volvo Vehicle Driver' is installed under Drivers Code."
        }
    }
}

// =================== Polling ===================

def pollVehicle() {
    def vin = settings.selectedVin
    if (!vin) return
    ensureChildDevice(vin)
    def d = getChildDevice(vin)
    if (!d) return

    // Fetch doors (also contains centralLock)
    def doorsResp = volvoGet("/connected-vehicle/v2/vehicles/${vin}/doors")
    updateLock(d,    doorsResp)
    updateDoors(d,   doorsResp)

    updateFuel(d,         volvoGet("/connected-vehicle/v2/vehicles/${vin}/fuel"))
    updateWindows(d,      volvoGet("/connected-vehicle/v2/vehicles/${vin}/windows"))
    updateTyres(d,        volvoGet("/connected-vehicle/v2/vehicles/${vin}/tyres"))
    updateOdometer(d,     volvoGet("/connected-vehicle/v2/vehicles/${vin}/odometer"))
    updateWarnings(d,     volvoGet("/connected-vehicle/v2/vehicles/${vin}/warnings"))
    updateEngineStatus(d, volvoGet("/connected-vehicle/v2/vehicles/${vin}/engine-status"))

    if (settings.hasEnergyApi)   updateEnergy(d,   volvoGet("/energy/v2/vehicles/${vin}/state"))
    if (settings.hasLocationApi) updateLocation(d, volvoGet("/location/v1/vehicles/${vin}/location"))

    // Fetch vehicle info once (model, year, fuelType, etc.)
    if (!state.vehicleInfoFetched) {
        updateVehicleInfo(d, volvoGet("/connected-vehicle/v2/vehicles/${vin}"))
    }

    d.sendEvent(name: "lastRefresh", value: new Date().format("MM/dd/yyyy HH:mm:ss", location.timeZone))
}

private void updateLock(def d, Map resp) {
    def lock = resp?.data?.centralLock?.value
    if (lock == null) { ifDebug("lock: no data"); return }
    def status = lock == "LOCKED" ? "locked" : "unlocked"
    d.sendEvent(name: "lock", value: status)
    ifDebug("lock: ${status}")
}

private void updateDoors(def d, Map resp) {
    def data = resp?.data
    if (!data) { ifDebug("doors: no data"); return }
    def doorMap = [
        doorFrontLeft : data.frontLeft?.value,
        doorFrontRight: data.frontRight?.value,
        doorRearLeft  : data.rearLeft?.value,
        doorRearRight : data.rearRight?.value,
        hood          : data.hood?.value,
        tailgate      : data.tailgate?.value,
        tankLid       : data.tankLid?.value
    ]
    doorMap.each { attr, val ->
        if (val != null) d.sendEvent(name: attr, value: val)
    }
    ifDebug("doors: ${doorMap}")
}

private void updateWindows(def d, Map resp) {
    def data = resp?.data
    if (!data) { ifDebug("windows: no data"); return }
    def winMap = [
        windowFrontLeft : data.frontLeft?.value,
        windowFrontRight: data.frontRight?.value,
        windowRearLeft  : data.rearLeft?.value,
        windowRearRight : data.rearRight?.value,
        sunroof         : data.sunroof?.value
    ]
    winMap.each { attr, val ->
        if (val != null) d.sendEvent(name: attr, value: val)
    }
    ifDebug("windows: ${winMap}")
}

private void updateTyres(def d, Map resp) {
    def data = resp?.data
    if (!data) { ifDebug("tyres: no data"); return }
    def tyreMap = [
        tyreFrontLeft : data.frontLeft?.value,
        tyreFrontRight: data.frontRight?.value,
        tyreRearLeft  : data.rearLeft?.value,
        tyreRearRight : data.rearRight?.value
    ]
    tyreMap.each { attr, val ->
        if (val != null) d.sendEvent(name: attr, value: val)
    }
    ifDebug("tyres: ${tyreMap}")
}

private void updateOdometer(def d, Map resp) {
    def data = resp?.data ?: resp
    if (!data) { ifDebug("odometer: no data"); return }
    def raw = data.odometer?.value
    if (raw == null) { ifDebug("odometer: field missing"); return }
    def apiUnit = (data.odometer?.unit ?: "km").toLowerCase()
    def val = toInt(raw)
    def displayUnit = apiUnit
    if (apiUnit == "km" && settings.useImperial) {
        val = Math.round(val * 0.621371) as Integer
        displayUnit = "mi"
    }
    d.sendEvent(name: "odometer", value: val, unit: displayUnit)
    ifDebug("odometer: ${val} ${unit}")
}

private void updateWarnings(def d, Map resp) {
    def data = resp?.data
    if (!data) { ifDebug("warnings: no data"); return }
    def warnMap = [
        warningBrakeLight : data.brakeLightCenterWarning?.value ?: data.brakeLightLeftWarning?.value ?: data.brakeLightRightWarning?.value,
        warningFuelLow    : data.fuelLevelWarning?.value,
        warningEngineLight: data.engineWarning?.value,
        warningOilLow     : data.oilLevelWarning?.value,
        warningWasherFluid: data.washerFluidLevelWarning?.value,
        warningBrakeFluid : data.brakeFluidWarning?.value
    ]
    warnMap.each { attr, val ->
        if (val != null) d.sendEvent(name: attr, value: val)
    }
    ifDebug("warnings: ${warnMap}")
}

private void updateEngineStatus(def d, Map resp) {
    def data = resp?.data ?: resp
    if (!data) { ifDebug("engineStatus: no data"); return }
    def status = data.engineStatus?.value ?: data.status?.value
    if (status != null) d.sendEvent(name: "engineStatus", value: status)
    ifDebug("engineStatus: ${status}")
}

private void updateVehicleInfo(def d, Map resp) {
    def data = resp?.data ?: resp
    if (!data) { ifDebug("vehicleInfo: no data"); return }
    def infoMap = [
        vehicleModel : data.descriptions?.model ?: data.model,
        modelYear    : data.modelYear?.toString(),
        fuelType     : data.fuelType,
        externalColor: data.externalColour ?: data.externalColor,
        gearbox      : data.gearbox
    ]
    infoMap.each { attr, val ->
        if (val != null) d.sendEvent(name: attr, value: val.toString())
    }
    if (infoMap.any { it.value != null }) state.vehicleInfoFetched = true
    ifDebug("vehicleInfo: ${infoMap}")
}

private void updateFuel(def d, Map resp) {
    def data = resp?.data ?: resp
    if (!data) { ifDebug("fuel: no data"); return }
    def level = data.fuelAmountLevel?.value
    def range = data.distanceToEmptyTank?.value
    if (level != null) d.sendEvent(name: "fuelLevel", value: toInt(level), unit: "%")
    if (range != null) {
        def apiUnit = (data.distanceToEmptyTank?.unit ?: "km").toLowerCase()
        def val = toInt(range)
        def displayUnit = apiUnit
        if (apiUnit == "km" && settings.useImperial) {
            val = Math.round(val * 0.621371) as Integer
            displayUnit = "mi"
        }
        d.sendEvent(name: "fuelRange", value: val, unit: displayUnit)
    }
    ifDebug("fuel: level=${level}% range=${range}")
}

private void updateEnergy(def d, Map resp) {
    // Energy v2 returns fields at the top level (no "data" wrapper)
    def data = resp?.data ?: resp
    if (!data) { ifDebug("energy: no data"); return }
    def level      = data.batteryChargeLevel?.value
    def range      = data.electricRange?.value ?: data.distanceToEmptyBattery?.value
    // v2 API uses chargingStatus; v1 used chargingSystemStatus — try both
    def status     = data.chargingSystemStatus?.value ?: data.chargingStatus?.value
    def connStatus = data.chargingConnectionStatus?.value ?: data.connectionStatus?.value
    def estMins    = data.estimatedChargingTime?.value
    def target     = data.targetBatteryChargeLevel?.value
    if (level == null && range == null) {
        ifDebug("energy: unrecognized response: ${resp}")
        return
    }
    if (level != null) d.sendEvent(name: "battery", value: toInt(level), unit: "%")
    if (range != null) {
        def apiUnit = (data.electricRange?.unit ?: data.distanceToEmptyBattery?.unit ?: "km").toLowerCase()
        def val = toInt(range)
        def displayUnit = apiUnit
        // Only convert if API is giving km and user wants miles
        if (apiUnit == "km" && settings.useImperial) {
            val = Math.round(val * 0.621371) as Integer
            displayUnit = "mi"
        }
        d.sendEvent(name: "batteryRange", value: val, unit: displayUnit)
    }
    if (status     != null) d.sendEvent(name: "chargingStatus",     value: status)
    if (connStatus != null) d.sendEvent(name: "chargingConnection", value: connStatus)
    if (target     != null) d.sendEvent(name: "chargeLimit",        value: toInt(target), unit: "%")
    ifDebug("energy: battery=${level}% range=${range} status=${status} conn=${connStatus} target=${target} estMins=${estMins}")

    if (settings.notifyDevices) {
        evaluateChargingNotifications(d.deviceNetworkId, toInt(level), status, connStatus, toInt(target), estMins)
    }
}

// Volvo numeric values can arrive as "50", "50.0", or numbers — coerce safely.
private Integer toInt(def v) {
    if (v == null) return null
    try { return Math.round(v.toString().toDouble()) as Integer }
    catch (e) { return null }
}

private void updateLocation(def d, Map resp) {
    // Location API returns a GeoJSON Feature, possibly wrapped in "data"
    def feature = resp?.data ?: resp
    def coords = feature?.geometry?.coordinates
    if (!coords || coords.size() < 2) { ifDebug("location: no data (${resp})"); return }
    def lon = coords[0]
    def lat = coords[1]
    d.sendEvent(name: "latitude",     value: lat.toString())
    d.sendEvent(name: "longitude",    value: lon.toString())
    d.sendEvent(name: "lastLocation", value: "${lat}, ${lon}")
    def heading = feature?.properties?.heading
    if (heading != null) d.sendEvent(name: "heading", value: heading.toString())
    def ts = feature?.properties?.timestamp
    if (ts != null) d.sendEvent(name: "locationTimestamp", value: ts.toString())
    ifDebug("location: lat=${lat} lon=${lon} heading=${heading}")
}

// =================== Notifications ===================
//
// Change-driven only — a poll never triggers a notification by itself.
// First poll after install records baseline silently.

private void evaluateChargingNotifications(String vin, Integer level, String status, String connStatus, Integer target, def estMins) {
    if (!settings.notifyDevices) return

    def ns   = state.notifyState ?: [:]
    def prev = ns[vin]
    boolean firstPoll = (prev == null)
    if (firstPoll) prev = [:]

    def msgs = []

    if (!firstPoll) {
        def prevStatus = prev.chargingStatus
        def isCharging    = status?.toUpperCase() == "CHARGING"
        def wasCharging   = prevStatus?.toUpperCase() == "CHARGING"
        def isComplete    = status?.toUpperCase() in ["FULLY_CHARGED", "DONE"]
        def wasComplete   = prevStatus?.toUpperCase() in ["FULLY_CHARGED", "DONE"]
        // Still physically connected but stopped charging = charge limit reached
        def stillConnected = connStatus != null && !connStatus.toUpperCase().contains("DISCONNECTED")

        // Charging started
        if (isCharging && !wasCharging && settings.notifyChargingStarted != false) {
            def msg = "Volvo charging started. Battery at ${level}%"
            if (target != null) msg += ", charging to ${target}%"
            if (estMins != null && estMins > 0) msg += ". Estimated finish: ${finishTime(estMins as Integer)}"
            else msg += "."
            msgs << msg
        }

        // Fully charged (100%)
        if (isComplete && !wasComplete && settings.notifyChargingComplete != false) {
            msgs << "Volvo fully charged. Battery at ${level}%."
        }

        // Was charging, now stopped — distinguish charge limit reached vs unplugged
        if (!isCharging && wasCharging && !isComplete && settings.notifyChargingStopped != false) {
            if (stillConnected) {
                def msg = "Volvo charge limit reached. Battery at ${level}%"
                if (target != null) msg += " (limit: ${target}%)"
                msg += "."
                msgs << msg
            } else {
                msgs << "Volvo charging stopped (disconnected). Battery at ${level}%."
            }
        }

        // 10% battery step increments while charging
        if (isCharging && settings.notifyBatterySteps && level != null) {
            def prevBand = prev.batteryBand ?: -1
            def curBand  = (int)(level / 10) * 10
            if (curBand > prevBand && curBand > 0) {
                msgs << "Volvo battery at ${curBand}% while charging."
            }
        }
    }

    msgs.each { sendVolvoNotification(it) }

    ns[vin] = [
        chargingStatus : status,
        batteryBand    : level != null ? ((int)(level / 10) * 10) : prev.batteryBand
    ]
    state.notifyState = ns
}

private void sendVolvoNotification(String msg) {
    ifDebug("Notification: ${msg}")
    settings.notifyDevices?.each { it.deviceNotification(msg) }
}

// Format minutes-remaining as a clock time (e.g. "2:45 AM")
private String finishTime(int minutes) {
    def finish = new Date(now() + minutes * 60000L)
    return finish.format("h:mm a", location.timeZone)
}

// =================== Commands from Driver ===================

def lockVehicle(String vin) {
    ifDebug("lockVehicle: ${vin}")
    def resp = volvoPost("/connected-vehicle/v2/vehicles/${vin}/commands/lock")
    def status = resp?.data?.invokeStatus ?: resp?.status
    if (status in ["COMPLETED", "DELIVERED"]) {
        getChildDevice(vin)?.sendEvent(name: "lock", value: "locked")
        return true
    }
    log.warn "Volvo lock command returned status: ${status}"
    return false
}

def unlockVehicle(String vin) {
    ifDebug("unlockVehicle: ${vin}")
    def resp = volvoPost("/connected-vehicle/v2/vehicles/${vin}/commands/unlock")
    def status = resp?.data?.invokeStatus ?: resp?.status
    if (status in ["COMPLETED", "DELIVERED"]) {
        getChildDevice(vin)?.sendEvent(name: "lock", value: "unlocked")
        return true
    }
    log.warn "Volvo unlock command returned status: ${status}"
    return false
}

def startEngineVehicle(String vin, def minutes) {
    ifDebug("startEngineVehicle: ${vin} for ${minutes} min")
    def runtime = (minutes != null) ? (toInt(minutes) ?: 15) : 15
    runtime = Math.min(Math.max(runtime, 1), 15)  // clamp 1–15
    def resp = volvoPost("/connected-vehicle/v2/vehicles/${vin}/commands/engine-start",
                         [runtimeMinutes: runtime])
    if (resp == null) return false  // 403 or network error already logged
    def status = resp?.data?.invokeStatus ?: resp?.status
    if (status in ["COMPLETED", "DELIVERED"]) {
        getChildDevice(vin)?.sendEvent(name: "engineStatus", value: "RUNNING")
        return true
    }
    log.warn "Volvo engine-start returned unexpected status: ${status}"
    return false
}

def stopEngineVehicle(String vin) {
    ifDebug("stopEngineVehicle: ${vin}")
    def resp = volvoPost("/connected-vehicle/v2/vehicles/${vin}/commands/engine-stop")
    if (resp == null) return false
    def status = resp?.data?.invokeStatus ?: resp?.status
    if (status in ["COMPLETED", "DELIVERED"]) {
        getChildDevice(vin)?.sendEvent(name: "engineStatus", value: "STOPPED")
        return true
    }
    log.warn "Volvo engine-stop returned unexpected status: ${status}"
    return false
}

def startClimatizationVehicle(String vin) {
    ifDebug("startClimatizationVehicle: ${vin}")
    def resp = volvoPost("/connected-vehicle/v2/vehicles/${vin}/commands/climatization-start")
    if (resp == null) return false
    def status = resp?.data?.invokeStatus ?: resp?.status
    if (!(status in ["COMPLETED", "DELIVERED"])) {
        log.warn "Volvo climatization-start returned unexpected status: ${status}"
        return false
    }
    return true
}

def stopClimatizationVehicle(String vin) {
    ifDebug("stopClimatizationVehicle: ${vin}")
    def resp = volvoPost("/connected-vehicle/v2/vehicles/${vin}/commands/climatization-stop")
    if (resp == null) return false
    def status = resp?.data?.invokeStatus ?: resp?.status
    if (!(status in ["COMPLETED", "DELIVERED"])) {
        log.warn "Volvo climatization-stop returned unexpected status: ${status}"
        return false
    }
    return true
}

def honkVehicle(String vin) {
    ifDebug("honkVehicle: ${vin}")
    def resp = volvoPost("/connected-vehicle/v2/vehicles/${vin}/commands/honk")
    if (resp == null) return
    def status = resp?.data?.invokeStatus ?: resp?.status
    if (!(status in ["COMPLETED", "DELIVERED"])) log.warn "Volvo honk returned unexpected status: ${status}"
}

def flashVehicle(String vin) {
    ifDebug("flashVehicle: ${vin}")
    def resp = volvoPost("/connected-vehicle/v2/vehicles/${vin}/commands/flash")
    if (resp == null) return
    def status = resp?.data?.invokeStatus ?: resp?.status
    if (!(status in ["COMPLETED", "DELIVERED"])) log.warn "Volvo flash returned unexpected status: ${status}"
}

def honkFlashVehicle(String vin) {
    ifDebug("honkFlashVehicle: ${vin}")
    def resp = volvoPost("/connected-vehicle/v2/vehicles/${vin}/commands/honk-and-flash")
    if (resp == null) return
    def status = resp?.data?.invokeStatus ?: resp?.status
    if (!(status in ["COMPLETED", "DELIVERED"])) log.warn "Volvo honk-and-flash returned unexpected status: ${status}"
}

def refreshVehicle(String vin) {
    runIn(1, pollVehicle)
}

// =================== Utility ===================

def logsOff() {
    log.warn "Volvo Connect: debug logging disabled"
    app.updateSetting("isDebug", [value: "false", type: "bool"])
}

private void ifDebug(String msg) {
    if (settings.isDebug) log.debug "Volvo Connect: ${msg}"
}
