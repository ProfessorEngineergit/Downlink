"""Alpha-safe supersample packing, contact sheets, and delivery validation.

Runs with host Python/Pillow, separately from Blender. Work in premultiplied
RGBA while filtering and restore straight alpha before writing WebP.
"""

import argparse
import hashlib
import json
import struct
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
KEYS = (
    "meteor",
    "iss",
    "tiangong",
    "metop",
    "hubble",
    "npp",
    "jpss",
    "aqua",
    "terra",
    "aura",
    "landsat",
    "so50",
    "greencube",
    "diwata",
)
STILLS = ROOT / "app/src/main/res/drawable-nodpi"
TURNS = ROOT / "app/src/main/assets/turn"


def bounds(paths):
    boxes = []
    for path in paths:
        with Image.open(path) as im:
            b = im.getchannel("A").point(lambda v: 255 if v > 2 else 0).getbbox()
            if b is None:
                raise ValueError(f"Empty render: {path}")
            if b[0] < 2 or b[1] < 2 or b[2] > im.width - 2 or b[3] > im.height - 2:
                raise ValueError(f"Clipped render: {path}")
            boxes.append(b)
    return (
        min(b[0] for b in boxes),
        min(b[1] for b in boxes),
        max(b[2] for b in boxes),
        max(b[3] for b in boxes),
    )


def crop_rect(b, aspect=1):
    x, y, r, t = b
    w = r - x
    h = t - y
    h = max(h, w / aspect) / 0.88
    w = h * aspect
    cx = (x + r) / 2
    cy = (y + t) / 2
    return (round(cx - w / 2), round(cy - h / 2), round(cx + w / 2), round(cy + h / 2))


def pack_image(path, dest, size, rect, limit):
    with Image.open(path) as source:
        im = (
            source.convert("RGBA")
            .crop(rect)
            .convert("RGBa")
            .resize(
                size,
                (
                    Image.Resampling.BICUBIC
                    if size == (420, 420)
                    else Image.Resampling.LANCZOS
                ),
            )
            .convert("RGBA")
        )
    # Clean hidden RGB; avoids accidental halos when other software reads straight alpha.
    pixels = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = pixels[x, y]
            if a == 0:
                pixels[x, y] = (0, 0, 0, 0)
    dest.parent.mkdir(parents=True, exist_ok=True)
    temp = dest.with_suffix(".tmp.webp")
    encodings = [(86, 100), (82, 100), (78, 100), (74, 100), (70, 100)]
    if size == (420, 420):
        # High-res ISS/Hubble silhouettes alone can exceed 20 KiB with lossless
        # alpha. Quantize coverage gradually before sacrificing surface detail.
        # RGB stays straight and filtering has already happened premultiplied.
        encodings += [
            (82, 70),
            (78, 65),
            (74, 60),
            (70, 55),
            (66, 50),
            (62, 45),
            (54, 35),
        ]
    else:
        encodings += [(66, 100), (62, 100), (58, 100), (54, 100), (50, 100)]
    for quality, alpha_quality in encodings:
        # libwebp's exhaustive lossless-alpha search at method 6 can take
        # seconds per candidate for fine spacecraft silhouettes. Method 4 has
        # the same lossless coverage and keeps budget fitting inexpensive.
        method = 4 if size == (420, 420) and alpha_quality == 100 else 6
        im.save(
            temp,
            "WEBP",
            quality=quality,
            method=method,
            exact=True,
            alpha_quality=alpha_quality,
        )
        if temp.stat().st_size <= limit:
            temp.replace(dest)
            return {
                "quality": quality,
                "alpha_quality": alpha_quality,
                "method": method,
                "bytes": dest.stat().st_size,
            }
    raise ValueError(f"Cannot meet size budget: {dest} ({temp.stat().st_size})")


