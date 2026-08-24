/**
 * WaterGuru Integration App
 *
 * 2.4.0 - Brian Wilson / bubba@bubba.org
 *
 * Native Hubitat integration — no external Python/Flask server required.
 * Authenticates directly with AWS Cognito (SRP flow) and calls the
 * WaterGuru Lambda function using AWS Signature V4.
 *
 * Features:
 *  - Discover available WaterGuru devices from the app UI
 *  - Select which device(s) to create child devices for
 *  - Configurable poll interval (1–12 hours)
 *  - Change-driven notifications (2.2.0): chemistry range alerts on new
 *    samples, status transitions, low cassette/battery thresholds, quiet
 *    hours, recovery alerts. Polling alone never triggers a notification.
 *  - Initial-state summary (2.2.1): first poll for a device emits a one-time
 *    summary of any pre-existing non-GREEN / out-of-range / low-threshold
 *    conditions, then arms the state machine so the same state does not
 *    re-alert on subsequent polls. A "Send Current State Summary" button
 *    re-triggers this for already-baselined devices.
 *  - First-poll digest (2.2.2): if "Send a digest for every new sample" is
 *    enabled, the initial-state summary also includes the current readings
 *    (pH, free chlorine, temperature, flow).
 *  - Stale-data alert filter (2.2.3): WaterGuru reports "measurement
 *    outdated" YELLOW alerts for old (e.g. years-stale) Total Alkalinity,
 *    Calcium Hardness, etc. readings even when current pH/chlorine are
 *    fine. These are informational and not actionable, so they are skipped
 *    when deciding whether to notify (a toggle controls this; default on).
 *    If they would be the only thing keeping Status from being GREEN, the
 *    notification engine treats the device as GREEN. Device attributes
 *    (Status, statusMsg) still reflect the raw WaterGuru state.
 *  - Import URL fix (2.2.4): corrected importUrl in both app and driver to
 *    point to the bdwilson/hubitat repository on master.
 *  - Full chemistry panel (2.3.0): parse and surface every reading WaterGuru
 *    returns — Total Alkalinity, Calcium Hardness, Cyanuric Acid, Salt,
 *    Phosphates, Copper, Iron, Saturation Index, Total Hardness — each with a
 *    per-reading RED/YELLOW/GREEN status and effective target, plus the lab-
 *    results timestamp so lab-sourced (Salt/Phosphates/Copper/Iron) readings
 *    are identifiable as stale. Also surfaces pool volume (sizeGallons),
 *    WaterGuru's own "add X of chemical Y" advice (doseAdvice), and the
 *    equipment/product config (chlorine + acid type and strengths, filter,
 *    surface, cover) — address and other PII deliberately excluded. Additive:
 *    existing attributes are unchanged.
 *    Also: (a) persist the Cognito refresh token after an SRP login so polls
 *    use REFRESH_TOKEN_AUTH instead of a full handshake every time (the
 *    refresh path was previously unreachable); (b) convert water temperature
 *    to the hub's °C/°F scale rather than always emitting the raw °F value.
 *  - Cassette type (2.4.0): derive and surface the installed cassette model
 *    (cassetteType: C2 / C5 / unknown) plus an optional human summary
 *    (cassetteInfo). WaterGuru's API never prints the model, so it is derived
 *    from whether Total Alkalinity / Calcium Hardness / Cyanuric Acid are
 *    freshly sampled (only a C5 measures those) or the pod carries a LAB
 *    lab-pad pack. Additive: existing attributes are unchanged.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

definition(
    name: "WaterGuru Integration",
    namespace: "brianwilson-hubitat",
    author: "bubba@bubba.org",
    description: "WaterGuru Integration App",
    category: "My Apps",
    importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/refs/heads/master/WaterGuru/WaterGuru-Integration.groovy",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    singleInstance: true
)

preferences {
    page(name: "mainPage")
}

// =================== UI ===================

def mainPage() {
    def discovered = state.discoveredDevices ?: [:]

    return dynamicPage(name: "mainPage", install: true, uninstall: true) {
        section("<b>WaterGuru Credentials</b>") {
            paragraph "Enter your WaterGuru account credentials, then click Discover to find your devices."
            input "wgUser", "email",    title: "WaterGuru Email",    description: "your@email.com", required: true,  submitOnChange: true
            input "wgPass", "password", title: "WaterGuru Password", required: true,                submitOnChange: true
        }

        section("<b>Device Discovery</b>") {
            if (settings.wgUser && settings.wgPass) {
                input "btnDiscover", "button", title: "Discover WaterGuru Devices", width: 4
                if (state.discoveryError) paragraph "<font color='red'>${state.discoveryError}</font>"
            } else {
                paragraph "Enter credentials above, then click Discover."
            }

            if (discovered) {
                paragraph "Found ${discovered.size()} device(s). Select the ones you want to monitor:"
                input "selectedDevices", "enum",
                    title: "WaterGuru Devices to Monitor",
                    options: discovered, multiple: true, required: false, submitOnChange: true
            } else if (settings.wgUser && settings.wgPass) {
                paragraph "<i>No devices discovered yet. Click the button above.</i>"
            }
        }

        if (settings.selectedDevices) {
            section("<b>Polling Schedule</b>") {
                paragraph "WaterGuru devices only sample a few times per day, so polling more often than every few hours provides no additional data. Each poll performs a full authentication with AWS Cognito."
                input "pollInterval", "enum",
                    title: "Poll Interval",
                    options: ["1": "Every 1 hour", "2": "Every 2 hours", "3": "Every 3 hours",
                              "4": "Every 4 hours", "6": "Every 6 hours", "8": "Every 8 hours",
                              "12": "Every 12 hours"],
                    defaultValue: "6", required: true, submitOnChange: true
                input "days", "enum",
                    title: "Only poll on these days (leave blank for every day)",
                    description: "All days",
                    required: false, multiple: true,
                    options: ["Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"]
            }

            section("<b>Notifications</b>") {
                paragraph "Notifications are change-driven: a poll by itself never sends anything. " +
                          "Chemistry is only evaluated when a <i>new sample</i> arrives (the measurement timestamp changes). " +
                          "Temperature and flow are included in messages as context but never trigger one."
                input "notifyDevices", "capability.notification",
                    title: "Send notifications to", multiple: true, required: false, submitOnChange: true
                if (settings.notifyDevices) {
                    input "btnTestNotify",    "button", title: "Send Test Notification",     width: 4
                    input "btnResendSummary", "button", title: "Send Current State Summary", width: 4
                    paragraph "<i>Test sends a plain ping. Summary clears the per-device baseline and runs a poll, so any current non-GREEN or out-of-range conditions are re-announced.</i>"
                    input "phMin", "decimal", title: "pH low alert threshold",            defaultValue: 7.2, required: false, width: 6
                    input "phMax", "decimal", title: "pH high alert threshold",           defaultValue: 7.8, required: false, width: 6
                    input "clMin", "decimal", title: "Free chlorine low alert (ppm)",     defaultValue: 1.0, required: false, width: 6
                    input "clMax", "decimal", title: "Free chlorine high alert (ppm)",    defaultValue: 3.0, required: false, width: 6
                    input "checksLeftThreshold", "number", title: "Cassette checks-left warning at or below", defaultValue: 10, required: false, width: 6
                    input "batteryThreshold",    "number", title: "Battery % warning at or below",            defaultValue: 20, required: false, width: 6
                    input "notifyOnNewSample",   "bool",   title: "Send a digest for every new sample (pH, chlorine, temp, flow)", defaultValue: false, required: false
                    input "notifyRecovery",      "bool",   title: "Also notify when conditions return to normal",                 defaultValue: false, required: false
                    input "suppressStaleAlerts", "bool",   title: "Ignore 'measurement outdated' alerts (Total Alkalinity, Salt, Cyanuric Acid, etc.)", defaultValue: true, required: false
                    paragraph "<i>Stale-data alerts are still shown in the device's Status Msg attribute, but won't trigger notifications. If a non-GREEN status is caused only by stale-data alerts, the notification engine treats it as GREEN.</i>"
                    input "quietStart", "time", title: "Quiet hours start (optional)", required: false, width: 6, submitOnChange: true
                    input "quietEnd",   "time", title: "Quiet hours end",              required: settings.quietStart != null, width: 6
                    paragraph "<i>Alerts during quiet hours are held and delivered when quiet hours end.</i>"
                    input "notificationsPaused", "bool", title: "Pause all notifications", defaultValue: false, required: false
                }
            }

            section("<b>Options</b>") {
                input "isDebug",     "bool", title: "Enable Debug Logging",                                       required: false, defaultValue: false, submitOnChange: true
                input "forceUpdate", "bool", title: "Force all state variables to be updated even if unchanged.", required: false, defaultValue: false, submitOnChange: true
            }
        }
    }
}

def appButtonHandler(btn) {
    if (btn == "btnDiscover") {
        state.discoveryError = null
        discoverDevicesForUI()
    } else if (btn == "btnTestNotify") {
        // Bypasses pause and quiet hours so routing can always be verified
        settings.notifyDevices?.each { it.deviceNotification("WaterGuru test notification from Hubitat") }
    } else if (btn == "btnResendSummary") {
        // Clear the per-device notification baseline; the next poll will treat
        // each device as first-poll and emit the initial-state summary.
        state.notifyState = [:]
        log.info "WaterGuru: notification baseline cleared; running poll to re-announce current state"
        runIn(2, discoverChildDevices)
    }
}

private discoverDevicesForUI() {
    ifDebug("discoverDevicesForUI called")
    try {
        def dashboard = getWaterGuruDashboard()
        if (!dashboard?.waterBodies) {
            state.discoveryError = "No devices found in your WaterGuru account."
            return
        }
        def found = [:]
        dashboard.waterBodies.each { item ->
            found[item.waterBodyId.toString()] = item.name ?: "Pool ${item.waterBodyId}"
        }
        state.discoveredDevices = found
        ifDebug("Discovered ${found.size()} device(s): ${found}")
    } catch (e) {
        state.discoveryError = "Discovery failed: ${e.message}"
        log.error "WaterGuru discovery error: ${e}"
    }
}

// =================== Lifecycle ===================

def installed()   { updated() }

def uninstalled() {
    unschedule()
    getAllChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

def updated() {
    unschedule()

    def selected = settings.selectedDevices ?: []
    getAllChildDevices().each { child ->
        if (!selected.contains(child.deviceNetworkId)) {
            log.debug "WaterGuru: removing deselected device ${child.displayName} (${child.deviceNetworkId})"
            deleteChildDevice(child.deviceNetworkId)
            if (state.notifyState) state.notifyState.remove(child.deviceNetworkId)
        }
    }

    if (selected) {
        runIn(5, discoverChildDevices)
        schedule(pollIntervalCron(pollInterval ?: "6"), timedRefresh)
    }

    if (settings.isDebug) runIn(3600, logsOff)
}

// Called by child driver's refresh() via parent.updateStatus()
def updateStatus() { discoverChildDevices() }

def timedRefresh() {
    if (isDayMatch()) updateStatus()
}

private boolean isDayMatch() {
    if (!settings.days) return true
    def df = new java.text.SimpleDateFormat("EEEE")
    df.setTimeZone(location.timeZone)
    return settings.days.contains(df.format(new Date()))
}

// Cron fires at top-of-hour every N hours; "1" needs special form since */1 == *
private String pollIntervalCron(String hours) {
    def h = (hours in ["1","2","3","4","6","8","12"]) ? hours : "6"
    return h == "1" ? "0 0 * * * ?" : "0 0 */${h} * * ?"
}

