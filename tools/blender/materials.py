"""Physically based, logo-free materials shared by all fourteen spacecraft."""

import bpy


def base(name, color, metallic=0, roughness=0.4):
    m = bpy.data.materials.new(name)
    m.diffuse_color = (*color, 1)
    m.use_nodes = True
    p = m.node_tree.nodes.get("Principled BSDF")
    p.inputs["Base Color"].default_value = (*color, 1)
    p.inputs["Metallic"].default_value = metallic
    p.inputs["Roughness"].default_value = roughness
    return m, m.node_tree, p


def foil(name, color):
    m, nt, p = base(name, color, 0.98, 0.26)
    p.inputs["Anisotropic"].default_value = 0.24
    tc = nt.nodes.new("ShaderNodeTexCoord")
    noise = nt.nodes.new("ShaderNodeTexNoise")
    noise.inputs["Scale"].default_value = 72
    noise.inputs["Detail"].default_value = 3
    noise.inputs["Roughness"].default_value = 0.72
    nt.links.new(tc.outputs["Generated"], noise.inputs["Vector"])
    warp = nt.nodes.new("ShaderNodeVectorMath")
    warp.operation = "SCALE"
    warp.inputs[3].default_value = 0.035
    nt.links.new(noise.outputs["Color"], warp.inputs[0])
    add = nt.nodes.new("ShaderNodeVectorMath")
    add.operation = "ADD"
    nt.links.new(tc.outputs["Generated"], add.inputs[0])
    nt.links.new(warp.outputs["Vector"], add.inputs[1])
    folds = nt.nodes.new("ShaderNodeTexVoronoi")
    folds.feature = "DISTANCE_TO_EDGE"
    folds.inputs["Scale"].default_value = 35
    nt.links.new(add.outputs[0], folds.inputs["Vector"])
    crease = nt.nodes.new("ShaderNodeValToRGB")
    crease.color_ramp.elements[0].position = 0.015
    crease.color_ramp.elements[1].position = 0.12
    nt.links.new(folds.outputs["Distance"], crease.inputs[0])
    bump = nt.nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = 0.64
    bump.inputs["Distance"].default_value = 0.018
    nt.links.new(crease.outputs[0], bump.inputs["Height"])
    nt.links.new(bump.outputs[0], p.inputs["Normal"])
    rough = nt.nodes.new("ShaderNodeMapRange")
    rough.inputs["To Min"].default_value = 0.17
    rough.inputs["To Max"].default_value = 0.39
    nt.links.new(noise.outputs["Fac"], rough.inputs[0])
    nt.links.new(rough.outputs[0], p.inputs["Roughness"])
    return m


def solar():
    m, nt, p = base(
        "Photovoltaic • sapphire / silver contacts", (0.018, 0.031, 0.11), 0.65, 0.22
    )
    p.inputs["Coat Weight"].default_value = 0.48
    p.inputs["Coat Roughness"].default_value = 0.13
    tc = nt.nodes.new("ShaderNodeTexCoord")
    brick = nt.nodes.new("ShaderNodeTexBrick")
    brick.offset = 0
    brick.inputs["Scale"].default_value = 1
    brick.inputs["Brick Width"].default_value = 0.14
    brick.inputs["Row Height"].default_value = 0.21
    brick.inputs["Mortar Size"].default_value = 0.0025
    brick.inputs["Mortar Smooth"].default_value = 0.001
    brick.inputs["Color1"].default_value = (0.018, 0.033, 0.10, 1)
    brick.inputs["Color2"].default_value = (0.045, 0.023, 0.12, 1)
    brick.inputs["Mortar"].default_value = (0.43, 0.48, 0.57, 1)
    nt.links.new(tc.outputs["UV"], brick.inputs["Vector"])
    wave = nt.nodes.new("ShaderNodeTexWave")
    wave.bands_direction = "X"
    wave.inputs["Scale"].default_value = 95
    nt.links.new(tc.outputs["UV"], wave.inputs["Vector"])
    thin = nt.nodes.new("ShaderNodeMath")
    thin.operation = "GREATER_THAN"
    thin.inputs[1].default_value = 0.997
    nt.links.new(wave.outputs["Fac"], thin.inputs[0])
    mix = nt.nodes.new("ShaderNodeMixRGB")
    mix.inputs[2].default_value = (0.34, 0.39, 0.48, 1)
    nt.links.new(thin.outputs[0], mix.inputs[0])
    nt.links.new(brick.outputs["Color"], mix.inputs[1])
    nt.links.new(mix.outputs[0], p.inputs["Base Color"])
    bump = nt.nodes.new("ShaderNodeBump")
    bump.inputs["Distance"].default_value = 0.0005
    nt.links.new(brick.outputs["Fac"], bump.inputs["Height"])
    nt.links.new(bump.outputs[0], p.inputs["Normal"])
    return m


