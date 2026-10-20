import struct, zlib

def png_chunk(tag, data):
    return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', (zlib.crc32(tag + data) & 0xFFFFFFFF))

def png_image(w, h, row_bytes_list):
    """row_bytes_list: list of bytearrays, each of length w*4"""
    raw = b''.join(row_bytes_list[y][:w*4] for y in range(h))
    return b'\x89PNG\r\n\x1a\n' + png_chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) + png_chunk(b'IDAT', zlib.compress(raw)) + png_chunk(b'IEND', b'')

# Create 1024x1024 icon (matrix-style PDF)
w, h = 1024, 1024
row_bytes_list = []
for y in range(h):
    row_bytes = bytearray()
    for x in range(w):
        if y < 300:
            t = y / 300
            r = int(239 + t * (13 - 239))
            g = int(68 + t * (19 - 68))
            b = int(68 + t * (24 - 68))
            a = 255
        else:
            r, g, b, a = 6, 78, 59, 255
        row_bytes.extend([r, g, b, a])
    row_bytes_list.append(row_bytes)

data = png_image(w, h, row_bytes_list)
with open('./icon.png', 'wb') as f:
    f.write(data)
print("icon.png saved")

# Create logo.png (512x512)
w512, h512 = 512, 512
row_bytes_list_512 = []
for y in range(h512):
    row_bytes = bytearray()
    for x in range(w512):
        if y < 150:
            t = y / 150
            r = int(239 + t * (13 - 239))
            g = int(68 + t * (19 - 68))
            b = int(68 + t * (24 - 68))
            a = 255
        else:
            r, g, b, a = 6, 78, 59, 255
        row_bytes.extend([r, g, b, a])
    row_bytes_list_512.append(row_bytes)

data512 = png_image(w512, h512, row_bytes_list_512)
with open('./logo.png', 'wb') as f:
    f.write(data512)
print("logo.png saved")