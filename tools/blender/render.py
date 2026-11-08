"""Downlink Cycles asset pipeline. Run with Blender 5.2, see README.md."""

import argparse
import hashlib
import json
import math
from pathlib import Path
import subprocess
import sys
import time

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import bpy
from mathutils import Vector, Euler, Matrix
from materials import palette
from models import BUILDERS
from sources import NASA, python
import nasa

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
# Base presentation poses, prior to a rotation around the image's vertical axis Z.
POSE = {
    "meteor": (115, -10, -14),
    "metop": (115, -12, -15),
    "jpss": (115, -8, -18),
    "tiangong": (59, 3, -8),
    "iss": (24, 90, 0),
    "hubble": (12, 0, -22),
    "npp": (15, 0, -25),
    "aqua": (15, 0, -22),
    "terra": (15, 0, -22),
    "aura": (15, 0, -22),
    "landsat": (15, 0, -22),
    "so50": (62, -15, -22),
    "greencube": (70, -15, -20),
    "diwata": (59, -12, -20),
}
# Normalize NASA source units to approximate published envelope spans in metres.
SPANS = {
    "iss": 109.0,
    "hubble": 13.2,
    "npp": 10.5,
    "aqua": 16.7,
    "terra": 14.5,
    "aura": 15.0,
    "landsat": 9.0,
}


def device(scene):
    prefs = bpy.context.preferences.addons["cycles"].preferences
    try:
        prefs.compute_device_type = "METAL"
        prefs.get_devices()
        gpu = False
        for d in prefs.devices:
            d.use = d.type == "METAL"
            gpu = gpu or d.use
        scene.cycles.device = "GPU" if gpu else "CPU"
    except (TypeError, RuntimeError):
        scene.cycles.device = "CPU"
    print("CYCLES DEVICE", scene.cycles.device, flush=True)


def lamp(name, kind, at, energy, color, size=1):
    data = bpy.data.lights.new(name, kind)
    data.energy = energy
    data.color = color
    if kind == "SUN":
        data.angle = math.radians(0.5)
    else:
        data.shape = "DISK"
        data.size = size
    o = bpy.data.objects.new(name, data)
    bpy.context.collection.objects.link(o)
    o.location = at
    o.rotation_euler = (-Vector(at)).to_track_quat("-Z", "Y").to_euler()


