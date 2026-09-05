"""Turns a BDF bitmap font into the compact .pxf the app reads. See PixelFont.kt."""
import struct, sys, os

# What a label plausibly needs: ASCII, Latin-1, Latin Extended-A (Polish, Czech, Turkish, ...)
# and the handful of punctuation marks that turn up in real text.
RANGES = [(0x20, 0x7E), (0xA0, 0xFF), (0x100, 0x17F)]
EXTRA = [0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x20AC]
WANTED = {c for a, b in RANGES for c in range(a, b + 1)} | set(EXTRA)

def parse(path):
    props, glyphs = {}, {}
    cur = None
    reading = False
    with open(path, encoding='latin-1') as f:
        for line in f:
            line = line.rstrip('\n')
            if reading:
                if line.startswith('ENDCHAR'):
                    reading = False
                    if cur['enc'] in WANTED:
                        glyphs[cur['enc']] = cur
                else:
                    cur['rows'].append(line.strip())
                continue
            parts = line.split()
            if not parts:
                continue
            key = parts[0]
            if key == 'STARTCHAR':
                cur = dict(enc=-1, bbx=None, rows=[])
            elif key == 'ENCODING':
                cur['enc'] = int(parts[1])
            elif key == 'BBX':
                cur['bbx'] = tuple(int(x) for x in parts[1:5])
            elif key == 'BITMAP':
                reading = True
            elif key in ('FONT_ASCENT', 'FONT_DESCENT', 'FONTBOUNDINGBOX'):
                props[key] = [int(x) for x in parts[1:]]
    return props, glyphs

def convert(path, out):
    props, glyphs = parse(path)
    cellW, cellH = props['FONTBOUNDINGBOX'][0], props['FONTBOUNDINGBOX'][1]
    ascent = props.get('FONT_ASCENT', [cellH + props['FONTBOUNDINGBOX'][3]])[0]
    stride = (cellW + 7) // 8
    body = bytearray()
    kept = sorted(glyphs)
    for cp in kept:
        g = glyphs[cp]
        bw, bh, xoff, yoff = g['bbx']
        cell = bytearray(stride * cellH)
        srcStride = (bw + 7) // 8
        for r, hexrow in enumerate(g['rows'][:bh]):
            y = ascent - yoff - bh + r          # baseline-relative -> top-down cell row
            if not (0 <= y < cellH):
                continue
            bits = int(hexrow, 16) if hexrow else 0
            for x in range(bw):
                if bits >> (srcStride * 8 - 1 - x) & 1:
                    cx = xoff + x
                    if 0 <= cx < cellW:
                        cell[y * stride + cx // 8] |= 0x80 >> (cx % 8)
        body += struct.pack('<H', cp) + cell
    head = b'LPXF' + struct.pack('<BBBBH', 1, cellW, cellH, ascent, len(kept))
    open(out, 'wb').write(head + body)
    return cellW, cellH, len(kept), len(head) + len(body)

if __name__ == '__main__':
    src, dst = sys.argv[1], sys.argv[2]
    os.makedirs(dst, exist_ok=True)
    total = 0
    for name in sorted(os.listdir(src)):
        if not name.endswith('.bdf'):
            continue
        out = os.path.join(dst, name[:-4].lower() + '.pxf')
        w, h, n, size = convert(os.path.join(src, name), out)
        total += size
        print('%-12s %2dx%-2d  %3d glyphs  %5d B' % (name, w, h, n, size))
    print('total %.1f KB' % (total / 1024))
