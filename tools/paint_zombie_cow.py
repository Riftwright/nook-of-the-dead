"""Paints the infected cow's skin (64 x 64, the cow model's own layout) - from nothing of vanilla's.

Run: python tools/paint_zombie_cow.py  (writes src/main/resources/assets/nookofthedead/textures/entity/zombie_cow.png)

The user (2026-10-25): only a recoloured cow, it looked like nothing much - rotting in places, its ribs showing, as the
zoglin is. So: a dirty grey-white hide with the cow's dark patches gone brown-grey, the hair fallen out in raw patches;
on each flank the hide torn open on the ribs, the bone bare over a dark hollow; one eye clouded, the other socket empty;
the muzzle raw; a horn snapped; bone through the hide of a foreleg and a hind; the udder shrunk to a grey bag.

The UV boxes (CowModel): a cube w x h x d at (u, v) has top (u+d, v) w x d, bottom (u+d+w, v) w x d, and its sides in a
row at v+d, h high: right d, front w, left d, back w. The body is turned on its side (a quarter turn about x): its front
face is the cow's back, its back face the belly, its sides the flanks (10 high on the cow, 18 along it).
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures', 'entity',
                   'zombie_cow.png')
rng = random.Random(1025)
img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))

HIDE = (138, 132, 118)       # what was white, gone grey and dirty
PATCH = (78, 68, 60)         # what was black, gone brown-grey
RAW = (138, 74, 70)          # hide gone, the flesh under it
ROT = (96, 48, 54)           # rotting deep
PUS = (150, 140, 92)         # its edge, yellowed
BONE = (214, 204, 176)
BONE_DARK = (170, 158, 128)
HOLLOW = (58, 24, 28)
HORN = (196, 186, 160)
EYE_CLOUD = (196, 204, 196)
MUZZLE = (112, 82, 84)
HOOF = (52, 46, 42)
UDDER = (128, 112, 112)


def jitter(c, amount=12):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, x + d)) for x in c) + (255,)


def px(x, y, c, amount=7):
    img.putpixel((x, y), jitter(c, amount))


def fill(x0, y0, w, h, colour, amount=9):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            px(x, y, colour, amount)


def box(u, v, w, h, d, side, top=None, bottom=None, front=None, back=None):
    fill(u + d, v, w, d, top or side)
    fill(u + d + w, v, w, d, bottom or side)
    fill(u, v + d, d, h, side)
    fill(u + d, v + d, w, h, front or side)
    fill(u + d + w, v + d, d, h, side)
    fill(u + 2 * d + w, v + d, w, h, back or side)


def patches(x0, y0, w, h, count, size):
    """The cow's dark patches, blotched, over a face."""
    for _ in range(count):
        cx = rng.randint(x0, x0 + w - 1)
        cy = rng.randint(y0, y0 + h - 1)
        r = rng.uniform(size * 0.6, size)
        for x in range(x0, x0 + w):
            for y in range(y0, y0 + h):
                if (x - cx) ** 2 + ((y - cy) * 1.2) ** 2 < r * r + rng.uniform(-2, 2):
                    px(x, y, PATCH, 6)


def sore(x0, y0, w, h, cx, cy, r):
    """A rotting sore: deep in the middle, raw round it, a yellowed rim."""
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 + rng.uniform(-0.5, 0.5)
            if d < r * 0.5:
                px(x, y, ROT, 6)
            elif d < r:
                px(x, y, RAW, 8)
            elif d < r + 0.8 and rng.random() < 0.6:
                px(x, y, PUS, 8)


def ribs(x0, y0, w, h, top, bottom, first, last):
    """The flank torn open from {first} to {last} along it, ragged: a dark wet hollow, the ribs across it - bone, curving,
    one broken short - the torn hide's edge raw and uneven, strings of flesh still across."""
    mid = (first + last) / 2
    half = (last - first) / 2 + 0.5
    def inside(x, y):
        # an uneven oval, its edge bitten
        t = (y - mid) / half
        reach = (1 - t * t) ** 0.5 if abs(t) < 1 else 0
        lo = top + (1 - reach) * 1.4 + rng.uniform(-0.5, 0.5)
        hi = bottom - (1 - reach) * 1.4 + rng.uniform(-0.5, 0.5)
        return lo <= x <= hi and abs(t) < 1
    hole = {(x, y) for x in range(w) for y in range(h) if inside(x, y)}
    for x, y in hole:
        px(x0 + x, y0 + y, HOLLOW if rng.random() < 0.8 else ROT, 6)
    rib_rows = list(range(first + 1, last, 2))
    for k, y in enumerate(rib_rows):
        cells = sorted(x for x, yy in hole if yy == y)
        if not cells:
            continue
        short = k == 1                                       # one broken short
        for i2, x in enumerate(cells):
            if short and i2 > len(cells) * 0.55:
                break
            yy = y + (1 if i2 == len(cells) - 1 and len(cells) > 3 else 0)    # the rib's end curving down
            px(x0 + x, y0 + yy, BONE if i2 % 3 else BONE_DARK, 6)
    for x, y in hole:                                       # the torn edge round the hole
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < w and 0 <= ny < h and (nx, ny) not in hole and rng.random() < 0.85:
                px(x0 + nx, y0 + ny, RAW if rng.random() < 0.7 else PUS, 8)


