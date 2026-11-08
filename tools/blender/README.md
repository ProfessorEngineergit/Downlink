# Downlink spacecraft portraits

Fourteen editable spacecraft, one shared Cycles studio, transparent WebP portraits
and 36-frame turntables. The Android application is not changed by this pipeline.

## Reproduce

Blender 5.2 LTS and a host Python 3.13 are used. Install the two packaging/download
dependencies once; Blender itself does not need third-party Python packages:

```sh
python3 -m venv tools/blender/.venv
tools/blender/.venv/bin/pip install -r tools/blender/requirements.txt

/Applications/Blender.app/Contents/MacOS/Blender -b -P tools/blender/render.py -- --all
/Applications/Blender.app/Contents/MacOS/Blender -b -P tools/blender/render.py -- meteor --turntable
```

Run from the repository root. `--all` renders all stills, all fourteen loops and
Meteor's hero. A single key renders its still and, for Meteor, the hero; add
`--turntable` for its loop. Useful development options:

```sh
# Build/save the editable models without rendering.
/Applications/Blender.app/Contents/MacOS/Blender -b --python-exit-code 1 -P tools/blender/render.py -- --all --build-only

# Rebuild one model after changing the modelling or material code.
/Applications/Blender.app/Contents/MacOS/Blender -b --python-exit-code 1 -P tools/blender/render.py -- meteor --rebuild --turntable

# Recreate the shared studio after changing the lighting/compositor.
/Applications/Blender.app/Contents/MacOS/Blender -b --python-exit-code 1 -P tools/blender/render.py -- meteor --rebuild-studio

# Look-development preview; production defaults to 256 samples.
/Applications/Blender.app/Contents/MacOS/Blender -b --python-exit-code 1 -P tools/blender/render.py -- --all --still-only --samples 32

# Render only the diagonally composed portrait-format intro hero.
/Applications/Blender.app/Contents/MacOS/Blender -b --python-exit-code 1 -P tools/blender/render.py -- meteor --hero-only

tools/blender/.venv/bin/python tools/blender/pack.py --sheets --validate
/Applications/Blender.app/Contents/MacOS/Blender -b --python-exit-code 1 -P tools/blender/validate_models.py
```

Use `DOWNLINK_PYTHON=/absolute/path/to/python` to use another environment containing
the requirements. Metal is selected when available; CPU is the fallback. The
`--python-exit-code 1` flag makes automation fail correctly on Python exceptions.
`--resume` reuses completed raw frames only when model, studio, pipeline and sample
settings match the cached render manifest.

## Deliverables

| Artifact | Location | Specification |
| --- | --- | --- |
| Editable, baked model | `models/<key>.blend` | Metres, identity transforms, centred geometry, named parts |
| Shared lights, camera and world | `studio.blend` | Cycles, AgX, 105 mm camera, invisible reflection environment |
| Still | `../../app/src/main/res/drawable-nodpi/sat_<key>.webp` | 512 × 512 RGBA, ≤ 60 KiB |
| Turntable | `../../app/src/main/assets/turn/<key>_00.webp` … `_35.webp` | 420 × 420 RGBA, ≤ 20 KiB per frame |
| Intro hero | `../../app/src/main/res/drawable-nodpi/hero_meteor.webp` | 1080 × 1350 RGBA |
| Two-background review | `contact_sheet.png` | Every still on #04070D and #173E78 |
| Actual list-size review | `preview_44px.png` | 44 px sprites on both backgrounds |
| Rotation review | `turntable_sheet.png` | Six equally spaced views of every spacecraft |
| Machine-readable checks | `validation.json` | Counts, dimensions, alpha, crop and byte budgets |

The raw 16-bit RGBA PNGs render at twice the delivered dimensions. The production
sample ceiling is 256 with adaptive sampling (minimum 16, noise threshold 0.035),
OpenImageDenoise and eight total ray bounces. Hard 0.5° warm sunlight, a large blue
lower fill, a mint rim and the Earth/horizon reflection world are shared by all
models. A small highlight-only glare is masked by the original render alpha.

Still framing uses the visible alpha bounds plus 6% padding on each side. Each
turntable gets **one union crop for all 36 views**, one perspective camera and one
object scale. The object rotates 0° to 350° around the image's vertical axis; the
next loop's 0° is the next 10° step. No frame receives an independent recenter or
zoom. Filtering happens in premultiplied alpha and WebP stores straight alpha.
Stills use Lanczos; turntables use bicubic filtering to suppress subpixel ringing
around the fine truss/antenna geometry before the smaller WebP encoding.
All zero-alpha pixels are cleared. Packing starts at quality 86 and reduces it
only when necessary to respect the byte budget. For exceptionally complex ISS and
Hubble silhouettes, turntable alpha coverage is also progressively quantized to
meet 20 KiB; stills and the hero retain lossless alpha. Per-frame RGB/alpha quality
and exact sizes are recorded in `cache/renders/<key>/packing.json`. Final files
are replaced atomically.

## Provenance and licences

