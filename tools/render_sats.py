"""Raytracte Satelliten-Sprites für Downlink (Blender, Cycles).

Aufruf:
  Blender -b -P tools/render_sats.py -- <modell-ordner> <ausgabe-ordner> [name ...] [--turntable name:frames]

NASA-Modelle (gemeinfrei, github.com/nasa/NASA-3D-Resources) werden importiert,
Meteor-M, Metop und Tiangong baut das Skript selbst, weil es dafür keine freien Modelle gibt.
"""
import bpy, bmesh, math, os, sys
from mathutils import Vector, Euler

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
MODELS, OUT = argv[0], argv[1]
rest = argv[2:]
turn = {}
names = []
i = 0
while i < len(rest):
    if rest[i] == "--turntable":
        n, f = rest[i + 1].split(":"); turn[n] = int(f); i += 2
    else:
        names.append(rest[i]); i += 1
os.makedirs(OUT, exist_ok=True)

# --- Szene -------------------------------------------------------------
def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = "CYCLES"
    prefs = bpy.context.preferences.addons["cycles"].preferences
    try:
        prefs.compute_device_type = "METAL"; prefs.get_devices()
        for d in prefs.devices: d.use = True
        sc.cycles.device = "GPU"
    except Exception:
        pass
    sc.cycles.samples = 96
    sc.cycles.use_denoising = True
    sc.render.film_transparent = True
    sc.render.resolution_x = sc.render.resolution_y = 640
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA"
    try: sc.view_settings.view_transform = "AgX"; sc.view_settings.look = "AgX - Punchy"
    except Exception: pass
    w = bpy.data.worlds.new("w"); sc.world = w
    w.use_nodes = True
    # Spiegelwelt: unten blaue Erde, oben schwarzer Raum. Unsichtbar im Bild, aber Metall braucht etwas zum Spiegeln.
    nt = w.node_tree; bg = nt.nodes["Background"]
    tc = nt.nodes.new("ShaderNodeTexCoord"); sep = nt.nodes.new("ShaderNodeSeparateXYZ"); ramp = nt.nodes.new("ShaderNodeValToRGB")
    nt.links.new(tc.outputs["Generated"], sep.inputs[0]); nt.links.new(sep.outputs["Z"], ramp.inputs["Fac"])
    ramp.color_ramp.elements[0].position = 0.38; ramp.color_ramp.elements[0].color = (0.12, 0.3, 0.6, 1)
    ramp.color_ramp.elements[1].position = 0.55; ramp.color_ramp.elements[1].color = (0.004, 0.006, 0.01, 1)
    e = ramp.color_ramp.elements.new(0.5); e.color = (0.5, 0.75, 1.0, 1)
    nt.links.new(ramp.outputs["Color"], bg.inputs[0]); bg.inputs[1].default_value = 0.9
    return sc

def light(name, kind, loc, energy, color, size=1.0, rot=None):
    d = bpy.data.lights.new(name, kind); d.energy = energy; d.color = color
    if kind == "AREA": d.size = size
    if kind == "SUN": d.angle = math.radians(1.5)
    o = bpy.data.objects.new(name, d); bpy.context.collection.objects.link(o)
    o.location = loc
    if rot: o.rotation_euler = rot
    else:
        dvec = Vector((0, 0, 0)) - Vector(loc); o.rotation_euler = dvec.to_track_quat("-Z", "Y").to_euler()
    return o

def rig():
    light("sun", "SUN", (6, -4, 5), 5.0, (1.0, 0.96, 0.9))           # Sonne: hartes Hauptlicht
    light("earth", "AREA", (-2, 3, -6), 900, (0.35, 0.6, 1.0), 12)    # Erdlicht von unten, blau
    light("rim", "AREA", (-6, 6, 3), 1400, (0.55, 1.0, 0.8), 4)       # Kante in Downlink-Mint

