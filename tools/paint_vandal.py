"""Paints the Vandal's skin and its glowing eyes (client/VandalModel) - from nothing of vanilla's.

Run: python tools/paint_vandal.py [--preview PATH]
(writes src/main/resources/assets/nookofthedead/textures/entity/vandal.png, vandal_eyes.png and vandal_eyes_rage.png,
256 x 128; --preview also writes the skin enlarged, to look at)

The look (the user, 2026-10-17, from a picture they gave: a hunched thing on long clawed arms, long black hair, strips
of skin and rags hanging off it; and "ゾンビが変異した果ての姿だから…ゾンビの延長線上にいることがわかるような表現"):
a zombie's green hide gone cold and grey, pale where the light falls on its back and shoulders and dark beneath, ribs
and spine standing out, the skin split and stretched over the hump; a man's face gone to a skull, deep sockets with a
point of light in each (yellow, red once it is wounded to half), the lips gone from the teeth; black hair on the head
and bristling along the back, but what hangs off the body pale - skin peeled into strips, as in the picture; and the
clothes every zombie wears, burst by what it grew into - the cyan shirt in shreds round its chest and a sleeve on each
arm, the blue trousers torn off at the thigh - and a bandage coming loose from each forearm, an iron cuff on the left
wrist with the chain broken off. Claws of dark horn, pale at the tips.

The light is painted in: every face of every box is shaded by how high it stands and which way it faces in the model's
rest pose (the same numbers as VandalModel's), lit from above, darkening to the ground - in a few steps of each colour,
as Minecraft's own creatures are, not a smooth gradient.

The UV boxes are VandalModel's: a cube w x h x d at (u, v) has top (u+d, v) w x d, bottom (u+d+w, v) w x d, and its sides
in a row at v+d, h high: right d, front w, left d, back w. A plane has only its two broad faces.
"""
import math
import os
import random
import sys
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures', 'entity')
W, H = 256, 128
img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
eyes = Image.new('RGBA', (W, H), (0, 0, 0, 0))
rage = Image.new('RGBA', (W, H), (0, 0, 0, 0))

# --- colours: each a ramp from shadow to light ---------------------------------------------------------------------
HIDE = [(20, 28, 30), (32, 44, 44), (46, 62, 58), (62, 84, 70), (80, 106, 84), (100, 128, 100), (124, 150, 120), (152, 174, 146)]
FACE = [(34, 42, 42), (52, 64, 60), (74, 88, 80), (100, 114, 102), (128, 140, 124), (156, 166, 148), (182, 188, 168)]
HAIR = [(12, 16, 16), (20, 26, 26), (30, 38, 36), (42, 52, 48), (58, 70, 64), (80, 92, 86)]
SHIRT = [(16, 46, 50), (22, 62, 66), (30, 80, 82), (40, 98, 96), (52, 114, 110), (64, 126, 120)]
TROUSERS = [(22, 22, 44), (32, 32, 62), (44, 44, 84), (58, 58, 106), (76, 76, 128)]
WRAP = [(52, 48, 40), (74, 70, 58), (98, 92, 76), (124, 118, 96), (150, 142, 116)]
FLESH = [(52, 30, 34), (76, 46, 50), (100, 66, 68), (122, 88, 86)]
MOUTH = [(18, 4, 8), (34, 8, 12), (54, 14, 20), (78, 22, 28), (104, 36, 40)]
BONE = [(70, 62, 48), (104, 94, 72), (142, 130, 100), (178, 166, 132), (206, 196, 164)]
HORN = [(22, 20, 20), (36, 32, 30), (56, 50, 44), (88, 80, 68), (128, 118, 98), (168, 158, 132)]
IRON = [(28, 28, 32), (46, 46, 52), (68, 68, 74), (96, 96, 102), (130, 130, 136), (164, 164, 170)]
RUST = [(62, 34, 24), (92, 50, 30), (124, 70, 40)]
SKIN_STRIP = FACE[:5]
PIT = (6, 6, 8)
GLOW = (200, 186, 120)
BLOOD = [(30, 6, 8), (52, 12, 14), (74, 18, 18)]
RED = (255, 54, 36)

