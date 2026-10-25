"""Makes town/s2_bare from the user's s2: the same building without the holdout's camp on its roof.

Run: python tools/s2_bare.py  (reads and writes src/main/resources/data/nookofthedead/structure/town/)

The user (2026-10-25): the camp on s2's roof - whoever held out up there - should be there only now and then. So the
town sets down s2_bare most times (Town.placeBuilding): s2 as it is, less what the camp put on the open roof. The room on
the roof (its cobblestone walls, what is in it) is the building's and stays; so do the roof's edge, the trees and the
vines over it. What goes, outside that room above the roof's deck: the frame and its cover, the planter and its crops and
water, the fences, the crates, the lodestone and the copper statue.
"""
import gzip
import io
import os
import struct

BASE = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'data', 'nookofthedead', 'structure', 'town')
DECK = 19                       # the roof's deck; the camp stands on it
CAMP = ('nookworks:dawnwood_', 'nookworks:stripped_dawnwood_', 'nookworks:rope', 'nookworks:hook', 'nookworks:hung_light', 'farmland',
        'carrots', 'nookworks:onions', 'nookworks:corn', 'water', 'spruce_trapdoor', 'acacia_fence', 'oak_fence', 'nookworks:crate',
        'lodestone', 'waxed_oxidized_copper_golem_statue', 'straw_bed', 'chest', 'crafting_table', 'loom', 'decorated_pot', 'barrel',
        'campfire', 'white_banner', 'cauldron')
ROOM_WALL = ('cobblestone', 'cracked_stone_bricks', 'mossy_cobblestone', 'iron_bars', 'nether_brick_wall', 'stone_bricks')


# --- NBT, typed: (tag, value) so it writes back as it was read ------------------------------------------------------

def read(f, t):
    if t == 1: return struct.unpack('>b', f.read(1))[0]
    if t == 2: return struct.unpack('>h', f.read(2))[0]
    if t == 3: return struct.unpack('>i', f.read(4))[0]
    if t == 4: return struct.unpack('>q', f.read(8))[0]
    if t == 5: return struct.unpack('>f', f.read(4))[0]
    if t == 6: return struct.unpack('>d', f.read(8))[0]
    if t == 7:
        n = read(f, 3); return f.read(n)
    if t == 8:
        n = struct.unpack('>H', f.read(2))[0]; return f.read(n).decode('utf-8')
    if t == 9:
        tt = read(f, 1); n = read(f, 3); return (tt, [read(f, tt) for _ in range(n)])
    if t == 10:
        d = {}
        while True:
            tt = read(f, 1)
            if tt == 0: return d
            name = read(f, 8)
            d[name] = (tt, read(f, tt))
    if t == 11:
        n = read(f, 3); return [read(f, 3) for _ in range(n)]
    if t == 12:
        n = read(f, 3); return [read(f, 4) for _ in range(n)]
    raise ValueError(t)


def write(f, t, v):
    if t == 1: f.write(struct.pack('>b', v))
    elif t == 2: f.write(struct.pack('>h', v))
    elif t == 3: f.write(struct.pack('>i', v))
    elif t == 4: f.write(struct.pack('>q', v))
    elif t == 5: f.write(struct.pack('>f', v))
    elif t == 6: f.write(struct.pack('>d', v))
    elif t == 7: write(f, 3, len(v)); f.write(v)
    elif t == 8:
        b = v.encode('utf-8'); f.write(struct.pack('>H', len(b))); f.write(b)
    elif t == 9:
        tt, items = v
        write(f, 1, tt if items else (tt or 0)); write(f, 3, len(items))
        for it in items:
            write(f, tt, it)
    elif t == 10:
        for name, (tt, val) in v.items():
            write(f, 1, tt); write(f, 8, name); write(f, tt, val)
        write(f, 1, 0)
    elif t == 11:
        write(f, 3, len(v)); [write(f, 3, x) for x in v]
    elif t == 12:
        write(f, 3, len(v)); [write(f, 4, x) for x in v]


def load(path):
    f = io.BytesIO(gzip.open(path).read())
    t = read(f, 1); name = read(f, 8)
    return t, name, read(f, t)