// =================== AWS / WaterGuru Constants ===================

private String wgRegion()       { "us-west-2" }
private String wgPoolId()       { "us-west-2_icsnuWQWw" }
private String wgClientId()     { "7pk5du7fitqb419oabb3r92lni" }
private String wgIdentityPool() { "us-west-2:691e3287-5776-40f2-a502-759de65a8f1c" }
private String wgIdpPool()      { "cognito-idp.${wgRegion()}.amazonaws.com/${wgPoolId()}" }

private String srpNHex() {
    "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
    "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
    "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
    "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
    "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
    "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
    "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
    "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
    "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
    "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
    "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64" +
    "ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
    "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6B" +
    "F12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
    "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB31" +
    "43DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF"
}

private BigInteger srpN() { new BigInteger(srpNHex(), 16) }
private BigInteger srpG() { BigInteger.valueOf(2L) }

// =================== SRP Helpers ===================

private String padHexStr(String h) {
    h = h.toLowerCase()
    if (h.length() % 2 == 1) return "0" + h
    if (Integer.parseInt(h.substring(0, 2), 16) > 127) return "00" + h
    return h
}

private byte[] hexToBytes(String hex) {
    int len = hex.length()
    byte[] out = new byte[len / 2]
    for (int i = 0; i < len; i += 2)
        out[(int)(i / 2)] = (byte)((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16))
    return out
}

private String bytesToHex(byte[] bytes) {
    def sb = new StringBuilder()
    for (byte b : bytes) sb.append(String.format("%02x", b))
    return sb.toString()
}

private byte[] sha256(byte[] data) {
    java.security.MessageDigest.getInstance("SHA-256").digest(data)
}

private String sha256Hex(String s) {
    bytesToHex(sha256(s.getBytes("UTF-8")))
}

private byte[] hmacSHA256(byte[] key, byte[] data) {
    def mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data)
}