SHADE = {}


def value_noise(x, y, scale, salt):
    """Smooth noise 0..1 over the sheet, so patches are blotches, not static."""
    def h(ix, iy):
        return random.Random(ix * 73856093 ^ iy * 19349663 ^ salt).random()
    fx, fy = x / scale, y / scale
    ix, iy = math.floor(fx), math.floor(fy)
    tx, ty = fx - ix, fy - iy
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a, b, c, d = h(ix, iy), h(ix + 1, iy), h(ix, iy + 1), h(ix + 1, iy + 1)
    return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty


def put(x, y, c, sheet=None, a=255):
    sheet = img if sheet is None else sheet
    if 0 <= x < W and 0 <= y < H:
        sheet.putpixel((x, y), tuple(int(v) for v in c[:3]) + (a,))


def paint(x, y, ramp, i):
    """A texel in a ramp's step (clamped), remembered so details can be laid over it a step lighter or darker."""
    i = max(0, min(len(ramp) - 1, int(i)))
    SHADE[(x, y)] = (ramp, i)
    put(x, y, ramp[i])


def shift(x, y, d, ramp=None):
    """A painted texel made d steps lighter (or darker), or the same depth of shade in another colour."""
    r, i = SHADE.get((x, y), (HIDE, 3))
    if ramp is not None and ramp is not r:
        i = round(i * (len(ramp) - 1) / (len(r) - 1))
        r = ramp
    paint(x, y, r, i + d)


# --- the rest pose (VandalModel's): where every face stands, to light it ------------------------------------------------
def rot(p, rx_, ry_, rz_):
    x, y, z = p
    c, s = math.cos(rx_), math.sin(rx_)
    y, z = y * c - z * s, y * s + z * c
    c, s = math.cos(ry_), math.sin(ry_)
    x, z = x * c + z * s, -x * s + z * c
    c, s = math.cos(rz_), math.sin(rz_)
    x, y = x * c - y * s, x * s + y * c
    return (x, y, z)


class Part:
    def __init__(self, parent, off, rx_=0.0, ry_=0.0, rz_=0.0):
        self.parent, self.off, self.r = parent, off, (rx_, ry_, rz_)

    def world(self, p, offsets=True):
        part = self
        while part is not None:
            p = rot(p, *part.r)
            if offsets:
                p = (p[0] + part.off[0], p[1] + part.off[1], p[2] + part.off[2])
            part = part.parent
        return p


A, C = 0.6, 0.45
HIPS = Part(None, (0, 9, 5))
ABDOMEN = Part(None, (0, 7, 4.5), A)
CHEST = Part(ABDOMEN, (0, -7.5, 0), C)
NECK = Part(CHEST, (0, -11, -1.5), 0.3)
HEAD = Part(NECK, (0, -4.5, 0), -1.2)
JAW = Part(HEAD, (0, -0.5, 0.5), 0.3)
UPPER = Part(None, (-8.5, -2.5, -6), 0.25, 0, 0.15)
FORE = Part(UPPER, (0, 11, 0), -0.8, 0, -0.1)
HAND = Part(FORE, (0, 12, 0), 0.35)
PROX = Part(HAND, (-0.6, 2.2, -1.0), 0.0, -0.07)
MID = Part(PROX, (0, 0, -3.3), 1.1)
CLAW = Part(MID, (0, 0, -2.4), 0.95)
THIGH = Part(None, (-4.5, 9.6, 5), -0.4)
SHIN = Part(THIGH, (0, 7, 0), 0.75)
FOOT = Part(SHIN, (0, 7, 0), -0.35)
TOE = Part(FOOT, (0, 1, -4.8), 0.3)

NORMALS = {"top": (0, -1, 0), "bottom": (0, 1, 0), "right": (-1, 0, 0), "front": (0, 0, -1), "left": (1, 0, 0), "back": (0, 0, 1)}


