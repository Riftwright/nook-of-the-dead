"""The Sentry Golem's model and skin, from one table of parts (client/SentryModel) - from nothing of vanilla's.

Run: python tools/paint_sentry_golem.py
writes textures/entity/sentry_golem.png (256 x 128), sentry_golem_eyes.png (its lights, for an EyesLayer),
sentry_golem_crack_low/medium/high.png (its own damage, on its own plates), rewrites the generated createBodyLayer() in
client/SentryModel.java from the same table, and build/sentry_golem_views.png (the model drawn front, side, back and
three-quarter, x6, to look at).

The look (the user, 2026-10-19: "ゴーレムはがっつり作り込んで。ある意味ラスボスだから"; the strict grade before it: a
reskinned village golem, the nose under the visor, the chest reading as a grin): a machine the base built to keep the
outside out. A heavy armoured trunk on a ribbed waist, the head sunk between the shoulders: a helmet with a jutting brow,
under it a dark band with two fixed red lamps, below them a faceplate of upright bars as a riot guard's, an armoured chin,
cheek plates. (A first cut had one red light running along a slit over a snout with a filter each side; the user,
2026-10-19, found it too like a well-known robot's face - nothing of that is kept: no single eye, no running light, no
snout.) Big
enamelled shoulder plates numbered as the base numbered its guards, hazard bands on their rims. A power pack on its back
with two exhausts and an aerial whose tip glows. Long arms down to its knees: a bolt launcher on the right forearm, a
guard plate on the left, heavy fists. Thighs of bare frame, armoured shins with knee caps and a piston behind each knee,
broad feet. The base's off-white enamel over dark gunmetal, chipped, rust run from the rivets, a faded red band across the
breastplate. Few colours, each in a few steps, lit as the house style does: lighter on top and high up, darker low and
underneath, a bevel on each plate's edge.

Model space: y down, the ground at 24, the front towards -z, the creature's right towards -x. A box (x, y, z, w, h, d)
at texture (u, v) has its top at (u+d, v) w x d, its bottom at (u+d+w, v), and its sides in a row at v+d, h high: right
(-x) d, front w, left (+x) d, back w.
"""
import math
import os
import random
import re
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures', 'entity')
JAVA = os.path.join(HERE, '..', 'src', 'main', 'java', 'dev', 'nookofthedead', 'client', 'SentryModel.java')
PREVIEW = os.path.join(HERE, '..', 'build')
TW, TH = 256, 128

