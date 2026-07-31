/**
 * Wyze Vacuum Connect App
 *
 * 1.0.0 - Brian Wilson / bubba@bubba.org
 *
 * Native Hubitat integration for the Wyze Robot Vacuum (e.g. 200S / JA_RO2).
 *
 * Wyze has no official public API for this device, so this app speaks the same
 * private/reverse-engineered app API used by the open-source wyze-sdk (Python),
 * homebridge-wyze-robovac, and matterbridge-wyze-robovac projects. That means:
 *   - It can break without notice if Wyze changes their backend.
 *   - It is not affiliated with or supported by Wyze Labs in any way.
 *   - You need a personal API key/key ID pair from developer-api-console.wyze.com
 *     (free) in addition to your normal Wyze account email/password.
 *
 * Setup:
 *  1. Create a key at https://developer-api-console.wyze.com/#/apikey/view
 *     (this gives you a Key Id and an API Key)
 *  2. Install this app and the "Wyze Robot Vacuum Driver", enter your Wyze email,
 *     password, Key Id, and API Key, and click "Log In"
 *  3. If prompted, enter your 2FA verification code
 *  4. Click "Discover Vacuums", select your vacuum(s), set a poll interval, Done
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at:
 *      http://www.apache.org/licenses/LICENSE-2.0
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Field
import java.security.MessageDigest

@Field static final String AUTH_BASE   = "https://auth-prod.api.wyze.com"
@Field static final String API_BASE    = "https://api.wyzecam.com"
@Field static final String VENUS_BASE  = "https://wyze-venus-service-vn.wyzecam.com"
@Field static final String VENUS_APP_ID = "venp_4c30f812828de875"
@Field static final String VENUS_SALT   = "CVCSNoa0ALsNEpgKls6ybVTVOmGzFoiq"
@Field static final String APP_VERSION  = "2.19.14"
@Field static final String WYZE_SC      = "a626948714654991afd3c0dbd7cdb901"
// Long-standing shared "app" key used by the open-source Wyze client ecosystem
// (wyze-sdk, wyze-node, etc.) to reach the auth-prod login endpoint. It is not a
// secret tied to any individual account. Wyze can rotate/revoke it at any time.
@Field static final String WYZE_X_API_KEY = "RckMFKbsds5p6QY3COEXc2ABwNTYY0q18ziEiSEm"
@Field static final String VACUUM_PRODUCT_MODEL = "JA_RO2"

definition(
    name: "Wyze Vacuum Connect",
    namespace: "brianwilson-hubitat",
    author: "bubba@bubba.org",
    description: "Native integration for Wyze Robot Vacuums (unofficial API)",
    category: "My Apps",
    importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/master/Wyze-Vacuum/Wyze-Vacuum-App.groovy",
    iconUrl: "",
    iconX2Url: "",
    singleInstance: true
)

preferences {
    page(name: "mainPage")
}

// =================== Page ===================

def mainPage() {
    def loggedIn = state.wyzeAccessToken != null
    def canInstall = loggedIn && settings.selectedVacuums

    return dynamicPage(name: "mainPage", install: canInstall, uninstall: true) {
        section("<b>Step 1 — Wyze Developer API Key</b>") {
            paragraph "Create a free key at <a href='https://developer-api-console.wyze.com/#/apikey/view' target='_blank'>developer-api-console.wyze.com</a> " +
                      "and enter the Key Id / API Key it gives you below."
            input "wyzeKeyId",  "text", title: "Key Id",  required: true, submitOnChange: true
            input "wyzeApiKey", "text", title: "API Key", required: true, submitOnChange: true
        }

        section("<b>Step 2 — Wyze Account</b>") {
            input "wyzeEmail",    "text",     title: "Wyze Email",    required: true, submitOnChange: true
            input "wyzePassword", "password", title: "Wyze Password", required: true, submitOnChange: true
            input "btnLogin", "button", title: loggedIn ? "Re-login" : "Log In", width: 3

            if (state.wyzeLoginError) paragraph "<font color='red'>${state.wyzeLoginError}</font>"

            if (state.wyzeMfa) {
                paragraph "<b>Two-factor authentication required.</b> Enter the code Wyze sent/your authenticator app shows."
                input "mfaCode", "text", title: "Verification Code", required: true, submitOnChange: true
                input "btnSubmitMfa", "button", title: "Submit Code", width: 3
            } else if (loggedIn) {
                paragraph "<font color='green'>&#10003; Logged in to Wyze</font>"
            }
        }

        if (loggedIn) {
            section("<b>Step 3 — Devices</b>") {
                input "btnDiscover", "button", title: "Discover Vacuums", width: 3
                if (state.discoveryError) paragraph "<font color='red'>${state.discoveryError}</font>"
                if (state.discoveredVacuums) {
                    input "selectedVacuums", "enum",
                        title: "Select Vacuum(s)",
                        options: state.discoveredVacuums,
                        multiple: true, required: true, submitOnChange: true
                }
            }

            if (settings.selectedVacuums) {
                section("<b>Polling</b>") {
                    input "pollInterval", "enum",
                        title: "Poll Interval",
                        options: ["1": "Every 1 min", "5": "Every 5 min", "10": "Every 10 min", "15": "Every 15 min", "30": "Every 30 min"],
                        defaultValue: "5", required: true
                }

                section("<b>Options</b>") {
                    input "isDebug", "bool", title: "Enable Debug Logging", defaultValue: false, submitOnChange: true
                }
            }
        }
    }
}

def appButtonHandler(btn) {
    if (btn == "btnLogin") {
        state.wyzeLoginError = null
        state.wyzeMfa = null
        loginWyze()
    } else if (btn == "btnSubmitMfa") {
        submitMfaCode()
    } else if (btn == "btnDiscover") {
        state.discoveryError = null
        discoverVacuums()
    }
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

    if (state.wyzeAccessToken && settings.selectedVacuums) {
        settings.selectedVacuums.each { mac -> ensureChildDevice(mac) }
        // remove child devices for macs the user deselected
        getAllChildDevices().each { d ->
            if (!(d.deviceNetworkId in settings.selectedVacuums)) deleteChildDevice(d.deviceNetworkId)
        }
        runIn(5, pollAllVacuums)
        schedule(pollCron(settings.pollInterval ?: "5"), pollAllVacuums)
    }
}

private String pollCron(String minutes) {
    switch (minutes) {
        case "1":  return "0 * * * * ?"
        case "5":  return "0 0/5 * * * ?"
        case "10": return "0 0/10 * * * ?"
        case "15": return "0 0/15 * * * ?"
        case "30": return "0 0/30 * * * ?"
        default:   return "0 0/5 * * * ?"
    }
}

private void ensureChildDevice(String mac) {
    def d = getChildDevice(mac)
    if (!d) {
        def label = state.discoveredVacuums?.get(mac) ?: "Wyze Vacuum ${mac}"
        log.info "Wyze Vacuum: creating child device for ${mac}"
        try {
            addChildDevice("brianwilson-hubitat", "Wyze Robot Vacuum Driver", mac, null,
                [name: "Wyze Vacuum", label: label, completedSetup: true])
        } catch (e) {
            log.error "Wyze Vacuum: failed to create child device: ${e.message}. Ensure 'Wyze Robot Vacuum Driver' is installed under Drivers Code."
        }
    }
}

// =================== Login / Auth ===================

private void loginWyze() {
    if (!state.wyzePhoneId) state.wyzePhoneId = java.util.UUID.randomUUID().toString()
    if (!settings.wyzeEmail || !settings.wyzePassword || !settings.wyzeKeyId || !settings.wyzeApiKey) {
        state.wyzeLoginError = "Enter Key Id, API Key, email, and password first."
        return
    }

    def hashedPw = md5Hex(md5Hex(md5Hex(settings.wyzePassword)))
    state.wyzePendingHashedPassword = hashedPw

    def nonce = now()
    def body = JsonOutput.toJson([nonce: "${nonce}", email: settings.wyzeEmail, password: hashedPw])

    Map result = null
    try {
        httpPost([
            uri: AUTH_BASE,
            path: "/api/user/login",
            requestContentType: "application/json",
            headers: [
                "x-api-key": WYZE_X_API_KEY,
                "keyid"    : settings.wyzeKeyId,
                "apikey"   : settings.wyzeApiKey,
                "user-agent": "hubitat-wyze-vacuum/1.0",
                "Accept-Encoding": "gzip"
            ],
            body: body,
            timeout: 30
        ]) { resp -> result = resp.data instanceof Map ? resp.data : new JsonSlurper().parseText(resp.data.text) }
    } catch (groovyx.net.http.HttpResponseException e) {
        state.wyzeLoginError = "Login failed (${e.statusCode}): ${e.message}"
        log.error "Wyze login failed: ${e.statusCode} ${e.message}"
        return
    } catch (e) {
        state.wyzeLoginError = "Login failed: ${e.message}"
        log.error "Wyze login error: ${e}"
        return
    }

    handleLoginResult(result)
}

private void handleLoginResult(Map result) {
    if (result?.access_token) {
        state.wyzeAccessToken  = result.access_token
        state.wyzeRefreshToken = result.refresh_token
        state.wyzeUserId       = result.user_id
        state.wyzeMfa = null
        state.wyzeLoginError = null
        ifDebug("Wyze login successful")
        discoverVacuums()
        return
    }

    def mfaOptions = result?.mfa_options
    if (mfaOptions && "TotpVerificationCode" in mfaOptions) {
        state.wyzeMfa = [
            type: "TotpVerificationCode",
            verificationId: result.mfa_details?.totp_apps?.getAt(0)?.app_id
        ]
        ifDebug("Wyze login requires TOTP 2FA")
        return
    }
    if (mfaOptions && "PrimaryPhone" in mfaOptions) {
        def smsResp = authPost("/user/login/sendSmsCode", [
            mfaPhoneType: "Primary",
            sessionId: result.sms_session_id,
            userId: result.user_id
        ])
        state.wyzeMfa = [
            type: "PrimaryPhone",
            verificationId: smsResp?.session_id
        ]
        ifDebug("Wyze login requires SMS 2FA")
        return
    }

    state.wyzeLoginError = "Login failed: ${result?.msg ?: result}"
    log.error "Wyze login failed: ${result}"
}

private void submitMfaCode() {
    if (!state.wyzeMfa || !settings.mfaCode) return
    def payload = [
        email: settings.wyzeEmail,
        password: state.wyzePendingHashedPassword,
        mfa_type: state.wyzeMfa.type,
        verification_id: state.wyzeMfa.verificationId,
        verification_code: settings.mfaCode
    ]
    def result = authPost("/user/login", payload)
    handleLoginResult(result)
}

private Map authPost(String path, Map body) {
    Map result = null
    try {
        httpPost([
            uri: AUTH_BASE,
            path: path,
            requestContentType: "application/json",
            headers: ["x-api-key": WYZE_X_API_KEY, "Accept-Encoding": "gzip"],
            body: JsonOutput.toJson(body),
            timeout: 30
        ]) { resp -> result = resp.data instanceof Map ? resp.data : new JsonSlurper().parseText(resp.data.text) }
    } catch (e) {
        log.error "Wyze auth POST ${path} failed: ${e}"
        return null
    }
    return result
}

private boolean refreshWyzeToken() {
    if (!state.wyzeRefreshToken) return false
    ifDebug("Refreshing Wyze access token")
    def resp = apiWyzeRequest("/app/user/refresh_token", [refresh_token: state.wyzeRefreshToken, sv: "d91914dd28b7492ab9dd17f7707d35a3"], false)
    def data = resp?.data ?: resp
    if (data?.access_token) {
        state.wyzeAccessToken = data.access_token
        if (data.refresh_token) state.wyzeRefreshToken = data.refresh_token
        ifDebug("Wyze token refresh succeeded")
        return true
    }
    log.error "Wyze token refresh failed: ${resp}"
    return false
}

// =================== Device Discovery ===================

private void discoverVacuums() {
    def resp = apiWyzeRequest("/app/v2/home_page/get_object_list", [sv: "c417b62d72ee44bf933054bdca183e77"])
    def list = resp?.data?.device_list
    if (list == null) {
        state.discoveryError = "Could not retrieve device list. Check credentials and logs."
        return
    }
    def found = [:]
    list.each { dev ->
        if (dev.product_model == VACUUM_PRODUCT_MODEL) {
            found[dev.mac] = dev.nickname ?: dev.mac
        }
    }
    if (!found) {
        state.discoveryError = "No Wyze vacuums found on this account."
        return
    }
    state.discoveredVacuums = found
    state.discoveryError = null
    ifDebug("Discovered vacuums: ${found}")
}

// =================== Polling ===================

def pollAllVacuums() {
    settings.selectedVacuums?.each { mac -> pollVacuum(mac) }
}

def pollVacuum(String mac) {
    def d = getChildDevice(mac)
    if (!d) return

    def keys = ["battary", "mode", "cleanlevel", "chargeState", "cleanSize", "cleanTime", "fault_code", "fault_type"]
    def propsResp = venusRequest("GET", "/plugin/venus/get_iot_prop", [did: mac, keys: keys.join(",")])
    def props = propsResp?.data?.props
    if (props != null) {
        if (props.battary != null)    d.sendEvent(name: "battery", value: toInt(props.battary), unit: "%")
        if (props.mode != null)       d.sendEvent(name: "mode", value: vacuumModeDescription(props.mode))
        if (props.cleanlevel != null) d.sendEvent(name: "suctionLevel", value: suctionLevelName(props.cleanlevel))
        if (props.chargeState != null) d.sendEvent(name: "charging", value: (toInt(props.chargeState) == 1) ? "true" : "false")
        if (props.cleanSize != null)  d.sendEvent(name: "cleanSize", value: toInt(props.cleanSize))
        if (props.cleanTime != null)  d.sendEvent(name: "cleanTime", value: toInt(props.cleanTime))
        d.sendEvent(name: "fault", value: (props.fault_code && toInt(props.fault_code) != 0) ? "${props.fault_type ?: props.fault_code}" : "none")
    } else {
        ifDebug("pollVacuum(${mac}): no props returned")
    }

    def statusResp = venusRequest("GET", "/plugin/venus/${mac}/status", [:])
    def workStatus = statusResp?.data?.heartBeat?.vacuum_work_status ?: statusResp?.data?.eventFlag?.vacuum_work_status
    if (workStatus != null) d.sendEvent(name: "status", value: vacuumStatusDescription(workStatus))

    d.sendEvent(name: "lastRefresh", value: new Date().format("MM/dd/yyyy HH:mm:ss", location.timeZone))
}

private String vacuumStatusDescription(def code) {
    def map = [1: "Standby", 2: "Cleaning", 3: "Returning to charge", 4: "Docked", 5: "Mapping", 6: "Paused", 7: "Error"]
    return map[toInt(code)] ?: "Unknown (${code})"
}

private String suctionLevelName(def code) {
    def map = [1: "Quiet", 2: "Standard", 3: "Strong"]
    return map[toInt(code)] ?: "Unknown (${code})"
}

private String vacuumModeDescription(def code) {
    def c = toInt(code)
    if (c in [1, 30, 1101, 1201, 1301, 1401])   return "Cleaning"
    if (c in [4, 31, 1102, 1202, 1302, 1402])   return "Paused"
    if (c in [10, 32, 1103, 1203, 1303, 1403])  return "Cleaning completed, returning to charge"
    if (c == 5)                                 return "Returning to charge"
    if (c in [11, 33, 1104, 1204, 1304, 1404])  return "Docked, cleaning will resume"
    if (c in [0, 14, 29, 35, 40])                return "Idle"
    return "Mode ${c}"
}

// =================== Commands from Driver ===================

def startVacuum(String mac) {
    ifDebug("startVacuum: ${mac}")
    venusControl(mac, 0, 1) // GLOBAL_SWEEPING / START
    pollVacuum(mac)
}

def pauseVacuum(String mac) {
    ifDebug("pauseVacuum: ${mac}")
    venusControl(mac, 0, 2) // GLOBAL_SWEEPING / PAUSE
    pollVacuum(mac)
}

def dockVacuum(String mac) {
    ifDebug("dockVacuum: ${mac}")
    venusControl(mac, 3, 1) // RETURN_TO_CHARGING / START
    pollVacuum(mac)
}

def setVacuumSuctionLevel(String mac, String level) {
    ifDebug("setVacuumSuctionLevel: ${mac} -> ${level}")
    def code = [Quiet: 1, Standard: 2, Strong: 3][level] ?: 2
    def body = [did: mac, model: VACUUM_PRODUCT_MODEL, cmd: "set_preference", params: [[ctrltype: 1, value: code]], is_sub_device: 0]
    venusRequest("POST", "/plugin/venus/set_iot_action", [:], body)
    pollVacuum(mac)
}

def refreshVacuum(String mac) {
    pollVacuum(mac)
}

private void venusControl(String mac, int type, int value) {
    def body = [type: type, value: value, vacuumMopMode: 0]
    def resp = venusRequest("POST", "/plugin/venus/${mac}/control", [:], body)
    if (resp != null && resp.code != "1") log.warn "Wyze Vacuum control (${mac}) returned: ${resp}"
}

// =================== Wyze API — signed Venus (vacuum) calls ===================

private Map venusRequest(String method, String path, Map query = [:], Map bodyMap = null, boolean retry = true) {
    if (!state.wyzeAccessToken) return null

    def nonce = now()
    def requestId = md5Hex(md5Hex(nonce.toString()))
    def signingKey = md5Hex("${state.wyzeAccessToken}${VENUS_SALT}")
    def headers = [
        "access_token": state.wyzeAccessToken,
        "requestid"   : requestId,
        "appid"       : VENUS_APP_ID,
        "appinfo"     : "wyze_android_${APP_VERSION}",
        "phoneid"     : state.wyzePhoneId,
        "User-Agent"  : "wyze_android_${APP_VERSION}",
        "Accept-Encoding": "gzip"
    ]

    Map result = null
    try {
        if (method == "GET") {
            def qp = new TreeMap()
            query.each { k, v -> qp[k] = v?.toString() }
            qp["nonce"] = nonce.toString()
            def sigString = qp.collect { k, v -> "${k}=${v}" }.join("&")
            headers["signature2"] = hmacMd5Hex(signingKey, sigString)

            httpGet([
                uri: VENUS_BASE, path: path, query: qp, headers: headers, timeout: 20
            ]) { resp -> result = resp.data instanceof Map ? resp.data : new JsonSlurper().parseText(resp.data.text) }
        } else {
            def payload = new LinkedHashMap(bodyMap ?: [:])
            payload["nonce"] = nonce.toString()
            def bodyJson = JsonOutput.toJson(payload)
            headers["signature2"] = hmacMd5Hex(signingKey, bodyJson)

            httpPost([
                uri: VENUS_BASE, path: path, requestContentType: "application/json",
                headers: headers, body: bodyJson, timeout: 20
            ]) { resp -> result = resp.data instanceof Map ? resp.data : new JsonSlurper().parseText(resp.data.text) }
        }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (retry && e.statusCode in [401, 403] && refreshWyzeToken()) {
            return venusRequest(method, path, query, bodyMap, false)
        }
        log.error "Wyze Venus ${method} ${path} failed (${e.statusCode}): ${e.message}"
        return null
    } catch (e) {
        log.error "Wyze Venus ${method} ${path} error: ${e}"
        return null
    }
    return result
}

// =================== Wyze API — unsigned general (api.wyzecam.com) calls ===================

private Map apiWyzeRequest(String path, Map extraBody = [:], boolean retry = true) {
    if (!state.wyzeAccessToken) return null

    def payload = new LinkedHashMap(extraBody)
    payload["access_token"] = state.wyzeAccessToken
    payload["app_name"] = "com.hualai"
    payload["app_ver"] = "com.hualai___${APP_VERSION}"
    payload["app_version"] = APP_VERSION
    payload["phone_id"] = state.wyzePhoneId
    payload["phone_system_type"] = "2"
    payload["sc"] = WYZE_SC
    payload["ts"] = now()

    Map result = null
    try {
        httpPost([
            uri: API_BASE, path: path, requestContentType: "application/json",
            headers: ["Connection": "keep-alive"], body: JsonOutput.toJson(payload), timeout: 20
        ]) { resp -> result = resp.data instanceof Map ? resp.data : new JsonSlurper().parseText(resp.data.text) }
    } catch (groovyx.net.http.HttpResponseException e) {
        if (retry && e.statusCode in [401, 403] && refreshWyzeToken()) {
            return apiWyzeRequest(path, extraBody, false)
        }
        log.error "Wyze API ${path} failed (${e.statusCode}): ${e.message}"
        return null
    } catch (e) {
        log.error "Wyze API ${path} error: ${e}"
        return null
    }
    return result
}

// =================== Crypto helpers ===================

private String md5Hex(String s) {
    return MessageDigest.getInstance("MD5").digest(s.getBytes("UTF-8")).encodeHex().toString()
}

// HMAC-MD5 implemented directly against MessageDigest (no javax.crypto dependency)
private String hmacMd5Hex(String keyHexString, String message) {
    int blockSize = 64
    byte[] key = keyHexString.getBytes("UTF-8")
    if (key.length > blockSize) key = MessageDigest.getInstance("MD5").digest(key)

    byte[] paddedKey = new byte[blockSize]
    for (int i = 0; i < key.length; i++) paddedKey[i] = key[i]  // rest defaults to 0 — zero-padding for free

    byte[] oKeyPad = new byte[blockSize]
    byte[] iKeyPad = new byte[blockSize]
    for (int i = 0; i < blockSize; i++) {
        oKeyPad[i] = (byte) (paddedKey[i] ^ (byte) 0x5c)
        iKeyPad[i] = (byte) (paddedKey[i] ^ (byte) 0x36)
    }

    def md = MessageDigest.getInstance("MD5")
    md.update(iKeyPad)
    md.update(message.getBytes("UTF-8"))
    byte[] innerHash = md.digest()

    md = MessageDigest.getInstance("MD5")
    md.update(oKeyPad)
    md.update(innerHash)
    return md.digest().encodeHex().toString()
}

// =================== Utility ===================

private Integer toInt(def v) {
    if (v == null) return null
    try { return Math.round(v.toString().toDouble()) as Integer }
    catch (e) { return null }
}

def logsOff() {
    log.warn "Wyze Vacuum Connect: debug logging disabled"
    app.updateSetting("isDebug", [value: "false", type: "bool"])
}

private void ifDebug(String msg) {
    if (settings.isDebug) log.debug "Wyze Vacuum Connect: ${msg}"
}
