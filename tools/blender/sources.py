"""Pinned NASA assets. Downloads stay outside version control."""

import hashlib
import json
from pathlib import Path
import subprocess
import urllib.parse
import urllib.request

HERE = Path(__file__).resolve().parent
CACHE = HERE / "cache"
REVISION = "11ebb4ee043715aefbba6aeec8a61746fad67fa7"
NASA = {
    "iss": "International Space Station (ISS) (C) (High Res)/International Space Station (ISS) (C) (High Res).7z",
    "hubble": "Hubble Space Telescope (B)/Hubble Space Telescope (B).glb",
    "npp": "Suomi National Polar-orbiting Partnership (Suomi NPP)/Suomi National Polar-orbiting Partnership (Suomi NPP).glb",
    "aqua": "Aqua (A)/Aqua (A).glb",
    "terra": "Terra/Terra.glb",
    "aura": "Aura (A)/Aura (A).glb",
    "landsat": "Landsat 8/Landsat 8.glb",
}


def python():
    """Use an explicit host Python, never attempt pip inside Blender."""
    import os

    candidate = Path(os.environ.get("DOWNLINK_PYTHON", HERE / ".venv/bin/python"))
    if not candidate.exists():
        raise RuntimeError(
            "Run python3 -m venv tools/blender/.venv and .venv/bin/pip install -r tools/blender/requirements.txt"
        )
    return str(candidate)


def ensure(key):
    relative = NASA[key]
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / (key + Path(relative).suffix)
    url = (
        "https://raw.githubusercontent.com/nasa/NASA-3D-Resources/"
        + REVISION
        + "/"
        + urllib.parse.quote("3D Models/" + relative)
    )
    if not path.exists():
        print("Downloading", key, url, flush=True)
        temp = path.with_suffix(path.suffix + ".part")
        urllib.request.urlretrieve(url, temp)
        temp.replace(path)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    lockpath = HERE / "sources.lock.json"
    lock = json.loads(lockpath.read_text()) if lockpath.exists() else {}
    expected = lock.get(key, {}).get("sha256")
    if expected and digest != expected:
        raise RuntimeError(f"NASA download checksum mismatch: {key}")
    lock[key] = {
        "path": "3D Models/" + relative,
        "revision": REVISION,
        "sha256": digest,
        "url": url,
    }
    lockpath.write_text(json.dumps(lock, indent=2) + "\n")
    if key == "iss":
        dest = CACHE / key
        if not (dest / "Scenes/ISS complete_2011.lws").exists():
            subprocess.run(
                [
                    python(),
                    "-c",
                    "import py7zr,sys; a=py7zr.SevenZipFile(sys.argv[1]); a.extractall(sys.argv[2]); a.close()",
                    str(path),
                    str(dest),
                ],
                check=True,
            )
        return dest
    return path
