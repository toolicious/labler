"""Soll-Matrizen aus der Referenz, mit drei belegten Fehlern der Referenz repariert."""
import base64, sys
sys.path.insert(0, 'rmqr-ref/src')

from rmqrcode import rMQR
from rmqrcode import encoder as enc
from rmqrcode.format.error_correction_level import ErrorCorrectionLevel as E
from rmqrcode.format.rmqr_versions import rMQRVersions as V

# 1+2: two table entries the reference contradicts itself on; Zint and the reference's own
# number_of_data_bits / codewords_total agree with these values.
V['R13x27']['blocks'][E.M] = [{'num': 1, 'c': 21, 'k': 12}]
V['R17x43']['blocks'][E.M] = [{'num': 1, 'c': 61, 'k': 39}]

# 3: the interleaver breaks out of the block loop instead of skipping the block that has run
# out, which drops one data codeword on every version whose blocks are of unequal length.
def make_final_codewords(self, blocks):
    out = []
    for i in range(max(b.data_length() for b in blocks)):
        for b in blocks:
            if i < b.data_length():
                out.append(b.get_data_at(i))
    for i in range(max(b.ecc_length() for b in blocks)):
        for b in blocks:
            if i < b.ecc_length():
                out.append(b.get_ecc_at(i))
    return out

rMQR._make_final_codewords = make_final_codewords

ENCODERS = {'numeric': enc.NumericEncoder, 'alnum': enc.AlphanumericEncoder, 'byte': enc.ByteEncoder}

def capacity_chars(name, kind):
    v = V[name]
    bits = v['number_of_data_bits'][E.M] - 3 - v['character_count_indicator_length'][ENCODERS[kind]]
    if kind == 'numeric':
        return (bits // 10) * 3 + (1 if bits % 10 >= 4 else 0)
    if kind == 'alnum':
        return (bits // 11) * 2 + (1 if bits % 11 >= 6 else 0)
    return bits // 8

def sample(kind, n):
    if kind == 'numeric':
        return ''.join(str(i % 10) for i in range(n))
    if kind == 'alnum':
        pool = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 $%*+-./:'
        return ''.join(pool[i % len(pool)] for i in range(n))
    return ''.join(chr(33 + i % 90) for i in range(n))

cases = []
order = sorted(V, key=lambda n: V[n]['version_indicator'])
# Every version brim full in byte mode: that exercises each version's own tables and layout.
for name in order:
    cases.append((name, 'byte', sample('byte', capacity_chars(name, 'byte'))))
# A spread of the other two modes and of half-full symbols, which is where the padding shows.
spread = ['R7x43', 'R9x59', 'R11x27', 'R13x77', 'R15x99', 'R17x139']
for name in spread:
    for kind in ('numeric', 'alnum'):
        cases.append((name, kind, sample(kind, capacity_chars(name, kind))))
    half = max(1, capacity_chars(name, 'byte') // 2)
    cases.append((name, 'byte', sample('byte', half)))
cases.append(('R11x139', 'byte', 'Küche: Mehl 500 g – Fach 3'))   # non-ASCII, UTF-8 bytes

with open('rmqr_fixtures.txt', 'w', newline='\n') as f:
    written = 0
    for name, kind, text in cases:
        q = rMQR(name, E.M)
        q.add_segment(text, encoder_class=ENCODERS[kind])
        q.make()
        rows = q.to_list(with_quiet_zone=False)
        assert len(rows) == V[name]['height'] and len(rows[0]) == V[name]['width'], name
        f.write('CASE %s %s %s\n' % (name, kind, base64.b64encode(text.encode()).decode()))
        for row in rows:
            f.write(''.join('#' if m else '.' for m in row) + '\n')
        written += 1
print('cases:', written)
