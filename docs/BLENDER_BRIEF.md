# Auftrag: 3D-Satelliten für Downlink (Blender)

Du baust und renderst die Satellitenmodelle für **Downlink**, eine Open-Source-Android-App (GPL-3.0). Die App zeigt Überflüge von Satelliten, eine Himmelskuppel, einen 3D-Globus und eine AR-Ansicht. Jeder Satellit erscheint als gerendertes Bild: groß im Datenblatt und im Intro, klein in Listen, auf der Karte, in AR und im Widget.

Die jetzigen Renders sind ein Platzhalter und nicht gut genug. Ziel ist **Premium-Qualität**: Die Modelle sollen wie Pressebilder von ESA oder NASA aussehen, nicht wie Spielzeug.

Projekt: `/Users/bahriannovotny/Desktop/DEV./Downlink`
Blender: `/Applications/Blender.app` (5.2 LTS, Cycles mit Metal-GPU)
Bisheriges Skript zum Ansehen: `tools/render_sats.py` (eigene Modelle sind dort nur Quader und Zylinder)

---

## 1. Was die App erwartet (fest, bitte genau so liefern)

### Standbilder (Pflicht)
- Pfad: `app/src/main/res/drawable-nodpi/sat_<key>.webp`
- 512 × 512 px, RGBA mit **transparentem Hintergrund**
- Motiv zentriert und auf den Alphakanal zugeschnitten, rundum etwa 6 % Rand
- WebP mit Qualität etwa 86, höchstens rund 60 KB pro Bild

### Drehungen (Turntables, Pflicht für alle)
- Pfad: `app/src/main/assets/turn/<key>_NN.webp`, NN = 00 bis 35
- 36 Bilder, je 10° Drehung um die Hochachse des Bildes, nahtlose Schleife
- 420 × 420 px, transparent, **gleicher Bildausschnitt über alle 36 Bilder** (nichts darf springen)
- höchstens rund 20 KB pro Bild
- Derzeit liest die App nur `meteor_NN`. Ich erweitere sie auf alle Keys, sobald die Dateien da sind.

### Heldenbild für das Intro (optional, aber erwünscht)
- `app/src/main/res/drawable-nodpi/hero_meteor.webp`, 1080 × 1350 px, transparent
- Meteor-M in Dreiviertelansicht, Sonne von oben rechts, deutliches blaues Erdlicht von unten
- Ohne Erde im Bild: Atmosphäre und Hintergrund zeichnet die App selbst

---

## 2. Die Objekte

| key | Objekt | Quelle | Hinweise zur Bauform |
|---|---|---|---|
| `meteor` | Meteor-M Nr. 2-3 / 2-4 | **selbst bauen** | Druckkörper als Zylinder in goldener und silberner Isolierfolie, Instrumentendeck mit Kamera MSU-MR, zwei große Solarflügel an Auslegern, Wendelantenne für 137 MHz, Radarbalken (Bildreferenzen: Roscosmos / VNIIEM) |
| `metop` | Metop-B / Metop-C | **selbst bauen** | Langer Kastenkörper, ein einzelner langer Solarflügel, ASCAT-Antennenbalken (sechs lange weiße Paddel), IASI- und AVHRR-Instrumente, viel Goldfolie (Referenz: ESA/EUMETSAT-Pressebilder) |
| `jpss` | NOAA 20 / NOAA 21 | **selbst bauen** oder `npp` umbauen | Ball-BCP-2000-Bus, ein Solarflügel, VIIRS-Instrument. Ähnlich wie Suomi NPP, aber sichtbar eigene Bauform |
| `npp` | Suomi NPP | NASA-Modell | „Suomi National Polar-orbiting Partnership (Suomi NPP)“ |
| `iss` | ISS | NASA-Modell | **„International Space Station (ISS) (C) (High Res)“** (7z) statt (A)/(B). Hilfsgeometrie entfernen. Truss waagerecht ins Bild |
| `tiangong` | Chinesische Raumstation | **selbst bauen** | T-Form: Tianhe in der Mitte, Wentian und Mengtian quer, riesige flexible Solarflügel an den Labormodulen, Andockknoten als Kugel, weißes Isoliermaterial |
| `hubble` | Hubble | NASA-Modell | „Hubble Space Telescope (B)“ (7z, höher aufgelöst). Silberne Folie und Solarflügel müssen glänzen |
| `aqua` | Aqua | NASA-Modell | „Aqua (A)“ |
| `terra` | Terra | NASA-Modell | „Terra“ |
| `aura` | Aura | NASA-Modell | „Aura (A)“ |
| `landsat` | Landsat 8 / 9 | NASA-Modell | „Landsat 8“ |
| `so50` | SO-50 (SaudiSat 1C) | **selbst bauen** | Kleiner Würfel von etwa 25 cm mit Solarzellen auf allen Seiten und vier Peitschenantennen |
| `greencube` | GreenCube (IO-117) | **selbst bauen** oder NASA-CubeSat umbauen | 3U-CubeSat, ausklappbare Solarpaneele, UHF-Antennen |
| `diwata` | Diwata-2 (PO-101) | **selbst bauen** | Mikrosatellit von etwa 50 kg, Kastenform, Solarflügel, Kameraöffnungen |