// HKDF-SHA256 single-block, 16-byte output — "Caldera Derived Key"
private byte[] computeHkdf(byte[] ikm, byte[] salt) {
    def mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(new javax.crypto.spec.SecretKeySpec(salt.length > 0 ? salt : new byte[32], "HmacSHA256"))
    def prk = mac.doFinal(ikm)
    mac.init(new javax.crypto.spec.SecretKeySpec(prk, "HmacSHA256"))
    mac.update("Caldera Derived Key".getBytes("UTF-8"))
    mac.update((byte) 1)
    def okm = mac.doFinal()
    def result = new byte[16]
    for (int i = 0; i < 16; i++) result[i] = okm[i]
    return result
}

// k = H( 00 || N || 02 )
private BigInteger srpK() {
    new BigInteger(bytesToHex(sha256(hexToBytes("00" + srpNHex() + "02"))), 16)
}

// Math.random() loop — SecureRandom is not permitted in the Hubitat sandbox
private BigInteger srpRandomBigInt(int numBytes) {
    def bytes = new byte[numBytes]
    for (int i = 0; i < numBytes; i++)
        bytes[i] = (byte)(Math.random() * 256 - 128)
    return new BigInteger(1, bytes)
}

private Map srpGenerateA() {
    def N = srpN()
    def a = srpRandomBigInt(128).mod(N)
    def A = srpG().modPow(a, N)
    return [smallA: a, largeA: A]
}

private byte[] srpPasswordKey(String username, String password, BigInteger srpB, BigInteger salt,
                               BigInteger smallA, BigInteger largeA) {
    def N = srpN()
    def g = srpG()
    def uHex = bytesToHex(sha256(hexToBytes(padHexStr(largeA.toString(16)) + padHexStr(srpB.toString(16)))))
    def u = new BigInteger(uHex, 16)
    if (u == BigInteger.ZERO) throw new Exception("SRP u value is zero")
    def poolName     = wgPoolId().split("_")[1]
    def userPassHash = bytesToHex(sha256("${poolName}${username}:${password}".getBytes("UTF-8")))
    def x    = new BigInteger(bytesToHex(sha256(hexToBytes(padHexStr(salt.toString(16)) + userPassHash))), 16)
    def k    = srpK()
    def gx   = g.modPow(x, N)
    def diff = srpB.subtract(k.multiply(gx).mod(N)).mod(N)
    def S    = diff.modPow(smallA.add(u.multiply(x)), N)
    return computeHkdf(hexToBytes(padHexStr(S.toString(16))), hexToBytes(padHexStr(u.toString(16))))
}

private String srpTimestamp() {
    def sdf = new java.text.SimpleDateFormat("EEE MMM d HH:mm:ss z yyyy", Locale.US)
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    return sdf.format(new Date())
}

// =================== Cognito REST Calls ===================

// Shared HTTP POST for both Cognito IDP and Identity endpoints
private Map cognitoPost(String uri, String target, Map bodyMap) {
    def result = [:]
    httpPost([
        uri: uri, requestContentType: "application/json",
        headers: ["X-Amz-Target": target, "Content-Type": "application/x-amz-json-1.1"],
        body: JsonOutput.toJson(bodyMap), timeout: 30
    ]) { resp -> result = new JsonSlurper().parseText(resp.data.text) }
    return result
}

private Map cognitoIdpPost(String target, Map bodyMap) {
    cognitoPost("https://cognito-idp.${wgRegion()}.amazonaws.com/", target, bodyMap)
}

private Map cognitoIdentityPost(String target, Map bodyMap) {
    cognitoPost("https://cognito-identity.${wgRegion()}.amazonaws.com/", target, bodyMap)
}

// =================== Auth: Full SRP ===================

private Map srpAuthenticate() {
    def srp     = srpGenerateA()
    def srpAHex = srp.largeA.toString(16)

    ifDebug("Initiating Cognito SRP auth for ${settings.wgUser}")
    def initResp = cognitoIdpPost(
        "AWSCognitoIdentityProviderService.InitiateAuth",
        [AuthFlow: "USER_SRP_AUTH", ClientId: wgClientId(),
         AuthParameters: [USERNAME: settings.wgUser, SRP_A: srpAHex]]
    )
    if (initResp?.ChallengeName != "PASSWORD_VERIFIER") {
        log.error "WaterGuru: unexpected auth challenge: ${initResp}"
        return null
    }

    def cp             = initResp.ChallengeParameters
    def userIdForSrp   = cp.USER_ID_FOR_SRP
    def srpB           = new BigInteger(cp.SRP_B, 16)
    def salt           = new BigInteger(cp.SALT, 16)
    def secretBlockB64 = cp.SECRET_BLOCK
    def timestamp      = srpTimestamp()
    def hkdf           = srpPasswordKey(userIdForSrp, settings.wgPass, srpB, salt, srp.smallA, srp.largeA)

    // msg = poolName || userIdForSrp || secretBlockBytes || timestamp  (raw bytes, no separators)
    def secretBlockBytes = secretBlockB64.decodeBase64()
    def poolName = wgPoolId().split("_")[1]
    def pnb  = poolName.getBytes("UTF-8")
    def uidb = userIdForSrp.getBytes("UTF-8")
    def tsb  = timestamp.getBytes("UTF-8")
    def msg  = new byte[pnb.length + uidb.length + secretBlockBytes.length + tsb.length]
    def off  = 0
    for (int i = 0; i < pnb.length;              i++) msg[off++] = pnb[i]
    for (int i = 0; i < uidb.length;             i++) msg[off++] = uidb[i]
    for (int i = 0; i < secretBlockBytes.length;  i++) msg[off++] = secretBlockBytes[i]
    for (int i = 0; i < tsb.length;              i++) msg[off++] = tsb[i]

    def signatureB64 = hmacSHA256(hkdf, msg).encodeBase64().toString()

    ifDebug("Responding to PASSWORD_VERIFIER challenge")
    def authResp = cognitoIdpPost(
        "AWSCognitoIdentityProviderService.RespondToAuthChallenge",
        [ClientId: wgClientId(), ChallengeName: "PASSWORD_VERIFIER",
         ChallengeResponses: [
             USERNAME: userIdForSrp,
             PASSWORD_CLAIM_SECRET_BLOCK: secretBlockB64,
             PASSWORD_CLAIM_SIGNATURE: signatureB64,
             TIMESTAMP: timestamp
         ]]
    )
    if (!authResp?.AuthenticationResult) {
        log.error "WaterGuru: auth challenge failed: ${authResp}"
        return null
    }

    return authResp.AuthenticationResult
}

// =================== Auth: Refresh Token ===================

private Map refreshAuthenticate() {
    ifDebug("Attempting refresh token auth")
    try {
        def resp = cognitoIdpPost(
            "AWSCognitoIdentityProviderService.InitiateAuth",
            [AuthFlow: "REFRESH_TOKEN_AUTH", ClientId: wgClientId(),
             AuthParameters: [REFRESH_TOKEN: state.wgRefreshToken]]
        )
        if (resp?.AuthenticationResult) {
            ifDebug("Refresh token auth succeeded")
            return resp.AuthenticationResult
        }
        return null
    } catch (groovyx.net.http.HttpResponseException e) {
        // Log the exact Cognito error body so we know whether REFRESH_TOKEN_AUTH is unsupported
        def body = ""
        try { body = e.response?.data?.text } catch (ignore) {}
        if (!body) try { body = e.response?.data?.toString() } catch (ignore) {}
        log.warn "WaterGuru: refresh token rejected (${e.statusCode}) — ${body ?: e.message}"
        // Disable refresh attempts for this session; this client may not allow REFRESH_TOKEN_AUTH
        state.wgRefreshTokenSupported = false
        return null
    }
}

