"""Small modelling vocabulary; dimensions are metres, all parts are named."""

import math
import bpy
import bmesh
from mathutils import Vector
from mathutils.noise import noise


def finish(o, name, material, bevel=0):
    o.name = name
    o.data.materials.append(material)
    if bevel:
        b = o.modifiers.new("Machined edge radii", "BEVEL")
        b.width = bevel
        b.segments = 3
        b.limit_method = "ANGLE"
        n = o.modifiers.new("Weighted corner normals", "WEIGHTED_NORMAL")
        n.keep_sharp = True
    return o


def box(name, size, at, mat, bevel=0.015, rot=None, wrinkled=False):
    bpy.ops.mesh.primitive_cube_add(size=1, location=at)
    o = bpy.context.object
    o.scale = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if wrinkled:
        bm = bmesh.new()
        bm.from_mesh(o.data)
        bmesh.ops.subdivide_edges(bm, edges=list(bm.edges), cuts=13, use_grid_fill=True)
        bm.normal_update()
        for v in bm.verts:
            v.co += v.normal * (noise(v.co * 33) * 0.008 + noise(v.co * 90) * 0.002)
        bmesh.ops.triangulate(bm, faces=list(bm.faces), quad_method="BEAUTY")
        bm.to_mesh(o.data)
        bm.free()
    if rot:
        o.rotation_euler = rot
    return finish(o, name, mat, bevel)


def cyl(name, radius, depth, at, mat, axis=(0, 0, 1), vertices=64, foil=False):
    if not foil:
        bpy.ops.mesh.primitive_cylinder_add(
            vertices=vertices, radius=radius, depth=depth, location=at
        )
        o = bpy.context.object
        for p in o.data.polygons:
            p.use_smooth = len(p.vertices) == 4
    else:
        verts = []
        faces = []
        rings = 48
        for j in range(rings + 1):
            z = depth * (j / rings - 0.5)
            for i in range(vertices):
                t = 2 * math.pi * i / vertices
                r = (
                    radius
                    + 0.008
                    * noise(Vector((math.cos(t) * 12, math.sin(t) * 12, z * 18)))
                    + 0.004 * math.sin(t * 19 + z * 43)
                )
                verts.append((r * math.cos(t), r * math.sin(t), z))
        for j in range(rings):
            for i in range(vertices):
                a = j * vertices + i
                b = j * vertices + (i + 1) % vertices
                c = b + vertices
                d = a + vertices
                faces.extend([(a, b, c), (a, c, d)])
        faces.extend(
            [
                tuple(reversed(range(vertices))),
                tuple(rings * vertices + i for i in range(vertices)),
            ]
        )
        mesh = bpy.data.meshes.new(name)
        mesh.from_pydata(verts, [], faces)
        mesh.update()
        o = bpy.data.objects.new(name, mesh)
        bpy.context.collection.objects.link(o)
        o.location = at
    o.rotation_euler = Vector(axis).to_track_quat("Z", "Y").to_euler()
    return finish(o, name, mat, min(0.008, radius * 0.06) if not foil else 0)


def rod(name, a, b, radius, mat):
    a, b = Vector(a), Vector(b)
    return cyl(name, radius, (b - a).length, (a + b) / 2, mat, b - a, vertices=12)


def wire(name, points, radius, mat):
    curve = bpy.data.curves.new(name, "CURVE")
    curve.dimensions = "3D"
    curve.bevel_depth = radius
    curve.bevel_resolution = 2
    sp = curve.splines.new("POLY")
    sp.points.add(len(points) - 1)
    for p, co in zip(sp.points, points):
        p.co = (*co, 1)
    o = bpy.data.objects.new(name, curve)
    bpy.context.collection.objects.link(o)
    o.data.materials.append(mat)
    return o


def sphere(name, at, radius, mat, scale=(1, 1, 1)):
    bpy.ops.mesh.primitive_uv_sphere_add(
        segments=32, ring_count=16, radius=radius, location=at
    )
    o = bpy.context.object
    o.scale = scale
    for p in o.data.polygons:
        p.use_smooth = True
    return finish(o, name, mat)


def panel(name, w, h, at, M, rot=(0, 0, 0), flexible=False):
    """Cell face, white reverse, perimeter, string separators and bus conductors."""
    parts = []
    x, y, z = at
    # A CubeSat panel is millimetres thick; using the large-wing hardware sizes
    # on a 10 cm module makes the frame dominate the entire satellite.
    detail = min(1.0, w, h)
    inset = 0.04 * detail
    parts.append(
        box(
            name + " / honeycomb back",
            (w, h, 0.025 * detail),
            at,
            M["back"],
            0.005 * detail,
        )
    )
    face = box(
        name + " / cell strings",
        (w - inset, h - inset, 0.007 * detail),
        (x, y, z + 0.018 * detail),
        M["cell"],
        0,
    )
    face.data.materials.append(M["back"])
    for polygon in face.data.polygons:
        polygon.material_index = 0 if polygon.normal.z > 0.5 else 1
    uv = face.data.uv_layers.active
    for p in face.data.polygons:
        for i in p.loop_indices:
            co = face.data.vertices[face.data.loops[i].vertex_index].co
            uv.data[i].uv = (co.x + w / 2, co.y + h / 2)
    parts.append(face)
    for s in (-1, 1):
        parts.append(
            box(
                name + " / edge rail",
                (0.024 * detail, h + 0.025 * detail, 0.045 * detail),
                (x + s * w / 2, y, z),
                M["alu"],
                0.004 * detail,
            )
        )
        parts.append(
            box(
                name + " / end rail",
                (w, 0.025 * detail, 0.045 * detail),
                (x, y + s * h / 2, z),
                M["alu"],
                0.004 * detail,
            )
        )
    for j in range(1, max(2, round(h / 0.42))):
        yy = y - h / 2 + h * j / max(2, round(h / 0.42))
        parts.append(
            box(
                name + " / string strap",
                (w - inset, 0.006 * detail, 0.004 * detail),
                (x, yy, z + 0.024 * detail),
                M["alu"],
                0,
            )
        )
    if flexible:
        for j in range(1, max(2, round(h / 0.25))):
            yy = y - h / 2 + h * j / max(2, round(h / 0.25))
            parts.append(
                box(
                    name + " / blanket fold",
                    (w - inset, 0.008 * detail, 0.008 * detail),
                    (x, yy, z + 0.024 * detail),
                    M["dark"],
                    0,
                )
            )
    if any(rot):
        from mathutils import Euler

        R = Euler(rot).to_matrix()
        center = Vector(at)
        for o in parts:
            o.location = center + R @ (o.location - center)
            o.rotation_euler = rot
    return parts


def optic(name, at, r, M, axis=(0, 1, 0)):
    a = Vector(axis).normalized()
    p = Vector(at)
    cyl(name + " / baffle", r, r * 0.7, p, M["dark"], a)
    cyl(name + " / retaining ring", r * 1.03, 0.025, p + a * r * 0.36, M["alu"], a)
    cyl(name + " / coated optic", r * 0.86, 0.027, p + a * r * 0.39, M["lens"], a)


def radiator(name, w, h, at, M):
    x, y, z = at
    box(name, (w, h, 0.04), at, M["white"], 0.008)
    for i in range(1, 12):
        box(
            name + " / heat pipe",
            (0.008, h - 0.035, 0.008),
            (x - w / 2 + w * i / 12, y, z + 0.024),
            M["alu"],
            0,
        )
