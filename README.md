# Solar Compass

**Where is Earth among the planets?** A top-down map of the solar system with Earth highlighted ("you are here"), distances, galactic coordinates, equinox and solstice dates, and a **compass mode** so you can tell where north is. Works **offline**.

- Package: `solarcompass.blackfalcon.jan` — APK: `SolarCompass.apk`
- Launching screen: falcon emoji + "Brought to you By: Black Falcon"
- **By: Black Falcon**

## The map
- The Sun in the middle, orbits of Mercury, Venus, **Earth**, Mars, Jupiter, Saturn, Uranus and Neptune, with emoji-style planet pictures and their full names. Positions are for the date you choose (orbits use square-root spacing so the inner planets are visible).
- **γ equinox direction:** the dashed gold line from the Sun shows the direction of the vernal equinox. Green / orange dots on Earth's orbit are the equinox / solstice points.
- Pick a planet in the drop-down: a line from Earth to it shows the distance.

## Compass mode (north)
- With GPS, the map turns so the line from Earth to the Sun on the map points to where the **Sun really is** in your sky. N, E, S and W letters turn around the map, so you can see where **north** is. A gold marker shows the way you are facing.
- A violet star shows where the **Galactic Centre** is in your sky.
- Switch it off with **Compass: OFF** for a fixed map.

## Numbers
- **Distance from Earth** for the Sun and every planet: AU, km and light-time (e.g. Sun: 1.000 AU = 149.6 million km = 8.3 light-minutes), and **distance from the Sun**.
- **Galactic coordinates** (l, b) of the Sun and each planet as seen from Earth, and where the Galactic Centre is now.
- **Equinoxes and solstices** of the year in your time zone, with a countdown to the next one.

## Time
- Slider ±180 days; buttons −1 d / +1 d, −1 w / +1 w, −1 mo / +1 mo, −1 y / +1 y; **Now** returns to live.

## Offline
- No internet permission. GPS (optional, only for compass mode) comes from the phone's GPS chip; the last position is saved.
- Positions are calculated on the phone (Paul Schlyter's low-precision method, 1–2 arc-minutes; years about 1900–2100). Equinox/solstice times use Meeus's algorithm (within about a minute).

## Build
Push to GitHub — the **Build APK** workflow runs automatically.
Open **Actions → latest run → Artifacts → SolarCompass** to download `SolarCompass.apk`.
