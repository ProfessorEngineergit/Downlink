"""Original CC0 geometry, modelled against the references listed in README."""

import math
from mathutils import Vector
from geometry import box, cyl, rod, wire, sphere, panel, optic, radiator

D = math.pi / 180


def meteor(M):
    cyl(
        "Service module / crinkled gold blanket",
        0.76,
        2.75,
        (0, 0, 0),
        M["gold"],
        (0, 1, 0),
        96,
        True,
    )
    for y in (-1.37, -0.85, 0.25, 1.37):
        cyl(
            "Service module / attachment band",
            0.775,
            0.045,
            (0, y, 0),
            M["alu"],
            (0, 1, 0),
        )
    # Separate silver upper shroud, exposed structural rails and thermal seams.
    cyl(
        "Avionics / silver blanket",
        0.77,
        0.65,
        (0, -0.98, 0),
        M["silver"],
        (0, 1, 0),
        96,
        True,
    )
    for a in range(8):
        t = 2 * math.pi * a / 8
        rod(
            "MLI / longitudinal restraint",
            (0.765 * math.cos(t), -0.65, 0.765 * math.sin(t)),
            (0.765 * math.cos(t), 1.33, 0.765 * math.sin(t)),
            0.008,
            M["titanium"],
        )
    box("Instrument platform", (1.7, 0.16, 1.65), (0, 1.47, 0), M["alu"], 0.035)
    box(
        "Payload / MSU-MR insulated optical housing",
        (0.88, 0.78, 0.8),
        (-0.33, 1.9, 0.1),
        M["silver"],
        0.022,
        wrinkled=True,
    )
    optic("MSU-MR / scanning aperture", (-0.32, 2.32, 0.1), 0.31, M)
    box(
        "Payload / KMSS camera",
        (0.52, 0.65, 0.49),
        (0.53, 1.8, -0.36),
        M["gold"],
        0.014,
        wrinkled=True,
    )
    for x in (0.38, 0.65):
        optic("KMSS / multispectral channel", (x, 2.15, -0.36), 0.095, M)
    box(
        "Payload / microwave sounder",
        (0.65, 0.48, 0.47),
        (0.42, 1.75, 0.48),
        M["white"],
    )
    optic("Microwave sounder / feed", (0.42, 2.02, 0.48), 0.16, M)
    radiator("Service module / nadir radiator", 0.63, 1.2, (0, 0.1, 0.76), M)
    for s in (-1, 1):
        rod(
            "Solar drive / axle",
            (s * 0.72, -0.1, 0),
            (s * 1.6, -0.1, 0),
            0.075,
            M["alu"],
        )
        cyl(
            "Solar drive / motor",
            0.15,
            0.23,
            (s * 0.94, -0.1, 0),
            M["white"],
            (1, 0, 0),
        )
        for k in range(3):
            x = s * (2.18 + k * 1.25)
            panel(f"Solar wing {s:+d} / section {k+1}", 1.19, 2.85, (x, -0.1, 0), M)
            for yy in (-1.2, 1.0):
                cyl(
                    "Solar wing / hinge",
                    0.045,
                    0.14,
                    (x + s * 0.60, yy, 0),
                    M["alu"],
                    (0, 1, 0),
                    16,
                )
        for yy in (-0.53, 0.35):
            rod(
                "Solar yoke / diagonal",
                (s * 0.7, yy, 0.3),
                (s * 1.58, yy, 0),
                0.025,
                M["alu"],
            )
    # Four actual helices rather than straight placeholder antenna rods.
    for a in range(4):
        t0 = a * math.pi / 2
        cx = 0.30 * math.cos(t0)
        cz = 0.30 * math.sin(t0)
        pts = []
        for k in range(145):
            t = k / 144
            ang = t * math.pi * 6 + t0
            pts.append(
                (cx + 0.10 * math.cos(ang), -1.48 - 0.9 * t, cz + 0.10 * math.sin(ang))
            )
        wire("137 MHz / quadrifilar helix", pts, 0.013, M["alu"])
    cyl("137 MHz / antenna pedestal", 0.26, 0.16, (0, -1.5, 0), M["white"], (0, 1, 0))
    box("BRLK / radar boom", (7.4, 0.23, 0.13), (0, -1.65, -0.57), M["white"], 0.015)
    for x in (-0.60, 0.60):
        rod("BRLK / support", (x, -1.15, -0.4), (x, -1.65, -0.57), 0.025, M["alu"])
    for x in [i * 0.16 for i in range(-22, 23)]:
        box(
            "BRLK / waveguide slot",
            (0.055, 0.012, 0.018),
            (x, -1.78, -0.57),
            M["dark"],
            0,
        )
    for x in (-0.54, 0.54):
        cyl(
            "Propulsion / nozzle",
            0.085,
            0.16,
            (x, -1.5, 0.35),
            M["titanium"],
            (0, -1, 0),
        )
    wire(
        "Payload / copper harness",
        [(0.66, -0.9, 0.42), (0.8, 0, 0.4), (0.8, 1.2, 0.4), (0.5, 1.6, 0.5)],
        0.018,
        M["copper"],
    )
    # Individually modelled fasteners, baffle vanes, cable bundles and connectors.
    for x in (-0.76, 0.76):
        for z in (-0.72, 0.72):
            cyl(
                "Instrument deck / captive bolt",
                0.026,
                0.032,
                (x, 1.565, z),
                M["titanium"],
                (0, 1, 0),
                12,
            )
    for x in (-0.66, -0.02):
        for z in (-0.21, 0.41):
            cyl(
                "MSU-MR / corner fastener",
                0.018,
                0.018,
                (x, 2.30, z),
                M["alu"],
                (0, 1, 0),
                12,
            )
    for x in (-0.70, -0.57, -0.44, -0.31, -0.18, -0.05):
        box(
            "MSU-MR / ventilation slot",
            (0.065, 0.012, 0.011),
            (x, 2.303, -0.25),
            M["dark"],
            0,
        )
    for i in range(3):
        wire(
            "Payload / shielded harness",
            [
                (0.52 + i * 0.035, 0.8, 0.54),
                (0.75 + i * 0.035, 1.35, 0.54),
                (0.54 + i * 0.035, 1.58, 0.7),
                (0.54 + i * 0.035, 1.80, 0.73),
            ],
            0.009,
            M["titanium"],
        )
    for y in (-0.45, 0.15, 0.75):
        box(
            "MLI / closure patch",
            (0.075, 0.09, 0.012),
            (0.32, y, 0.695),
            M["gold"],
            0.002,
            rot=(0, 0.42, 0),
        )
    cyl(
        "S band / antenna pedestal",
        0.12,
        0.16,
        (-0.6, 1.25, -0.65),
        M["alu"],
        (-1, 0, -0.4),
    )
    sphere(
        "S band / antenna reflector",
        (-0.77, 1.25, -0.72),
        0.28,
        M["white"],
        (0.2, 1, 1),
    )


