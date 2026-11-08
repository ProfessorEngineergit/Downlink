import math, sys
sys.path.insert(0, sys.path[0])
from gen import Icon, land_paths, ortho, bg_only

# Natural-Earth-Küsten, liegen in Downlink/app/src/main/assets/land.json
LAND = sys.argv[1] if len(sys.argv) > 1 else "land.json"

# ============ Yearflight: Erdkugel mit echten Kontinenten und Pins ============
def yearflight():
    I = Icon()
    I.grad("bg", "rad", (54, 30, 90), [(0, "FF16305A"), (0.55, "FF0A1630"), (1, "FF040810")])
    I.path("M0,0h108v108h-108z", fill="bg")
    for x, y, r, a in [(20, 22, 0.8, 0.7), (88, 18, 0.7, 0.6), (92, 70, 0.6, 0.5), (16, 78, 0.6, 0.4), (78, 92, 0.5, 0.4)]:
        I.circle(x, y, r, fill="#FFFFFFFF", alpha=a)

    cx, cy, R = 54, 56, 30
    lat0, lon0 = 26, -32
    # Atmosphäre als weicher Schein hinter der Kugel
    I.grad("atmo", "rad", (cx, cy, R + 7), [(0.8, "004FB8FF"), (0.88, "664FB8FF"), (1, "004FB8FF")])
    I.circle(cx, cy, R + 7, fill="atmo")
    I.grad("ocean", "rad", (cx - 11, cy - 13, R * 1.9), [(0, "FF4FC3F0"), (0.45, "FF1C6FB8"), (1, "FF0A2A63")])
    I.circle(cx, cy, R, fill="ocean")
    I.grad("land", "lin", (cx - R, cy - R, cx + R, cy + R), [(0, "FFB6F2CF"), (0.5, "FF5DBE8E"), (1, "FF2E7F62")])
    for d in land_paths(lat0, lon0, R, cx, cy, LAND):
        I.path(d, fill="land")
    # Gradnetz, ganz zart
    for lo in range(-120, 60, 30):
        pts = [ortho(la, lo, lat0, lon0, R, cx, cy) for la in range(-85, 86, 5)]
        vis = [p for p in pts if p[2] > 0]
        if len(vis) > 2:
            I.path("M" + "L".join(f"{x:.2f},{y:.2f}" for x, y, _ in vis), stroke="#33FFFFFF", sw=0.35)
    # Licht von links oben, Schatten nach rechts unten
    I.grad("shade", "rad", (cx - 12, cy - 14, R * 1.55), [(0, "00000814"), (0.45, "00000814"), (0.8, "8C000814"), (1, "D9000814")])
    I.circle(cx, cy, R, fill="shade")
    I.grad("spec", "rad", (cx - 12, cy - 14, 13), [(0, "59FFFFFF"), (1, "00FFFFFF")])
    I.circle(cx - 12, cy - 14, 13, fill="spec")
    I.grad("rim", "lin", (cx - R, cy - R, cx + R, cy + R), [(0, "FFBFF0FF"), (0.5, "404FB8FF"), (1, "004FB8FF")])
    I.circle(cx, cy, R, stroke="rim", sw=1.1)

    # Route: Großkreis New York -> Lissabon, angehoben
    ny = (40.7, -74.0); li = (38.7, -9.1); rio = (-22.9, -43.2)
    def route(a, b, lift):
        pts = []
        for k in range(41):
            t = k / 40
            la = a[0] + (b[0] - a[0]) * t
            lo = a[1] + (b[1] - a[1]) * t
            x, y, z = ortho(la, lo, lat0, lon0, R, cx, cy)
            h = 1 + lift * math.sin(math.pi * t)
            pts.append((cx + (x - cx) * h, cy + (y - cy) * h))
        return "M" + "L".join(f"{x:.2f},{y:.2f}" for x, y in pts)
    I.path(route(ny, li, 0.16), stroke="#FFFFFFFF", sw=1.1, alpha=0.9, cap="round")
    I.path(route(rio, ny, 0.12), stroke="#FFFFFFFF", sw=0.9, alpha=0.55, cap="round")

    # Pins: Spitze auf dem Ort, Kopf darüber
    def pin(lat, lon, color_id, scale):
        x, y, _ = ortho(lat, lon, lat0, lon0, R, cx, cy)
        r = 4.2 * scale
        h = 10.5 * scale
        I.circle(x, y, 1.6 * scale, fill="#66000814")  # Schatten
        cxh, cyh = x, y - h + r
        d = (f"M{x:.2f},{y:.2f}"
             f"C{x - r * 0.35:.2f},{y - h * 0.45:.2f} {cxh - r:.2f},{cyh + r * 0.35:.2f} {cxh - r:.2f},{cyh:.2f}"
             f"A{r:.2f},{r:.2f} 0 1,1 {cxh + r:.2f},{cyh:.2f}"
             f"C{cxh + r:.2f},{cyh + r * 0.35:.2f} {x + r * 0.35:.2f},{y - h * 0.45:.2f} {x:.2f},{y:.2f}Z")
        I.path(d, fill=color_id)
        I.circle(cxh, cyh, r * 0.42, fill="#FFFFFFFF")
    I.grad("pinA", "lin", (0, 20, 0, 60), [(0, "FFFF8FA3"), (1, "FFE0446A")])
    I.grad("pinB", "lin", (0, 20, 0, 70), [(0, "FFFFD27A"), (1, "FFF29A2E")])
    pin(*rio, "pinB", 0.85)
    pin(*ny, "pinB", 0.95)
    pin(*li, "pinA", 1.12)
    return I

