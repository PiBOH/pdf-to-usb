import os, zlib, struct

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

ORANGE = (230, 81, 0, 255)
WHITE = (255, 255, 255, 255)
EMPTY = (0, 0, 0, 0)


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


def inside(px, py, triangle):
    (x1, y1), (x2, y2), (x3, y3) = triangle

    def sign(ax, ay, bx, by, cx, cy):
        return (ax - cx) * (by - cy) - (bx - cx) * (ay - cy)

    d1 = sign(px, py, x1, y1, x2, y2)
    d2 = sign(px, py, x2, y2, x3, y3)
    d3 = sign(px, py, x3, y3, x1, y1)
    return not ((d1 < 0 or d2 < 0 or d3 < 0) and (d1 > 0 or d2 > 0 or d3 > 0))


def mark(size, with_background):
    """Riquadro arancione arrotondato, pagina PDF bianca, freccia arancione verso B."""
    buf = bytearray(size * size * 4)
    page = (0.24, 0.16, 0.68, 0.66)
    shaft = (0.395, 0.30, 0.515, 0.48)
    head = ((0.30, 0.455), (0.61, 0.455), (0.455, 0.655))
    radius = 0.18
    for y in range(size):
        fy = (y + 0.5) / size
        for x in range(size):
            fx = (x + 0.5) / size
            colour = EMPTY
            if with_background:
                dx = max(radius - fx, 0, fx - (1 - radius))
                dy = max(radius - fy, 0, fy - (1 - radius))
                if dx * dx + dy * dy <= radius * radius:
                    colour = ORANGE
            if page[0] <= fx <= page[2] and page[1] <= fy <= page[3]:
                colour = WHITE
            if shaft[0] <= fx <= shaft[2] and shaft[1] <= fy <= shaft[3]:
                colour = ORANGE
            if inside(fx, fy, head):
                colour = ORANGE
            i = (y * size + x) * 4
            buf[i:i + 4] = bytes(colour)
    return buf


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


icon_size = 1024
icon = mark(icon_size, True)
png(os.path.join(ROOT, "icon.png"), icon_size, icon_size, icon)
png(os.path.join(ROOT, "logo.png"), 512, 512, downscale(icon, icon_size, 512))

densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, size in densities.items():
    png(os.path.join(RES, "mipmap-" + name, "ic_launcher.png"), size, size,
        downscale(icon, icon_size, size))

# adaptive icon (Android 8): foreground = segno senza riquadro, nel 72% centrale
fg = mark(1024, False)
for name, size in densities.items():
    canvas = size * 108 // 48
    png(os.path.join(RES, "mipmap-" + name, "ic_launcher_foreground.png"), canvas, canvas,
        downscale(fg, 1024, canvas))
print("icon.png, logo.png, mipmap e foreground generati")