def metop(M):
    box(
        "Service module",
        (2.05, 1.55, 2.05),
        (0, -1.65, 0),
        M["gold"],
        0.035,
        wrinkled=True,
    )
    box(
        "Payload module / MLI",
        (1.75, 3.25, 1.8),
        (0, 0.65, 0),
        M["gold"],
        0.025,
        wrinkled=True,
    )
    for y in (-2.44, -0.87, 2.30):
        box("Payload / structural bulkhead", (2.08, 0.075, 2.08), (0, y, 0), M["alu"])
    radiator("Thermal radiator", 1.55, 1.9, (0, 0.35, 0.94), M)
    box(
        "IASI / interferometer",
        (1.1, 0.9, 0.7),
        (-0.42, 2.45, 0.1),
        M["silver"],
        0.025,
        wrinkled=True,
    )
    optic("IASI / primary aperture", (-0.42, 2.95, 0.1), 0.37, M)
    box("AVHRR / scanner", (0.65, 0.65, 0.72), (0.60, 2.4, -0.5), M["white"])
    optic("AVHRR / telescope", (0.60, 2.77, -0.5), 0.22, M)
    for s in (-1, 1):
        # Three long slotted waveguide arrays on each side, ±45°,90°,135°.
        for i, ang in enumerate((-43, 0, 43)):
            start = Vector((s * 0.9, 0.4, -0.72))
            end = start + Vector(
                (s * 2.9 * math.cos(ang * D), 2.9 * math.sin(ang * D), -0.6)
            )
            rod("ASCAT / deployment strut", start, end, 0.035, M["alu"])
            mid = (start + end) / 2
            o = box(
                f"ASCAT / antenna {s:+d} {i+1}",
                (0.28, (end - start).length, 0.12),
                mid,
                M["white"],
                0.015,
            )
            o.rotation_euler = (end - start).to_track_quat("Y", "Z").to_euler()
            for q in range(14):
                p = start.lerp(end, (q + 0.5) / 14)
                b = box(
                    "ASCAT / slot array",
                    (0.16, 0.03, 0.007),
                    p + Vector((0, 0, 0.07)),
                    M["titanium"],
                    0,
                )
                b.rotation_euler = o.rotation_euler
    rod("Solar wing / drive boom", (0.9, -1.6, 0), (2.3, -1.6, 0), 0.08, M["alu"])
    for k in range(5):
        panel(f"Solar wing / section {k+1}", 1.9, 3.15, (3.25 + k * 1.96, -1.6, 0), M)
    cyl("GRAS / antenna", 0.24, 0.15, (0, -2.65, 0.4), M["white"], (0, 1, 0))
    wire(
        "Payload / harness",
        [(-0.9, -1.8, 0.8), (-0.95, -0.4, 0.8), (-0.9, 1.6, 0.8), (-0.4, 2.2, 0.6)],
        0.022,
        M["copper"],
    )


