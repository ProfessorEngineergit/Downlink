"""NASA glTF and a dependency-free LWO2/LWS importer for the high-res ISS.

The ISS archive contains the original LightWave assembly, not a complete glTF.
Import only geometry and static transforms at frame zero. All legacy textures,
lights, cameras, decals and image maps are intentionally replaced.
"""

import re
import struct
import bpy
from mathutils import Matrix, Vector
from materials import identify
from sources import ensure


def s0(data, pos=0):
    end = data.index(b"\0", pos)
    return data[pos:end].decode("latin1"), end + 1 + (end + 1 - pos) % 2


def vx(data, pos):
    if data[pos] == 255:
        return int.from_bytes(data[pos + 1 : pos + 4], "big"), pos + 4
    return int.from_bytes(data[pos : pos + 2], "big"), pos + 2


def lwo(path):
    b = path.read_bytes()
    if b[:4] != b"FORM" or b[8:12] != b"LWO2":
        raise ValueError(f"Unsupported NASA object: {path}")
    layers = {}
    tags = []
    current = None
    pos = 12
    while pos + 8 <= len(b):
        typ = b[pos : pos + 4]
        n = struct.unpack_from(">I", b, pos + 4)[0]
        d = b[pos + 8 : pos + 8 + n]
        pos += 8 + n + n % 2
        if typ == b"TAGS":
            k = 0
            while k < len(d):
                t, k = s0(d, k)
                tags.append(t)
        elif typ == b"LAYR":
            number = struct.unpack_from(">H", d)[0]
            current = {
                "verts": [],
                "faces": [],
                "surf": {},
                "pivot": struct.unpack_from(">3f", d, 4),
            }
            layers[number] = current
        elif current is not None and typ == b"PNTS":
            current["verts"] = list(struct.iter_unpack(">3f", d))
        elif (
            current is not None
            and typ == b"POLS"
            and d[:4] in (b"FACE", b"PTCH", b"SUBD")
        ):
            k = 4
            while k < len(d):
                count = struct.unpack_from(">H", d, k)[0] & 1023
                k += 2
                face = []
                for _ in range(count):
                    v, k = vx(d, k)
                    face.append(v)
                current["faces"].append(face)
        elif current is not None and typ == b"PTAG" and d[:4] == b"SURF":
            k = 4
            while k < len(d):
                p, k = vx(d, k)
                t = struct.unpack_from(">H", d, k)[0]
                k += 2
                current["surf"][p] = t
    return layers, tags


def import_iss(M):
    root = ensure("iss")
    text = (root / "Scenes/ISS complete_2011.lws").read_text()
    chunks = re.split(
        r"(?=^(?:AddNullObject|LoadObjectLayer|AddLight|AddCamera) )", text, flags=re.M
    )
    objects = {}
    parents = {}
    meshes = {}
    conv = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, 1, 0, 0), (0, 0, 0, 1)))
    for block in chunks:
        match = re.match(r"LoadObjectLayer (\d+) (\S+) (.+)", block)
        null = re.match(r"AddNullObject (\S+) (.+)", block)
        if not (match or null):
            continue
        if match:
            layer, ident, relative = match.groups()
            path = root / relative.strip()
            if path not in meshes:
                meshes[path] = lwo(path)
            layers, tags = meshes[path]
            data = layers.get(int(layer) - 1)
            if data is None:
                raise ValueError(f"Missing layer {layer} in {path}")
            mesh = bpy.data.meshes.new(path.stem + " / layer " + layer)
            # Mirror handedness, reverse winding. Use the assembly's transforms below.
            # Legacy LWO layers can contain 1/2-point construction polygons and
            # repeated indices. They are not renderable faces and must be removed.
            valid = []
            surfaces = []
            for i, face in enumerate(data["faces"]):
                if len(set(face)) >= 3 and all(
                    0 <= v < len(data["verts"]) for v in face
                ):
                    valid.append(tuple(reversed(face)))
                    surfaces.append(data["surf"].get(i, 0))
            mesh.from_pydata(data["verts"], [], valid)
            mesh.validate(verbose=False)
            mesh.update()
            for tag in tags:
                mesh.materials.append(identify(tag, M))
            for i, p in enumerate(mesh.polygons):
                p.material_index = min(surfaces[i], max(0, len(tags) - 1))
                # Smooth rounded hulls while preserving the flat truss and radiators.
                p.use_smooth = any(
                    t in tags[p.material_index].lower()
                    for t in ("allum", "hull", "cone", "tank")
                )
            o = bpy.data.objects.new("ISS / " + path.stem + " / " + layer, mesh)
        else:
            ident, name = null.groups()
            o = bpy.data.objects.new(name.strip(), None)
        bpy.context.collection.objects.link(o)
        objects[ident] = o
        values = [0.0] * 6 + [1.0] * 3
        for ch, body in re.findall(
            r"Channel (\d+)\s*\{ Envelope(.*?)\n\}", block, re.S
        ):
            keys = re.findall(r"Key ([-+\d.eE]+) ([-+\d.eE]+)", body)
            if keys:
                values[int(ch)] = float(min(keys, key=lambda k: abs(float(k[1])))[0])
        x, y, z, h, p, b, sx, sy, sz = values
        pivot = re.search(r"^PivotPosition (.+)", block, re.M)
        pv = Vector(tuple(map(float, pivot[1].split()))) if pivot else Vector((0, 0, 0))
        # LightWave heading/pitch/bank, applied in its native coordinate basis.
        matrix = (
            Matrix.Translation((x, y, z))
            @ Matrix.Rotation(h, 4, "Y")
            @ Matrix.Rotation(p, 4, "X")
            @ Matrix.Rotation(b, 4, "Z")
            @ Matrix.Diagonal((sx, sy, sz, 1))
            @ Matrix.Translation(-pv)
        )
        o.matrix_local = matrix
        parent = re.search(r"^ParentItem (\S+)", block, re.M)
        if parent:
            parents[ident] = parent[1]
    for ident, parent in parents.items():
        if parent in objects:
            objects[ident].parent = objects[parent]
    # Coordinate conversion on a single root preserves all parent-child transforms.
    assembly = bpy.data.objects.new("NASA ISS / LightWave coordinate basis", None)
    bpy.context.collection.objects.link(assembly)
    for ident, o in objects.items():
        if not o.parent:
            o.parent = assembly
    assembly.matrix_world = conv
    bpy.context.view_layer.update()
    # Remove any nominal camera/sun pivots; imported assembly meshes only survive save.