// Use refresh token if supported and < 25 days old, fall back to full SRP otherwise
private Map getAuthTokens() {
    long twentyFiveDays = 25L * 24 * 60 * 60 * 1000
    if (state.wgRefreshTokenSupported != false &&
        state.wgRefreshToken && state.wgRefreshTokenTime &&
        (now() - state.wgRefreshTokenTime) < twentyFiveDays) {
        def ar = refreshAuthenticate()
        if (ar) return ar
        state.wgRefreshToken     = null
        state.wgRefreshTokenTime = null
    }
    // Full SRP login. Persist the refresh token so subsequent polls can use
    // the (much cheaper) REFRESH_TOKEN_AUTH path above instead of a fresh SRP
    // handshake every time. Without this the refresh branch is unreachable —
    // state.wgRefreshToken is never set — and every poll does a full login.
    // A refresh response omits RefreshToken, so only overwrite when present.
    def ar = srpAuthenticate()
    if (ar?.RefreshToken) {
        state.wgRefreshToken     = ar.RefreshToken
        state.wgRefreshTokenTime = now()
    }
    return ar
}

// =================== AWS Signature V4 ===================

private byte[] aws4SigningKey(String secretKey, String dateStamp) {
    def kDate    = hmacSHA256(("AWS4" + secretKey).getBytes("UTF-8"), dateStamp.getBytes("UTF-8"))
    def kRegion  = hmacSHA256(kDate,    wgRegion().getBytes("UTF-8"))
    def kService = hmacSHA256(kRegion,  "lambda".getBytes("UTF-8"))
    return hmacSHA256(kService, "aws4_request".getBytes("UTF-8"))
}

private Map aws4SignLambda(String body, String accessKeyId, String secretKey, String sessionToken) {
    def region   = wgRegion()
    def now      = new Date()
    def dateSdf  = new java.text.SimpleDateFormat("yyyyMMdd")
    dateSdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    def timeSdf  = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'")
    timeSdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    def dateStamp = dateSdf.format(now)
    def amzDate   = timeSdf.format(now)

    def host     = "lambda.${region}.amazonaws.com"
    def path     = "/2015-03-31/functions/prod-getDashboardView/invocations"
    def bodyHash = sha256Hex(body)
    def signedHeaders = "content-type;host;x-amz-date;x-amz-security-token"
    def canonHeaders  =
        "content-type:application/x-amz-json-1.0\n" +
        "host:${host}\n" +
        "x-amz-date:${amzDate}\n" +
        "x-amz-security-token:${sessionToken}\n"

    def canonRequest = "POST\n${path}\n\n${canonHeaders}\n${signedHeaders}\n${bodyHash}"
    def credScope    = "${dateStamp}/${region}/lambda/aws4_request"
    def stringToSign = "AWS4-HMAC-SHA256\n${amzDate}\n${credScope}\n${sha256Hex(canonRequest)}"
    def sig          = bytesToHex(hmacSHA256(aws4SigningKey(secretKey, dateStamp), stringToSign.getBytes("UTF-8")))
    def authHdr      = "AWS4-HMAC-SHA256 Credential=${accessKeyId}/${credScope}, SignedHeaders=${signedHeaders}, Signature=${sig}"

    return [
        "Authorization"        : authHdr,
        "Content-Type"         : "application/x-amz-json-1.0",
        "X-Amz-Date"           : amzDate,
        "X-Amz-Security-Token" : sessionToken
    ]
}

// =================== Core API Call (shared by discovery and polling) ===================

private Map getWaterGuruDashboard() {
    def ar = getAuthTokens()
    if (!ar) return null

    ifDebug("Fetching userId from Cognito GetUser")
    def userResp = cognitoIdpPost(
        "AWSCognitoIdentityProviderService.GetUser", [AccessToken: ar.AccessToken]
    )
    def userId = userResp?.Username
    if (!userId) { log.error "WaterGuru: could not retrieve userId"; return null }
    ifDebug("userId: ${userId}")

    ifDebug("Getting Cognito Identity ID")
    def idResp = cognitoIdentityPost(
        "AWSCognitoIdentityService.GetId", [IdentityPoolId: wgIdentityPool()]
    )
    def identityId = idResp?.IdentityId
    if (!identityId) { log.error "WaterGuru: could not get IdentityId"; return null }

    ifDebug("Getting temporary AWS credentials")
    def credsResp = cognitoIdentityPost(
        "AWSCognitoIdentityService.GetCredentialsForIdentity",
        [IdentityId: identityId, Logins: [(wgIdpPool()): ar.IdToken]]
    )
    def creds = credsResp?.Credentials
    if (!creds) { log.error "WaterGuru: could not get credentials"; return null }

    ifDebug("Calling WaterGuru Lambda for userId: ${userId}")
    return callWaterGuruLambda(userId, creds.AccessKeyId, creds.SecretKey, creds.SessionToken)
}

private Map callWaterGuruLambda(String userId, String accessKeyId, String secretKey, String sessionToken) {
    def host    = "lambda.${wgRegion()}.amazonaws.com"
    def path    = "/2015-03-31/functions/prod-getDashboardView/invocations"
    def body    = JsonOutput.toJson([userId: userId, clientType: "WEB_APP", clientVersion: "0.2.3"])
    def headers = aws4SignLambda(body, accessKeyId, secretKey, sessionToken)
    headers["User-Agent"] = "aws-sdk-iOS/2.24.3 iOS/14.7.1 en_US invoker"

    def result = [:]
    httpPost([uri: "https://${host}", path: path, headers: headers, body: body, timeout: 30]) { resp ->
        // Lambda returns application/json so Hubitat auto-parses resp.data into a Map;
        // fall back to resp.data.text when it comes back as a stream instead.
        def text = resp.data?.text
        result = text ? new JsonSlurper().parseText(text) : resp.data
    }
    return result
}

// =================== Scheduled Polling ===================

def discoverChildDevices() {
    try {
        def dashboard = getWaterGuruDashboard()
        if (!dashboard) return
        processWaterGuruData(dashboard)
    } catch (e) {
        log.error "WaterGuru discoverChildDevices error: ${e}"
    }
}

// Identifies WaterGuru's "measurement outdated" YELLOW alerts so the
// notification engine can ignore them. Device attributes still show the
// full set so dashboards see the real WG state.
private boolean isStaleDataAlert(alert) {
    alert?.text && alert.text.toLowerCase().contains("measurement outdated")
}

// =================== Data Processing ===================