def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def point(box, face, i, j):
    """Where texel (i, j) of a face lies on the box (in its part's frame) - Minecraft's way of laying a cube's faces out."""
    x0, y0, z0, w, h, d = box
    return {"top": (x0 + i + .5, y0, z0 + d - (j + .5)), "bottom": (x0 + i + .5, y0 + h, z0 + d - (j + .5)),
            "right": (x0, y0 + j + .5, z0 + d - (i + .5)), "front": (x0 + i + .5, y0 + j + .5, z0),
            "left": (x0 + w, y0 + j + .5, z0 + i + .5), "back": (x0 + w - (i + .5), y0 + j + .5, z0 + d)}[face]


def lit(part, p, normal, x, y, salt, bias=0.0, grain=1.0):
    """How much light a texel takes, 0..1: higher up and facing up, more; a blotch of its own."""
    wy = part.world(p)[1]
    up = -part.world(normal, offsets=False)[1]
    n = (value_noise(x, y, 2.5, salt) - 0.5) * 2
    return 0.1 + 0.62 * (24 - wy) / 36 + 0.2 * up + 0.16 * n * grain + bias


def solid(part, u, v, box, ramp, salt, bias=0.0, grain=1.0):
    """A box painted in a ramp, lit as it stands. Returns its faces' rectangles and where on the box each texel lies."""
    x0, y0, z0, w, h, d = box
    rects = faces(u, v, int(w), int(h), int(d))
    where = {}
    for face, (fx, fy, fw, fh) in rects.items():
        for i in range(fw):
            for j in range(fh):
                p = point(box, face, i, j)
                where[(fx + i, fy + j)] = (face, p)
                paint(fx + i, fy + j, ramp, lit(part, p, NORMALS[face], fx + i, fy + j, salt, bias, grain) * len(ramp))
    return rects, where


def peel(rect, count, salt):
    """Splits in the skin: a short raw line, its lower lip pale."""
    x0, y0, w, h = rect
    r = random.Random(salt)
    for _ in range(count):
        x, y = x0 + r.randrange(w), y0 + r.randrange(h)
        for k in range(r.randint(2, 3)):
            if x + k < x0 + w:
                shift(x + k, y, -1, FLESH)
                if y + 1 < y0 + h:
                    shift(x + k, y + 1, 1)


def strands(x0, y0, w, h, salt, ramps, gaps=0.12, min_len=0.5, up=False, light=0.6):
    """Hair hanging (or, up=True, standing) in a plane's rectangle: a strand a column, uneven, thinning to a point;
    each strand in one of the ramps, lit from its root."""
    r = random.Random(salt)
    for x in range(x0, x0 + w):
        if r.random() < gaps:
            continue
        length = max(1, int(h * r.uniform(min_len, 1.0)))
        ramp = r.choice(ramps)
        base = light + r.uniform(-0.18, 0.12)
        for k in range(length):
            if k == length - 1 and length > 2 and r.random() < 0.5:
                continue
            y = y0 + h - 1 - k if up else y0 + k
            t = k / max(1, h - 1)
            paint(x, y, ramp, (base - t * 0.45 + (value_noise(x, y, 2, salt) - 0.5) * 0.2) * len(ramp))


def fur(x0, y0, w, h, salt, light=0.6):
    """Hair lying thick (along the back): every column covered down to a ragged end, each strand wandering a texel aside
    now and then, in close tones, lit from where it grows."""
    r = random.Random(salt)
    for x in range(x0, x0 + w):
        length = h - r.randint(0, 4)
        base = light + r.uniform(-0.08, 0.08)
        drift = 0
        for k in range(length):
            if r.random() < 0.12:
                drift = max(-1, min(1, drift + r.choice((-1, 1))))
            xx = min(x0 + w - 1, max(x0, x + drift))
            t = k / max(1, h - 1)
            paint(xx, y0 + k, HAIR, (base - t * 0.35 + (value_noise(xx, k, 1.5, salt) - 0.5) * 0.25) * len(HAIR))
            if xx != x:
                paint(x, y0 + k, HAIR, (base - t * 0.35 - 0.1) * len(HAIR))