def import_gltf(key, M):
    bpy.ops.import_scene.gltf(filepath=str(ensure(key)))
    for o in list(bpy.context.scene.objects):
        if o.type in ("LIGHT", "CAMERA"):
            bpy.data.objects.remove(o, do_unlink=True)
            continue
        if o.type != "MESH":
            continue
        if key == "hubble":
            n = o.name.lower()
            mat = (
                M["cell"]
                if n.startswith("sa") and any(c.isdigit() for c in n)
                else M["silver"]
            )
            if any(
                t in n for t in ("handle", "latch", "hinge", "mast", "boom", "grapple")
            ):
                mat = M["alu"]
            if any(
                t in n for t in ("shade", "baffle", "mirror", "aperture", "-inside")
            ):
                mat = M["dark"]
            o.data.materials.clear()
            o.data.materials.append(mat)
            for p in o.data.polygons:
                p.material_index = 0
        elif key == "npp":
            for slot in o.material_slots:
                old = slot.material
                if not old:
                    continue
                n = old.name.lower()
                if n.startswith(("tex_01", "tex_02", "shiny_panel")):
                    # Visually reviewed atlases: hardware, fasteners and solar
                    # cells. Neither atlas contains agency lettering or flags.
                    material = old.copy()
                    material.name = "NPP / reviewed photographic hardware / " + n
                    principled = material.node_tree.nodes.get("Principled BSDF")
                    if principled:
                        principled.inputs["Metallic"].default_value = (
                            0.55 if "shiny" in n else 0.3
                        )
                        principled.inputs["Roughness"].default_value = (
                            0.25 if "shiny" in n else 0.43
                        )
                        principled.inputs["Coat Weight"].default_value = 0.25
                    slot.material = material
                elif n.startswith("ao_01"):
                    slot.material = M["titanium"]
                else:
                    slot.material = identify(n, M)
        else:
            for slot in o.material_slots:
                old = slot.material
                if old:
                    slot.material = identify(old.name, M, old.diffuse_color[:3])


def load(key, M):
    if key == "iss":
        import_iss(M)
    else:
        import_gltf(key, M)