# --- the parts: name, parent, pivot (in the parent's frame), boxes (x, y, z, w, h, d, material, tag) ----------------
PARTS = [
    ('waist', None, (0, -3, 0), [
        (-5, 0, -3.5, 10, 6, 7, 'frame', 'abdomen'),
        (-7.5, 5, -4.5, 15, 5, 9, 'steel', 'pelvis'),
        (-3, 6, -5.5, 6, 4, 1, 'frame', 'cod'),
        (-10, 6, -4, 2, 7, 8, 'enamel', 'tasset_r'),
        (8, 6, -4, 2, 7, 8, 'enamel', 'tasset_l')]),
    ('chest', None, (0, -3, 0), [
        (-10, -14, -6, 20, 14, 12, 'steel', 'torso'),
        (-7, -12.5, -7, 14, 9, 1, 'enamel', 'breast'),
        (-6, -15.5, -5, 12, 2, 9, 'frame', 'collar'),
        (-11, -10, -3, 1, 6, 6, 'frame', 'vent_r'),
        (10, -10, -3, 1, 6, 6, 'frame', 'vent_l')]),
    ('pack', 'chest', (0, -8, 6), [
        (-6.5, -5, 0, 13, 12, 5, 'steel', 'pack'),
        (-6, -10, 1, 2, 5, 2, 'frame', 'pipe'),
        (4, -10, 1, 2, 5, 2, 'frame', 'pipe'),
        (-5, -3, 5, 1, 8, 2, 'frame', 'fin'),
        (-0.5, -3, 5, 1, 8, 2, 'frame', 'fin'),
        (4, -3, 5, 1, 8, 2, 'frame', 'fin')]),
    ('antenna', 'pack', (3.5, -5, 3), [
        (-0.5, -14, -0.5, 1, 14, 1, 'frame', 'whip'),
        (-1, -15, -1, 2, 1, 2, 'enamel', 'tip')]),
    ('head', None, (0, -15, -1.5), [
        (-5, -9, -5, 10, 9, 9, 'steel', 'helmet'),
        (-5.5, -9.5, -7.5, 11, 3, 4, 'steel', 'brow'),
        (-4, -6.5, -5.5, 8, 6, 1, 'frame', 'grille'),
        (-4.5, -1, -6.5, 9, 2, 2, 'enamel', 'chin'),
        (-6, -6, -6, 1, 5, 2, 'steel', 'cheek'),
        (5, -6, -6, 1, 5, 2, 'steel', 'cheek'),
        (-6, -7, -2, 1, 4, 4, 'frame', 'ear'),
        (5, -7, -2, 1, 4, 4, 'frame', 'ear')]),
    ('right_arm', None, (-12, -14, 0), [
        (-5, -4, -5, 9, 6, 10, 'enamel', 'pauldron_r'),
        (-3, -1, -3, 6, 12, 6, 'frame', 'upper')]),
    ('right_forearm', 'right_arm', (0, 11, 0), [
        (-2.5, -1.5, -2.5, 5, 3, 5, 'frame', 'elbow'),
        (-3.5, 0, -3.5, 7, 12, 7, 'steel', 'fore'),
        (-7.5, 1, -2, 4, 12, 4, 'steel', 'launcher'),
        (-7, 13, -1.5, 3, 6, 3, 'frame', 'barrel'),
        (-9.5, 4, -2.5, 2, 5, 5, 'steel', 'magazine')]),
    ('right_fist', 'right_forearm', (0, 12, 0), [
        (-4, 0, -4, 8, 7, 8, 'steel', 'fist'),
        (-4.5, 2, -5, 9, 3, 1, 'enamel', 'knuckles')]),
    ('left_arm', None, (12, -14, 0), [
        (-4, -4, -5, 9, 6, 10, 'enamel', 'pauldron_l'),
        (-3, -1, -3, 6, 12, 6, 'frame', 'upper')]),
    ('left_forearm', 'left_arm', (0, 11, 0), [
        (-2.5, -1.5, -2.5, 5, 3, 5, 'frame', 'elbow'),
        (-3.5, 0, -3.5, 7, 12, 7, 'steel', 'fore'),
        (3.5, 1, -4, 2, 8, 8, 'enamel', 'guard')]),
    ('left_fist', 'left_forearm', (0, 12, 0), [
        (-4, 0, -4, 8, 7, 8, 'steel', 'fist'),
        (-4.5, 2, -5, 9, 3, 1, 'enamel', 'knuckles')]),
]
for side, x in (('right', -5), ('left', 5)):
    PARTS += [
        (side + '_leg', None, (x, 5, 0.5), [
            (-3, -1, -3, 6, 10, 6, 'frame', 'thigh')]),
        (side + '_shin', side + '_leg', (0, 9, 0), [
            (-3.5, 0, -3.5, 7, 8, 7, 'steel', 'shin'),
            (-3, 0.5, -4.5, 6, 6, 1, 'enamel', 'greave'),
            (-2, -2, -4.5, 4, 3, 2, 'enamel', 'knee'),
            (-1, -5, 3.5, 2, 9, 2, 'piston', 'piston')]),
        (side + '_foot', side + '_shin', (0, 8, 0), [
            (-4, 0, -5.5, 8, 2, 10, 'frame', 'foot')]),
    ]

# --- colours: ramps, shadow to light ----------------------------------------------------------------------------------
RAMPS = {
    'steel': [(30, 33, 40), (44, 48, 57), (60, 65, 75), (79, 85, 96), (101, 107, 118), (128, 135, 148)],
    'frame': [(17, 19, 23), (27, 29, 34), (39, 42, 48), (53, 57, 63), (68, 72, 78)],
    'enamel': [(96, 94, 84), (124, 120, 106), (150, 145, 128), (174, 168, 148), (194, 187, 165)],
    'rubber': [(20, 20, 20), (30, 29, 28), (41, 39, 37), (54, 51, 48), (66, 62, 58)],
    'glass': [(7, 8, 11), (12, 14, 18), (20, 24, 30), (34, 40, 50), (56, 66, 80)],
    'piston': [(64, 68, 72), (96, 100, 104), (130, 134, 138), (164, 168, 172), (192, 196, 198)],
}
HAZARD = [(150, 112, 34), (182, 140, 48), (204, 164, 62)]
BLACK = (22, 22, 24)
BAND = [(84, 28, 26), (112, 38, 32), (132, 48, 38)]
RUST = [(70, 40, 28), (102, 58, 36)]
PRIMER = (88, 52, 42)
OIL = (18, 17, 16)
SOOT = (14, 14, 15)
LAMP = [(150, 20, 16), (236, 48, 34), (255, 150, 120)]
AMBER = (255, 168, 40)

FACE_LIGHT = {'top': 3.0, 'front': 2.2, 'right': 1.7, 'left': 1.5, 'back': 1.1, 'bottom': 0.2}


