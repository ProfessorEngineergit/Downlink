"""Blender-side validation of all editable delivery scenes (no rendering)."""

from pathlib import Path
import json
import bpy
from mathutils import Vector, Matrix

HERE = Path(__file__).resolve().parent
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
report = {}
errors = []
for key in KEYS:
    bpy.ops.wm.open_mainfile(filepath=str(HERE / "models" / (key + ".blend")))
    scene = bpy.context.scene
    objects = list(scene.objects)
    if scene.unit_settings.system != "METRIC":
        errors.append(key + ": units")
    if "SPACECRAFT / " + key not in bpy.data.collections:
        errors.append(key + ": collection")
    for o in objects:
        if o.type != "MESH":
            errors.append(key + ": non-geometry " + o.name)
            continue
        if any(
            abs(o.matrix_world[i][j] - Matrix.Identity(4)[i][j]) > 1e-5
            for i in range(4)
            for j in range(4)
        ):
            errors.append(key + ": unapplied transform " + o.name)
        if o.data.validate(verbose=False):
            errors.append(key + ": invalid mesh " + o.name)
        if (
            any("Photovoltaic" in m.name for m in o.data.materials)
            and not o.data.uv_layers
        ):
            errors.append(key + ": solar geometry missing UV " + o.name)
    pts = [v.co for o in objects if o.type == "MESH" for v in o.data.vertices]
    lo = Vector(tuple(min(v[i] for v in pts) for i in range(3)))
    hi = Vector(tuple(max(v[i] for v in pts) for i in range(3)))
    if ((lo + hi) / 2).length > 1e-4:
        errors.append(key + ": off-centre geometry")
    for image in bpy.data.images:
        if image.source == "FILE" and image.users and not image.packed_file:
            errors.append(key + ": external image " + image.name)
    report[key] = {
        "objects": len(objects),
        "faces": sum(len(o.data.polygons) for o in objects if o.type == "MESH"),
        "dimensions_metres": [round(v, 3) for v in hi - lo],
        "license": scene.get("license"),
    }
out = {"models": report, "errors": errors}
(HERE / "model_validation.json").write_text(json.dumps(out, indent=2) + "\n")
print(json.dumps(out, indent=2), flush=True)
if errors:
    raise RuntimeError("Model validation failed")