def jpss(M):
    box(
        "BCP-2000 / service bus",
        (1.5, 2.35, 1.7),
        (0, 0, 0),
        M["gold"],
        0.045,
        wrinkled=True,
    )
    for y in (-1.18, 1.18):
        box("BCP-2000 / bulkhead", (1.6, 0.08, 1.8), (0, y, 0), M["alu"])
    box(
        "VIIRS / scanner enclosure",
        (1.45, 1.05, 0.9),
        (0, 1.68, 0.12),
        M["silver"],
        0.04,
        wrinkled=True,
    )
    optic("VIIRS / rotating telescope", (0, 2.25, 0.12), 0.46, M)
    box(
        "CrIS / instrument",
        (0.82, 1.1, 0.75),
        (-0.68, 0.5, -0.7),
        M["gold"],
        0.025,
        wrinkled=True,
    )
    optic("CrIS / aperture", (-0.68, 1.08, -0.7), 0.24, M)
    box("ATMS / instrument", (0.78, 0.75, 0.72), (0.63, 1.35, -0.7), M["white"])
    optic("ATMS / reflector", (0.63, 1.77, -0.7), 0.24, M)
    radiator("VIIRS / cryoradiator", 1.3, 0.85, (0, 1.5, 0.65), M)
    radiator("Service module / radiator", 1.0, 1.5, (0, -0.2, 0.89), M)
    rod("Solar drive", (0.75, -0.45, 0), (1.85, -0.45, 0), 0.07, M["alu"])
    for k in range(4):
        panel(f"Solar wing / section {k+1}", 1.6, 2.5, (2.65 + k * 1.67, -0.45, 0), M)
    cyl("X band / reflector", 0.35, 0.09, (-0.45, -1.45, 0.5), M["white"], (0, -1, 0.5))
    for x in (-0.55, 0.55):
        cyl("Thruster / nozzle", 0.08, 0.14, (x, -1.3, -0.4), M["titanium"], (0, 1, 0))


