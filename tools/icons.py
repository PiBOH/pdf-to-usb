"""Rigenera icon.png, logo.png e tutti i mipmap partendo da matrix_logo.svg.

Solo libreria standard. Il disegno e' composto con le stesse coordinate, gli stessi
colori e gli stessi testi del file SVG (viewBox 512): il SVG resta la sorgente del
marchio, questo script lo riproduce in PNG a tutte le densita'.
"""

import math
import os
import struct
import xml.etree.ElementTree as ET
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
SVG = os.path.join(ROOT, "matrix_logo.svg")

VIEW = 512.0
SS = 4                      # campioni per unita' SVG: 2048 px, poi ridotti con filtro a media
RENDER = int(VIEW * SS)

# ---------------------------------------------------------------- PNG e riduzione

def png(path, w, h, rgba):
    """RGBA PNG (color type 6): gli angoli arrotondati restano trasparenti."""
    raw = b"".join(b"\x00" + bytes(rgba[y * w * 4:(y + 1) * w * 4]) for y in range(h))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    data = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(data)


def downscale(src, src_size, dst_size):
    """Media pesata sull'alfa: i bordi trasparenti non tirano il colore verso il nero."""
    out = bytearray(dst_size * dst_size * 4)
    factor = src_size / dst_size
    for y in range(dst_size):
        y0 = int(y * factor)
        y1 = max(y0 + 1, int((y + 1) * factor))
        for x in range(dst_size):
            x0 = int(x * factor)
            x1 = max(x0 + 1, int((x + 1) * factor))
            r = g = b = a = n = 0
            for sy in range(y0, min(y1, src_size)):
                row = sy * src_size
                for sx in range(x0, min(x1, src_size)):
                    i = (row + sx) * 4
                    alpha = src[i + 3]
                    if not alpha:
                        continue
                    r += src[i]
                    g += src[i + 1]
                    b += src[i + 2]
                    a += alpha
                    n += 1
            j = (y * dst_size + x) * 4
            if n:
                out[j:j + 4] = bytes((r // n, g // n, b // n, a // n))
    return out


def blit(dst, dst_size, src, src_size, left, top):
    """Copia src (RGBA) dentro dst alla posizione indicata, in pixel."""
    for y in range(src_size):
        dy = top + y
        if dy < 0 or dy >= dst_size:
            continue
        row = (dy * dst_size + left) * 4
        srow = y * src_size * 4
        dst[row:row + src_size * 4] = src[srow:srow + src_size * 4]

# ---------------------------------------------------------------- composizione

def over(buf, i, rgb, a):
    if a <= 0:
        return
    da = buf[i + 3] / 255.0
    oa = a + da * (1.0 - a)
    if oa <= 0:
        return
    buf[i] = int((rgb[0] * a + buf[i] * da * (1.0 - a)) / oa + 0.5)
    buf[i + 1] = int((rgb[1] * a + buf[i + 1] * da * (1.0 - a)) / oa + 0.5)
    buf[i + 2] = int((rgb[2] * a + buf[i + 2] * da * (1.0 - a)) / oa + 0.5)
    buf[i + 3] = int(oa * 255 + 0.5)


def span(buf, w, y, xa, xb, rgb, a):
    """Riempie una fascia orizzontale del canvas, coordinate in unita' SVG."""
    if a <= 0:
        return
    i0 = max(0, int(round(xa * SS)))
    i1 = min(w, int(round(xb * SS)))
    if i1 <= i0:
        return
    base = (y * w) * 4
    for px in range(i0, i1):
        over(buf, base + px * 4, rgb, a)


def rows(y0, y1):
    """Righe in pixel toccate da una fascia verticale in unita' SVG."""
    return range(max(0, int(math.floor(y0 * SS))), min(RENDER, int(math.ceil(y1 * SS))))


def rect(buf, w, x, y, rw, rh, rgb, a):
    for py in rows(y, y + rh):
        span(buf, w, py, x, x + rw, rgb, a)


def round_rect(buf, w, x, y, rw, rh, r, rgb, a):
    r = max(0.0, min(r, rw / 2.0, rh / 2.0))
    for py in rows(y, y + rh):
        sy = (py + 0.5) / SS
        dx = 0.0
        if sy < y + r:
            dx = r - math.sqrt(max(0.0, r * r - (y + r - sy) ** 2))
        elif sy > y + rh - r:
            dx = r - math.sqrt(max(0.0, r * r - (sy - (y + rh - r)) ** 2))
        span(buf, w, py, x + dx, x + rw - dx, rgb, a)


def circle(buf, w, cx, cy, r, rgb, a):
    for py in rows(cy - r, cy + r):
        sy = (py + 0.5) / SS
        d = r * r - (sy - cy) ** 2
        if d <= 0:
            continue
        h = math.sqrt(d)
        span(buf, w, py, cx - h, cx + h, rgb, a)

# ---------------------------------------------------------------- gradiente

def gradient(stops):
    """stops = [(posizione 0..1, (r,g,b), alfa)]: restituisce t -> (rgb, alfa)."""
    def at(t):
        t = 0.0 if t < 0 else (1.0 if t > 1 else t)
        for i in range(1, len(stops)):
            p0, c0, a0 = stops[i - 1]
            p1, c1, a1 = stops[i]
            if t <= p1 or i == len(stops) - 1:
                k = 0.0 if p1 == p0 else (t - p0) / (p1 - p0)
                k = max(0.0, min(1.0, k))
                return (tuple(int(c0[j] + (c1[j] - c0[j]) * k + 0.5) for j in range(3)),
                        a0 + (a1 - a0) * k)
        return stops[-1][1], stops[-1][2]
    return at


def fill_gradient(buf, w, y0, y1, x_of_y, stops):
    """Colora riga per riga fra due quote SVG: x_of_y(sy) -> (xa, xb)."""
    at = gradient(stops)
    for py in rows(y0, y1):
        sy = (py + 0.5) / SS
        rgb, a = at((sy - y0) / (y1 - y0))
        xa, xb = x_of_y(sy)
        span(buf, w, py, xa, xb, rgb, a)

# ---------------------------------------------------------------- forme del marchio

BG = gradient([(0.0, (17, 24, 39), 1.0), (0.6, (5, 28, 20), 1.0), (1.0, (2, 12, 8), 1.0)])
DOC_STOPS = [(0.0, (248, 250, 252), 0.98), (0.4, (226, 232, 240), 0.85),
             (0.7, (6, 78, 59), 0.4), (1.0, (2, 44, 34), 0.0)]

GREEN = {(52, 211, 153), (16, 185, 129), (5, 150, 105), (4, 120, 87), (6, 95, 70),
         (110, 231, 183), (167, 243, 208)}


def background(buf, w, with_background):
    if not with_background:
        return
    for py in rows(0, VIEW):
        sy = (py + 0.5) / SS
        rgb, a = BG(sy / VIEW)
        r = 112.0
        dx = 0.0
        if sy < r:
            dx = r - math.sqrt(max(0.0, r * r - (r - sy) ** 2))
        elif sy > VIEW - r:
            dx = r - math.sqrt(max(0.0, r * r - (sy - (VIEW - r)) ** 2))
        span(buf, w, py, dx, VIEW - dx, rgb, a)


def grid(buf, w):
    for k in range(64, 512, 64):
        for py in rows(0, VIEW):
            span(buf, w, py, k - 0.5, k + 0.5, (16, 185, 129), 0.05)
        span(buf, w, int(k * SS), 0, VIEW, (16, 185, 129), 0.05)


def document(buf, w):
    """Path del foglio: M140 80 H310 L372 142 V420 A20 20 ... H160 A20 20 ... Z."""
    def edges(sy):
        left, right = 140.0, 372.0
        if sy < 142 and sy > 80:
            right = min(372.0, 310.0 + (sy - 80.0))
        if sy > 420:
            d = math.sqrt(max(0.0, 400.0 - (sy - 420.0) ** 2))
            left, right = 160.0 - d, 352.0 + d
        return left, right
    fill_gradient(buf, w, 80, 440, edges, DOC_STOPS)


def folded_corner(buf, w):
    """Path della piega: M310 80 V132 A10 10 0 0 0 320 142 H372 Z."""
    for py in rows(80, 142):
        sy = (py + 0.5) / SS
        left = 310.0
        if sy >= 132:
            left = max(310.0, 320.0 - math.sqrt(max(0.0, 100.0 - (sy - 132.0) ** 2)))
        span(buf, w, py, left, 310.0 + (sy - 80.0), (203, 213, 225), 0.9)


def badge(buf, w):
    # filter="badgeShadow": dy 4, sfocatura 4, opacita' 0.3 (tre strati bastano).
    round_rect(buf, w, 168, 117, 80, 38, 10, (0, 0, 0), 0.10)
    round_rect(buf, w, 169, 118, 78, 36, 9, (0, 0, 0), 0.10)
    round_rect(buf, w, 170, 119, 76, 34, 8, (0, 0, 0), 0.12)
    at = gradient([(0.0, (239, 68, 68), 1.0), (1.0, (220, 38, 38), 1.0)])
    for py in rows(115, 149):
        sy = (py + 0.5) / SS
        for px in range(int(170.5 * SS), int(245.5 * SS)):
            sx = (px + 0.5) / SS
            if round_rect_test(sx, sy, 170, 115, 76, 34, 8):
                rgb, a = at(((sx - 170) / 76 + (sy - 115) / 34) / 2.0)
                over(buf, (py * w + px) * 4, rgb, a)


def round_rect_test(x, y, rx, ry, rw, rh, r):
    if x < rx or x > rx + rw or y < ry or y > ry + rh:
        return False
    cx = min(max(x, rx + r), rx + rw - r)
    cy = min(max(y, ry + r), ry + rh - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def bars(buf, w):
    """Le righe del documento: grigie verso l'alto, verdi verso la matrice."""
    for x, y, rw, rh, rgb, a in [
            (260, 124, 70, 8, (100, 116, 139), 0.7),
            (260, 138, 45, 6, (148, 163, 184), 0.5),
            (170, 170, 160, 8, (71, 85, 105), 0.8),
            (170, 188, 140, 8, (100, 116, 139), 0.7),
            (170, 206, 160, 8, (100, 116, 139), 0.6),
            (170, 228, 90, 7, (16, 185, 129), 0.7)]:
        round_rect(buf, w, x, y, rw, rh, rh / 2.0, rgb, a)
    circle(buf, w, 275, 231.5, 3.5, (52, 211, 153), 1.0)
    circle(buf, w, 290, 231.5, 3.0, (16, 185, 129), 1.0)
    circle(buf, w, 310, 231.5, 2.0, (5, 150, 105), 1.0)


def gloss(buf, w):
    """Path del riflesso: M0 112 A112 ... V160 L0 260 Z, bianco al 3%."""
    for py in rows(0, 260):
        sy = (py + 0.5) / SS
        limit = (260.0 - sy) * (VIEW / 100.0)   # diagonale (0,260) - (512,160)
        left, right = 0.0, VIEW
        if sy < 112:
            left = 112.0 - math.sqrt(max(0.0, 112.0 ** 2 - (112.0 - sy) ** 2))
            right = 400.0 + math.sqrt(max(0.0, 112.0 ** 2 - (112.0 - sy) ** 2))
        span(buf, w, py, left, min(right, limit), (255, 255, 255), 0.03)


def particles(buf, w):
    for cx, cy, r, rgb, a in [
            (150, 300, 2.0, (52, 211, 153), 0.6), (190, 350, 1.5, (16, 185, 129), 0.4),
            (230, 420, 2.5, (110, 231, 183), 0.8), (265, 370, 1.5, (52, 211, 153), 0.5),
            (300, 430, 2.0, (16, 185, 129), 0.6), (340, 310, 2.5, (110, 231, 183), 0.7),
            (330, 380, 1.5, (5, 150, 105), 0.4)]:
        circle(buf, w, cx, cy, r, rgb, a)

# ---------------------------------------------------------------- testo

FONT = {
    "0": ("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
    "1": ("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
    "A": ("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
    "D": ("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
    "F": ("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
    "K": ("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
    "P": ("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    "X": ("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
    "Y": ("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
    "Z": ("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
}


def colour(value, default=(255, 255, 255)):
    try:
        value = value.strip()
        if value.startswith("#") and len(value) == 7:
            return int(value[1:3], 16), int(value[3:5], 16), int(value[5:7], 16)
    except (AttributeError, ValueError):
        pass
    return default


def draw_text(buf, w, text, x, baseline, size, rgb, alpha, anchor="start",
              letter_spacing=0.0, bold=0.0, glow=0.0):
    """Testo monospazio disegnato con un font 5x7: nessun font esterno da caricare."""
    height = 0.62 * size
    step = height / 7.0
    advance = 0.6 * size + letter_spacing
    width = advance * len(text)
    if anchor == "middle":
        x -= width / 2.0
    elif anchor == "end":
        x -= width
    for n, ch in enumerate(text):
        glyph = FONT.get(ch.upper())
        if not glyph:
            continue
        gx = x + n * advance
        for row, bits in enumerate(glyph):
            for col, bit in enumerate(bits):
                if bit != "1":
                    continue
                px = gx + col * step - bold / 2.0
                py = baseline - height + row * step - bold / 2.0
                if glow:
                    rect(buf, w, px - glow, py - glow, step + bold + 2 * glow,
                         step + bold + 2 * glow, rgb, alpha * 0.22)
                rect(buf, w, px, py, step + bold, step + bold, rgb, alpha)


def svg_texts():
    """Legge i <text> di matrix_logo.svg nell'ordine in cui compaiono."""
    out = []
    root = ET.parse(SVG).getroot()
    for node in root.iter():
        if not node.tag.endswith("text") or not (node.text or "").strip():
            continue
        a = node.attrib
        out.append({
            "text": node.text.strip(),
            "x": float(a.get("x", 0)),
            "y": float(a.get("y", 0)),
            "size": float(a.get("font-size", 16)),
            "rgb": colour(a.get("fill")),
            "alpha": float(a.get("opacity", 1)),
            "anchor": a.get("text-anchor", "start"),
            "spacing": float(a.get("letter-spacing", 0)),
            "bold": 0.0 if float(a.get("font-weight", 400)) < 600 else 0.06 * float(a.get("font-size", 16)),
        })
    return out

# ---------------------------------------------------------------- disegno completo

def render(with_background):
    buf = bytearray(RENDER * RENDER * 4)
    background(buf, RENDER, with_background)
    if with_background:
        grid(buf, RENDER)
    document(buf, RENDER)
    folded_corner(buf, RENDER)
    badge(buf, RENDER)
    bars(buf, RENDER)
    texts = svg_texts()
    for t in texts:
        # Il filtro greenGlow del SVG vale per la pioggia di caratteri verdi.
        glow = 0.9 if t["rgb"] in GREEN and with_background else 0.0
        draw_text(buf, RENDER, t["text"], t["x"], t["y"], t["size"], t["rgb"],
                  t["alpha"], t["anchor"], t["spacing"], t["bold"], glow)
    particles(buf, RENDER)
    if with_background:
        gloss(buf, RENDER)
    return buf


icon = render(True)
png(os.path.join(ROOT, "icon.png"), 1024, 1024, downscale(icon, RENDER, 1024))
png(os.path.join(ROOT, "logo.png"), 512, 512, downscale(icon, RENDER, 512))

densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, size in densities.items():
    png(os.path.join(RES, "mipmap-" + name, "ic_launcher.png"), size, size,
        downscale(icon, RENDER, size))

# Adattiva (Android 8): primo piano senza sfondo, dentro il 66% centrale sicuro.
foreground = render(False)
safe = 0.86
for name, size in densities.items():
    canvas = size * 108 // 48
    inner = max(1, int(round(canvas * safe)))
    layer = downscale(foreground, RENDER, inner)
    out = bytearray(canvas * canvas * 4)
    blit(out, canvas, layer, inner, (canvas - inner) // 2, (canvas - inner) // 2)
    png(os.path.join(RES, "mipmap-" + name, "ic_launcher_foreground.png"), canvas, canvas, out)

print("icon.png, logo.png, mipmap e foreground generati da matrix_logo.svg")
