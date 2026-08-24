# Weewx Local Capabilities

Please see the [Hubitat Community](https://community.hubitat.com/t/release-weewx-local-capabilities/17709) for more info. You need [this driver](https://github.com/bdwilson/hubitat-weewx-driver) in order to generate the Json data for this app. 


# Notes:

Load up you.weewx.url/daily.json and make sure you get the right variables for
your installation. I am using the ones in the screenshot
[here](https://community.hubitat.com/t/release-weewx-local-capabilities/17709),
but the info you want could vary depending on which devices you have.

This also turns on/off a switch (whatever this device is called) based on the
rain criteria - on for wet/off for dry..  You can use this for
Irrigation apps like Simple Irrigation.

# Extended Data (1.2.0+)

`daily.json` has always carried far more than the driver published. As of 1.2.0
the driver reads the whole file and exposes it as normal Hubitat attributes, so
you can drop them straight onto a dashboard tile or use them in Rule Machine -
no custom source paths to configure.

## Rain

Controlled by **Publish extended rain data** (on by default). These are the same
numbers a Weewx web dashboard shows for rain:

| Attribute | daily.json source | Example |
| --- | --- | --- |
| `rainRate` | `stats.current.rainRate` | 0.00 (in/hr) |
| `rainToday` | `stats.sinceMidnight.rainSum` | 1.41 |
| `rainYesterday` | `stats.yesterday.rainSum` | 0.00 |
| `rain2Days` .. `rain7Days` | `stats.rainstats.rainSum2days` .. `rainSum7days` | 1.84 / 2.11 / ... |
| `rainWeek` | `stats.week.rainSum` | 7.37 |
| `rainUnit` | detected from Weewx | `in` or `mm` |
| `rainSummary` | - | `Rate: 0.00 in/hr \| Total: 1.41 in \| Yesterday: 0.00 in \| 2 Day: 1.84 in \| 3 Day: 2.11 in \| Week: 7.37 in` |
| `rainTile` | - | small HTML block for a dashboard **Attribute** tile |

Units are taken from whatever Weewx formatted the value as, so metric stations
report `mm` without any extra configuration.

`rain2Days` through `rain7Days` need the `rainstats` block, which comes from the
current [hubitat-weewx-driver](https://github.com/bdwilson/hubitat-weewx-driver)
skin. If your `daily.json` has no `rainstats` section, update the skin on your
Weewx server - the driver skips those attributes rather than erroring.

Note that `rainWeek` and `rain7Days` are both a rolling seven day total in the
current skin, so they will normally read the same.

## Everything else

Controlled by **Publish extended Weewx data** (off by default, so existing
devices stay as they were until you turn it on):

`dewpoint`, `windChill`, `heatIndex`, `insideTemperature`, `insideHumidity`,
`highTempToday`, `lowTempToday`, `highTempYesterday`, `lowTempYesterday`,
`avgTempYesterday`, `windSpeed`, `windGust`, `windDirection`,
`windDirectionText`, `pressure`, `pressureTrend`, `ultravioletIndex`,
`solarRadiation`, `sunrise`, `sunset`, `moonPhase`, `moonFullness`,
`stationHardware`, `weewxVersion`, `observationTime`, `lastPoll`

Anything your station doesn't report is simply skipped. There is also an
**Estimate illuminance (lux) from solar radiation** option which populates the
`illuminance` attribute (roughly 126.7 lux per W/m²) - the Illuminance
Measurement capability was declared but never populated before.

## Dashboard tips

* For a single tile that looks like a weather site's rain panel, add an
  **Attribute** tile bound to `rainTile`.
* For individual numbers, use **Attribute** tiles bound to `rainToday`,
  `rain2Days`, etc.
* All rain values are numeric, so Rule Machine comparisons like
  "`rain3Days` less than 0.5" work directly - no string parsing needed.

## Other 1.2.0 changes

* Added the `Refresh` capability, so `refresh()` works from dashboards, Rule
  Machine and the device page.
* `updated()` now calls `unschedule()` first - previously every settings save
  added another polling schedule on top of the existing ones.
* `temperature` and `humidity` are now published as numbers with units instead
  of strings.
* Custom source paths (Temp/Humidity/Custom Rain Source) are no longer limited
  to four levels deep, and a missing or `N/A` value is skipped instead of
  throwing.