def pack_key(key, still=True, turn=True, hero=True):
    raw = HERE / "cache/renders" / key
    quality_path = raw / "packing.json"
    qualities = json.loads(quality_path.read_text()) if quality_path.exists() else {}
    if still:
        paths = [raw / "still.png"]
        rect = crop_rect(bounds(paths))
        qualities["still"] = pack_image(
            paths[0], STILLS / f"sat_{key}.webp", (512, 512), rect, 60 * 1024
        )
    if turn:
        paths = [raw / f"turn_{i:02d}.png" for i in range(36)]
        rect = crop_rect(bounds(paths))
        qualities["turntable"] = [
            pack_image(p, TURNS / f"{key}_{i:02d}.webp", (420, 420), rect, 20 * 1024)
            for i, p in enumerate(paths)
        ]
        qualities["turntable_crop"] = rect
    if hero and key == "meteor":
        p = raw / "hero.png"
        rect = crop_rect(bounds([p]), 1080 / 1350)
        qualities["hero"] = pack_image(
            p, STILLS / "hero_meteor.webp", (1080, 1350), rect, 300 * 1024
        )
    (raw / "packing.json").write_text(json.dumps(qualities, indent=2) + "\n")
    print(
        "PACKED",
        key,
        "still",
        qualities.get("still"),
        "turntable frames",
        len(qualities.get("turntable", [])),
        flush=True,
    )