# ============ Downlink: großer Wettersatellit über der Erde ============
def downlink():
    I = Icon()
    I.grad("bg", "rad", (54, 40, 85), [(0, "FF102A22"), (0.6, "FF07140F"), (1, "FF020605")])
    I.path("M0,0h108v108h-108z", fill="bg")
    for x, y, r, a in [(18, 20, 0.8, 0.7), (90, 24, 0.7, 0.6), (24, 56, 0.5, 0.4), (86, 52, 0.6, 0.5), (60, 14, 0.5, 0.5)]:
        I.circle(x, y, r, fill="#FFFFFFFF", alpha=a)

    # Erdkrümmung unten mit Atmosphärensaum
    I.grad("earthglow", "rad", (54, 150, 78), [(0.82, "007CDAA9"), (0.9, "887CDAA9"), (1, "007CDAA9")])
    I.circle(54, 150, 78, fill="earthglow")
    I.grad("earth", "rad", (40, 82, 70), [(0, "FF2E7D9C"), (0.5, "FF15435A"), (1, "FF071B26")])
    I.circle(54, 150, 70, fill="earth")
    I.grad("eland", "lin", (0, 80, 0, 108), [(0, "FF6FCB9B"), (1, "FF2E7F62")])
    for d in land_paths(52, 12, 70, 54, 150, LAND):
        I.path(d, fill="eland", alpha=0.85)
    I.grad("eshade", "rad", (54, 150, 70), [(0.86, "00000000"), (1, "66000000")])
    I.circle(54, 150, 70, fill="eshade")

    # Signal vom Satelliten zur Erde
    for k, (r, a) in enumerate([(9, 0.9), (14, 0.6), (19, 0.35)]):
        ang0, ang1 = math.radians(60), math.radians(120)
        sx, sy = 54, 62
        x0, y0 = sx + r * math.cos(ang0), sy + r * math.sin(ang0)
        x1, y1 = sx + r * math.cos(ang1), sy + r * math.sin(ang1)
        I.path(f"M{x0:.2f},{y0:.2f}A{r},{r} 0 0,1 {x1:.2f},{y1:.2f}", stroke="#FF9CF0C6", sw=1.6, alpha=a, cap="round")

    I.push(sc=0.88, px=54, py=48)
    I.push(tx=54, ty=44, rot=-24)
    # Solarflügel mit Zellraster
    I.grad("panel", "lin", (-30, -7, -12, 7), [(0, "FF5C9DFF"), (0.5, "FF2459C9"), (1, "FF12317D")])
    I.grad("panel2", "lin", (12, -7, 32, 7), [(0, "FF5C9DFF"), (0.5, "FF2459C9"), (1, "FF12317D")])
    for x0, gid in [(-33, "panel"), (11, "panel2")]:
        I.path(f"M{x0},-7.5h22v15h-22z", fill=gid)
        grid = "".join(f"M{x0 + i * 22 / 5:.2f},-7.5v15" for i in range(1, 5)) + "M%d,-2.5h22M%d,2.5h22" % (x0, x0)
        I.path(grid, stroke="#8FB9FF", sw=0.45, alpha=0.7)
        I.path(f"M{x0},-7.5h22v15h-22z", stroke="#FFC9DCFF", sw=0.7)
        I.path(f"M{x0},-7.5h9l-6,15h-3z", fill="#FFFFFFFF", alpha=0.18)
    # Ausleger
    I.path("M-11,-1.1h4.6v2.2h-4.6zM6.4,-1.1h4.6v2.2h-4.6z", fill="#FFC8D2DC")
    # Körper mit Goldfolie
    I.grad("gold", "lin", (-7, -12, 7, 12), [(0, "FFFFEBA8"), (0.35, "FFF0B44A"), (0.7, "FFB97A1F"), (1, "FF6E4510")])
    I.path("M-4.6,-12.5h9.2a2.4,2.4 0 0,1 2.4,2.4v20.2a2.4,2.4 0 0,1 -2.4,2.4h-9.2a2.4,2.4 0 0,1 -2.4,-2.4v-20.2a2.4,2.4 0 0,1 2.4,-2.4z", fill="gold")
    I.path("M-7,-5h14M-7,1.5h14M-7,7h14", stroke="#7A4E12", sw=0.5, alpha=0.6)
    I.path("M-5.5,-11 L-2,-11 L-4,8 L-6,8Z", fill="#FFFFFFFF", alpha=0.22)
    # Deckel und Instrumente
    I.path("M-5,-15.2h10v3h-10z", fill="#FFE6ECF2")
    I.path("M-1.2,-19.5h2.4v4.4h-2.4z", fill="#FFD0D8E0")
    I.path("M0,-15.2 L4,-22", stroke="#FFD0D8E0", sw=0.8, cap="round")
    I.circle(4, -22, 1, fill="#FFE6ECF2")
    # Parabolantenne nach unten
    I.grad("dish", "lin", (-8, 13, 8, 19), [(0, "FFFFFFFF"), (1, "FFAAB6C2")])
    I.path("M-8,13.5 Q0,22 8,13.5 Z", fill="dish")
    I.path("M-8,13.5h16", stroke="#FFFFFFFF", sw=0.9, cap="round")
    I.path("M0,13.5v4.2", stroke="#FF8A96A2", sw=0.8)
    I.circle(0, 18.2, 0.9, fill="#FF9CF0C6")
    I.pop()
    I.pop()
    return I