def save(path, t, name, root):
    out = io.BytesIO()
    write(out, 1, t); write(out, 8, name); write(out, t, root)
    with gzip.open(path, 'wb') as g:
        g.write(out.getvalue())


def block_name(entry):
    for key in ('Name', 'id', 'name'):
        if key in entry:
            return entry[key][1].replace('minecraft:', '')
    return '?'


t, rootname, root = load(os.path.join(BASE, 's2.nbt'))
palette = root['palette'][1][1]
names = [block_name(p) for p in palette]
air = names.index('air')
blocks = root['blocks'][1][1]
pos = {tuple(b['pos'][1][1]): b for b in blocks}

# the room on the roof: its walls, and what they close in (flooded from inside it at the deck's next level)
walls = {(x, z) for (x, y, z), b in pos.items() if DECK + 2 <= y <= DECK + 4 and names[b['state'][1]] in ROOM_WALL}
inside = set()
start = (12, 21)
stack = [start]
while stack:
    x, z = stack.pop()
    if (x, z) in inside or (x, z) in walls or not (0 <= x < 32 and 0 <= z < 32):
        continue
    inside.add((x, z))
    stack += [(x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)]
assert len(inside) < 120, len(inside)                       # it is closed in, not the whole roof
room = inside | walls

gone = 0
for (x, y, z), b in pos.items():
    if y <= DECK or (x, z) in room:
        continue
    nm = names[b['state'][1]]
    if any(nm == c or nm.startswith(c) for c in CAMP):
        b['state'] = (3, air)
        b.pop('nbt', None)
        gone += 1
# the room on the roof without them (the user, 2026-10-25: "本来はただの倉庫"): what they lived with taken out - beds,
# desk, shelves of books, the bench, the pot, the lights and ropes hung up - and the room a store again, barrels and
# crates along its walls; its chest kept
ROOM_CAMP = ('straw_bed', 'nookworks:oak_desk', 'chiseled_bookshelf', 'crafting_table', 'decorated_pot', 'nookworks:hook',
             'nookworks:hung_light', 'nookworks:rope', 'loom', 'oak_fence')
for (x, y, z), b in pos.items():
    if y > DECK and (x, z) in inside and names[b['state'][1]] in ROOM_CAMP:
        b['state'] = (3, air)
        b.pop('nbt', None)
        gone += 1
ents = root['entities'][1]
kept = [e for e in ents[1] if not (e['blockPos'][1][1][1] > DECK and (e['blockPos'][1][1][0], e['blockPos'][1][1][2]) in inside)]
root['entities'] = (9, (ents[0] if kept else 10, kept))


def state(block_id, props=None):
    entry = {'id': (8, block_id)} if 'id' in palette[0] else {'Name': (8, block_id)}
    if props:
        entry['properties' if 'id' in palette[0] else 'Properties'] = (10, {k: (8, v) for k, v in props.items()})
    palette.append(entry)
    names.append(block_id.replace('minecraft:', ''))
    return len(palette) - 1


barrel = state('minecraft:barrel', {'facing': 'up', 'open': 'false'})
crate = state('nookworks:crate')
by_wall = sorted((x, z) for (x, z) in inside if pos.get((x, DECK + 1, z)) is not None
                 and names[pos[(x, DECK + 1, z)]['state'][1]] == 'air'
                 and any((x + dx, z + dz) in walls for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))))
stored = 0
for i, (x, z) in enumerate(by_wall):
    if i % 2:
        continue
    pos[(x, DECK + 1, z)]['state'] = (3, barrel if i % 4 == 0 else crate)
    if i % 8 == 0 and pos.get((x, DECK + 2, z)) is not None and names[pos[(x, DECK + 2, z)]['state'][1]] == 'air':
        pos[(x, DECK + 2, z)]['state'] = (3, crate)            # stacked
    stored += 1
save(os.path.join(BASE, 's2_bare.nbt'), t, rootname, root)
print('room', len(room), 'columns; camp blocks taken off:', gone, '; stores set down:', stored, '; entities kept:', len(kept))