def studio(rebuild=False):
    path = HERE / "studio.blend"
    if path.exists() and not rebuild:
        bpy.ops.wm.open_mainfile(filepath=str(path))
        device(bpy.context.scene)
        return
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    sc.render.film_transparent = True
    sc.cycles.samples = 256
    sc.cycles.use_adaptive_sampling = True
    sc.cycles.adaptive_threshold = 0.035
    sc.cycles.adaptive_min_samples = 16
    sc.cycles.use_denoising = True
    sc.cycles.denoiser = "OPENIMAGEDENOISE"
    sc.cycles.max_bounces = 8
    sc.cycles.glossy_bounces = 4
    sc.cycles.transparent_max_bounces = 8
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA"
    sc.render.image_settings.color_depth = "16"
    sc.render.image_settings.compression = 35
    sc.render.use_persistent_data = True
    sc.view_settings.view_transform = "AgX"
    sc.view_settings.look = "AgX - Medium High Contrast"
    sc.view_settings.exposure = 0.35
    world = bpy.data.worlds.new("Earth / limb / deep space reflection environment")
    sc.world = world
    world.use_nodes = True
    nt = world.node_tree
    bg = nt.nodes.get("Background")
    bg.inputs["Strength"].default_value = 0.7
    tc = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(tc.outputs["Normal"], sep.inputs[0])
    remap = nt.nodes.new("ShaderNodeMapRange")
    remap.inputs["From Min"].default_value = -1
    remap.inputs["From Max"].default_value = 1
    nt.links.new(sep.outputs["Z"], remap.inputs[0])
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.elements.remove(ramp.color_ramp.elements[1])
    stops = [
        (0, (0.055, 0.14, 0.32, 1)),
        (0.38, (0.12, 0.27, 0.5, 1)),
        (0.49, (0.6, 0.72, 0.85, 1)),
        (0.55, (0.045, 0.06, 0.09, 1)),
        (1, (0.006, 0.009, 0.016, 1)),
    ]
    for i, (pos, col) in enumerate(stops):
        e = ramp.color_ramp.elements[0] if i == 0 else ramp.color_ramp.elements.new(pos)
        e.position = pos
        e.color = col
    nt.links.new(remap.outputs[0], ramp.inputs[0])
    nt.links.new(ramp.outputs[0], bg.inputs["Color"])
    lamp("SUN / 5800 K / 0.5 degree", "SUN", (7, -5, 9), 3.4, (1.0, 0.94, 0.85))
    lamp("EARTH / blue lower fill", "AREA", (-3, -4, -5), 450, (0.069, 0.262, 1.0), 8)
    lamp("LIMB / mint separation", "AREA", (-5, 4, 3), 650, (0.20, 0.70, 0.40), 5)
    cam = bpy.data.cameras.new("Portrait / 105 mm")
    cam.lens = 105
    cam.sensor_width = 36
    cam.sensor_fit = "HORIZONTAL"
    o = bpy.data.objects.new("Camera", cam)
    bpy.context.collection.objects.link(o)
    o.location = (0, -24, 0)
    o.rotation_euler = (math.pi / 2, 0, 0)
    sc.camera = o
    # Alpha-masked glare: compositing cannot grow an opaque halo beyond the sprite.
    sc.use_nodes = True
    tree = bpy.data.node_groups.new(
        "Downlink / alpha-safe highlight bloom", "CompositorNodeTree"
    )
    sc.compositing_node_group = tree
    rl = tree.nodes.new("CompositorNodeRLayers")
    glare = tree.nodes.new("CompositorNodeGlare")
    glare.inputs["Type"].default_value = "Fog Glow"
    glare.inputs["Quality"].default_value = "High"
    # Blender 5 uses sockets for glare controls; defaults are intentionally subtle.
    if "Threshold" in glare.inputs:
        glare.inputs["Threshold"].default_value = 3
    if "Strength" in glare.inputs:
        glare.inputs["Strength"].default_value = 0.035
    alpha = tree.nodes.new("CompositorNodeSetAlpha")
    alpha.inputs["Type"].default_value = "Replace Alpha"
    tree.links.new(rl.outputs["Image"], glare.inputs["Image"])
    tree.links.new(glare.outputs["Image"], alpha.inputs["Image"])
    tree.links.new(rl.outputs["Alpha"], alpha.inputs["Alpha"])
    tree.interface.new_socket(
        name="Image", in_out="OUTPUT", socket_type="NodeSocketColor"
    )
    out = tree.nodes.new("NodeGroupOutput")
    tree.links.new(alpha.outputs["Image"], out.inputs["Image"])
    device(sc)
    bpy.ops.wm.save_as_mainfile(filepath=str(path), compress=True)


def build(key, rebuild=False):
    path = HERE / "models" / f"{key}.blend"
    if path.exists() and not rebuild:
        return
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.context.scene.unit_settings.system = "METRIC"
    M = palette()
    if key in BUILDERS:
        BUILDERS[key](M)
    else:
        nasa.load(key, M)
    bpy.context.view_layer.update()
    geo = [o for o in bpy.context.scene.objects if o.type in ("MESH", "CURVE")]
    # Bake evaluated geometry and the original NASA assembly in one dependency graph.
    baked = []
    depsgraph = bpy.context.evaluated_depsgraph_get()
    for o in geo:
        mesh = bpy.data.meshes.new_from_object(
            o.evaluated_get(depsgraph),
            preserve_all_data_layers=True,
            depsgraph=depsgraph,
        )
        mesh.transform(o.matrix_world)
        new = bpy.data.objects.new(o.name, mesh)
        baked.append(new)
    for o in list(bpy.context.scene.objects):
        bpy.data.objects.remove(o, do_unlink=True)
    for o in baked:
        bpy.context.collection.objects.link(o)
    if key == "hubble":
        baked = nasa.complete_hubble(baked, M)
    # Geometry-centred origin with identity transforms, in real-world metre units.
    pts = [v.co for o in baked for v in o.data.vertices]
    lo = Vector(tuple(min(v[i] for v in pts) for i in range(3)))
    hi = Vector(tuple(max(v[i] for v in pts) for i in range(3)))
    center = (lo + hi) / 2
    scale = SPANS[key] / max(hi - lo) if key in SPANS else 1
    transform = Matrix.Scale(scale, 4) @ Matrix.Translation(-center)
    for o in baked:
        o.data.transform(transform)
        missing_uv = not o.data.uv_layers
        uv = (
            o.data.uv_layers.new(name="Metre-scaled planar cells")
            if missing_uv
            else o.data.uv_layers.active
        )
        for p in o.data.polygons:
            # NASA's untextured faces may have no UVs, or legacy atlas UVs which
            # collapse onto mortar lines. Physical planar UVs keep cell pitch
            # consistent across different mission models and real dimensions.
            solar = bool(
                len(o.data.materials)
                and "Photovoltaic" in o.data.materials[p.material_index].name
            )
            if missing_uv or (key in NASA and solar):
                axis = max(range(3), key=lambda i: abs(p.normal[i]))
                axes = [i for i in range(3) if i != axis]
                for li in p.loop_indices:
                    v = o.data.vertices[o.data.loops[li].vertex_index].co
                    uv.data[li].uv = (v[axes[0]], v[axes[1]])
    col = bpy.data.collections.new("SPACECRAFT / " + key)
    bpy.context.scene.collection.children.link(col)
    for o in baked:
        for c in list(o.users_collection):
            c.objects.unlink(o)
        col.objects.link(o)
    bpy.context.scene["downlink_key"] = key
    bpy.context.scene["license"] = (
        "NASA public domain; procedural material enhancements CC0"
        if key in NASA
        else "CC0-1.0"
    )
    bpy.context.scene["source"] = NASA.get(key, "Original Downlink geometry")
    bpy.data.orphans_purge(do_recursive=True)
    bpy.ops.file.pack_all()
    path.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(path), compress=True)
    print(
        "BUILT",
        key,
        len(baked),
        "objects",
        sum(len(o.data.polygons) for o in baked),
        "faces",
        flush=True,
    )