def sheets():
    font = (
        ImageFont.truetype("/System/Library/Fonts/Menlo.ttc", 15)
        if Path("/System/Library/Fonts/Menlo.ttc").exists()
        else ImageFont.load_default()
    )
    sheet = Image.new("RGB", (1792, 1152), (4, 7, 13))
    draw = ImageDraw.Draw(sheet)
    for i, key in enumerate(KEYS):
        p = STILLS / f"sat_{key}.webp"
        if not p.exists():
            continue
        with Image.open(p) as im:
            for side, color in enumerate(((4, 7, 13), (23, 62, 120))):
                x = side * 896 + (i % 4) * 224
                y = (i // 4) * 280 + 24
                tile = Image.new("RGBA", (224, 250), (*color, 255))
                sprite = (
                    im.convert("RGBa")
                    .resize((210, 210), Image.Resampling.LANCZOS)
                    .convert("RGBA")
                )
                tile.alpha_composite(sprite, (7, 0))
                sheet.paste(tile.convert("RGB"), (x, y))
                draw.text((x + 12, y + 218), key, font=font, fill=(205, 220, 235))
    draw.text(
        (16, 6),
        "DOWNLINK / ORBITAL PORTRAITS / #04070D",
        font=font,
        fill=(130, 180, 190),
    )
    draw.text((912, 6), "DAY SKY / #173E78", font=font, fill=(160, 205, 235))
    sheet.save(HERE / "contact_sheet.png")
    tiny = Image.new("RGB", (1120, 200), (4, 7, 13))
    d = ImageDraw.Draw(tiny)
    for i, key in enumerate(KEYS):
        p = STILLS / f"sat_{key}.webp"
        if not p.exists():
            continue
        im = Image.open(p).convert("RGBA")
        x = i * 80 + 18
        for y, color in ((16, (4, 7, 13)), (106, (23, 62, 120))):
            tile = Image.new("RGBA", (80, 88), (*color, 255))
            tile.alpha_composite(
                im.convert("RGBa")
                .resize((44, 44), Image.Resampling.LANCZOS)
                .convert("RGBA"),
                (18, 2),
            )
            tiny.paste(tile.convert("RGB"), (i * 80, y))
            d.text((i * 80 + 3, y + 51), key, fill=(215, 225, 235))
    tiny.save(HERE / "preview_44px.png")
    # Six evenly spaced views make the reverse faces and stable framing reviewable.
    turns = Image.new("RGB", (896, 14 * 132 + 28), (4, 7, 13))
    td = ImageDraw.Draw(turns)
    for column, angle in enumerate(range(0, 360, 60)):
        td.text((104 + column * 132, 7), f"{angle:03d} deg", fill=(160, 195, 215))
    for row, key in enumerate(KEYS):
        td.text((7, row * 132 + 83), key, fill=(210, 225, 235))
        for column, index in enumerate(range(0, 36, 6)):
            path = TURNS / f"{key}_{index:02d}.webp"
            if not path.exists():
                continue
            with Image.open(path) as source:
                sprite = (
                    source.convert("RGBa")
                    .resize((124, 124), Image.Resampling.BICUBIC)
                    .convert("RGBA")
                )
            tile = Image.new("RGBA", (124, 124), (4, 7, 13, 255))
            tile.alpha_composite(sprite)
            turns.paste(tile.convert("RGB"), (100 + column * 132, row * 132 + 28))
    turns.save(HERE / "turntable_sheet.png")


def validate():
    report = {"keys": {}, "errors": []}
    for key in KEYS:
        files = [(STILLS / f"sat_{key}.webp", (512, 512), 60 * 1024)]
        files += [
            (TURNS / f"{key}_{i:02d}.webp", (420, 420), 20 * 1024) for i in range(36)
        ]
        count = 0
        maxbytes = 0
        for p, size, limit in files:
            if not p.exists():
                report["errors"].append("Missing " + str(p.relative_to(ROOT)))
                continue
            im = Image.open(p)
            if im.size != size or im.mode != "RGBA":
                report["errors"].append("Dimensions/alpha: " + p.name)
            if p.stat().st_size > limit:
                report["errors"].append("Size budget: " + p.name)
            bbox = im.getchannel("A").getbbox()
            if (
                not bbox
                or min(bbox[:2]) < 3
                or bbox[2] > size[0] - 3
                or bbox[3] > size[1] - 3
            ):
                report["errors"].append("Crop: " + p.name)
            count += 1
            maxbytes = max(maxbytes, p.stat().st_size)
        for model in [HERE / "models" / f"{key}.blend"]:
            if not model.exists():
                report["errors"].append("Missing model: " + key)
        report["keys"][key] = {"images": count, "maximum_bytes": maxbytes}
        turn_paths = [TURNS / f"{key}_{i:02d}.webp" for i in range(36)]
        if all(path.exists() for path in turn_paths):
            unique = len(
                {hashlib.sha256(path.read_bytes()).digest() for path in turn_paths}
            )
            if unique != 36:
                report["errors"].append(
                    f"Duplicate turntable frames: {key} ({unique}/36)"
                )
            report["keys"][key]["turntable_maximum_bytes"] = max(
                p.stat().st_size for p in turn_paths
            )
            report["keys"][key]["unique_frames"] = unique
        packing = HERE / "cache/renders" / key / "packing.json"
        if packing.exists():
            encoded = json.loads(packing.read_text()).get("turntable", [])
            if encoded:
                report["keys"][key]["minimum_rgb_quality"] = min(
                    e["quality"] if isinstance(e, dict) else e for e in encoded
                )
                report["keys"][key]["minimum_alpha_quality"] = min(
                    e.get("alpha_quality", 100) if isinstance(e, dict) else 100
                    for e in encoded
                )
        # When intermediates are present, check that production really used
        # 16-bit RGBA at 2x. The ignored cache is optional in a fresh checkout.
        raw = HERE / "cache/renders" / key
        for path, size in [(raw / "still.png", (1024, 1024))] + [
            (raw / f"turn_{i:02d}.png", (840, 840)) for i in range(36)
        ]:
            if path.exists():
                with path.open("rb") as png:
                    header = png.read(26)
                if (
                    header[:8] != b"\x89PNG\r\n\x1a\n"
                    or struct.unpack(">II", header[16:24]) != size
                    or header[24:26] != bytes((16, 6))
                ):
                    report["errors"].append(
                        "Intermediate format: " + str(path.relative_to(HERE))
                    )
    hero = STILLS / "hero_meteor.webp"
    if not hero.exists():
        report["errors"].append("Missing/invalid hero")
    else:
        with Image.open(hero) as hero_image:
            if (
                hero_image.size != (1080, 1350)
                or hero_image.mode != "RGBA"
                or hero_image.getchannel("A").getextrema() != (0, 255)
            ):
                report["errors"].append("Hero dimensions/alpha")
        report["hero_bytes"] = hero.stat().st_size
    (HERE / "validation.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))
    if report["errors"]:
        raise SystemExit(1)


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("key", nargs="?", choices=KEYS)
    p.add_argument("--still-only", action="store_true")
    p.add_argument("--hero-only", action="store_true")
    p.add_argument("--no-hero", action="store_true")
    p.add_argument("--sheets", action="store_true")
    p.add_argument("--validate", action="store_true")
    a = p.parse_args()
    if a.key:
        pack_key(
            a.key,
            still=not a.hero_only,
            turn=not (a.still_only or a.hero_only),
            hero=not a.no_hero,
        )
    if a.sheets:
        sheets()
    if a.validate:
        validate()
