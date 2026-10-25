"""Where the refuge's sealed bulkheads go (world/Refuge#hull), worked out from the institute's deep part as shipped.

Run: python tools/refuge_hull.py
reads src/main/resources/data/nookofthedead/structure/town/institute_deep.nbt and rewrites the generated HULL in
world/Refuge.java: the cells, in the template's own coordinates, to be set to nookofthedead:bulkhead.

The rule (the user, 2026-10-19: one could dig into the refuge from anywhere and never meet the Vandal; "このmod用の破壊不可
能なブロックを作りましょう"): the refuge is cut into a box of deepslate tiles (FILL); every block face-to-face with that box
from outside is made a bulkhead - out of sight in the rock - except where the outside there is open (the observation
gallery above its north side), and there the box's own block is, so no way is left through. The one way in is the passage
from the containment hall, stopped by its iron plug: the passage itself stays open and its walls, from the hall's face to
the box, are made bulkheads too, so the plug can be reached only from the hall.
"""
import gzip
import os
import struct

HERE = os.path.dirname(os.path.abspath(__file__))
NBT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'data', 'nookofthedead', 'structure', 'town', 'institute_deep.nbt')
JAVA = os.path.join(HERE, '..', 'src', 'main', 'java', 'dev', 'nookofthedead', 'world', 'Refuge.java')

FILL = ((7, 1, 39), (57, 8, 63))                 # build_world/ai_l2_refuge.py: the shell the rooms are cut from
PASSAGE = ((37, 2, 35), (38, 4, 39))             # the way in from the hall, its plug of iron at z 36


def read(path):
    data = gzip.open(path).read()
    pos = 0

    def u(fmt, n):
        nonlocal pos
        v = struct.unpack_from(fmt, data, pos)
        pos += n
        return v[0]

    def string():
        nonlocal pos
        n = u('>H', 2)
        s = data[pos:pos + n].decode('utf-8')
        pos += n
        return s

    def payload(t):
        nonlocal pos
        if t == 1:
            return u('>b', 1)
        if t == 2:
            return u('>h', 2)
        if t == 3:
            return u('>i', 4)
        if t == 4:
            return u('>q', 8)
        if t == 5:
            return u('>f', 4)
        if t == 6:
            return u('>d', 8)
        if t == 7:
            n = u('>i', 4)
            pos += n
            return None
        if t == 8:
            return string()
        if t == 9:
            et = u('>b', 1)
            return [payload(et) for _ in range(u('>i', 4))]
        if t == 10:
            d = {}
            while True:
                tt = u('>b', 1)
                if tt == 0:
                    return d
                name = string()
                d[name] = payload(tt)
        if t == 11:
            return [u('>i', 4) for _ in range(u('>i', 4))]
        if t == 12:
            return [u('>q', 8) for _ in range(u('>i', 4))]
    u('>b', 1)
    string()
    return payload(10)


def main():
    root = read(NBT)
    names = [p.get('Name', p.get('id')) for p in root['palette']]
    blocks = {tuple(b['pos']): names[b['state']] for b in root['blocks']}

    def solid(p):
        # outside the template is the world's rock; inside, anything but air
        return blocks.get(p, 'minecraft:deepslate') not in ('minecraft:air', 'minecraft:cave_air')

    (x0, y0, z0), (x1, y1, z1) = FILL

    def in_fill(p):
        return x0 <= p[0] <= x1 and y0 <= p[1] <= y1 and z0 <= p[2] <= z1

    (px0, py0, pz0), (px1, py1, pz1) = PASSAGE

    def in_passage(p):
        return px0 <= p[0] <= px1 and py0 <= p[1] <= py1 and pz0 <= p[2] <= pz1

    hull = set()
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                p = (x, y, z)
                for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                    o = (x + d[0], y + d[1], z + d[2])
                    if in_fill(o) or in_passage(o) or in_passage(p):
                        continue
                    if solid(o):
                        hull.add(o)
                    elif solid(p):
                        hull.add(p)                      # open outside (the gallery): the box's own block, then
    # the passage's walls, floor and roof, from the hall's face to the box
    for z in range(pz0, pz1 + 1):
        for x in range(px0 - 1, px1 + 2):
            for y in range(py0 - 1, py1 + 2):
                p = (x, y, z)
                if not in_passage(p) and not in_fill(p) and solid(p):
                    hull.add(p)
    # as runs along x, row by row: "y z x0 x1" each
    rows = {}
    for x, y, z in hull:
        rows.setdefault((y, z), []).append(x)
    runs = []
    for (y, z), xs in sorted(rows.items()):
        xs.sort()
        a = b = xs[0]
        for x in xs[1:]:
            if x == b + 1:
                b = x
            else:
                runs.append((y, z, a, b))
                a = b = x
        runs.append((y, z, a, b))
    text = ' '.join('%d,%d,%d,%d' % r for r in runs)
    lines = []
    while text:
        cut = text.rfind(' ', 0, 110) if len(text) > 110 else len(text)
        lines.append(text[:cut].strip())
        text = text[cut:].strip()
    body = '    static final String HULL = ""\n' + '\n'.join('            + "%s "' % l for l in lines) + ';\n'
    src = open(JAVA, encoding='utf-8').read()
    a, b = '    // BEGIN GENERATED by tools/refuge_hull.py\n', '    // END GENERATED\n'
    i, j = src.index(a) + len(a), src.index(b)
    src = src[:i] + body + src[j:]
    open(JAVA, 'w', encoding='utf-8', newline='\n').write(src)
    print('ok: %d cells in %d runs' % (len(hull), len(runs)))


if __name__ == '__main__':
    main()