def load_model(key):
    with bpy.data.libraries.load(str(HERE / "models" / f"{key}.blend"), link=False) as (
        src,
        dst,
    ):
        dst.collections = ["SPACECRAFT / " + key]
    col = dst.collections[0]
    bpy.context.scene.collection.children.link(col)
    pts = [v.co for o in col.objects for v in o.data.vertices]
    lo = Vector(tuple(min(v[i] for v in pts) for i in range(3)))
    hi = Vector(tuple(max(v[i] for v in pts) for i in range(3)))
    norm = 6 / max(hi - lo)
    root = bpy.data.objects.new("Turntable / image vertical axis", None)
    bpy.context.scene.collection.objects.link(root)
    pose = Euler(tuple(math.radians(a) for a in POSE[key])).to_matrix().to_4x4()
    if key in NASA:
        # Legacy models use different coordinate conventions. Frame the actual
        # cell face, never a guessed Euler view of the white panel reverse.
        candidates = []
        for o in col.objects:
            for p in o.data.polygons:
                if len(o.data.materials):
                    name = o.data.materials[p.material_index].name.lower()
                    if "photovoltaic" in name or "shiny_panel" in name:
                        candidates.append(p)
        if candidates:
            normal = max(candidates, key=lambda p: p.area).normal.normalized()
            extent = hi - lo
            axis = max(
                range(3),
                key=lambda i: extent[i] * math.sqrt(max(0, 1 - normal[i] ** 2)),
            )
            horizontal = Vector(tuple(1 if i == axis else 0 for i in range(3)))
            if key == "iss":
                centers = []
                for o in col.objects:
                    if any(
                        t in o.name
                        for t in (
                            "s0-ani",
                            "s1-ani",
                            "p1-ani",
                            "p3-ani",
                            "p4-ani",
                            "s3-ani",
                            "s4-ani",
                            "p6-ani",
                            "s6-ani",
                        )
                    ):
                        centers.append(
                            sum((Vector(c) for c in o.bound_box), Vector()) / 8
                        )
                if len(centers) > 2:
                    horizontal = max(
                        (b - a for a in centers for b in centers),
                        key=lambda v: v.length,
                    ).normalized()
            horizontal = (horizontal - normal * horizontal.dot(normal)).normalized()
            source = Matrix((horizontal, normal.cross(horizontal), normal)).transposed()
            target_normal = Vector(
                (0, -0.86, 0.51) if key == "iss" else (0.15, -0.86, 0.49)
            ).normalized()
            # Aqua's slightly corrugated blankets otherwise reflect the sun
            # directly into the camera on alternating strips at the hero angle.
            if key == "aqua":
                target_normal = Vector((-0.28, -0.9, 0.32)).normalized()
            target_h = Vector((1, 0, 0) if key == "iss" else (0.98, 0, 0.18))
            target_h = (
                target_h - target_normal * target_h.dot(target_normal)
            ).normalized()
            target = Matrix(
                (target_h, target_normal.cross(target_h), target_normal)
            ).transposed()
            pose = (target @ source.transposed()).to_4x4()
    for o in col.objects:
        o.parent = root
        o.matrix_parent_inverse = Matrix.Identity(4)
        o.matrix_basis = Matrix.Scale(norm, 4) @ pose
    bpy.context.view_layer.update()
    return root, col