private void processWaterGuruData(def response) {
    def selected = settings.selectedDevices ?: []
    // Always log selection state at info level so misconfiguration is obvious
    def bodyIds = response.waterBodies?.collect { it.waterBodyId?.toString() } ?: []
    log.info "WaterGuru: waterBodies in response: ${bodyIds}"
    log.info "WaterGuru: selectedDevices setting: ${selected}"
    if (!selected) { log.warn "WaterGuru: no devices selected — configure via app UI"; return }

    def date  = new Date().format("MM/dd/yyyy HH:mm:ss")
    def force = settings.forceUpdate  // read once; avoids repeated setting lookups per device

    response.waterBodies.each { item ->
        def id = item.waterBodyId?.toString()
        if (!selected.contains(id)) { ifDebug("Skipping unselected device: ${item.name} (${id})"); return }

        ifDebug("Processing: ${item.name}")
        def name                 = item.name
        // WaterGuru reports water temperature in °F; convert to the hub's
        // configured scale so a °C hub doesn't display an °F magnitude.
        def tempScale            = location.temperatureScale ?: "F"
        def temp                 = item.waterTemp
        if (temp != null && tempScale == "C")
            temp = Math.round(((temp - 32) / 1.8) * 10) / 10.0
        def status               = item.status
        def LastMeasurementHuman = item.latestMeasureTimeHuman
        def LastMeasurement      = item.latestMeasureTime

        // statusMsg for the device attribute uses every alert (dashboards stay accurate)
        def statusMsg = ""
        def count = 0
        item.alerts.each { alert ->
            count++
            statusMsg += "${alert.status} Alert (${count}): ${alert.text}\n"
            ifDebug("alert: ${alert.status} — ${alert.text}")
        }
        if (!statusMsg) statusMsg = "None"

        // Build the actionable view of alerts for the notification engine: drop
        // "measurement outdated" entries unless the user disabled the filter.
        def rawAlerts        = item.alerts ?: []
        def filterStale      = (settings.suppressStaleAlerts != false)
        def actionableAlerts = filterStale ? rawAlerts.findAll { !isStaleDataAlert(it) } : rawAlerts
        if (filterStale && rawAlerts.size() != actionableAlerts.size()) {
            ifDebug("Filtered ${rawAlerts.size() - actionableAlerts.size()} stale-data alert(s) for ${name}")
        }
        // If WG flagged a non-GREEN status but every alert behind it was stale-
        // data, treat the device as effectively GREEN for notification purposes.
        def notifyStatus = (status && status != "GREEN" && actionableAlerts.isEmpty()) ? "GREEN" : status
        // statusMsg for the notification body mirrors actionableAlerts so the
        // detail text in alerts doesn't carry stale-data warnings.
        def notifyStatusMsg = ""
        def nCount = 0
        actionableAlerts.each { alert ->
            nCount++
            notifyStatusMsg += "${alert.status} Alert (${nCount}): ${alert.text}\n"
        }
        if (!notifyStatusMsg) notifyStatusMsg = "None"

        // WaterGuru's own dosing recommendations, surfaced verbatim. Each alert
        // (water-body and per-measurement) may carry advice.action.summary such
        // as "Add 2.4 cups of dry acid"; join them for the doseAdvice attribute.
        def adviceLines = []
        def collectAdvice = { al -> def s = al?.advice?.action?.summary; if (s) adviceLines << s.toString() }
        rawAlerts.each { collectAdvice(it) }
        item.measurements?.each { mm -> mm.alerts?.each { collectAdvice(it) } }
        def doseAdvice = adviceLines ? adviceLines.unique().join("\n") : "None"

        // Index every measurement by WaterGuru type so the whole panel is
        // available, not just the three the app historically surfaced.
        def measByType = [:]
        item.measurements?.each { m -> if (m.type) measByType[m.type] = m }
        def measVal  = { String t -> def m = measByType[t]; m == null ? null : (m.floatValue != null ? m.floatValue : m.intValue) }
        def measStat = { String t -> measByType[t]?.status }
        def measTgt  = { String t -> measByType[t]?.target }

        def freeChlorine = measVal("FREE_CL")
        def pH           = measVal("PH")
        def rate         = measVal("SKIMMER_FLOW")
        ifDebug("measurements: freeChlorine=${freeChlorine} pH=${pH} rate=${rate}")

        // Full chemistry panel (2.1.0): the readings older versions discarded.
        // Salt/Phosphates/Copper/Iron are lab-sourced (see labResultsTime) and
        // may be stale; they are still surfaced so a rule/dashboard can decide.
        def totalAlkalinity     = measVal("TA")
        def calciumHardness     = measVal("CH")
        def cyanuricAcid        = measVal("CYA")
        def saturationIndex     = measVal("SATURATION_INDEX")
        def totalHardness       = measVal("TH")
        def salt                = measVal("SALT")
        def phosphates          = measVal("PHOSPHATES")
        def copper              = measVal("COPPER")
        def iron                = measVal("IRON")
        def labResultsTime      = item.labResultsTime
        def labResultsTimeHuman = item.labResultsTimeHuman

        // Pool volume + equipment/product config from WaterGuru's pool settings
        // (the waterBody sub-object). Address and other PII are intentionally
        // not read. Product fields let a dosing app adopt the user's own products.
        def wbCfg              = item.waterBody ?: [:]
        def poolVolume         = wbCfg.sizeGallons
        def sanitizerType      = item.sanitizerType
        def chlorineType       = wbCfg.userCl
        def chlorineProductPct = wbCfg.userClLiqProductPct
        def calHypoPct         = wbCfg.userCalHypoPct
        def acidType           = wbCfg.userAcid
        def acidMuriaticPct    = wbCfg.userAcidMuriaticPct
        def acidBisulfatePct   = wbCfg.userAcidBisulfatePct
        def equipmentParts     = []
        if (wbCfg.type)       equipmentParts << wbCfg.type.toString().toLowerCase().replace("_", " ")
        if (wbCfg.surface)    equipmentParts << wbCfg.surface.toString().toLowerCase()
        if (wbCfg.filterType) equipmentParts << "${wbCfg.filterType.toString().toLowerCase()} filter"
        if (wbCfg.cover)      equipmentParts << "${wbCfg.cover.toString().toLowerCase()} cover"
        def equipment          = equipmentParts ? equipmentParts.join(" · ") : null

        def index   // reused by the pod / refillable lookups below

        def rssi = item.pods ? item.pods[0]?.rssiInfo?.rssi : null
        def CassetteStatus, batteryStatus, CassettePercent, CassetteTimeLeft, CassetteChecksLeft, batteryPct
        item.pods?.each { pod ->
            index = pod.refillables?.findIndexOf { it.label == "Cassette" }
            if (index != null && index >= 0) {
                CassetteStatus     = pod.refillables[index].status
                CassettePercent    = pod.refillables[index].pctLeft
                CassetteTimeLeft   = pod.refillables[index].timeLeftText
                CassetteChecksLeft = pod.refillables[index].amountLeft
            }
            index = pod.refillables?.findIndexOf { it.label == "Battery" }
            if (index != null && index >= 0) {
                batteryStatus = pod.refillables[index].status
                batteryPct    = pod.refillables[index].pctLeft
            }
        }

        // ---- Cassette type: C2 vs C5 (2.4.0) -----------------------------
        // WaterGuru never prints the cassette model (pod.product is always
        // "SENSE"), but it's derivable. A C5 cassette additionally samples
        // Total Alkalinity, Calcium Hardness and Cyanuric Acid on each run,
        // while a C2 measures only free chlorine + pH; and a C5 pod carries a
        // "LAB" refillable (its lab-pad pack). So a *fresh* TA/CH/CYA reading
        // — one sampled at (close to) the water body's latest measurement,
        // not a years-stale leftover — or a present LAB pad pack means a C5;
        // a pod that reports neither means C2; nothing to go on ⇒ unknown.
        def toEpoch = { s ->
            if (!s) return null
            try { return toDateTime(s.toString())?.time } catch (ignored) { return null }
        }
        def latestMs      = toEpoch(item.latestMeasureTime)
        def freshWindowMs = 2 * 24 * 60 * 60 * 1000L        // within ~2 days of the latest sample
        def chemPresent   = false
        def chemFresh     = false
        ["TA", "CH", "CYA"].each { t ->
            def m = measByType[t]
            if (m == null) return
            chemPresent = true
            def mMs = toEpoch(m.measureTime)
            if (mMs != null && latestMs != null) {
                if (Math.abs(latestMs - mMs) <= freshWindowMs) chemFresh = true
            } else if (!(m.alerts?.any { isStaleDataAlert(it) })) {
                // No per-measurement timestamp available: fall back to "not
                // flagged as an outdated (stale-data) reading".
                chemFresh = true
            }
        }

        // Pod LAB refillable — the C5 lab-pad pack — drives cassetteInfo and
        // is itself a positive C5 signal (a C2 has no lab pads).
        def labPack
        item.pods?.each { pod ->
            def li = pod.refillables?.findIndexOf { it.label == "LAB" || it.unit == "pad" }
            if (li != null && li >= 0) labPack = pod.refillables[li]
        }

        def cassetteType = (chemFresh || labPack != null) ? "C5" : (chemPresent ? "C2" : "unknown")
        def cassetteInfo = null
        if (labPack != null) {
            def parts = [cassetteType]
            def rt = toEpoch(labPack.refillTime)
            if (rt != null) parts << "installed ${new Date(rt).format('MMM d, yyyy')}"
            if (labPack.amountLeft != null && labPack.maxAmount != null)
                parts << "${labPack.amountLeft}/${labPack.maxAmount} pads"
            cassetteInfo = parts.join(" · ")
        }
        ifDebug("cassetteType=${cassetteType} cassetteInfo=${cassetteInfo}")

        def d = getChildDevices()?.find { it.deviceNetworkId == id }
        if (!d) {
            log.info "WaterGuru: attempting to create child device '${name} Pool' (networkId: ${id})"
            try {
                d = addChildDevice("brianwilson-hubitat", "WaterGuru Integration Driver", id, null,
                    [name: "${name} Pool", label: "${name} Pool", completedSetup: true])
                log.info "WaterGuru: child device created successfully for ${name}"
            } catch (e) {
                log.error "WaterGuru: failed to create child device for ${name} — ${e.message}. Make sure 'WaterGuru Integration Driver' is installed in Hubitat (Drivers Code)."
                return
            }
        }
        if (!d) { log.error "WaterGuru: addChildDevice returned null for ${name}"; return }

        if (date != d.currentValue("LastUpdated") || force)
            d.sendEvent(name: "LastUpdated", value: date)
        if (d.currentValue("battery") != batteryPct || force)
            d.sendEvent(name: "battery", value: batteryPct, isStateChange: true)
        if (d.currentValue("batteryStatus") != batteryStatus || force)
            d.sendEvent(name: "batteryStatus", value: batteryStatus, isStateChange: true)
        if (d.currentValue("CassettePercent") != CassettePercent || force)
            d.sendEvent(name: "CassettePercent", value: CassettePercent, isStateChange: true)
        if (d.currentValue("temperature") != temp || force)
            d.sendEvent(name: "temperature", value: temp, unit: "°${tempScale}", isStateChange: true)
        if (d.currentValue("CassetteStatus") != CassetteStatus || force) {
            d.sendEvent(name: "CassetteStatus", value: CassetteStatus, isStateChange: true)
            switch (CassetteStatus) {
                case "GREEN":  d.sendEvent(name: "consumableStatus", value: "good"); break
                case "YELLOW": d.sendEvent(name: "consumableStatus", value: "replace"); break
                case "RED":    d.sendEvent(name: "consumableStatus", value: "missing"); break
                default:       d.sendEvent(name: "consumableStatus", value: "maintenance_required"); break
            }
        }
        if (d.currentValue("CassetteTimeLeft") != CassetteTimeLeft || force)
            d.sendEvent(name: "CassetteTimeLeft", value: CassetteTimeLeft, isStateChange: true)
        if (d.currentValue("LastMeasurementHuman") != LastMeasurementHuman || force)
            d.sendEvent(name: "LastMeasurementHuman", value: LastMeasurementHuman, isStateChange: true)
        if (d.currentValue("LastMeasurement") != LastMeasurement || force)
            d.sendEvent(name: "LastMeasurement", value: LastMeasurement, isStateChange: true)
        if (d.currentValue("CassetteChecksLeft") != CassetteChecksLeft || force)
            d.sendEvent(name: "CassetteChecksLeft", value: CassetteChecksLeft, isStateChange: true)
        if (d.currentValue("Status") != status || force)
            d.sendEvent(name: "Status", value: status, isStateChange: true)
        if (d.currentValue("rssi") != rssi || force)
            d.sendEvent(name: "rssi", value: rssi, isStateChange: true)
        if (d.currentValue("statusMsg") != statusMsg || force)
            d.sendEvent(name: "statusMsg", value: statusMsg, isStateChange: true)
        if (d.currentValue("freeChlorine") != freeChlorine || force)
            d.sendEvent(name: "freeChlorine", value: freeChlorine, isStateChange: true)
        if (d.currentValue("pH") != pH || force)
            d.sendEvent(name: "pH", value: pH, isStateChange: true)
        if (d.currentValue("rate") != rate || force)
            d.sendEvent(name: "rate", unit: "GPM", value: rate, isStateChange: true)

        // ---- Full chemistry panel (2.1.0) --------------------------------
        // Change-gated emitter, mirroring the per-attribute pattern above.
        // Skips nulls so absent readings don't clobber a prior value.
        def emit = { String attr, def val ->
            if (val != null && (d.currentValue(attr)?.toString() != val.toString() || force))
                d.sendEvent(name: attr, value: val, isStateChange: true)
        }

        emit("totalAlkalinity",  totalAlkalinity)
        emit("calciumHardness",  calciumHardness)
        emit("cyanuricAcid",     cyanuricAcid)
        emit("saturationIndex",  saturationIndex)
        emit("totalHardness",    totalHardness)
        emit("salt",             salt)
        emit("phosphates",       phosphates)
        emit("copper",           copper)
        emit("iron",             iron)

        emit("freeChlorineStatus",    measStat("FREE_CL"))
        emit("pHStatus",              measStat("PH"))
        emit("skimmerFlowStatus",     measStat("SKIMMER_FLOW"))
        emit("totalAlkalinityStatus", measStat("TA"))
        emit("calciumHardnessStatus", measStat("CH"))
        emit("cyanuricAcidStatus",    measStat("CYA"))
        emit("saturationIndexStatus", measStat("SATURATION_INDEX"))
        emit("totalHardnessStatus",   measStat("TH"))
        emit("saltStatus",            measStat("SALT"))
        emit("phosphatesStatus",      measStat("PHOSPHATES"))
        emit("copperStatus",          measStat("COPPER"))
        emit("ironStatus",            measStat("IRON"))

        // Targets: per-reading target, falling back to the waterBody's
        // *TargetEffective for readings that expose one there.
        emit("freeChlorineTarget",    measTgt("FREE_CL")      ?: item.freeClTargetEffective)
        emit("pHTarget",              measTgt("PH")           ?: item.phTargetEffective)
        emit("skimmerFlowTarget",     measTgt("SKIMMER_FLOW") ?: item.flowGpmTargetEffective)
        emit("totalAlkalinityTarget", measTgt("TA")           ?: item.taTargetEffective)
        emit("calciumHardnessTarget", measTgt("CH")           ?: item.chTargetEffective)
        emit("cyanuricAcidTarget",    measTgt("CYA")          ?: item.cyaTargetEffective)
        emit("saltTarget",            measTgt("SALT")         ?: item.saltTargetEffective)

        emit("labResultsTime",        labResultsTime)
        emit("labResultsTimeHuman",   labResultsTimeHuman)

        emit("poolVolume",            poolVolume)
        emit("doseAdvice",            doseAdvice)
        emit("sanitizerType",         sanitizerType)
        emit("chlorineType",          chlorineType)
        emit("chlorineProductPct",    chlorineProductPct)
        emit("calHypoPct",            calHypoPct)
        emit("acidType",              acidType)
        emit("acidMuriaticPct",       acidMuriaticPct)
        emit("acidBisulfatePct",      acidBisulfatePct)
        emit("equipment",             equipment)
        emit("cassetteType",          cassetteType)
        emit("cassetteInfo",          cassetteInfo)

        evaluateNotifications(id, name, [
            lastMeasurement : LastMeasurement,
            status          : notifyStatus,
            statusMsg       : notifyStatusMsg,
            cassetteStatus  : CassetteStatus,
            batteryStatus   : batteryStatus,
            checksLeft      : CassetteChecksLeft,
            batteryPct      : batteryPct,
            pH              : pH,
            freeChlorine    : freeChlorine,
            temp            : temp,
            rate            : rate,
            alerts          : actionableAlerts.collect { "${it.status}: ${it.text}".toString() }
        ])
    }

    if (force) app.updateSetting("forceUpdate", [value: "false", type: "bool"])
}