**NASA-Modelle:** https://github.com/nasa/NASA-3D-Resources (Ordner `3D Models/`). Sie sind gemeinfrei und dürfen benutzt werden. Wenn ein NASA-Modell zu grob ist (das ISS-Modell (A) z. B. hat nur rund 40 KB), nimm die High-Res-Version oder bessere es nach: Materialien, Fasen, Kleinteile.

**Lizenz, wichtig:** Keine Modelle von Sketchfab, TurboSquid, ESA oder Roscosmos übernehmen, auch keine „free“-Modelle mit unklarer Lizenz. Alles, was nicht von der NASA stammt, baust du selbst. Fotos dienen nur als Vorlage für die Form. Eigene Modelle stehen unter CC0 oder GPL-3.0 wie das Projekt.

---

## 3. Look und Qualität

**Gesamtwirkung:** Wie ein Satellit im Orbit bei hartem Sonnenlicht, dazu ein blauer Schimmer von der Erde. Edel, technisch, glaubwürdig. Kein Comic, kein Plastik.

**Materialien (der größte Hebel):**
- **Isolierfolie (MLI):** gold und silber, metallisch, mit echten Knittern und Falten. Verwende geometrische Unebenheit oder eine starke Bump-/Displacement-Map, nicht nur Rauschen. Dazu leichte Anisotropie und Stellen mit unterschiedlicher Rauheit. Sie muss das Licht in Flecken zurückwerfen.
- **Solarzellen:** tiefblau bis violett mit sichtbarem Zellraster, silbernen Kontaktlinien und Rahmen. Eine leichte Klarlackschicht erzeugt Glanzlichter. Die Rückseite der Paneele ist hellgrau oder weiß, wenn sie im Bild ist.
- **Weiße Thermalfarbe / Beta-Tuch:** mattweiß mit feiner Gewebestruktur.
- **Aluminium und Titan:** gebürstet, Kanten gefast.
- **Optiken:** schwarzes Glas mit Reflexen.
- **Fasen:** Alle harten Kanten bekommen kleine Bevels, damit sie Licht fangen.