# --- Materialien ------------------------------------------------------------
def mat(name, base, metal=0.0, rough=0.4, bump=0.0, scale=40.0, emit=None, cells=None):
    m = bpy.data.materials.new(name); m.use_nodes = True
    nt = m.node_tree; p = nt.nodes["Principled BSDF"]
    p.inputs["Base Color"].default_value = (*base, 1)
    p.inputs["Metallic"].default_value = metal
    p.inputs["Roughness"].default_value = rough
    if cells:  # Solarzellen: Ziegelmuster als Zellraster
        tc = nt.nodes.new("ShaderNodeTexCoord"); br = nt.nodes.new("ShaderNodeTexBrick")
        br.inputs["Color1"].default_value = (*base, 1); br.inputs["Color2"].default_value = (*[c * 0.8 for c in base], 1)
        br.inputs["Mortar"].default_value = (0.75, 0.78, 0.82, 1); br.inputs["Scale"].default_value = cells
        br.inputs["Mortar Size"].default_value = 0.012; br.offset = 0.0; br.squash = 1.0
        br.inputs["Brick Width"].default_value = 0.5; br.inputs["Row Height"].default_value = 0.5
        nt.links.new(tc.outputs["UV"], br.inputs["Vector"])
        nt.links.new(br.outputs["Color"], p.inputs["Base Color"])
        p.inputs["Coat Weight"].default_value = 0.6; p.inputs["Coat Roughness"].default_value = 0.05
    if bump:  # zerknitterte Isolierfolie
        tc = nt.nodes.new("ShaderNodeTexCoord"); vo = nt.nodes.new("ShaderNodeTexVoronoi"); vo.inputs["Scale"].default_value = scale
        no = nt.nodes.new("ShaderNodeTexNoise"); no.inputs["Scale"].default_value = scale * 0.6; no.inputs["Detail"].default_value = 8
        mx = nt.nodes.new("ShaderNodeMath"); mx.operation = "ADD"
        bp = nt.nodes.new("ShaderNodeBump"); bp.inputs["Strength"].default_value = bump
        nt.links.new(tc.outputs["Object"], vo.inputs["Vector"]); nt.links.new(tc.outputs["Object"], no.inputs["Vector"])
        nt.links.new(vo.outputs["Distance"], mx.inputs[0]); nt.links.new(no.outputs["Fac"], mx.inputs[1])
        nt.links.new(mx.outputs[0], bp.inputs["Height"]); nt.links.new(bp.outputs["Normal"], p.inputs["Normal"])
    if emit:
        p.inputs["Emission Color"].default_value = (*emit, 1); p.inputs["Emission Strength"].default_value = 3
    return m

def materials():
    return dict(
        gold=mat("gold", (1.0, 0.72, 0.32), metal=1, rough=0.22, bump=0.35, scale=18),
        silver=mat("silver", (0.86, 0.87, 0.9), metal=1, rough=0.18, bump=0.25, scale=22),
        white=mat("white", (0.88, 0.88, 0.86), rough=0.55, bump=0.12, scale=12),
        dark=mat("dark", (0.06, 0.065, 0.07), metal=0.4, rough=0.35),
        alu=mat("alu", (0.75, 0.76, 0.78), metal=1, rough=0.32),
        cell=mat("cell", (0.03, 0.07, 0.2), metal=0.3, rough=0.12, cells=14),
        cellw=mat("cellw", (0.05, 0.11, 0.28), metal=0.3, rough=0.12, cells=6),
        lens=mat("lens", (0.02, 0.03, 0.05), metal=0.2, rough=0.05),
    )