def tiangong(M):
    # Approximately full-scale orbital configuration. Main axes: Tianhe Y, labs X.
    cyl(
        "Tianhe / habitation section", 2.1, 7.2, (0, -2.9, 0), M["white"], (0, 1, 0), 96
    )
    cyl("Tianhe / service section", 1.45, 5.2, (0, 3.1, 0), M["white"], (0, 1, 0), 96)
    sphere("Tianhe / spherical docking node", (0, 6.2, 0), 1.5, M["white"])
    for y in (-6.45, -3.8, -1.1, 0.75, 3.6, 5.6):
        cyl(
            "Tianhe / exterior thermal seam",
            2.115 if y < 0.5 else 1.465,
            0.045,
            (0, y, 0),
            M["alu"],
            (0, 1, 0),
        )
    for s, name in [(-1, "Mengtian"), (1, "Wentian")]:
        cyl(
            name + " / pressurized laboratory",
            2.1,
            10.0,
            (s * 7.4, 6.2, 0),
            M["white"],
            (1, 0, 0),
            96,
        )
        cyl(
            name + " / equipment section",
            1.4,
            3.1,
            (s * 13.95, 6.2, 0),
            M["silver"],
            (1, 0, 0),
            64,
            True,
        )
        for x in (3, 5.8, 8.6, 11.4):
            cyl(
                name + " / thermal seam",
                2.115,
                0.055,
                (s * x, 6.2, 0),
                M["alu"],
                (1, 0, 0),
            )
        for yy in (-1, 1):
            rod(
                name + " / solar drive",
                (s * 14.8, 6.2, 0),
                (s * 14.8, 6.2 + yy * 3.1, 0),
                0.14,
                M["alu"],
            )
            for k in range(12):
                panel(
                    name + f" / flexible blanket {yy:+d} {k+1}",
                    4.7,
                    1.92,
                    (s * 14.8, 6.2 + yy * (4.05 + k * 1.99), 0),
                    M,
                    flexible=True,
                )
            rod(
                name + " / extensible mast",
                (s * 14.8, 6.2 + yy * 2.0, -0.18),
                (s * 14.8, 6.2 + yy * 27.8, -0.18),
                0.045,
                M["alu"],
            )
        for i in range(6):
            radiator(
                name + " / heat rejection",
                1.15,
                1.5,
                (s * (3.5 + i * 1.4), 6.2, 2.12),
                M,
            )
        for i in range(4):
            rod(
                name + " / EVA handrail",
                (s * (4 + i * 1.4), 4.09, 0.4),
                (s * (4.7 + i * 1.4), 4.09, 0.4),
                0.025,
                M["gold"],
            )
    for s in (-1, 1):
        rod("Tianhe / solar drive", (s * 2, -3, 0), (s * 3, -3, 0), 0.12, M["alu"])
        for i in range(5):
            panel(
                "Tianhe / smaller solar blanket",
                1.95,
                3.7,
                (s * (4 + i * 2.02), -3, 0),
                M,
                flexible=True,
            )
    for at, axis in [
        ((0, 7.6, 0), (0, 1, 0)),
        ((0, 6.2, 1.4), (0, 0, 1)),
        ((0, -6.6, 0), (0, -1, 0)),
    ]:
        cyl("Docking / collar", 0.88, 0.45, at, M["alu"], axis)
        cyl("Docking / hatch", 0.65, 0.48, at, M["dark"], axis)
    # Robotic arm with articulated elbows and external payloads.
    pts = [(1.4, -4.3, 1.65), (2.2, -2, 2.8), (2.4, 1.7, 3.5), (0.8, 3.8, 2.4)]
    for a, b in zip(pts, pts[1:]):
        rod("Tianhe / robotic arm", a, b, 0.12, M["white"])
    for p in pts:
        sphere("Robotic arm / joint", p, 0.23, M["alu"])
    for x in (-0.65, 0.65):
        optic("Tianhe / optical window", (x, -6.55, 0.4), 0.24, M, (0, -1, 0))