NASA originals are fetched on demand into the ignored `cache/` directory. Downloads
are pinned to NASA repository revision
`11ebb4ee043715aefbba6aeec8a61746fad67fa7`; `sources.lock.json` records exact paths,
URLs and SHA-256 hashes and rejects altered downloads. No raw NASA archives, glTFs
or external reference photos belong in the Git commit.

[NASA 3D Resources](https://github.com/nasa/NASA-3D-Resources) describes its assets
as free and without copyright. See also
[NASA media usage guidance](https://www.nasa.gov/nasa-brand-center/images-and-media/).
NASA-derived models and renders retain that provenance. Original spacecraft
geometry, procedural materials and authored additions are offered under
[CC0-1.0](https://creativecommons.org/publicdomain/zero/1.0/). Pipeline Python code
uses the repository's GPL-3.0 licence. Agency logos, flags and lettering are omitted.

| Key | Source / NASA directory | Licence and changes |
| --- | --- | --- |
| `meteor` | Original model | CC0; cylindrical MLI bus, instrument deck, MSU-MR/KMSS apertures, twin solar wings, four helical conductors, BRLK radar beam, harness and fasteners |
| `metop` | Original model | CC0; separate service/payload buses, IASI/AVHRR optics, single wing, six slotted ASCAT arrays |
| `jpss` | Original model | CC0; BCP-2000-style exterior, distinct VIIRS/CrIS/ATMS arrangement, one wing; independently authored, not an NPP recolour |
| `tiangong` | Original model | CC0; Tianhe plus Wentian/Mengtian, docking node, folded flexible blankets, radiator panels and articulated arm |
| `so50` | Original model | CC0; solar-covered 25 cm bus and four whips |
| `greencube` | Original model | CC0; 3U rails, deployable blankets, UHF tape antennas and payload window |
| `diwata` | Original model | CC0; microsatellite bus, deployable wings and separate camera apertures |
| `iss` | International Space Station (ISS) (C) (High Res), `.7z` | NASA public domain; original LWO2 layers assembled from LWS hierarchy at frame zero, construction polygons removed, new materials/UVs, truss presented horizontally |
| `hubble` | Hubble Space Telescope (B), `.glb` | NASA public domain; detailed servicing CAD cleaned to the telescope exterior, detached servicing geometry removed, original CC0 closed thermal bay doors, optical baffle and deployed SA3 wings added |
| `npp` | Suomi National Polar-orbiting Partnership (Suomi NPP), `.glb` | NASA public domain; new MLI/metal shaders and reviewed photographic hardware/solar atlases retained and packed |
| `aqua` | Aqua (A), `.glb` | NASA public domain; new PBR materials and physical solar-cell mapping |
| `terra` | Terra, `.glb` | NASA public domain; new PBR materials and physical solar-cell mapping |
| `aura` | Aura (A), `.glb` | NASA public domain; new PBR materials and physical solar-cell mapping |
| `landsat` | Landsat 8, `.glb` | NASA public domain; new PBR materials and physical solar-cell mapping |

Hubble B's `.7z` contains only a 3ds Max `.max` file. The NASA-provided glTF export
of the same B model is used because Blender cannot directly read `.max`. It has
hundreds of thousands of faces, unlike a simplified replacement. The original B
asset is a servicing illustration with detached pieces and no complete deployed
arrays; `nasa.complete_hubble` removes the detached exterior CAD and adds the
current rigid arrays using NASA's published **7.1 × 2.6 m** dimensions:
[Hubble electrical power](https://science.nasa.gov/mission/hubble/observatory/design/electrical-power/).

The ISS is the archive's 2011 configuration, not a claim to reproduce its changing
2026 docked-vehicle configuration. Dimensions of NASA source exports are normalized
to approximate published spacecraft envelopes. Original models are visual exterior
reconstructions rather than engineering CAD or instrument-accurate digital twins.

## Visual references (reference only; not redistributed or used as texture assets)

- [Meteor-M / VNIIEM](https://www.vniiem.ru/meteor-m-number2) and its labelled
  [instrument-layout illustration](https://r4uab.ru/meteor-m2-satellite/).
- [ESA: ASCAT's two sets of three antennae](https://www.esa.int/ESA_Multimedia/Images/2006/02/ASCAT_s_two_sets_of_three_antennae)
  and [EUMETSAT: Metop](https://www.eumetsat.int/our-satellites/metop-series).
- [China Manned Space: Tianhe](https://en.cmse.gov.cn/missions/CMTH/) and the
  Shenzhou-16 fly-around photograph of the completed station.
- [NASA: Hubble electrical power and SA3 arrays](https://science.nasa.gov/mission/hubble/observatory/design/electrical-power/).

Only NPP's two visually reviewed, NASA-supplied photo atlases are retained as
images. Other legacy image maps and decals are replaced by authored shaders.

## App integration

The new keys are **`jpss`, `so50`, `greencube`, `diwata`**. All fourteen keys have
turntables. Kotlin key mapping and the currently Meteor-only turntable reader are
intentionally left for the app integration described in the brief.