def frame(root, col, angles, aspect=1):
    # Analytically fit every corner at every turn angle using the perspective frustum.
    # Fixed distance and crop across the whole loop: never reframe individual frames.
    cam = bpy.context.scene.camera
    tanx = cam.data.sensor_width / (2 * cam.data.lens)
    tany = tanx / aspect
    distance = 0
    for angle in angles:
        root.rotation_euler.z = math.radians(angle)
        bpy.context.view_layer.update()
        for o in col.objects:
            for corner in o.bound_box:
                p = o.matrix_world @ Vector(corner)
                distance = max(
                    distance,
                    abs(p.x) / (0.83 * tanx) - p.y,
                    abs(p.z) / (0.83 * tany) - p.y,
                )
    cam.location = (0, -distance, 0)
    cam.data.clip_end = max(100, distance * 4)
    root.rotation_euler.z = 0
    bpy.context.view_layer.update()


def image(path, width, height, samples):
    sc = bpy.context.scene
    sc.render.resolution_x = width
    sc.render.resolution_y = height
    sc.render.resolution_percentage = 100
    sc.cycles.samples = samples
    sc.render.filepath = str(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    start = time.monotonic()
    bpy.ops.render.render(write_still=True)
    print(
        "RENDERED", path.name, round(time.monotonic() - start, 2), "seconds", flush=True
    )


def main():
    argv = sys.argv[sys.argv.index("--") + 1 :] if "--" in sys.argv else []
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("keys", nargs="*", choices=None)
    p.add_argument("--all", action="store_true")
    p.add_argument("--turntable", action="store_true")
    p.add_argument("--still-only", action="store_true")
    p.add_argument("--hero-only", action="store_true")
    p.add_argument("--build-only", action="store_true")
    p.add_argument("--rebuild", action="store_true")
    p.add_argument("--rebuild-studio", action="store_true")
    p.add_argument("--samples", type=int, default=256)
    p.add_argument("--resume", action="store_true")
    a = p.parse_args(argv)
    keys = list(KEYS) if a.all else (a.keys or ["meteor"])
    if any(k not in KEYS for k in keys):
        p.error("Unknown satellite key")
    if a.hero_only and keys != ["meteor"]:
        p.error("--hero-only requires the meteor key")
    for key in keys:
        build(key, a.rebuild)
    if a.build_only:
        return
    # --all delivers all loops by default. An individual key only renders its still
    # unless --turntable is supplied; still-only is useful for look development.
    turns = (a.all or a.turntable) and not a.still_only and not a.hero_only
    for key in keys:
        studio(a.rebuild_studio)
        root, col = load_model(key)
        raw = HERE / "cache/renders" / key
        raw.mkdir(parents=True, exist_ok=True)
        config = {
            "samples": a.samples,
            "engine": "CYCLES",
            "denoiser": "OPENIMAGEDENOISE",
            "supersampling": 2,
            "model_sha256": hashlib.sha256(
                (HERE / "models" / f"{key}.blend").read_bytes()
            ).hexdigest(),
            "studio_sha256": hashlib.sha256(
                (HERE / "studio.blend").read_bytes()
            ).hexdigest(),
            "pipeline_sha256": hashlib.sha256(
                b"".join(f.read_bytes() for f in sorted(HERE.glob("*.py")))
            ).hexdigest(),
        }
        manifest = raw / "render.json"
        previous = json.loads(manifest.read_text()) if manifest.exists() else {}
        completed = (
            previous.get("completed", [])
            if a.resume and previous.get("config") == config
            else []
        )

        def render_once(path, width, height):
            if path.name in completed and path.exists():
                return
            image(path, width, height, a.samples)
            completed.append(path.name)
            manifest.write_text(
                json.dumps({"config": config, "completed": completed}, indent=2) + "\n"
            )

        if not a.hero_only:
            frame(root, col, [0])
            still = raw / "still.png"
            render_once(still, 1024, 1024)
        if turns:
            frame(root, col, range(0, 360, 10))
            for i in range(36):
                path = raw / f"turn_{i:02d}.png"
                root.rotation_euler.z = math.radians(i * 10)
                render_once(path, 840, 840)
        if key == "meteor":
            root.rotation_euler.z = 0
            root.rotation_euler.y = math.radians(-35)
            frame(root, col, [0], 1080 / 1350)
            hero = raw / "hero.png"
            render_once(hero, 2160, 2700)
        command = [python(), str(HERE / "pack.py"), key]
        if a.hero_only:
            command.append("--hero-only")
        elif not turns:
            command.append("--still-only")
        subprocess.run(command, check=True)
    subprocess.run([python(), str(HERE / "pack.py"), "--sheets"], check=True)


if __name__ == "__main__":
    main()