def complete_hubble(objects, M):
    """Turn NASA B's servicing CAD into an orbital exterior.

    B includes detached servicing hardware and lacks deployed solar blankets.
    Keep the detailed telescope, trim the detached CAD, add original SA3 wings
    using NASA's published 7.1 x 2.6 metre dimensions.
    """
    import bmesh
    import math
    from geometry import panel, rod, cyl, box

    shroud = next(o for o in objects if "-SHROUD" in o.name)
    pts = [v.co.copy() for v in shroud.data.vertices]
    lo = Vector(tuple(min(v[i] for v in pts) for i in range(3)))
    hi = Vector(tuple(max(v[i] for v in pts) for i in range(3)))
    radius = (hi.y - lo.y) / 2
    unit = radius / 2.1
    cy = (hi.y + lo.y) / 2
    cz = (hi.z + lo.z) / 2
    matrix = Matrix.Scale(1 / unit, 4) @ Matrix.Translation(
        (-lo.x - 6.6 * unit, -cy, -cz)
    )
    keep = []
    for o in objects:
        if o.name.lower().startswith("sa"):
            bpy.data.objects.remove(o, do_unlink=True)
            continue
        o.data.transform(matrix)
        bm = bmesh.new()
        bm.from_mesh(o.data)
        bad = [
            v
            for v in bm.verts
            if v.co.x > 6.6
            or v.co.x < -6.8
            or ("-ADOOR" not in o.name and v.co.y * v.co.y + v.co.z * v.co.z > 3.15**2)
        ]
        bmesh.ops.delete(bm, geom=bad, context="VERTS")
        bm.to_mesh(o.data)
        bm.free()
        o.data.update()
        if not len(o.data.polygons):
            bpy.data.objects.remove(o, do_unlink=True)
        else:
            keep.append(o)
    before = set(bpy.context.scene.objects)
    # The B CAD shows bays opened for servicing. Reconstruct the closed outer
    # thermal doors while retaining the detailed NASA structure underneath.
    for i in range(8):
        angle = i * math.pi / 4
        normal = Vector((0, math.cos(angle), math.sin(angle)))
        rotation = (angle - math.pi / 2, 0, 0)
        box(
            "Hubble / equipment bay perimeter",
            (4.55, 1.63, 0.055),
            Vector((3.72, 0, 0)) + normal * 1.98,
            M["alu"],
            0.018,
            rotation,
        )
        for x in (2.60, 4.84):
            box(
                "Hubble / closed MLI equipment door",
                (2.16, 1.53, 0.03),
                Vector((x, 0, 0)) + normal * 2.02,
                M["silver"],
                0.006,
                rotation,
                wrinkled=True,
            )
            for s in (-1, 1):
                tangent = Vector((0, -math.sin(angle), math.cos(angle)))
                position = Vector((x + 0.75, 0, 0)) + normal * 2.08 + tangent * s * 0.53
                cyl(
                    "Hubble / door captive fastener",
                    0.025,
                    0.025,
                    position,
                    M["alu"],
                    normal,
                    12,
                )
            handle = Vector((x, 0, 0)) + normal * 2.12
            rod(
                "Hubble / EVA door handle",
                handle + Vector((-0.21, 0, 0)),
                handle + Vector((0.21, 0, 0)),
                0.024,
                M["gold"],
            )
    cyl(
        "Hubble / aft thermal closure",
        1.99,
        0.08,
        (6.04, 0, 0),
        M["white"],
        (1, 0, 0),
        64,
    )
    # A recessed black baffle and coated entrance replace the source's pale
    # unshaded cap; the outer NASA aperture rim and deployed door remain visible.
    cyl(
        "Hubble / entrance light baffle",
        1.44,
        0.06,
        (-6.60, 0, 0),
        M["dark"],
        (1, 0, 0),
        96,
    )
    cyl(
        "Hubble / optical entrance",
        1.29,
        0.014,
        (-6.638, 0, 0),
        M["lens"],
        (1, 0, 0),
        96,
    )
    for s in (-1, 1):
        rod(
            "Hubble / SA3 drive shaft",
            (0.7, s * 1.9, 0),
            (0.7, s * 3.2, 0),
            0.09,
            M["alu"],
        )
        cyl(
            "Hubble / SADM housing",
            0.23,
            0.45,
            (0.7, s * 2.15, 0),
            M["white"],
            (0, 1, 0),
        )
        for i in range(2):
            panel(
                "Hubble / SA3 rigid solar section",
                3.51,
                2.6,
                (0.7 + (i - 0.5) * 3.57, s * 4.5, 0),
                M,
            )
    bpy.context.view_layer.update()
    deps = bpy.context.evaluated_depsgraph_get()
    for o in list(set(bpy.context.scene.objects) - before):
        mesh = bpy.data.meshes.new_from_object(
            o.evaluated_get(deps), preserve_all_data_layers=True, depsgraph=deps
        )
        mesh.transform(o.matrix_world)
        n = bpy.data.objects.new(o.name, mesh)
        bpy.context.collection.objects.link(n)
        keep.append(n)
        bpy.data.objects.remove(o, do_unlink=True)
    return keep