def pack():
    """Each box a place on the sheet, by shelves from the tallest down; the same every run."""
    boxes = [(pi, bi, b) for pi, p in enumerate(PARTS) for bi, b in enumerate(p[3])]
    order = sorted(boxes, key=lambda t: (-(t[2][5] + t[2][4]), -(t[2][5] + t[2][3]), t[0], t[1]))
    uv = {}
    x = y = shelf = 0
    for pi, bi, b in order:
        w, h, d = b[3], b[4], b[5]
        bw, bh = 2 * (d + w), d + h
        if x + bw > TW:
            x, y, shelf = 0, y + shelf, 0
        uv[(pi, bi)] = (x, y)
        x += bw
        shelf = max(shelf, bh)
    assert y + shelf <= TH, 'the parts do not fit the sheet'
    return uv


UV = pack()


def world_of(pi):
    """A part's pivot in model space (no part turned at rest)."""
    x = y = z = 0.0
    while pi is not None:
        name, parent, off, _ = PARTS[pi]
        x, y, z = x + off[0], y + off[1], z + off[2]
        pi = next((i for i, p in enumerate(PARTS) if p[0] == parent), None) if parent else None
    return x, y, z


def faces(u, v, b):
    """Each face of a box: name, its rectangle on the sheet, and where a texel (c, r) in it lies on the box."""
    x0, y0, z0, w, h, d = b[:6]
    return [
        ('top', (u + d, v, w, d), lambda c, r: (x0 + c + .5, y0, z0 + d - r - .5)),
        ('bottom', (u + d + w, v, w, d), lambda c, r: (x0 + c + .5, y0 + h, z0 + d - r - .5)),
        ('right', (u, v + d, d, h), lambda c, r: (x0, y0 + r + .5, z0 + d - c - .5)),
        ('front', (u + d, v + d, w, h), lambda c, r: (x0 + c + .5, y0 + r + .5, z0)),
        ('left', (u + d + w, v + d, d, h), lambda c, r: (x0 + w, y0 + r + .5, z0 + c + .5)),
        ('back', (u + 2 * d + w, v + d, w, h), lambda c, r: (x0 + w - c - .5, y0 + r + .5, z0 + d)),
    ]


img = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))
eyes = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))
cracks = [Image.new('RGBA', (TW, TH), (0, 0, 0, 0)) for _ in range(3)]
glow = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))


def hs(*k):
    """A steady hash (not Python's salted one), 0..1."""
    n = 2166136261
    for v in k:
        for ch in str(v):
            n = ((n ^ ord(ch)) * 16777619) & 0xffffffff
    return (n % 100003) / 100003


DIGITS = {'0': ['111', '101', '101', '101', '111'], '7': ['111', '001', '010', '010', '010'],
          '4': ['101', '101', '111', '001', '001'], '2': ['111', '001', '111', '100', '111']}


class Face:
    def __init__(self, pi, bi, b, name, rect, at):
        self.pi, self.bi, self.b, self.name = pi, bi, b, name
        self.u, self.v, self.w, self.h = rect
        self.at = at
        self.mat, self.tag = b[6], b[7]
        self.base = world_of(pi)

    def world(self, c, r):
        x, y, z = self.at(c, r)
        return x + self.base[0], y + self.base[1], z + self.base[2]

    def level(self, c, r):
        """How light a texel is: its face, how high it stands, the plate's bevel."""
        _, wy, _ = self.world(c, r)
        lv = FACE_LIGHT[self.name] + (6 - wy) / 30.0
        if self.name not in ('top', 'bottom'):
            if self.h >= 4:
                lv += 0.6 - 1.2 * r / (self.h - 1)          # down the panel: lit at its top, in shadow at its foot
            if r == 0:
                lv += 0.8
            elif r == self.h - 1:
                lv -= 0.8
            if c == 0:
                lv += 0.25
            elif c == self.w - 1:
                lv -= 0.5
        else:
            if c == 0 or c == self.w - 1 or r == 0 or r == self.h - 1:
                lv -= 0.4
        return lv

    def put(self, c, r, col, sheet=None):
        if 0 <= c < self.w and 0 <= r < self.h:
            (sheet or img).putpixel((self.u + c, self.v + r), tuple(col) + (255,))

    def tone(self, c, r, lv, ramp=None):
        ramp = ramp or RAMPS[self.mat]
        self.put(c, r, ramp[max(0, min(len(ramp) - 1, int(round(lv))))])

    def each(self):
        for r in range(self.h):
            for c in range(self.w):
                yield c, r


def base_coat(f):
    for c, r in f.each():
        f.tone(c, r, f.level(c, r))