def strips(x0, y0, w, h, salt, kinds, light=0.62):
    """Strips of skin and cloth hanging side by side, one to three texels wide, of uneven length, gaps between, the ends
    torn ragged."""
    r = random.Random(salt)
    x = x0
    while x < x0 + w:
        width = r.choice((1, 1, 2, 2, 3))
        if r.random() < 0.18:
            x += 1                                              # a gap
            continue
        ramp = r.choice(kinds)
        length = int(h * r.uniform(0.45, 1.0))
        base = light + r.uniform(-0.15, 0.1)
        for xx in range(x, min(x0 + w, x + width)):
            ragged = length - r.randint(0, 2)
            for k in range(ragged):
                t = k / max(1, h - 1)
                edge = xx in (x, x + width - 1) and width > 1
                paint(xx, y0 + k, ramp, (base - t * 0.4 - (0.08 if edge else 0) + (value_noise(xx, k, 2, salt) - 0.5) * 0.18) * len(ramp))
        x += width


# --- the hips, in the trousers --------------------------------------------------------------------------------------
hips, where = solid(HIPS, 0, 0, (-5, -3, -3, 10, 6, 6), TROUSERS, 11)
for (x, y), (face, p) in where.items():
    if face != "top" and value_noise(x, y, 2.0, 12) < 0.16:      # torn through, the skin showing
        shift(x, y, 0, HIDE)
    elif face in ("front", "back", "right", "left") and p[1] < -2.2:
        shift(x, y, -1)                                         # the waistband, black with filth