def palette():
    M = {
        "gold": foil("MLI • aluminized Kapton / gold", (0.78, 0.43, 0.085)),
        "silver": foil("MLI • silver Mylar", (0.72, 0.76, 0.82)),
        "cell": solar(),
    }
    for key, name, color, metal, rough in [
        ("white", "Beta cloth • woven PTFE", (0.78, 0.80, 0.79), 0, 0.68),
        ("back", "Solar blanket • white reverse", (0.63, 0.68, 0.71), 0.15, 0.54),
        ("alu", "Aluminium • brushed", (0.62, 0.69, 0.76), 1, 0.3),
        ("titanium", "Titanium • satin", (0.27, 0.30, 0.34), 0.9, 0.4),
        ("dark", "Carbon / black thermal coating", (0.012, 0.017, 0.024), 0.22, 0.44),
        ("lens", "Optics • coated black glass", (0.006, 0.018, 0.027), 0.75, 0.08),
        ("copper", "Copper • wiring", (0.56, 0.21, 0.075), 0.9, 0.3),
    ]:
        m, nt, p = base(name, color, metal, rough)
        M[key] = m
        if key in ("white", "back", "alu"):
            tc = nt.nodes.new("ShaderNodeTexCoord")
            n = nt.nodes.new("ShaderNodeTexNoise")
            n.inputs["Scale"].default_value = 260
            nt.links.new(tc.outputs["Generated"], n.inputs["Vector"])
            b = nt.nodes.new("ShaderNodeBump")
            b.inputs["Distance"].default_value = 0.0008
            b.inputs["Strength"].default_value = 0.25
            nt.links.new(n.outputs["Fac"], b.inputs["Height"])
            nt.links.new(b.outputs[0], p.inputs["Normal"])
            if key == "alu":
                p.inputs["Anisotropic"].default_value = 0.48
    return M


def identify(name, M, color=None):
    """Replace legacy shaders and image decals; agency marks cannot leak through."""
    n = name.lower()
    if "celback" in n:
        return M["back"]
    if "pan-cels" in n:
        return M["cell"]
    if any(x in n for x in ("back", "reverse")) and any(
        x in n for x in ("panel", "solar")
    ):
        return M["back"]
    if any(x in n for x in ("solar", "cell", "photovolta", "pv-", "pva", "pvr")):
        return M["cell"]
    if n in ("blue", "dkblu"):
        return M["cell"]
    if any(x in n for x in ("gold", "bronze", "kapton", "foil_gold", "handrail")):
        return M["gold"]
    if any(x in n for x in ("glass", "lens", "optic")):
        return M["lens"]
    if any(x in n for x in ("black", "dark", "thruster", "carbon")):
        return M["dark"]
    if "foil" in n and "silver" not in n:
        return M["gold"]
    if any(x in n for x in ("silver", "mylar", "blanket")):
        return M["silver"]
    if any(
        x in n
        for x in ("white", "radiator", "shield", "cloth", "logo", "flag", "outer")
    ):
        return M["white"]
    if color and color[2] > color[0] * 1.7:
        return M["cell"]
    return M["alu"]