// =================== Notifications ===================
//
// Change-driven, never poll-driven:
//  - First poll for a device emits a one-time summary of any pre-existing
//    non-GREEN / out-of-range / low-threshold conditions, plus a current-
//    readings digest if "Send a digest for every new sample" is on. The
//    state machine is then armed so the same state does NOT re-alert on
//    subsequent polls.
//  - Chemistry (pH / free chlorine) is otherwise only evaluated when
//    LastMeasurement changes (a real new sample, not just a poll).
//  - Status / cassette / battery status alerts fire on the transition edge
//    (GREEN -> non-GREEN), not while the condition persists. Stale-data
//    YELLOW alerts ("measurement outdated ... years ago") are filtered out
//    upstream so they don't contribute to status here — see
//    processWaterGuruData / isStaleDataAlert.
//  - Checks-left and battery % thresholds are edge-triggered and re-arm
//    only after the value climbs back above threshold (replacement).
//  - Temperature and flow are context in the message body, never a trigger.
//  - All triggers from one poll are coalesced into a single message.
//
// Use the "Send Current State Summary" button to clear the per-device
// baseline and re-emit the initial-state summary (useful after upgrade or
// when notifications were turned on while a device was already non-GREEN).

private void evaluateNotifications(String id, String name, Map cur) {
    if (!settings.notifyDevices) return

    def ns   = state.notifyState ?: [:]
    def prev = ns[id]
    boolean firstPoll = (prev == null)
    if (firstPoll) prev = [:]

    def alerts     = []
    def recoveries = []

    // A changed measurement timestamp is the only reliable "new reading" signal
    boolean newSample = !firstPoll && cur.lastMeasurement && cur.lastMeasurement != prev.lastMeasurement

    def phLo = (settings.phMin != null ? settings.phMin : 7.2) as BigDecimal
    def phHi = (settings.phMax != null ? settings.phMax : 7.8) as BigDecimal
    def clLo = (settings.clMin != null ? settings.clMin : 1.0) as BigDecimal
    def clHi = (settings.clMax != null ? settings.clMax : 3.0) as BigDecimal

    boolean phOut = cur.pH != null           && ((cur.pH as BigDecimal) < phLo || (cur.pH as BigDecimal) > phHi)
    boolean clOut = cur.freeChlorine != null && ((cur.freeChlorine as BigDecimal) < clLo || (cur.freeChlorine as BigDecimal) > clHi)

    boolean phAlerted = prev.phAlerted ?: false
    boolean clAlerted = prev.clAlerted ?: false

    if (firstPoll) {
        // Surface any pre-existing conditions as a one-time summary, then arm
        // per-condition flags so the same state does not re-alert next poll.
        // If the digest toggle is on, also include current readings so the
        // initial summary covers the "every new sample" expectation — there
        // is no prior LastMeasurement to compare against on first poll.
        if (settings.notifyOnNewSample && (cur.pH != null || cur.freeChlorine != null)) {
            def parts = []
            if (cur.pH != null)           parts << "pH ${cur.pH}"
            if (cur.freeChlorine != null) parts << "free chlorine ${cur.freeChlorine} ppm"
            alerts << "current readings — ${parts.join(', ')}"
        }
        if (phOut) { alerts << "pH ${cur.pH} is outside ${phLo}–${phHi}"; phAlerted = true }
        if (clOut) { alerts << "free chlorine ${cur.freeChlorine} ppm is outside ${clLo}–${clHi} ppm"; clAlerted = true }
        if (cur.status && cur.status != "GREEN") {
            def aCount = (cur.alerts ?: []).size()
            def first  = (cur.alerts ?: [])[0]
            def detail = ""
            if (aCount > 0) {
                detail = " — ${first}"
                if (aCount > 1) detail += " (+${aCount - 1} more)"
            }
            alerts << "status is ${cur.status}${detail}"
        }
        if (cur.cassetteStatus && cur.cassetteStatus != "GREEN")
            alerts << "cassette status is ${cur.cassetteStatus}"
        if (cur.batteryStatus && cur.batteryStatus != "GREEN")
            alerts << "battery status is ${cur.batteryStatus}"
    } else if (newSample) {
        if (settings.notifyOnNewSample)
            alerts << "new sample — pH ${cur.pH}, free chlorine ${cur.freeChlorine} ppm"
        if (phOut && !phAlerted)  { alerts << "pH ${cur.pH} is outside ${phLo}–${phHi}"; phAlerted = true }
        if (!phOut && phAlerted)  { recoveries << "pH back in range (${cur.pH})"; phAlerted = false }
        if (clOut && !clAlerted)  { alerts << "free chlorine ${cur.freeChlorine} ppm is outside ${clLo}–${clHi} ppm"; clAlerted = true }
        if (!clOut && clAlerted)  { recoveries << "free chlorine back in range (${cur.freeChlorine} ppm)"; clAlerted = false }
    }

    if (!firstPoll) {
        if (cur.status != prev.status) {
            if (cur.status && cur.status != "GREEN") {
                def detail = (cur.statusMsg && cur.statusMsg != "None") ? " — ${cur.statusMsg.trim().replace('\n', '; ')}" : ""
                alerts << "status is ${cur.status}${detail}"
            } else if (prev.status && prev.status != "GREEN") {
                recoveries << "status back to GREEN"
            }
        }
        if (cur.cassetteStatus != prev.cassetteStatus) {
            if (cur.cassetteStatus && cur.cassetteStatus != "GREEN")
                alerts << "cassette status is ${cur.cassetteStatus}"
            else if (prev.cassetteStatus && prev.cassetteStatus != "GREEN")
                recoveries << "cassette status back to GREEN"
        }
        if (cur.batteryStatus != prev.batteryStatus) {
            if (cur.batteryStatus && cur.batteryStatus != "GREEN")
                alerts << "battery status is ${cur.batteryStatus}"
            else if (prev.batteryStatus && prev.batteryStatus != "GREEN")
                recoveries << "battery status back to GREEN"
        }
        // Device alert texts we haven't reported before
        def seen = prev.alertsSeen ?: []
        ((cur.alerts ?: []) - seen).each { alerts << "device alert — ${it}" }
    }

    int checksThr = (settings.checksLeftThreshold != null ? settings.checksLeftThreshold : 10) as int
    int battThr   = (settings.batteryThreshold   != null ? settings.batteryThreshold   : 20) as int

    boolean checksArmed = prev.containsKey("checksArmed") ? prev.checksArmed : true
    boolean battArmed   = prev.containsKey("battArmed")   ? prev.battArmed   : true

    if (cur.checksLeft != null) {
        int v = cur.checksLeft as int
        if (firstPoll) {
            if (v <= checksThr) { alerts << "only ${v} cassette checks left"; checksArmed = false }
            else                { checksArmed = true }
        } else if (checksArmed && v <= checksThr) {
            alerts << "only ${v} cassette checks left"
            checksArmed = false
        } else if (!checksArmed && v > checksThr + 5) {
            // value jumped back up: cassette was replaced
            checksArmed = true
            recoveries << "cassette replaced (${v} checks left)"
        }
    }
    if (cur.batteryPct != null) {
        int v = cur.batteryPct as int
        if (firstPoll) {
            if (v <= battThr) { alerts << "battery low (${v}%)"; battArmed = false }
            else              { battArmed = true }
        } else if (battArmed && v <= battThr) {
            alerts << "battery low (${v}%)"
            battArmed = false
        } else if (!battArmed && v > battThr + 5) {
            battArmed = true
            recoveries << "battery replaced (${v}%)"
        }
    }

    def lines = []
    lines.addAll(alerts)
    if (settings.notifyRecovery) lines.addAll(recoveries)
    if (lines) {
        def ctx = []
        if (cur.temp != null) ctx << "temp ${cur.temp}°"
        if (cur.rate != null) ctx << "flow ${cur.rate} GPM"
        def context = ctx ? " (${ctx.join(', ')})" : ""
        def prefix  = firstPoll ? "WaterGuru ${name} initial state — " : "WaterGuru ${name}: "
        sendWgNotification("${prefix}${lines.join('; ')}${context}")
    }

    ns[id] = [
        lastMeasurement : cur.lastMeasurement,
        status          : cur.status,
        cassetteStatus  : cur.cassetteStatus,
        batteryStatus   : cur.batteryStatus,
        phAlerted       : phAlerted,
        clAlerted       : clAlerted,
        checksArmed     : checksArmed,
        battArmed       : battArmed,
        alertsSeen      : cur.alerts ?: []
    ]
    state.notifyState = ns
}