**Licht (für alle Objekte gleich, damit die Sammlung zusammenpasst):**
- Sonne: Sun-Lamp, Winkel etwa 0,5°, warmweiß (5800 K), von oben rechts vorne, hart
- Erdlicht: große Flächenleuchte von unten links, blau (#4A8CFF), etwa 15 bis 20 % der Sonnenhelligkeit
- Kantenlicht: von hinten links, leicht mint (#7CDAA9), schmal, damit sich die Silhouette vom dunklen App-Hintergrund löst
- Spiegelwelt: Die Umgebung ist für die Kamera unsichtbar (Film transparent), wird aber in Reflexen gesehen. Unten das Blau der Erde, oben Schwarz, ein heller Streifen am Horizont. Ohne sie wirkt Metall schwarz.

**Kamera:**
- Brennweite 85 bis 135 mm (wenig Verzerrung), Dreiviertelansicht etwa 20 bis 30° von oben
- Solarflügel nie genau von der Kante zeigen
- Längste Ausdehnung waagerecht oder diagonal ins quadratische Bild

**Render:**
- Cycles, 256 Samples mit Denoiser (OpenImageDenoise), View Transform AgX, Look „Punchy“ oder „Medium High Contrast“
- Film transparent, Ausgabe zuerst als PNG mit 16 Bit und 2× Größe, dann herunterskaliert und als WebP gespeichert (siehe 1)
- Leichter Bloom oder Glare nur auf den hellsten Glanzlichtern, über den Compositor, mit Alphakanal

**Prüfung vor der Abgabe:**
- Ein Kontaktbogen aller Standbilder auf #04070D (das Dunkel der App) und auf #173E78 (der Taghimmel der App)
- In beiden Fällen gut lesbar, keine hellen Ränder oder Halos am Alphakanal (unvormultiplizierte Kanten prüfen)
- Auch bei 44 px Größe noch erkennbar. Dafür eine verkleinerte Vorschau beilegen

---

## 4. Ablauf und Lieferung

1. Modelle als `.blend` in `tools/blender/models/<key>.blend` ablegen (sauber benannt, angewendete Transformationen, Ursprung im Schwerpunkt, Maßstab in Metern).
2. Ein Render-Skript `tools/blender/render.py`, das aus der Kommandozeile läuft:
   ```bash
   /Applications/Blender.app/Contents/MacOS/Blender -b -P tools/blender/render.py -- --all
   /Applications/Blender.app/Contents/MacOS/Blender -b -P tools/blender/render.py -- meteor --turntable
   ```
   Es lädt eine gemeinsame Licht- und Weltszene (`tools/blender/studio.blend`), hängt das Modell ein, rendert Standbild und 36 Drehbilder und erledigt Zuschnitt und WebP-Umwandlung selbst (Pillow über Blenders Python oder ein separates Skript `tools/blender/pack.py`).
3. NASA-Rohmodelle **nicht** ins Repo legen. Das Skript lädt sie bei Bedarf nach `tools/blender/cache/`, und dieser Ordner kommt in `.gitignore`.
4. In `tools/blender/README.md` stehen pro Objekt die Quelle (NASA-Ordnername oder „eigenes Modell“) und die Lizenz, außerdem die Befehle oben.
5. Abgabe: Die WebP-Dateien liegen an den Pfaden aus Abschnitt 1, dazu `tools/blender/contact_sheet.png`. Committe auf einem eigenen Branch `blender-sprites` und baue die App nicht um. Wenn du neue Keys hinzufügst (`jpss`, `so50`, `greencube`, `diwata`), sag Bescheid; die Zuordnung im Code ändere ich.

---

## 5. Was du nicht anfassen sollst

- Keinen Kotlin-Code und keine Gradle-Dateien. Die Einbindung mache ich.
- Keine Fremdmodelle mit unklarer Lizenz.
- Keine Logos oder Schriftzüge von Raumfahrtagenturen auf den Modellen (Flaggen und Agenturlogos weglassen).

## 6. Reihenfolge, falls die Zeit knapp ist

1. `meteor`: Standbild, Drehung und Heldenbild. Das ist der Star der App.
2. `iss`, `tiangong`, `metop`
3. `hubble`, `npp`/`jpss`
4. Erdbeobachtung (`aqua`, `terra`, `aura`, `landsat`)
5. Kleinsatelliten (`so50`, `greencube`, `diwata`)
