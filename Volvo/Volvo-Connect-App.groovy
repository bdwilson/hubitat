/**
 * Volvo Connect App
 *
 * 1.0.0 - Brian Wilson / bubba@bubba.org
 *
 * Native Hubitat integration for Volvo vehicles via the Volvo Connected Vehicle API.
 * Supports lock/unlock, fuel/battery level, range, and GPS location.
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
    description: "Volvo Connected Vehicle integration — lock, fuel/battery, range, location",
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
                      "<b>Connected Vehicle API</b> is always included (lock/unlock, fuel, doors)."
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

    // Core: Connected Vehicle API scopes (always required)
    def scopeList = [
        "openid",
        "conve:vehicle_relation",
        "conve:fuel_status",
        "conve:lock_status",
        "conve:lock",
        "conve:unlock",
        "conve:command_accessibility",
        "conve:doors_status"
    ]
    // Optional: Energy API — only if user has subscribed.
    // Real scope names per the Volvo developer portal.
    if (settings.hasEnergyApi) {
        scopeList += ["energy:state:read"]
    }
    // Optional: Location API — only if user has subscribed
    if (settings.hasLocationApi) {
        scopeList += ["location:read"]
    }
    def scopes = scopeList.join("%20")

    return "https://volvoid.eu.volvocars.com/as/authorization.oauth2" +
        "?response_type=code" +
        "&client_id=${java.net.URLEncoder.encode(settings.clientId, 'UTF-8')}" +
        "&redirect_uri=${java.net.URLEncoder.encode(callbackUrl, 'UTF-8')}" +
        "&scope=${scopes}" +
        "&state=${java.net.URLEncoder.encode(state.oauthState, 'UTF-8')}" +
        "&response_mode=pi.flow" +
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
        log.error "Volvo GET ${path} failed (${e.statusCode}): ${e.message}"
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
        log.error "Volvo POST ${path} failed (${e.statusCode}): ${e.message}"
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

    updateLock(d,   volvoGet("/connected-vehicle/v2/vehicles/${vin}/doors"))
    updateFuel(d,   volvoGet("/connected-vehicle/v2/vehicles/${vin}/fuel"))
    if (settings.hasEnergyApi)   updateEnergy(d,   volvoGet("/energy/v1/vehicles/${vin}/recharge-status"))
    if (settings.hasLocationApi) updateLocation(d, volvoGet("/location/v1/vehicles/${vin}/location"))

    d.sendEvent(name: "lastRefresh", value: new Date().format("MM/dd/yyyy HH:mm:ss", location.timeZone))
}

private void updateLock(def d, Map resp) {
    def lock = resp?.data?.centralLock?.value
    if (lock == null) { ifDebug("lock: no data"); return }
    def status = lock == "LOCKED" ? "locked" : "unlocked"
    d.sendEvent(name: "lock", value: status)
    ifDebug("lock: ${status}")
}

private void updateFuel(def d, Map resp) {
    def data = resp?.data
    if (!data) { ifDebug("fuel: no data"); return }
    def level = data.fuelAmountLevel?.value
    def range = data.distanceToEmptyTank?.value
    def unit  = data.distanceToEmptyTank?.unit ?: "km"
    if (level != null) d.sendEvent(name: "fuelLevel", value: level as Integer, unit: "%")
    if (range != null) d.sendEvent(name: "fuelRange",  value: range as Integer, unit: unit)
    ifDebug("fuel: level=${level}% range=${range}${unit}")
}

private void updateEnergy(def d, Map resp) {
    def data = resp?.data
    if (!data) { ifDebug("energy: no data"); return }
    def level  = data.batteryChargeLevel?.value
    def range  = data.electricRange?.value ?: data.distanceToEmptyBattery?.value
    def unit   = data.electricRange?.unit ?: data.distanceToEmptyBattery?.unit ?: "km"
    def status = data.chargingStatus?.value
    if (level  != null) d.sendEvent(name: "battery",        value: level as Integer, unit: "%")
    if (range  != null) d.sendEvent(name: "batteryRange",   value: range as Integer, unit: unit)
    if (status != null) d.sendEvent(name: "chargingStatus", value: status)
    ifDebug("energy: battery=${level}% range=${range}${unit} status=${status}")
}

private void updateLocation(def d, Map resp) {
    def coords = resp?.data?.geometry?.coordinates
    if (!coords || coords.size() < 2) { ifDebug("location: no data"); return }
    def lon = coords[0]
    def lat = coords[1]
    d.sendEvent(name: "latitude",     value: lat.toString())
    d.sendEvent(name: "longitude",    value: lon.toString())
    d.sendEvent(name: "lastLocation", value: "${lat}, ${lon}")
    ifDebug("location: lat=${lat} lon=${lon}")
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