private void sendWgNotification(String msg) {
    if (settings.notificationsPaused) {
        ifDebug("Notifications paused; suppressing: ${msg}")
        return
    }
    if (inQuietHours()) {
        def pending = state.pendingNotify ?: []
        pending << msg
        state.pendingNotify = pending
        scheduleQuietFlush()
        ifDebug("Quiet hours; queued: ${msg}")
        return
    }
    ifDebug("Sending notification: ${msg}")
    settings.notifyDevices?.each { it.deviceNotification(msg) }
}

private boolean inQuietHours() {
    if (!settings.quietStart || !settings.quietEnd) return false
    def nowD = new Date()
    def s = timeToday(settings.quietStart, location.timeZone)
    def e = timeToday(settings.quietEnd, location.timeZone)
    if (!e.after(s)) {
        // window crosses midnight (e.g. 22:00 – 07:00)
        return !nowD.before(s) || nowD.before(e)
    }
    return !nowD.before(s) && nowD.before(e)
}

private void scheduleQuietFlush() {
    def e = timeToday(settings.quietEnd, location.timeZone)
    if (!e.after(new Date())) e = new Date(e.time + 86400000L)
    runOnce(e, "flushPendingNotifications")
}

def flushPendingNotifications() {
    def pending = state.pendingNotify ?: []
    if (!pending) return
    state.pendingNotify = []
    if (settings.notificationsPaused) {
        ifDebug("Notifications paused; dropping ${pending.size()} queued message(s)")
        return
    }
    def msg = pending.join("\n")
    ifDebug("Flushing ${pending.size()} queued notification(s)")
    settings.notifyDevices?.each { it.deviceNotification(msg) }
}

def logsOff() {
    log.warn "WaterGuru: debug logging disabled"
    app.updateSetting("isDebug", [value: "false", type: "bool"])
}

private ifDebug(msg) {
    if (msg && settings.isDebug) log.debug "WaterGuru Integration: ${msg}"
}
