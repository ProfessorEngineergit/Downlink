"""Tight-cropped 1024 px portraits for the intro, cut from the raw still renders."""
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
OUT = HERE.parent.parent / "app/src/main/res/drawable-nodpi"
for key in ("meteor", "iss", "tiangong", "metop", "hubble", "npp", "aqua", "landsat"):
    im = Image.open(HERE / "cache/renders" / key / "still.png").convert("RGBA")
    box = im.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
    im = im.crop(box)
    s = 1024 / max(im.size)
    im = im.resize((round(im.width * s), round(im.height * s)), Image.Resampling.LANCZOS)
    im.save(OUT / f"intro_{key}.webp", quality=88, alpha_quality=100, method=6)
    print(key, im.size, (OUT / f"intro_{key}.webp").stat().st_size)
