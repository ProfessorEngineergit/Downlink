"""Icon-Generator: ein Motiv, zwei Ausgaben (SVG zur Vorschau, VectorDrawable für Android)."""
import json, math, re, sys

# --- Bausteine -------------------------------------------------------------
class Icon:
    def __init__(self):
        self.grads = {}
        self.items = []   # (depth, svg, vd)
        self.stack = []

    def grad(self, gid, kind, coords, stops):
        self.grads[gid] = (kind, coords, stops)
        return gid

    def push(self, tx=0, ty=0, rot=0, sc=1, px=54, py=54):
        t = []
        if tx or ty: t.append(f"translate({tx} {ty})")
        if sc != 1: t.append(f"translate({px} {py}) scale({sc}) translate({-px} {-py})")
        if rot: t.append(f"rotate({rot})")
        self.items.append(("open", f'<g transform="{" ".join(t)}">',
            self._vd_group(tx, ty, rot, sc, px, py)))

    def _vd_group(self, tx, ty, rot, sc, px, py):
        # VD: erst skalieren und drehen um den Pivot, dann verschieben
        if sc != 1:
            return f'<group android:pivotX="{px}" android:pivotY="{py}" android:scaleX="{sc}" android:scaleY="{sc}">'
        return f'<group android:translateX="{tx}" android:translateY="{ty}" android:rotation="{rot}">'

    def pop(self):
        self.items.append(("close", "</g>", "</group>"))

    def path(self, d, fill=None, stroke=None, sw=None, alpha=1.0, cap=None, join=None):
        self.items.append(("path", d, dict(fill=fill, stroke=stroke, sw=sw, alpha=alpha, cap=cap, join=join)))

    def circle(self, cx, cy, r, **kw):
        self.path(f"M{cx - r:.2f},{cy:.2f}a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0", **kw)

    # --- Ausgabe ---
    def svg(self, size=512, mask=False):
        out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 108 108"><defs>']
        for gid, (kind, c, st) in self.grads.items():
            s = "".join(f'<stop offset="{o}" stop-color="#{col[2:]}" stop-opacity="{int(col[:2], 16) / 255:.3f}"/>' for o, col in st)
            if kind == "lin":
                out.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{c[0]}" y1="{c[1]}" x2="{c[2]}" y2="{c[3]}">{s}</linearGradient>')
            else:
                out.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{c[0]}" cy="{c[1]}" r="{c[2]}">{s}</radialGradient>')
        if mask:
            out.append('<clipPath id="m"><circle cx="54" cy="54" r="40"/></clipPath>')
        out.append('</defs>')
        if mask: out.append('<rect width="108" height="108" fill="#ffffff"/><g clip-path="url(#m)">')
        for kind, a, b in self.items:
            if kind == "open": out.append(a)
            elif kind == "close": out.append(a)
            else:
                p = b
                attrs = f'd="{a}"'
                attrs += f' fill="{self._svgpaint(p["fill"])}"'
                if p["fill"] and p["fill"].startswith("#") and len(p["fill"]) == 9:
                    attrs += f' fill-opacity="{int(p["fill"][1:3], 16) / 255:.3f}"'
                if p["stroke"]:
                    attrs += f' stroke="{self._svgpaint(p["stroke"])}" stroke-width="{p["sw"]}"'
                    if p["stroke"].startswith("#") and len(p["stroke"]) == 9:
                        attrs += f' stroke-opacity="{int(p["stroke"][1:3], 16) / 255:.3f}"'
                    if p["cap"]: attrs += f' stroke-linecap="{p["cap"]}"'
                    if p["join"]: attrs += f' stroke-linejoin="{p["join"]}"'
                if p["alpha"] != 1: attrs += f' opacity="{p["alpha"]}"'
                out.append(f"<path {attrs}/>")
        if mask: out.append("</g>")
        out.append("</svg>")
        return "".join(out)

    def _svgpaint(self, p):
        if not p: return "none"
        if p.startswith("#"):
            return "#" + p[3:] if len(p) == 9 else p
        return f"url(#{p})"

    def vd(self, skip_first=False):
        out = ['<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">']
        items = self.items[1:] if skip_first else self.items
        for kind, a, b in items:
            if kind == "open": out.append(b)
            elif kind == "close": out.append(b)
            else:
                p = b
                attr = f'<path android:pathData="{a}"'
                inner = ""
                if p["fill"]:
                    if p["fill"].startswith("#"): attr += f' android:fillColor="{p["fill"]}"'
                    else: inner += self._vdgrad(p["fill"], "android:fillColor")
                    if p["alpha"] != 1: attr += f' android:fillAlpha="{p["alpha"]}"'
                if p["stroke"]:
                    if p["stroke"].startswith("#"): attr += f' android:strokeColor="{p["stroke"]}"'
                    else: inner += self._vdgrad(p["stroke"], "android:strokeColor")
                    attr += f' android:strokeWidth="{p["sw"]}"'
                    if p["cap"]: attr += f' android:strokeLineCap="{p["cap"]}"'
                    if p["join"]: attr += f' android:strokeLineJoin="{p["join"]}"'
                    if p["alpha"] != 1: attr += f' android:strokeAlpha="{p["alpha"]}"'
                out.append(attr + (f">{inner}</path>" if inner else "/>"))
        out.append("</vector>")
        return "\n".join(out)

    def _vdgrad(self, gid, name):
        kind, c, st = self.grads[gid]
        s = "".join(f'<item android:offset="{o}" android:color="#{col}"/>' for o, col in st)
        if kind == "lin":
            g = f'<gradient android:type="linear" android:startX="{c[0]}" android:startY="{c[1]}" android:endX="{c[2]}" android:endY="{c[3]}">{s}</gradient>'
        else:
            g = f'<gradient android:type="radial" android:centerX="{c[0]}" android:centerY="{c[1]}" android:gradientRadius="{c[2]}">{s}</gradient>'
        return f'<aapt:attr name="{name}">{g}</aapt:attr>'


def bg_only(icon):
    """Nur das erste Element (Hintergrundfläche) als eigenes VD."""
    b = Icon(); b.grads = icon.grads; b.items = icon.items[:1]
    return b.vd()


# --- Orthografische Projektion für den Globus -----------------------------
def ortho(lat, lon, lat0, lon0, R, cx, cy):
    la, lo, la0, lo0 = map(math.radians, (lat, lon, lat0, lon0))
    x = math.cos(la) * math.sin(lo - lo0)
    y = math.cos(la0) * math.sin(la) - math.sin(la0) * math.cos(la) * math.cos(lo - lo0)
    z = math.sin(la0) * math.sin(la) + math.cos(la0) * math.cos(la) * math.cos(lo - lo0)
    if z < 0:  # hinter dem Horizont: auf den Rand drücken
        n = math.hypot(x, y) or 1
        x, y = x / n, y / n
    return cx + R * x, cy - R * y, z


def land_paths(lat0, lon0, R, cx, cy, path_json):
    polys = json.load(open(path_json))
    out = []
    for poly in polys:
        pts = [ortho(la, lo, lat0, lon0, R, cx, cy) for lo, la in poly]
        if max(p[2] for p in pts) < 0.02: continue
        # sehr kleine Inseln weglassen, sie werden im Icon zu Rauschen
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
        if (max(xs) - min(xs)) * (max(ys) - min(ys)) < 1.2: continue
        d = "M" + "L".join(f"{x:.2f},{y:.2f}" for x, y, _ in pts) + "Z"
        out.append(d)
    return out