def so50(M):
    box("SaudiSat 1C / chassis", (0.25, 0.25, 0.25), (0, 0, 0), M["alu"], 0.006)
    for rot, at in [
        ((0, 0, 0), (0, 0, 0.133)),
        ((math.pi, 0, 0), (0, 0, -0.133)),
        ((math.pi / 2, 0, 0), (0, -0.133, 0)),
        ((-math.pi / 2, 0, 0), (0, 0.133, 0)),
        ((0, math.pi / 2, 0), (0.133, 0, 0)),
        ((0, -math.pi / 2, 0), (-0.133, 0, 0)),
    ]:
        panel("Body-mounted solar array", 0.225, 0.225, at, M, rot)
    for s in (-1, 1):
        for t in (-1, 1):
            a = (s * 0.1, t * 0.1, -0.14)
            b = (s * 0.43, t * 0.36, -0.28)
            cyl("Antenna / feedthrough", 0.012, 0.04, a, M["white"])
            wire("UHF / whip", [a, (s * 0.25, t * 0.23, -0.20), b], 0.0025, M["alu"])


def greencube(M):
    box("3U / anodized chassis", (0.10, 0.10, 0.34), (0, 0, 0), M["dark"], 0.003)
    for x in (-0.053, 0.053):
        for y in (-0.053, 0.053):
            box("3U / aluminium rail", (0.008, 0.008, 0.35), (x, y, 0), M["alu"], 0.001)
    for z in (-0.172, -0.057, 0.057, 0.172):
        box("3U / end frame", (0.115, 0.115, 0.007), (0, 0, z), M["alu"], 0.001)
    for s in (-1, 1):
        panel(
            "Deployable / solar wing",
            0.11,
            0.32,
            (s * 0.12, 0, 0.04),
            M,
            (0, s * 12 * D, 0),
        )
        rod("Solar hinge", (s * 0.055, 0, 0.04), (s * 0.075, 0, 0.04), 0.004, M["alu"])
    panel("Body / solar face", 0.085, 0.30, (0, -0.058, 0), M, (math.pi / 2, 0, 0))
    for s, t in [(1, 1), (-1, 1), (1, -1), (-1, -1)]:
        wire(
            "UHF / tape antenna",
            [
                (s * 0.04, t * 0.04, -0.18),
                (s * 0.12, t * 0.09, -0.24),
                (s * 0.23, t * 0.12, -0.22),
            ],
            0.0018,
            M["alu"],
        )
    optic("Payload / experiment window", (0, 0, 0.18), 0.027, M, (0, 0, 1))


def diwata(M):
    box(
        "Diwata-2 / microsatellite bus",
        (0.55, 0.56, 0.78),
        (0, 0, 0),
        M["gold"],
        0.012,
        wrinkled=True,
    )
    for z in (-0.4, 0.4):
        box("Bus / aluminium deck", (0.59, 0.59, 0.035), (0, 0, z), M["alu"], 0.004)
    radiator("Bus / thermal radiator", 0.40, 0.48, (0, 0, 0.427), M)
    for s in (-1, 1):
        rod("Solar / yoke", (s * 0.28, 0, 0), (s * 0.46, 0, 0), 0.012, M["alu"])
        for k in range(2):
            panel(
                "Solar / deployable section",
                0.40,
                0.69,
                (s * (0.66 + k * 0.42), 0, 0),
                M,
            )
    box(
        "High precision telescope / housing",
        (0.27, 0.18, 0.31),
        (-0.12, -0.36, -0.08),
        M["silver"],
        0.008,
        wrinkled=True,
    )
    optic("High precision telescope", (-0.12, -0.48, -0.08), 0.095, M, (0, -1, 0))
    for x, z in [(0.16, 0.12), (0.16, -0.15)]:
        optic("Multispectral / camera", (x, -0.31, z), 0.05, M, (0, -1, 0))
    for x in (-0.22, 0.22):
        wire(
            "VHF / antenna",
            [(x, 0.28, 0.2), (x * 1.4, 0.5, 0.35), (x * 2, 0.75, 0.40)],
            0.003,
            M["alu"],
        )


BUILDERS = {
    "meteor": meteor,
    "metop": metop,
    "jpss": jpss,
    "tiangong": tiangong,
    "so50": so50,
    "greencube": greencube,
    "diwata": diwata,
}