def mono_yf():
    I = Icon()
    I.circle(54, 56, 29, stroke="#FFFFFFFF", sw=3.2)
    I.path("M30,50 C42,44 66,44 78,50", stroke="#FFFFFFFF", sw=2.2, cap="round")
    I.path("M54,27 C44,40 44,72 54,85", stroke="#FFFFFFFF", sw=2.2, cap="round")
    for x, y, s in [(42, 48, 1.0), (68, 52, 1.2), (52, 74, 0.9)]:
        r = 4.2 * s; h = 10.5 * s; cy = y - h + r
        I.path(f"M{x},{y}C{x - r * 0.35:.2f},{y - h * 0.45:.2f} {x - r:.2f},{cy + r * 0.35:.2f} {x - r:.2f},{cy:.2f}A{r:.2f},{r:.2f} 0 1,1 {x + r:.2f},{cy:.2f}C{x + r:.2f},{cy + r * 0.35:.2f} {x + r * 0.35:.2f},{y - h * 0.45:.2f} {x},{y}Z", fill="#FFFFFFFF")
    return I

def mono_dl():
    I = Icon()
    I.push(sc=0.88, px=54, py=48)
    I.push(tx=54, ty=44, rot=-24)
    I.path("M-33,-7.5h22v15h-22zM11,-7.5h22v15h-22zM-11,-1.1h22v2.2h-22zM-7,-12.5h14v25h-14zM-5,-15.2h10v3h-10z", fill="#FFFFFFFF")
    I.path("M-8,13.5 Q0,22 8,13.5 Z", fill="#FFFFFFFF")
    I.pop(); I.pop()
    for r in (10, 15):
        I.path(f"M{54 + r * 0.5:.2f},{62 + r * 0.87:.2f}A{r},{r} 0 0,1 {54 - r * 0.5:.2f},{62 + r * 0.87:.2f}", stroke="#FFFFFFFF", sw=2.2, cap="round")
    return I

open("yf_mono.xml", "w").write(mono_yf().vd())
open("dl_mono.xml", "w").write(mono_dl().vd())

for name, icon in [("yf", yearflight()), ("dl", downlink())]:
    open(f"{name}.svg", "w").write(icon.svg())
    open(f"{name}_mask.svg", "w").write(icon.svg(mask=True))
    open(f"{name}_fg.xml", "w").write(icon.vd(skip_first=True))
    open(f"{name}_bg.xml", "w").write(bg_only(icon))
print("ok")