# --- Grundformen --------------------------------------------------------------
def box(sx, sy, sz, loc, m, bevel=0.02, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
    o = bpy.context.object; o.scale = (sx, sy, sz)
    bpy.ops.object.transform_apply(scale=True)
    if bevel:
        b = o.modifiers.new("b", "BEVEL"); b.width = bevel; b.segments = 2
    o.data.materials.append(m)
    bpy.ops.object.shade_smooth() if bevel else None
    return o

def cyl(r, h, loc, m, rot=(0, 0, 0), verts=48):
    bpy.ops.mesh.primitive_cylinder_add(radius=r, depth=h, location=loc, rotation=rot, vertices=verts)
    o = bpy.context.object; o.data.materials.append(m)
    bpy.ops.object.shade_smooth()
    return o

def panel(w, h, loc, m, frame, rot=(0, 0, 0)):
    """Solarpaneel mit Rahmen. Breite w entlang x, Höhe h entlang y."""
    p = box(w, h, 0.03, loc, m, bevel=0, rot=rot)
    # UVs passend zur Größe, damit die Zellen quadratisch bleiben
    for poly in p.data.polygons:
        for li in poly.loop_indices:
            v = p.data.vertices[p.data.loops[li].vertex_index].co
            p.data.uv_layers.active.data[li].uv = (v.x / max(w, h) + 0.5, v.y / max(w, h) + 0.5) if p.data.uv_layers.active else (0, 0)
    f = box(w + 0.04, h + 0.04, 0.02, (loc[0], loc[1], loc[2] - 0.015), frame, bevel=0, rot=rot)
    return p

def wing(n, pw, ph, gap, start, axis, m, frame, z=0.0):
    """Flügel aus n Paneelen in Richtung axis (+1/-1 auf x)."""
    for k in range(n):
        x = start + axis * (pw / 2 + k * (pw + gap))
        panel(pw, ph, (x, 0, z), m, frame)
    # Ausleger
    box(abs(start) * 2 if False else abs(start), 0.05, 0.05, (axis * abs(start) / 2, 0, z), frame, bevel=0)

# --- Eigene Modelle ---------------------------------------------------------
def build_meteor(M):
    # Körper: Zylinder in Goldfolie, oben das Instrumentendeck
    cyl(0.62, 2.6, (0, 0, 0), M["gold"], rot=(math.pi / 2, 0, 0))
    cyl(0.66, 0.08, (0, 1.3, 0), M["silver"], rot=(math.pi / 2, 0, 0))
    cyl(0.66, 0.08, (0, -1.3, 0), M["silver"], rot=(math.pi / 2, 0, 0))
    box(1.3, 0.9, 0.8, (0, 1.75, 0), M["white"])               # Instrumentenblock
    box(0.5, 0.4, 0.35, (0.35, 2.25, 0.2), M["silver"])
    cyl(0.16, 0.25, (-0.3, 2.3, -0.15), M["lens"], rot=(math.pi / 2, 0, 0))   # Kamera MSU-MR
    cyl(0.2, 0.12, (-0.3, 2.42, -0.15), M["dark"], rot=(math.pi / 2, 0, 0))
    box(0.9, 0.6, 0.12, (0, -1.6, 0.35), M["alu"])            # Heck
    # zwei Solarflügel, je 3 Paneele, an Auslegern
    for s in (1, -1):
        box(0.9, 0.06, 0.06, (s * 1.05, 0.2, 0), M["alu"], bevel=0)
        for k in range(3):
            panel(1.1, 2.1, (s * (1.6 + k * 1.15), 0.2, 0), M["cell"], M["alu"])
    # Antennen: Stäbe der 137-MHz-Spirale und ein Mast
    for a in range(4):
        ang = a * math.pi / 2 + math.pi / 4
        cyl(0.02, 0.9, (0.35 * math.cos(ang), -1.75, 0.35 * math.sin(ang)), M["alu"], rot=(math.pi / 2, 0, 0), verts=8)
    cyl(0.025, 1.2, (0, -0.4, -1.1), M["alu"], verts=8)

def build_metop(M):
    box(1.9, 4.6, 1.8, (0, 0, 0), M["gold"], bevel=0.04)       # langer Körper
    box(1.95, 0.6, 1.85, (0, 2.0, 0), M["white"], bevel=0.03)
    box(1.2, 0.8, 0.6, (0, 2.6, 0.5), M["silver"])
    cyl(0.35, 0.5, (0.5, 2.6, -0.6), M["dark"], rot=(math.pi / 2, 0, 0))
    # ein einziger, langer Solarflügel
    box(0.06, 1.2, 0.06, (0, -2.9, 0), M["alu"], bevel=0)
    for k in range(8):
        panel(2.3, 1.15, (0, -3.6 - k * 1.2, 0), M["cell"], M["alu"])
    # ASCAT: lange weiße Antennenbalken schräg nach unten
    for s in (1, -1):
        box(0.25, 3.2, 0.06, (s * 1.7, 0.6, -0.9), M["white"], rot=(0, s * math.radians(28), 0))
    cyl(0.6, 0.06, (-0.6, 1.0, 1.0), M["white"])                # Schüssel
    cyl(0.03, 0.4, (-0.6, 1.0, 1.2), M["alu"], verts=8)

def build_tiangong(M):
    # Tianhe-Kernmodul entlang y, zwei Labormodule quer
    cyl(0.85, 5.0, (0, 0, 0), M["white"], rot=(math.pi / 2, 0, 0))
    cyl(1.05, 2.2, (0, -1.6, 0), M["white"], rot=(math.pi / 2, 0, 0))
    cyl(0.6, 1.0, (0, 3.0, 0), M["silver"], rot=(math.pi / 2, 0, 0))
    bpy.ops.mesh.primitive_uv_sphere_add(radius=0.75, location=(0, 3.7, 0)); s = bpy.context.object; s.data.materials.append(M["white"]); bpy.ops.object.shade_smooth()
    for sx in (1, -1):
        cyl(0.85, 5.5, (sx * 3.4, 3.7, 0), M["white"], rot=(0, math.pi / 2, 0))
        cyl(0.88, 0.6, (sx * 5.6, 3.7, 0), M["gold"], rot=(0, math.pi / 2, 0))
        # riesige Flügel an den Labormodulen
        for k in range(2):
            for sz in (1, -1):
                panel(1.6, 6.5, (sx * (4.5 + k * 1.8), 3.7, sz * 4.2), M["cellw"], M["alu"], rot=(math.pi / 2, 0, 0))
        box(0.08, 0.08, 7.0, (sx * 5.4, 3.7, 0), M["alu"], bevel=0)
    # kleinere Flügel am Kernmodul
    for sx in (1, -1):
        for k in range(2):
            panel(1.2, 3.2, (sx * (1.9 + k * 1.3), -1.0, 0), M["cellw"], M["alu"])

BUILDERS = {"meteor": build_meteor, "metop": build_metop, "tiangong": build_tiangong}

# --- Import und Rahmen -------------------------------------------------------
def import_glb(path):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    return [o for o in bpy.data.objects if o not in before]

def frame_all(rot):
    objs = [o for o in bpy.data.objects if o.type == "MESH"]
    # alles unter einen Leer-Knoten, damit wir drehen können
    root = bpy.data.objects.new("root", None); bpy.context.collection.objects.link(root)
    for o in bpy.data.objects:
        if o.parent is None and o is not root and o.type not in ("LIGHT", "CAMERA"):
            o.parent = root
    bpy.context.view_layer.update()
    pts = [o.matrix_world @ Vector(c) for o in objs for c in o.bound_box]
    lo = Vector((min(p.x for p in pts), min(p.y for p in pts), min(p.z for p in pts)))
    hi = Vector((max(p.x for p in pts), max(p.y for p in pts), max(p.z for p in pts)))
    ctr = (lo + hi) / 2; size = max(hi - lo)
    s = 5.4 / size
    root.scale = (s, s, s); root.location = -ctr * s
    pivot = bpy.data.objects.new("pivot", None); bpy.context.collection.objects.link(pivot)
    root.parent = pivot
    pivot.rotation_euler = Euler([math.radians(a) for a in rot])
    return pivot

def camera():
    c = bpy.data.cameras.new("cam"); c.lens = 85
    o = bpy.data.objects.new("cam", c); bpy.context.collection.objects.link(o)
    o.location = (0, -15.5, 0); o.rotation_euler = (math.radians(90), 0, 0)
    bpy.context.scene.camera = o

EXCLUDE = {"iss": ("bendedtru", "pCylinder")}  # Hilfsgeometrie im NASA-Modell

# name: (datei oder builder, Drehung x/y/z in Grad)
CATALOG = {
    "iss": ("InternationalSpaceStationISSB.glb", (30, 0, 75)),
    "hubble": ("HubbleSpaceTelescopeA.glb", (15, 30, 35)),
    "npp": ("SuomiNationalPolarorbitingPartnershipSuomiNPP.glb", (15, 0, 35)),
    "aqua": ("AquaA.glb", (15, 0, 35)),
    "terra": ("Terra.glb", (15, 0, 35)),
    "aura": ("AuraA.glb", (15, 0, 35)),
    "landsat": ("Landsat8.glb", (15, 0, 35)),
    "cube1": ("CubeSat1RUGeneric.glb", (25, 0, 35)),
    "cube2": ("CubeSat2RUGeneric.glb", (25, 0, 35)),
    "meteor": ("meteor", (55, 0, 25)),
    "metop": ("metop", (50, 0, 60)),
    "tiangong": ("tiangong", (20, 0, 25)),
}

for name in (names or list(CATALOG)):
    src, rot = CATALOG[name]
    sc = reset(); M = materials()
    if src in BUILDERS: BUILDERS[src](M)
    else:
        for o in import_glb(os.path.join(MODELS, src)):
            if any(o.name.startswith(x) for x in EXCLUDE.get(name, ())): bpy.data.objects.remove(o)
    pivot = frame_all(rot); camera(); rig()
    frames = turn.get(name, 0)
    if frames:
        sc.render.resolution_x = sc.render.resolution_y = 480
        sc.cycles.samples = 48
        base = pivot.rotation_euler.copy()
        for f in range(frames):
            pivot.rotation_euler = Euler((base.x, base.y, base.z + 2 * math.pi * f / frames))
            sc.render.filepath = os.path.join(OUT, f"{name}_turn_{f:02d}.png")
            bpy.ops.render.render(write_still=True)
    else:
        sc.render.filepath = os.path.join(OUT, f"{name}.png")
        bpy.ops.render.render(write_still=True)
    print("RENDERED", name)