x0, y0, w, h = hips["front"]
for y in range(y0 + 1, y0 + h - 1):                             # the fly's seam
    shift(x0 + w // 2, y, -1)


# --- the belly and the chest: hide over the ribs, the shirt still clinging under them, burst along the back ---------
def shirt_over(where, salt, below):
    """The shirt where a texel lies on the underside (local z under {below}, the edge torn), holed, its edge rotten."""
    for (x, y), (face, p) in where.items():
        edge = below + (value_noise(x, y, 2.2, salt) - 0.5) * 3.0
        if face in ("top", "back") or p[2] > edge:
            continue
        if value_noise(x, y, 1.6, salt + 1) < 0.2:
            continue                                            # a hole, the skin through it
        shift(x, y, 0 if p[2] < edge - 0.8 else -1, SHIRT)


belly, where = solid(ABDOMEN, 32, 0, (-5, -8, -3.5, 10, 8, 7), HIDE, 21)
fx, fy, fw, fh = belly["front"]
for y in (fy + 2, fy + 4, fy + 6):                              # the lowest ribs under tight skin
    for x in range(fx + 1, fx + fw - 1):
        if abs(x - (fx + fw / 2 - 0.5)) > 1:
            shift(x, y, 1)
shirt_over(where, 22, -0.5)
peel(belly["right"], 1, 23)
peel(belly["left"], 1, 24)
solid(CHEST, 240, 0, (-1, -5, 3.2, 2, 2, 2), HIDE, 25, bias=0.05)     # the spine's knuckles, under the skin

chest, where = solid(CHEST, 0, 16, (-8, -11, -5.5, 16, 11, 11), HIDE, 31)
fx, fy, fw, fh = chest["front"]
for y in range(fy + 1, fy + fh - 1, 2):                         # the ribs on the underside, the breastbone between
    for x in range(fx + 1, fx + fw - 1):
        mid = abs(x - (fx + fw / 2 - 0.5))
        if mid < 0.6:
            shift(x, y, 1)
        elif mid > 1.5:
            shift(x, y, 1)
            if y + 1 < fy + fh:
                shift(x, y + 1, -1)
bx, by, bw, bh = chest["back"]
for y in range(by, by + bh):                                    # the spine along its back, ridged
    shift(bx + bw // 2, y, 1)
    shift(bx + bw // 2 - 1, y, 1 if y % 2 else -1)
    shift(bx + bw // 2 + 1, y, -1)
r = random.Random(33)
for name in ("back", "top", "right", "left"):                   # the skin stretched over what it grew: pale marks across it
    x0, y0, w, h = chest[name]
    for _ in range(5 if name in ("back", "top") else 3):
        x, y = x0 + r.randrange(w), y0 + r.randrange(h)
        for k in range(r.randint(2, 4)):
            if x + k < x0 + w:
                shift(x + k, y, 1)
shirt_over(where, 34, -1.0)
for (x, y), (face, p) in where.items():
    if face == "top" or (face in ("right", "left") and p[2] > 2.0 + value_noise(x, 0, 1.3, 38) * 3.0):
        r = random.Random(x * 31 + y)
        shift(x, y, -2 + r.randint(-1, 1), HAIR)
peel(chest["back"], 3, 35)
peel(chest["right"], 1, 36)
peel(chest["left"], 1, 37)

hump, where = solid(CHEST, 66, 0, (-6.5, -13.5, -1, 13, 3, 6), HIDE, 41, bias=0.05)
for (x, y), (face, p) in where.items():                         # hair over the hump, the stretched skin showing through
    if value_noise(x, y, 1.6, 42) > 0.38:
        shift(x, y, -2, HAIR)
    elif face in ("back", "top"):
        shift(x, y, 1)

# --- the neck and the head ------------------------------------------------------------------------------------------
neck, where = solid(NECK, 104, 0, (-2.5, -5, -2.5, 5, 5, 5), HIDE, 51, bias=-0.05)
for name in ("front", "right", "left"):                         # the cords of the neck
    x0, y0, w, h = neck[name]
    for y in range(y0, y0 + h):
        shift(x0 + 1, y, 1)
        shift(x0 + w - 2, y, 1)

skull, where = solid(HEAD, 124, 0, (-3.5, -7.5, -4.5, 7, 7, 7), FACE, 61)
for (x, y), (face, p) in where.items():                         # the scalp: hair over all but the face, thin in patches
    if face in ("top", "back") or (face in ("right", "left") and p[2] > -3.0 + value_noise(x, y, 1.5, 62) * 2.0):
        shift(x, y, -1, HAIR)
        if random.Random(x * 7 + y * 13).random() < 0.12:
            shift(x, y, 1)
    if face == "bottom" and p[2] < -1.0:                        # under the skull: the roof of the mouth in front
        shift(x, y, 0, MOUTH)
# The face (the user, 2026-10-18: "顔、ちょっと間抜けっぽい…もうちょっとホラーっぽく"): not round eyes over buck teeth -
# a glare. The brow pulled down to a point between the sockets, the sockets uneven and black, a pinpoint of light low
# in one and high in the other (a lock of hair over the first), blood run down from them; the nose a hole; the cheeks
# torn back from the mouth; a mouthful of thin uneven teeth (VandalModel's needles), the gums black.
fx, fy, fw, fh = skull["front"]
for x in range(fx, fx + fw):                                    # the hairline, ragged
    shift(x, fy, -1, HAIR)
    if x in (fx + 1, fx + 4):
        shift(x, fy + 1, -1, HAIR)
for y in range(fy + 1, fy + 3):                                 # the temples fallen in
    shift(fx, y, -2)
    shift(fx + fw - 1, y, -2)
shift(fx + 3, fy + 1, -2)                                       # a furrow down the forehead
for dx in range(fw):                                            # the brow's shadow, deepest where it is pulled down
    shift(fx + dx, fy + 2, -1 if dx in (0, 6) else -2 if dx in (1, 5) else -3)
for (dx, dy) in ((1, 3), (2, 3), (1, 4), (2, 4), (4, 3), (5, 3), (4, 4), (5, 4), (5, 5)):  # the sockets, uneven
    put(fx + dx, fy + dy, PIT)
    SHADE[(fx + dx, fy + dy)] = (HIDE, 0)
for (dx, dy) in ((2, 4), (4, 3)):                               # the light in them: low in one, high in the other
    put(fx + dx, fy + dy, (36, 30, 14))
    put(fx + dx, fy + dy, GLOW, eyes)
    put(fx + dx, fy + dy, RED, rage)
paint(fx + 3, fy + 3, FACE, 3)                                  # the bone between them
shift(fx + 3, fy + 4, -2)                                       # the nose a hole
put(fx + 3, fy + 5, PIT)
for (dx, dy) in ((1, 5), (1, 6), (5, 6), (4, 5)):               # blood run down from the sockets
    paint(fx + dx, fy + dy, BLOOD, 1 if dy == 6 else 2)
for (dx, dy) in ((0, 5), (0, 6), (6, 5), (6, 6), (2, 6)):       # the cheeks fallen in
    shift(fx + dx, fy + dy, -2)

brow, where = solid(HEAD, 152, 0, (-4, -6.6, -5.3, 8, 2, 2), HIDE, 63, bias=0.0)
x0, y0, w, h = brow["front"]
for x in range(x0, x0 + w):                                     # the ridge of the brow, its under edge dark and dipping
    shift(x, y0 + h - 1, -2 if x - x0 in (3, 4) else -1)
    if x - x0 in (1, 6):
        shift(x, y0, 1)
maxilla, where = solid(HEAD, 180, 0, (-2.5, -1.5, -5, 5, 2, 3), FACE, 65, bias=-0.1)
x0, y0, w, h = maxilla["front"]
for x in range(x0, x0 + w):                                     # the lip gone: the gum, black and split, over the teeth
    paint(x, y0, BLOOD if (x - x0) % 2 else FACE, 0)
    paint(x, y0 + 1, MOUTH, 0 if (x - x0) % 2 else 1)
for name in ("right", "left"):                                  # the cheeks torn back from the corners of the mouth
    x0, y0, w, h = maxilla[name]
    for x in range(x0, x0 + w):
        paint(x, y0 + h - 1, MOUTH, 1)
        if x == x0 + w // 2:
            paint(x, y0, BLOOD, 1)
x0, y0, w, h = maxilla["bottom"]
for x in range(x0, x0 + w):
    for y in range(y0, y0 + h):
        paint(x, y, MOUTH, 0 if value_noise(x, y, 1.2, 68) < 0.5 else 1)
solid(HEAD, 218, 0, (-2.5, -0.6, -4.2, 5, 2, 4), MOUTH, 66, bias=-0.6)    # the throat, black
for (u, h, salt) in ((180, 3, 67), (186, 2, 69)):              # the needles: yellowed, dark at the gum, a pale point
    needle, where = solid(HEAD, u, 8, (-0.5, 0, -0.5, 1, h, 1), BONE, salt, bias=0.1, grain=0.4)
    for (x, y), (face, p) in where.items():
        if face not in ("top", "bottom"):
            paint(x, y, BONE, 1 + p[1] / h * 3.2)

jaw, where = solid(JAW, 196, 0, (-3, 0, -5.5, 6, 2, 5), FACE, 71, bias=-0.15)
x0, y0, w, h = jaw["top"]                                       # the tongue's bed, black-red
for x in range(x0, x0 + w):
    for y in range(y0, y0 + h):
        paint(x, y, MOUTH, int(value_noise(x, y, 1.5, 72) * 2.2))
x0, y0, w, h = jaw["front"]
for x in range(x0, x0 + w):                                     # the chin, the lip gone from it too
    paint(x, y0, MOUTH, 1 if (x - x0) % 2 else 0)
    if x - x0 in (1, 4):
        paint(x, y0 + 1, BLOOD, 1)
for name in ("right", "left"):
    x0, y0, w, h = jaw[name]
    for x in range(x0, x0 + w):
        paint(x, y0, MOUTH, 1)

strands(46, 40, 10, 6, 81, [HAIR, HAIR, BLOOD], gaps=0.3, min_len=0.3, light=0.35)   # strands from the chin, black, wet
strips(56, 44, 8, 5, 82, [SKIN_STRIP, SKIN_STRIP, BLOOD], light=0.4)       # the torn cheeks

# --- the hair, the bristling ridge, what lies along the back --------------------------------------------------------
strands(0, 40, 16, 18, 91, [HAIR], gaps=0.08, min_len=0.55, light=0.55)
strands(16, 47, 14, 16, 92, [HAIR], gaps=0.08, min_len=0.5, light=0.5)
strands(198, 40, 8, 16, 98, [HAIR], gaps=0.05, min_len=0.6, light=0.5)
r = random.Random(93)
for i in range(8):                                              # the fringe: a lock down over one eye, short elsewhere
    for x in (30 + i, 38 + (7 - i)):
        length = 4 if i in (1, 2) else r.randint(3, 4) if i in (0, 7) else r.randint(1, 2)
        for y in range(40, 40 + length):
            paint(x, y, HAIR, 3 - (y - 40))
strands(64, 40, 24, 8, 94, [HAIR], gaps=0.15, min_len=0.35, up=True, light=0.75)
strands(88, 40, 20, 6, 95, [HAIR], gaps=0.2, min_len=0.35, up=True, light=0.75)
fur(150, 40, 28, 16, 96, light=0.68)
fur(178, 40, 20, 14, 97, light=0.6)

# --- what hangs from it: strips of skin, of the shirt, locks of hair ------------------------------------------------
strips(0, 64, 16, 20, 101, [SKIN_STRIP, SKIN_STRIP, HIDE, SHIRT, SKIN_STRIP], light=0.66)
strips(16, 64, 12, 16, 102, [SKIN_STRIP, HIDE, SHIRT, SKIN_STRIP], light=0.66)
strips(28, 64, 20, 18, 103, [SKIN_STRIP, SKIN_STRIP, HIDE, SHIRT, HAIR], light=0.66)
strands(48, 72, 16, 20, 104, [SKIN_STRIP, SKIN_STRIP, HIDE, HAIR], gaps=0.12, min_len=0.45, light=0.62)
strands(64, 70, 12, 16, 105, [SKIN_STRIP, HAIR, SKIN_STRIP, HIDE], gaps=0.1, min_len=0.45, light=0.62)
strips(100, 64, 28, 16, 106, [SKIN_STRIP, HIDE, HAIR, TROUSERS, SKIN_STRIP], light=0.55)

# --- rags: the trousers' at the waist, the sleeve's and the bandage's on the forearms -------------------------------
strips(108, 40, 24, 8, 111, [TROUSERS], light=0.6)
strips(132, 40, 12, 6, 112, [SHIRT], light=0.6)
strips(144, 40, 6, 10, 113, [WRAP], light=0.6)

# --- the arms, the hands, the claws ---------------------------------------------------------------------------------
upper, where = solid(UPPER, 54, 16, (-3.5, -2, -3.5, 7, 13, 7), HIDE, 121)
for (x, y), (face, p) in where.items():
    if face == "top":                                           # the shoulder, under its hair
        paint(x, y, HAIR, 2 + random.Random(x * 5 + y).randint(0, 2))
        continue
    if face == "bottom":
        continue
    r = random.Random(x * 977 + 5)
    reach = (2.5 if face == "left" else 5.0) + r.random() * 4.0   # how far down this lock hangs (less on the inside)
    pale = r.random() < 0.55                                    # a strip of pale skin among the hair
    if p[1] < -2 + reach:
        k = (p[1] + 2) / reach
        ramp = SKIN_STRIP if pale else HAIR
        paint(x, y, ramp, ((0.72 if pale else 0.64) - k * 0.35) * len(ramp))
    elif p[1] < -2 + reach + 2.2 and value_noise(x, y, 1.6, 124) > 0.2:
        shift(x, y, 0, SHIRT)                                   # the sleeve, split round the swollen arm, under the hair
    elif face in ("front", "back") and abs(p[0]) < 0.6:
        shift(x, y, 1)                                          # a sinew down it
peel(upper["back"], 1, 125)
peel(upper["right"], 1, 126)


def bandage(where, salt, phase):
    """A bandage wound round, coming loose: diagonal turns of it, frayed, gaps where it has slipped."""
    for (x, y), (face, p) in where.items():
        if face in ("top", "bottom"):
            continue
        turn = (p[1] + (p[0] + p[2]) * 0.35 + phase) % 4.0
        if turn < 1.6 and value_noise(x, y, 2.6, salt) > 0.3:
            shift(x, y, 1 if turn < 1.0 else 0, WRAP)


fore_a, where = solid(FORE, 82, 16, (-2.5, -0.5, -2.5, 5, 7, 5), HIDE, 131, bias=-0.05)
bandage(where, 132, 0.0)
fore_b, where = solid(FORE, 102, 16, (-2, 6, -2, 4, 6, 4), HIDE, 133, bias=-0.1)
bandage(where, 134, 0.6)
palm, where = solid(HAND, 118, 16, (-2.5, -0.5, -1.5, 5, 3, 3), HIDE, 141, bias=0.05)
x0, y0, w, h = palm["front"]
for x in range(x0, x0 + w, 2):                                  # the knuckles, bony
    shift(x, y0 + h - 1, 2)
x0, y0, w, h = palm["back"]
for y in range(y0, y0 + h):                                     # the tendons on the back of the hand
    for x in range(x0 + 1, x0 + w - 1, 2):
        shift(x, y, 1)
prox, where = solid(PROX, 152, 8, (-0.5, -0.5, -3.5, 1, 1, 4), HIDE, 151, bias=0.05)
for (x, y), (face, p) in where.items():
    if p[2] > -0.5 and face != "bottom":
        shift(x, y, 1)                                          # the knuckle
solid(MID, 162, 8, (-0.5, -0.5, -2.6, 1, 1, 3), HIDE, 152, bias=0.0)
claw, where = solid(CLAW, 170, 8, (-0.5, -0.5, -2.8, 1, 1, 3), HORN, 153, grain=0.4)
for (x, y), (face, p) in where.items():                         # horn, dark at the root and pale at the point
    paint(x, y, HORN, 1 + (0.2 - p[2]) / 3.0 * 4.5)

cuff, where = solid(FORE, 200, 16, (-2.5, 8.5, -2.5, 5, 2, 5), IRON, 161)
for (x, y), (face, p) in where.items():
    if value_noise(x, y, 1.6, 162) > 0.8:
        shift(x, y, 0, RUST)                                    # rust
    elif face not in ("top", "bottom") and p[1] < 8.9:
        shift(x, y, 1)                                          # the rim catching the light
for x in (220, 222):                                            # the chain: two links one under another, a third half gone
    for y in range(16, 22):
        k = (y - 16) % 3
        put(x, y, IRON[3] if k == 1 else IRON[2] if k == 0 else IRON[1])
        if y < 21:
            put(x + 1, y, IRON[1] if k == 1 else IRON[4] if k == 0 else IRON[2])

# --- the legs and the feet ------------------------------------------------------------------------------------------
thigh, where = solid(THIGH, 136, 16, (-3, -1, -3, 6, 8, 6), HIDE, 171)
for (x, y), (face, p) in where.items():                         # the trousers, torn off above the knee
    if face == "top" or p[1] < 3.5 + (value_noise(x, y, 1.8, 172) - 0.5) * 3.0:
        if value_noise(x, y, 1.5, 173) > 0.15:
            shift(x, y, 0, TROUSERS)
    elif face == "front" and p[1] > 5.5 and abs(p[0]) < 1.5:
        shift(x, y, 1)                                          # the knee
shin, where = solid(SHIN, 160, 16, (-2.5, 0, -2.5, 5, 7, 5), HIDE, 181, bias=-0.05)
x0, y0, w, h = shin["front"]
for y in range(y0, y0 + h - 2):                                 # the shinbone under the skin
    shift(x0 + w // 2, y, 1)
solid(FOOT, 180, 16, (-2, -0.5, -5, 4, 2, 6), HIDE, 191, bias=-0.15)
toe, where = solid(TOE, 248, 0, (-0.5, -0.5, -3, 1, 1, 3), HORN, 192, grain=0.4)
for (x, y), (face, p) in where.items():
    paint(x, y, HORN, 1 + (-p[2]) / 3.0 * 3.5)

os.makedirs(OUT, exist_ok=True)
img.save(os.path.join(OUT, "vandal.png"))
eyes.save(os.path.join(OUT, "vandal_eyes.png"))
rage.save(os.path.join(OUT, "vandal_eyes_rage.png"))
for old in ("vandal_tyrant.png", "vandal_beast.png", "vandal_bound.png"):
    path = os.path.join(OUT, old)
    if os.path.exists(path):
        os.remove(path)
if "--preview" in sys.argv:
    img.resize((W * 4, H * 4), Image.NEAREST).save(sys.argv[sys.argv.index("--preview") + 1])
print("wrote vandal.png, vandal_eyes.png and vandal_eyes_rage.png")
