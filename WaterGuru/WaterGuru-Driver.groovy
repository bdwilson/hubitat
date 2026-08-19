/*
 * Water Guru Integration Driver
 *
 * 2.2.0 - Cassette type: expose the installed cassette model (cassetteType:
 *         C2 / C5 / unknown) and an optional human summary (cassetteInfo).
 *         WaterGuru's API never prints the model, so the app derives it from
 *         whether Total Alkalinity / Calcium Hardness / Cyanuric Acid are
 *         freshly sampled (a C5 measures those; a C2 measures only free
 *         chlorine + pH). Additive only.
 * 2.1.0 - Full chemistry panel: expose every WaterGuru reading (Total
 *         Alkalinity, Calcium Hardness, Cyanuric Acid, Salt, Phosphates,
 *         Copper, Iron, Saturation Index, Total Hardness) plus a per-reading
 *         RED/YELLOW/GREEN status and effective target for each, and the lab-
 *         results timestamp so stale (lab-sourced) readings are identifiable.
 *         Additive only — no existing attribute changes.
 * 2.0.1 - Brian Wilson / bubba@bubba.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 * 
 */

metadata {
    definition(name: "WaterGuru Integration Driver", namespace: "brianwilson-hubitat", author: "Brian Wilson", importUrl: "https://raw.githubusercontent.com/bdwilson/hubitat/refs/heads/master/WaterGuru/WaterGuru-Driver.groovy") {
        capability "Battery"
        capability "Consumable"
		capability "LiquidFlowRate"
        capability "Refresh"
        capability "SignalStrength"
        capability "TemperatureMeasurement"
        capability "pHMeasurement"

		command "battery"
		command "refresh"
        
        attribute "freeChlorine", "NUMBER"
        attribute "LastUpdated", "DATE"
        attribute "LastMeasurementHuman", "STRING"
        attribute "LastMeasurement", "DATE"
        attribute "CassettePercent", "NUMBER"
        attribute "CassetteChecksLeft", "NUMBER"
        attribute "CassetteTimeLeft", "STRING"
        attribute "CassetteStatus", "ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "batteryStatus", "ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "Status", "ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "statusMsg", "STRING"

        // ---- Full chemistry panel (2.1.0) --------------------------------
        // WaterGuru returns these on every dashboard call; older versions
        // parsed only free chlorine, pH and skimmer flow. All additive.
        //
        // pH lives on the pHMeasurement capability, free chlorine and skimmer
        // flow (rate) on their existing attributes/capabilities. Everything
        // below is new. Note: Salt, Phosphates, Copper and Iron are sourced
        // from WaterGuru's periodic lab test (see labResultsTime), not the
        // daily pod sample, so they can be weeks-to-years stale.
        attribute "totalAlkalinity",  "NUMBER"   // ppm
        attribute "calciumHardness",  "NUMBER"   // ppm
        attribute "cyanuricAcid",     "NUMBER"   // ppm (stabilizer / CYA)
        attribute "saturationIndex",  "NUMBER"   // Langelier Saturation Index
        attribute "totalHardness",    "NUMBER"   // ppm
        attribute "salt",             "NUMBER"   // ppm  (lab-sourced)
        attribute "phosphates",       "NUMBER"   // ppb  (lab-sourced)
        attribute "copper",           "NUMBER"   // ppm  (lab-sourced)
        attribute "iron",             "NUMBER"   // ppm  (lab-sourced)

        // Per-reading WaterGuru verdict, so Rule Machine can trigger on the
        // sensor's own RED/YELLOW/GREEN call instead of re-deriving thresholds.
        attribute "freeChlorineStatus",   "ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "pHStatus",             "ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "skimmerFlowStatus",    "ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "totalAlkalinityStatus","ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "calciumHardnessStatus","ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "cyanuricAcidStatus",   "ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "saturationIndexStatus","ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "totalHardnessStatus",  "ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "saltStatus",           "ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "phosphatesStatus",     "ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "copperStatus",         "ENUM", ["RED", "YELLOW", "GREEN"]
        attribute "ironStatus",           "ENUM", ["RED", "YELLOW", "GREEN"]

        // WaterGuru's effective target for each reading (dashboard reference).
        attribute "freeChlorineTarget",   "NUMBER"
        attribute "pHTarget",             "NUMBER"
        attribute "skimmerFlowTarget",    "NUMBER"
        attribute "totalAlkalinityTarget","NUMBER"
        attribute "calciumHardnessTarget","NUMBER"
        attribute "cyanuricAcidTarget",   "NUMBER"
        attribute "saltTarget",           "NUMBER"

        // Timestamp of the last periodic lab test (drives Salt/Phosphates/
        // Copper/Iron). Lets a dashboard or rule flag those readings as stale.
        attribute "labResultsTime",       "DATE"
        attribute "labResultsTimeHuman",  "STRING"

        // Pool volume (gallons) as reported by WaterGuru (waterBody.sizeGallons)
        // — useful to any dosing/automation app — and WaterGuru's own
        // "add X of chemical Y" recommendations for readings needing attention,
        // newline-joined (e.g. "Add 2.4 cups of dry acid"); "None" when clear.
        attribute "poolVolume",           "NUMBER"   // gallons
        attribute "doseAdvice",           "STRING"

        // Equipment / product configuration from WaterGuru's pool-settings
        // (waterBody sub-object). Lets a dosing app auto-adopt the products you
        // actually use. Address and other PII are deliberately NOT surfaced.
        attribute "sanitizerType",        "STRING"   // e.g. FREE_CL
        attribute "chlorineType",         "STRING"   // userCl, e.g. CL_LIQ
        attribute "chlorineProductPct",   "NUMBER"   // liquid chlorine strength %
        attribute "calHypoPct",           "NUMBER"   // cal-hypo strength %
        attribute "acidType",             "STRING"   // userAcid, e.g. BISULFATE / MURIATIC
        attribute "acidMuriaticPct",      "NUMBER"   // muriatic strength %
        attribute "acidBisulfatePct",     "NUMBER"   // dry-acid strength %
        attribute "equipment",            "STRING"   // human summary: type · surface · filter · cover

        // Installed cassette model. WaterGuru's API never reports it literally
        // (pod.product is always "SENSE"); the app derives it from whether the
        // pod freshly samples TA/CH/CYA — a C5 does, a C2 measures only free
        // chlorine + pH. "unknown" when there is nothing to derive from.
        attribute "cassetteType",         "STRING"   // C2 / C5 / unknown
        // Optional human summary, e.g. "C5 · installed Aug 12, 2026 · 28/30 pads".
        attribute "cassetteInfo",         "STRING"
    }
}

def installed() {
    refresh()    
}    

def refresh() {
    parent.updateStatus()
}

def updated() {
    refresh()
}

def battery() {
    refresh()
}