def seams(f, every, salt):
    """Panel lines and the rivets at their ends, on steel."""
    if f.w < 4 or f.h < 4 or f.name in ('top', 'bottom'):
        return
    for c in range(every, f.w - 1, every):
        for r in range(1, f.h - 1):
            f.tone(c, r, f.level(c, r) - 1.6)
        for r in (1, f.h - 2):
            rivet(f, c - 1, r, salt + c)


def rivet(f, c, r, salt):
    if not (0 <= c < f.w and 0 <= r < f.h):
        return
    f.tone(c, r, f.level(c, r) + 1.3)
    if r + 1 < f.h:
        f.tone(c, r + 1, f.level(c, r + 1) - 1.2)
    if hs('rust', f.tag, f.name, salt) < 0.5:               # rust run down from it
        n = 1 + int(hs('run', f.tag, salt) * 4)
        for k in range(2, 2 + n):
            if r + k < f.h - 1:
                f.put(c, r + k, RUST[0 if k > 2 else 1])


def chips(f, count, salt):
    """The enamel knocked off to the metal, on the plate's edges most."""
    for i in range(count):
        edge = hs('e', f.tag, f.name, salt, i)
        c = int(hs('c', f.tag, f.name, salt, i) * f.w)
        r = int(hs('r', f.tag, f.name, salt, i) * f.h)
        if edge < 0.5:
            r = 0 if edge < 0.25 else f.h - 1
        size = 1 + int(hs('s', f.tag, salt, i) * 2)
        for dc in range(size):
            for dr in range((size + 1) // 2):
                if 0 <= c + dc < f.w and 0 <= r + dr < f.h:
                    inner = dc == 0 and dr == 0 and size > 1
                    f.put(c + dc, r + dr, RAMPS['steel'][2] if inner else PRIMER)


def hazard(f, r0, r1, phase=0):
    for r in range(max(0, r0), min(f.h, r1)):
        for c in range(f.w):
            yellow = ((c + r + phase) // 2) % 2 == 0
            if yellow:
                lv = 1 if f.name in ('front', 'top') else 0
                f.put(c, r, HAZARD[min(2, lv + (1 if r == r0 else 0))])
            else:
                f.put(c, r, BLACK)


def slits(f, rows, c0, c1, lv_off=-3):
    for r in rows:
        for c in range(c0, c1):
            f.tone(c, r, f.level(c, r) + lv_off)
        if r + 1 < f.h:
            for c in range(c0, c1):
                f.tone(c, r + 1, f.level(c, r + 1) + 0.6)


def stencil(f, text, c0, r0, col=BLACK):
    for i, ch in enumerate(text):
        for rr, row in enumerate(DIGITS[ch]):
            for cc, bit in enumerate(row):
                if bit == '1':
                    f.put(c0 + i * 4 + cc, r0 + rr, col)


def ribs(f, every=2):
    """Ribbed frame: a dark line every few rows, light on the row under it."""
    if f.name in ('top', 'bottom'):
        return
    for r in range(f.h):
        if r % every == every - 1:
            for c in range(f.w):
                f.tone(c, r, f.level(c, r) - 1.5)


def cables(f, cols):
    if f.name in ('top', 'bottom'):
        return
    for c in cols:
        if 0 <= c < f.w:
            for r in range(f.h):
                f.tone(c, r, f.level(c, r) - 1.4)
                if c + 1 < f.w and r % 3 == 0:
                    f.tone(c + 1, r, f.level(c + 1, r) + 0.5)


def grime(f):
    """Soot and dried mud low on it, in streaks up from the ground; heavier the lower."""
    if f.name in ('top', 'bottom'):
        return
    for c in range(f.w):
        reach = hs('mud', f.tag, f.name, f.pi, c)
        for r in range(f.h):
            _, wy, _ = f.world(c, r)
            if wy > 24 - 7 * reach - 2:
                f.tone(c, r, f.level(c, r) - 1)


def wear(f):
    """Years on it: rust bled from the enamel's lower edge, oil run from the joints, scratches on the steel, soot by the
    exhausts."""
    if f.name in ('top', 'bottom'):
        if f.name == 'top' and f.tag in ('pack', 'torso', 'collar') :
            for c, r in f.each():
                if hs('soot', f.tag, c, r // 2) < 0.25:
                    f.tone(c, r, f.level(c, r) - 1.2)
        return
    if f.mat == 'enamel' and f.h >= 4 and f.tag != 'breast':
        for c in range(f.w):
            k = hs('bleed', f.tag, f.name, f.pi, c)
            if k < 0.3:
                for r in range(f.h - 1 - int(k * 10), f.h):
                    f.put(c, r, RUST[1] if r == f.h - 1 else RUST[0])
    if f.mat in ('steel', 'frame') and f.tag in ('fore', 'shin', 'upper', 'thigh', 'abdomen', 'pelvis') and f.h >= 6:
        for c in range(f.w):
            k = hs('oil', f.tag, f.name, f.pi, c)
            if k < 0.18:
                for r in range(0, 2 + int(k * 25)):
                    f.put(c, r, OIL)
    if f.mat == 'steel' and f.w >= 6 and f.h >= 6:
        for i in range(min(2, f.w * f.h // 70)):
            c = int(hs('sx', f.tag, f.name, f.pi, i) * (f.w - 3))
            r = int(hs('sy', f.tag, f.name, f.pi, i) * (f.h - 2)) + 1
            for k in range(3):
                if 0 <= c + k < f.w and 0 <= r - k // 2 < f.h:
                    f.tone(c + k, r - k // 2, f.level(c + k, r - k // 2) + 1.3)


def detail(f):
    t, n = f.tag, f.name
    grime(f)
    if f.mat == 'steel' and t not in ('launcher', 'fist'):
        seams(f, 6 if f.w > 9 else 4, int(hs(t) * 97))
    if f.mat == 'enamel' and t != 'breast':
        chips(f, f.w * f.h // 45, 3)
        if f.name not in ('top', 'bottom') and f.h >= 4:      # grime gathered at the lower edge
            for c in range(f.w):
                if hs('grime', f.tag, f.name, c) < 0.6:
                    f.tone(c, f.h - 2, f.level(c, f.h - 2) - 1)
    if t == 'torso':
        if n == 'front':                                     # round the breastplate: the frame shows
            for r in range(f.h):
                for c in (0, 1, f.w - 2, f.w - 1):
                    f.tone(c, r, f.level(c, r) - 0.4)
        if n in ('right', 'left'):
            slits(f, (4, 7, 10), 3, f.w - 3)
        if n == 'back':
            for c, r in f.each():
                if 3 <= c < f.w - 3 and r >= 6:
                    f.tone(c, r, f.level(c, r) - 1)          # where the pack sits
    if t == 'breast':
        if n == 'front':
            for c, r in f.each():                            # a plain bevelled plate: the face, not the chest, leads
                lv = 3
                if r == 0 or c == 0:
                    lv = 4
                elif r == f.h - 1 or c == f.w - 1:
                    lv = 1
                f.tone(c, r, lv)
            stencil(f, '07', 1, 1)                           # its number, off to one side (nothing centred: a centred mark read as a mouth)
            for c in range(f.w - 5, f.w - 1):
                f.put(c, f.h - 2, HAZARD[1] if c % 2 == 0 else BLACK)
            for c, r in ((1, f.h - 2), (2, f.h - 2), (f.w - 2, f.h - 2), (f.w - 3, f.h - 2), (1, f.h - 3), (f.w - 2, f.h - 3)):
                f.put(c, r, RUST[0] if r == f.h - 3 else RUST[1])
            f.put(f.w - 3, 1, (40, 40, 36))                  # the status light's socket
            eyes.putpixel((f.u + f.w - 3, f.v + 1), AMBER + (255,))
            img.putpixel((f.u + f.w - 3, f.v + 1), (196, 120, 40, 255))
    if t == 'collar' or t == 'abdomen' or t == 'elbow':
        ribs(f, 2)
    if t == 'pelvis' and n == 'front':
        for c in range(f.w):
            if c % 3 == 1:
                f.tone(c, f.h - 2, f.level(c, f.h - 2) - 1.6)
    if t in ('vent_r', 'vent_l') and n in ('right', 'left'):
        for r in range(1, f.h - 1, 2):
            for c in range(1, f.w - 1):
                f.put(c, r, SOOT)
    if t == 'pack':
        if n == 'back':
            slits(f, (2, 4, 6), 2, f.w - 2)
            for r in (2, 4, 6):                              # the vents glowing from inside
                for c in range(3, f.w - 3):
                    f.put(c, r, (60, 14, 10))
                    eyes.putpixel((f.u + c, f.v + r), (110, 22, 12, 255))
            for c in range(3, 8):                            # a warning label
                for r in range(8, 11):
                    f.put(c, r, HAZARD[1])
            f.put(5, 9, BLACK)
            f.put(5, 8, BLACK)
            f.put(9, 9, (40, 40, 36))
            eyes.putpixel((f.u + 9, f.v + 9), LAMP[1] + (255,))
            img.putpixel((f.u + 9, f.v + 9), LAMP[0] + (255,))
        if n in ('right', 'left'):
            cables(f, (1, 3))
    if t == 'pipe':
        if n == 'top':
            for c, r in f.each():
                f.put(c, r, (90, 30, 14))
                eyes.putpixel((f.u + c, f.v + r), (150, 52, 18, 255))   # the exhausts' heat
        else:
            for c in range(f.w):
                f.tone(c, 0, 0)
    if t == 'fin' and n not in ('top', 'bottom'):
        for r in range(0, f.h, 2):
            f.tone(0, r, f.level(0, r) - 1)
    if t == 'magazine' and n in ('right', 'top'):
        for c in range(0, f.w, 2):
            f.put(c, 0 if n == 'right' else f.h - 1, (120, 108, 86))
    if t in ('tasset_r', 'tasset_l'):
        outer = 'right' if t == 'tasset_r' else 'left'
        if n == outer:
            hazard(f, f.h - 1, f.h, 0)
            rivet(f, 1, 1, 5)
            rivet(f, f.w - 2, 1, 6)
    if t == 'whip':
        if n not in ('top', 'bottom'):
            for r in range(0, f.h, 3):
                f.tone(0, r, 3.5)
    if t == 'tip':
        for c, r in f.each():
            f.put(c, r, LAMP[0])
            eyes.putpixel((f.u + c, f.v + r), LAMP[1] + (255,))
    if t == 'helmet':
        if n == 'top':
            for r in range(f.h):                             # the crest down the crown
                for c in (4, 5):
                    f.tone(c, r, f.level(c, r) + 1)
            for c in range(f.w):
                f.tone(c, 2, f.level(c, 2) - 1.4)
        if n == 'front':
            for c, r in f.each():
                if r >= 2:
                    f.tone(c, r, 0)                          # under the brow, round the visor: in shadow
        if n in ('right', 'left'):
            for r in range(2, f.h - 1):
                f.tone(f.w // 2, r, f.level(f.w // 2, r) - 1.5)
        if n == 'back':
            slits(f, (3, 5), 2, f.w - 2)
    if t == 'brow':
        if n == 'front':
            for c in range(f.w):
                f.tone(c, f.h - 1, 1.2)
        if n == 'bottom':
            for c, r in f.each():
                f.tone(c, r, 0)
    if t == 'grille':
        if n == 'front':
            for c, r in f.each():
                if r <= 1:                                   # the dark band under the brow, two lamps set in it
                    lamp = c in (1, 2, 5, 6)
                    ring = c in (0, 7)
                    if lamp:
                        col = LAMP[2] if (c in (1, 6) and r == 0) else LAMP[1]
                        f.put(c, r, col)
                        eyes.putpixel((f.u + c, f.v + r), col + (255,))
                    elif ring:
                        f.put(c, r, (120, 20, 20))
                        eyes.putpixel((f.u + c, f.v + r), (90, 12, 10, 255))
                    else:
                        f.tone(c, r, 0)
                elif r == 2:
                    f.tone(c, r, 3)                          # the grille's frame
                else:                                        # bars, upright, as a riot guard's
                    f.tone(c, r, 3 if c % 2 == 0 else 0)
        elif n != 'back':
            for c, r in f.each():
                f.tone(c, r, 1)
    if t == 'chin':
        if n == 'front':
            for c in range(f.w):
                f.tone(c, 0, 4)
            rivet(f, 1, 1, 21)
            rivet(f, f.w - 2, 1, 22)
    if t == 'cheek' and n == 'front':
        for r in range(f.h):
            f.tone(0, r, f.level(0, r) + (0.8 if r % 2 == 0 else -0.6))
    if t == 'ear' and n in ('right', 'left'):
        for c, r in f.each():
            if 1 <= c <= 2 and 1 <= r <= 2:
                f.tone(c, r, 3.5 if (c, r) == (1, 1) else 2.5)
    if t in ('pauldron_r', 'pauldron_l'):
        outer = 'right' if t == 'pauldron_r' else 'left'
        if n == outer:
            hazard(f, f.h - 2, f.h, 0)
            stencil(f, '07' if t == 'pauldron_r' else '07', 1, 0)
        if n in ('front', 'back'):
            hazard(f, f.h - 2, f.h, 1)
        if n == 'top':
            for c, r in f.each():                            # its ridge
                if (t == 'pauldron_r' and c == 3) or (t == 'pauldron_l' and c == f.w - 4):
                    f.tone(c, r, 4.5)
    if t == 'upper' or t == 'thigh':
        cables(f, (1, f.w - 2))
    if t == 'fore':
        if n == 'front' or n == 'back':
            for c in range(f.w):
                f.tone(c, 1, f.level(c, 1) - 1.4)
                f.tone(c, f.h - 2, f.level(c, f.h - 2) - 1.4)
    if t == 'launcher' and n not in ('top', 'bottom'):
        for r in (0, 4, 8, f.h - 1):                         # its bands
            for c in range(f.w):
                f.tone(c, r, f.level(c, r) - 1.4)
        if n == 'right':
            for r in range(1, 4):                            # a window on its feed: the bolts inside
                f.put(1, r, (150, 134, 100))
                f.put(2, r, (92, 80, 60))
    if t == 'magazine' and n == 'right':
        for r in range(1, f.h - 1):
            f.put(1, r, (150, 134, 100) if r % 2 else (92, 80, 60))
    if t == 'barrel':
        if n == 'bottom':
            for c, r in f.each():
                hot = (c, r) == (1, 1)
                f.put(c, r, (40, 12, 8) if not hot else (150, 40, 20))
                eyes.putpixel((f.u + c, f.v + r), ((230, 70, 30) if hot else (120, 24, 12)) + (255,))   # the bore, hot
        elif n not in ('top',):
            hazard(f, 0, 1, 0)                               # a hazard ring at its root
            for c in range(f.w):
                f.tone(c, f.h - 1, 3.5)                      # the muzzle's lip
    if t == 'guard':
        if n == 'left':
            hazard(f, 0, 2, 0)
    if t == 'fist':
        if n == 'front':
            for c in range(f.w):                             # the knuckles
                f.tone(c, 1, f.level(c, 1) + (1.2 if c % 2 == 0 else -1.4))
            for c in range(f.w):
                f.tone(c, 3, f.level(c, 3) - 1.5)
        if n == 'bottom':
            for c, r in f.each():
                f.tone(c, r, 0 if c in (0, f.w - 1) or r in (0, f.h - 1) else 0.8)
        if n in ('right', 'left', 'back'):
            for c in range(f.w):
                f.tone(c, 3, f.level(c, 3) - 1.5)
    if t == 'shin' and n in ('right', 'left'):
        hazard(f, 0, 1, 0)
    if t == 'greave' and n == 'front':
        for r in range(f.h):
            f.tone(f.w // 2 - 1, r, f.level(f.w // 2 - 1, r) + 1)
    if t == 'knee':
        if n == 'front':
            f.tone(1, 1, 4.5)
            f.tone(2, 1, 4)
    if t == 'piston' and n not in ('top', 'bottom'):
        for c, r in f.each():
            if r < 3:
                f.tone(c, r, 1, RAMPS['frame'])              # the cylinder; the rod below it bright
    if t == 'knuckles' and n == 'front':
        for c, r in f.each():
            f.tone(c, r, 1 if c in (0, 4, 8) else (4 if r == 0 else 2))   # four knuckle plates
    if t == 'foot':
        if n == 'bottom':
            for c, r in f.each():
                f.tone(c, r, 0 if r % 3 == 0 else 1)
        if n == 'front':
            hazard(f, 0, 1, 0)
            for c in range(f.w):
                f.put(c, 1, RUST[0] if hs('toe', f.pi, c) < 0.5 else RAMPS['frame'][1])
        if n == 'top':
            for c in range(f.w):
                f.tone(c, 0, 3.5)


def paint():
    allf = []
    for (pi, bi), (u, v) in sorted(UV.items()):
        b = PARTS[pi][3][bi]
        for name, rect, at in faces(u, v, b):
            if rect[2] <= 0 or rect[3] <= 0:
                continue
            f = Face(pi, bi, b, name, rect, at)
            base_coat(f)
            allf.append(f)
    for f in allf:
        detail(f)
    for f in allf:
        wear(f)
    return allf


def crack_layers(allf):
    """Its own damage: dark cracks, each on one slant, more and longer at each level (no holes: they read as spots)."""
    big = [f for f in allf if f.w >= 4 and f.h >= 4 and f.mat in ('steel', 'enamel')]
    for level, sheet in enumerate(cracks):
        for f in big:
            n = (1, 2, 3)[level] if f.w * f.h > 40 else (0, 1, 1)[level]
            for i in range(n):
                if hs('ck', f.tag, f.name, f.pi, i) > 0.6 + 0.12 * (2 - level) and level < 2:
                    continue
                c = int(hs('cx', f.tag, f.name, f.pi, i) * f.w)
                r = int(hs('cy', f.tag, f.name, f.pi, i) * f.h)
                length = 3 + level * 3 + int(hs('cl', f.pi, i) * 3)
                for k in range(length):
                    if 0 <= c < f.w and 0 <= r < f.h:
                        sheet.putpixel((f.u + c, f.v + r), (16, 16, 18, 255))
                        if c + 1 < f.w and sheet.getpixel((f.u + c + 1, f.v + r))[3] == 0:
                            sheet.putpixel((f.u + c + 1, f.v + r), (210, 206, 192, 255) if f.mat == 'enamel' else (101, 107, 118, 255))
                        if level == 2 and k == length // 2 and i == 0:
                            glow.putpixel((f.u + c, f.v + r), (255, 140, 40, 255))   # the works glowing inside
                            sheet.putpixel((f.u + c, f.v + r), (200, 100, 30, 255))
                    turn = hs('ct', f.pi, f.name, i, k)
                    lean = 1 if hs('lean', f.pi, f.name, i) < 0.5 else -1
                    c += lean if turn < 0.55 else 0                 # a crack runs on one slant, stepping
                    r += 1
        # what each level shows includes the one before
        if level > 0:
            under = cracks[level - 1]
            for y in range(TH):
                for x in range(TW):
                    if under.getpixel((x, y))[3] and not sheet.getpixel((x, y))[3]:
                        sheet.putpixel((x, y), under.getpixel((x, y)))


# --- the Java: createBodyLayer() from the same table -----------------------------------------------------------------
def num(v):
    s = ('%.3f' % v).rstrip('0').rstrip('.')
    return (s if '.' in s else s + '.0') + 'F'


def java():
    lines = ['    public static LayerDefinition createBodyLayer() {',
             '        MeshDefinition mesh = new MeshDefinition();',
             '        PartDefinition root = mesh.getRoot();']
    for pi, (name, parent, off, boxes) in enumerate(PARTS):
        b = 'CubeListBuilder.create()'
        for bi, (x, y, z, w, hh, d, _, _) in enumerate(boxes):
            u, v = UV[(pi, bi)]
            b += '\n                .texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s)' % (u, v, num(x), num(y), num(z), num(w), num(hh), num(d))
        par = 'root' if parent is None else 'p_' + parent
        lines.append('        PartDefinition p_%s = %s.addOrReplaceChild("%s", %s,\n                PartPose.offset(%s, %s, %s));'
                     % (name, par, name, b, num(off[0]), num(off[1]), num(off[2])))
    lines.append('        return LayerDefinition.create(mesh, %d, %d);' % (TW, TH))
    lines.append('    }')
    body = '\n'.join(lines)
    src = open(JAVA, encoding='utf-8').read()
    a, b = '    // BEGIN GENERATED by tools/paint_sentry_golem.py\n', '    // END GENERATED\n'
    i, j = src.index(a) + len(a), src.index(b)
    src = src[:i] + body + '\n' + src[j:]
    open(JAVA, 'w', encoding='utf-8', newline='\n').write(src)


# --- a look at it: the model drawn in parallel projection ------------------------------------------------------------
def view(allf, yaw, scale=6):
    """The model at rest, turned yaw degrees (0 = from the front), each texel a small square, nearest drawn last."""
    ca, sa = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    pts = []
    for f in allf:
        for c, r in f.each():
            px = img.getpixel((f.u + c, f.v + r))
            if px[3] == 0:
                continue
            x, y, z = f.world(c, r)
            # the camera looks along +z from -z (front), turned about y
            sx = x * ca - z * sa
            sz = x * sa + z * ca
            pts.append((sz, sx, y, px))
    pts.sort(key=lambda p: -p[0])
    W, H = 64 * scale, 56 * scale
    out = Image.new('RGBA', (W, H), (200, 210, 220, 255))
    d = ImageDraw.Draw(out)
    for sz, sx, y, px in pts:
        cx, cy = W / 2 - sx * scale, (y + 28) * scale
        d.rectangle([cx - scale * .6, cy - scale * .6, cx + scale * .6, cy + scale * .6], fill=px)
    return out


def main():
    allf = paint()
    crack_layers(allf)
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, 'sentry_golem.png'))
    eyes.save(os.path.join(OUT, 'sentry_golem_eyes.png'))
    for name, sheet in zip(('low', 'medium', 'high'), cracks):
        sheet.save(os.path.join(OUT, 'sentry_golem_crack_%s.png' % name))
    glow.save(os.path.join(OUT, 'sentry_golem_crack_glow.png'))
    java()
    os.makedirs(PREVIEW, exist_ok=True)
    views = [view(allf, a) for a in (0, 90, 180, 35)]
    sheet = Image.new('RGBA', (sum(v.width for v in views), views[0].height))
    x = 0
    for v in views:
        sheet.paste(v, (x, 0))
        x += v.width
    sheet.save(os.path.join(PREVIEW, 'sentry_golem_views.png'))
    colours = {p for p in img.getdata() if p[3]}
    print('ok: %d colours, sheet used to v=%d' % (len(colours), max(v + PARTS[pi][3][bi][5] + PARTS[pi][3][bi][4] for (pi, bi), (u, v) in UV.items())))


if __name__ == '__main__':
    main()