# --- the head (8 x 8 x 6 at 0,0): hide, a dark patch over one eye, the eyes, the snapped horn -------------------------
box(0, 0, 8, 8, 6, HIDE)
patches(6, 6, 8, 8, 2, 3.0)              # the face
patches(0, 6, 6, 8, 1, 3.0)
patches(20, 6, 8, 8, 2, 3.5)             # the back of the head
patches(6, 0, 8, 6, 1, 3.0)
# the eyes, on the face's third row: the left clouded white, the right socket empty with bone round it
for x, y, c in ((7, 9, EYE_CLOUD), (8, 9, (150, 160, 150))):
    px(x, y, c, 4)
px(12, 9, HOLLOW, 3)
px(11, 9, HOLLOW, 3)
px(11, 8, BONE_DARK, 5)
px(12, 8, BONE, 5)
px(12, 10, BONE_DARK, 5)
# the hide rotted off the cheek under the empty socket, and a sore on the brow
sore(6, 6, 8, 8, 12, 12, 1.6)
sore(0, 6, 6, 8, 2, 10, 1.4)
# the horns (1 x 3 x 1 at 22,0): grey bone, the tip of one darker where it snapped
box(22, 0, 1, 3, 1, HORN)
px(23, 1, BONE_DARK, 4)
# the muzzle (6 x 3 x 1 at 1,33): raw and dark at the nostrils
box(1, 33, 6, 3, 1, MUZZLE)
for x in (3, 6):
    px(x, 35, HOLLOW, 4)
px(4, 34, RAW, 6)

# --- the body (12 x 18 x 10 at 18,4) ------------------------------------------------------------------------------
box(18, 4, 12, 18, 10, HIDE)
patches(18, 14, 10, 18, 3, 3.5)          # the right flank
patches(40, 14, 10, 18, 3, 3.5)          # the left
patches(28, 14, 12, 18, 3, 4.0)          # the back
patches(28, 4, 12, 10, 1, 3.0)           # the chest's end
patches(40, 4, 12, 10, 1, 3.5)           # the rump's
# the spine standing out down the back: a row of bone knuckles where the hide has gone thin
for y in range(15, 31, 2):
    px(33 + (y % 3 == 0), y, BONE_DARK, 6)
# the ribs: the right flank open on five, the left on three, further back
ribs(18, 14, 10, 18, top=1, bottom=8, first=3, last=13)
ribs(40, 14, 10, 18, top=2, bottom=8, first=6, last=14)
# sores: on the back, the rump, the belly, the left flank's front
sore(28, 14, 12, 18, 37, 26, 2.2)
sore(40, 4, 12, 10, 44, 9, 1.8)
sore(50, 14, 12, 18, 55, 22, 2.6)
sore(40, 14, 10, 18, 44, 17, 1.5)
# more of it rotting: small sores scattered, and grime down the lower flanks
for (fx, fy, fw, fh) in ((18, 14, 10, 18), (40, 14, 10, 18), (28, 14, 12, 18), (0, 6, 6, 8), (20, 6, 8, 8)):
    for _ in range(2):
        sore(fx, fy, fw, fh, rng.randint(fx, fx + fw - 1), rng.randint(fy, fy + fh - 1), rng.uniform(0.8, 1.3))
for (fx, fy, fw, fh, lower) in ((18, 14, 10, 18, 'left'), (40, 14, 10, 18, 'right')):
    for x in range(fx, fx + fw):
        for y in range(fy, fy + fh):
            edge = (x - fx) if lower == 'left' else (fx + fw - 1 - x)
            if edge < 2 and rng.random() < 0.5:
                r, g, b2, a2 = img.getpixel((x, y))
                img.putpixel((x, y), (int(r * 0.78), int(g * 0.78), int(b2 * 0.78), a2))
# the belly (the box's back face): paler, the hide thin and grey
for x in range(50, 62):
    for y in range(14, 32):
        if img.getpixel((x, y))[:3] == img.getpixel((x, y))[:3] and rng.random() < 0.3:
            px(x, y, (150, 140, 130), 8)
# the udder (4 x 6 x 1 at 52,0): shrunk, grey
box(52, 0, 4, 6, 1, UDDER)
px(54, 5, RAW, 6)

# --- the legs (4 x 12 x 4 at 0,16, one picture for all four, the left two mirrored) --------------------------------
box(0, 16, 4, 12, 4, HIDE, top=HIDE, bottom=HOOF)
for x in range(0, 16):                   # the hooves, the lower three rows
    for y in range(29, 32):
        px(x, y, HOOF, 6)
patches(0, 20, 16, 8, 2, 2.5)
# the shin's bone through the hide on the front and one side, a hand's width above the hoof
for y in range(23, 27):
    px(5, y, BONE if y % 2 else BONE_DARK, 5)
    px(6, y, RAW, 8)
    if y > 23:
        px(4, y, ROT, 8)
for y in range(24, 27):
    px(1, y, RAW, 8)
    px(2, y, ROT, 6)

img.save(OUT)
print('wrote', OUT)
