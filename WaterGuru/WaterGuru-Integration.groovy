/**
 * WaterGuru Integration App
 *
 * 2.1.0 - Brian Wilson / bubba@bubba.org
 *
 * Native Hubitat integration — no external Python/Flask server required.
 * Authenticates directly with AWS Cognito (SRP flow) and calls the
 * WaterGuru Lambda function using AWS Signature V4.
 *
 * Features:
 *  - Discover available WaterGuru devices from the app UI
 *  - Select which device(s) to create child devices for
 *  - Configurable poll interval (1–12 hours)
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
    importUrl: "https://raw.githubusercontent.com/bdwilson/waterguru-api/main/hubitat/WaterGuru-Integration.groovy",
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
    return srpAuthenticate()
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
        def temp                 = item.waterTemp
        def status               = item.status
        def LastMeasurementHuman = item.latestMeasureTimeHuman
        def LastMeasurement      = item.latestMeasureTime

        def statusMsg = ""
        def count = 0
        item.alerts.each { alert ->
            count++
            statusMsg += "${alert.status} Alert (${count}): ${alert.text}\n"
            ifDebug("alert: ${alert.status} — ${alert.text}")
        }
        if (!statusMsg) statusMsg = "None"

        def index = item.measurements?.findIndexOf { it.type == "FREE_CL" }
        def freeChlorine = (index != null && index >= 0) ? item.measurements[index].floatValue : null
        index = item.measurements?.findIndexOf { it.type == "PH" }
        def pH = (index != null && index >= 0) ? item.measurements[index].floatValue : null
        index = item.measurements?.findIndexOf { it.type == "SKIMMER_FLOW" }
        def rate = (index != null && index >= 0) ? item.measurements[index].intValue : null
        ifDebug("measurements: freeChlorine=${freeChlorine} pH=${pH} rate=${rate}")

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
            d.sendEvent(name: "temperature", value: temp, isStateChange: true)
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
    }

    if (force) app.updateSetting("forceUpdate", [value: "false", type: "bool"])
}

def logsOff() {
    log.warn "WaterGuru: debug logging disabled"
    app.updateSetting("isDebug", [value: "false", type: "bool"])
}

private ifDebug(msg) {
    if (msg && settings.isDebug) log.debug "WaterGuru Integration: ${msg}"
}
