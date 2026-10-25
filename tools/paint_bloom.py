"""Paints the sewer bloom's skin, on BloomModel's current UV layout
(128 x 64, unchanged boxes), from nothing of vanilla's.

Run: python tools/paint_bloom.py
writes textures/entity/sewer_bloom.png, sewer_bloom_glow.png (for an EyesLayer: the throat), and build/bloom_sheet_x8.png,
build/bloom_petal_x16.png to look at.

What changes from tools/paint_bloom.py, and why (review_creatures.md, Sewer bloom):
- the petals are cut to a shape by the texture's alpha (BloomModel draws with the game's entityCutout, so a texel of
  alpha 0 is a hole): a pointed, broad-bellied petal instead of a 12 x 12 square, with a fringe of pale spines along
  its edges (a flytrap's), and its thin side faces cleared where the shape does not reach them. No model change. When
  shut the four petals' tips no longer cross at the corners, and between them gaps open onto the red inside - the bud
  shows its mouth through the seams.
- the flesh is painted as a plant grows, not as camouflage: streaks and ribs along the stalk's length, thin veins
  running up and branching, instead of round blotches of purple on green (vis_bloom_shut_front reads as army camo);
  and a step darker and browner than the vault's moss carpet, so the bud stands apart from the floor it grows in
  (old_b2, old_c1: it disappears into the moss).
- the outside of each petal darkens to its hinge and has a dark red rim; the inside keeps its flesh-pink, wet, with a
  pale highlight or two.
- a glow layer for the throat: a dull ember-red in the middle of the mouth - the focal point in the dark sewer.
"""
import math
import os
import random
import sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures', 'entity')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'build')
rng = random.Random(1019)

STALK = (84, 94, 58)
STALK_DARK = (40, 46, 32)
PALE = (146, 140, 100)
VEIN = (92, 42, 74)
PETAL_OUT = (70, 84, 52)
PETAL_RIM = (112, 44, 46)
PETAL_IN = (214, 160, 150)
PETAL_DEEP = (120, 36, 44)
WET = (240, 206, 196)
MOUTH = (70, 14, 22)
MOUTH_RING = (150, 70, 76)
BONE = (222, 210, 180)
ROOT_C = (46, 50, 34)
ROOT_DARK = (26, 28, 20)
GLOW = (190, 70, 34)
CLEAR = (0, 0, 0, 0)

img = Image.new('RGBA', (128, 64), CLEAR)
glow = Image.new('RGBA', (128, 64), CLEAR)


def jitter(c, amount=6):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, x + d)) for x in c[:3]) + (255,)


def noise(x, y, sx, sy, salt):
    """Value noise, stretched: sx across, sy along."""
    def h(ix, iy):
        return random.Random(ix * 73856093 ^ iy * 19349663 ^ salt).random()
    fx, fy = x / sx, y / sy
    ix, iy = math.floor(fx), math.floor(fy)
    tx, ty = fx - ix, fy - iy
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a, b, c, d = h(ix, iy), h(ix + 1, iy), h(ix, iy + 1), h(ix + 1, iy + 1)
    return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def flesh(u, v, w, h, d, base, dark, salt, ribbed=False, veined=0.05):
    """The plant's flesh: lighter on top and high on the sides, darker low (a few steps); streaked along its length,
    ribbed if asked, with thin veins running up it."""
    for name, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        for x in range(x0, x0 + fw):
            for y in range(y0, y0 + fh):
                if name == "top":
                    t = 0.0
                    c = mix(base, PALE, 0.2)
                elif name == "bottom":
                    t = 1.0
                    c = dark
                else:
                    t = (y - y0) / max(1, fh - 1)
                    c = mix(base, dark, round(t * 3) / 3 * 0.55)        # stepped, not smooth
                c = mix(c, dark, (noise(x, y, 1.3, 6.0, salt) - 0.5) * 0.9)   # streaks along the length
                if ribbed and name not in ("top", "bottom") and (x - x0) % 3 == 0:
                    c = mix(c, dark, 0.4)
                if ribbed and name not in ("top", "bottom") and (x - x0) % 3 == 1:
                    c = mix(c, PALE, 0.12)                              # the rib's lit edge
                # veins: thin lines wandering up (the zero line of a noise stretched along the length)
                if name not in ("bottom",) and abs(noise(x, y, 2.2, 9.0, salt + 11) - 0.5) < veined:
                    c = mix(c, VEIN, 0.7)
                if noise(x, y, 3.0, 3.0, salt + 5) > 0.78:
                    c = mix(c, PALE, 0.35)                              # yellowed where no light comes
                img.putpixel((x, y), jitter(c, 5))


# --- the petal: 12 x 12 x 1 at (0, 40), its hinge at the bottom (fy 11), its tip at the top (fy 0) -----------------
# half-width of the shape at each row, tip to hinge
HALF = [0.6, 1.4, 2.4, 3.4, 4.3, 5.0, 5.6, 6.0, 6.0, 5.8, 5.4, 5.0]


def inside(fx, fy):
    return abs(fx + 0.5 - 6.0) < HALF[fy]


def spine(fx, fy):
    """A spine of the fringe: one texel just outside the shape, on every other row from near the tip to the belly."""
    if inside(fx, fy) or fy < 1 or fy > 8 or fy % 2 == 0:
        return False
    return abs(fx + 0.5 - 6.0) < HALF[fy] + 1.0


def petal(u, v):
    for name, (x0, y0, fw, fh) in faces(u, v, 12, 12, 1).items():
        for fx in range(fw):
            for fy in range(fh):
                x, y = x0 + fx, y0 + fy
                if name in ("front", "back"):
                    if spine(fx, fy):
                        img.putpixel((x, y), jitter(BONE, 4))
                        continue
                    if not inside(fx, fy):
                        continue
                    edge = not inside(fx - 1, fy) or not inside(fx + 1, fy) or fy == 0 or not inside(fx, fy - 1)
                    t = fy / 11                                         # 0 at the tip, 1 at the hinge
                    if name == "back":                                  # the inside
                        c = mix(PETAL_IN, PETAL_DEEP, round(t * 4) / 4 * 0.8)
                        if noise(x, y, 1.6, 1.6, 41) > 0.76:
                            c = mix(c, PETAL_DEEP, 0.8)                 # spots
                        if abs(fx + 0.5 - 6.0) < 0.6 and fy > 2:
                            c = mix(c, PETAL_DEEP, 0.5)                 # the midrib, a crease
                        if (fx, fy) in ((4, 4), (7, 6), (5, 7)):
                            c = WET                                     # wet, catching what light there is
                        if edge:
                            c = mix(c, PETAL_RIM, 0.7)
                    else:                                               # the outside
                        c = mix(PETAL_OUT, STALK_DARK, round(t * 3) / 3 * 0.6)
                        c = mix(c, STALK_DARK, (noise(x, y, 1.5, 5.0, 45) - 0.5) * 0.8)
                        if abs(fx + 0.5 - 6.0) < 0.6:
                            c = mix(c, PALE, 0.3)                       # the midrib, standing out
                        if abs(noise(x, y, 2.0, 5.0, 49) - 0.5) < 0.05:
                            c = mix(c, VEIN, 0.6)
                        if edge:
                            c = mix(c, PETAL_RIM, 0.75)                 # the red lip of it
                    img.putpixel((x, y), jitter(c, 4))
                elif name == "bottom":                                  # the hinge's edge, against the bulb
                    img.putpixel((x, y), jitter(mix(PETAL_OUT, STALK_DARK, 0.6), 3))
                elif name == "top":                                     # the tip's edge: only its middle is there
                    if inside(fx, 0):
                        img.putpixel((x, y), jitter(PETAL_RIM, 3))
                else:                                                   # the side edges: only where the shape is widest
                    if HALF[fy] >= 5.9:
                        img.putpixel((x, y), jitter(PETAL_RIM, 3))


def mouth(u, v):
    for name, (x0, y0, fw, fh) in faces(u, v, 12, 1, 12).items():
        for x in range(x0, x0 + fw):
            for y in range(y0, y0 + fh):
                if name == "top":
                    r = math.hypot(x - x0 - 5.5, y - y0 - 5.5) / 6.0
                    c = mix((14, 4, 6), MOUTH, r * 1.6) if r < 0.7 else mix(MOUTH, MOUTH_RING, round((r - 0.7) / 0.3 * 2) / 2)
                    if 0.45 < r < 0.7 and noise(x, y, 1.2, 1.2, 51) > 0.6:
                        c = mix(c, MOUTH_RING, 0.5)
                    if r < 0.32:                                        # the ember in the throat
                        g = mix(GLOW, (90, 20, 14), r / 0.32)
                        c = mix(c, g, 0.6)
                        glow.putpixel((x, y), g + (255,))
                else:
                    c = mix(STALK, MOUTH_RING, 0.4)
                img.putpixel((x, y), jitter(c, 4))


def knotted(u, v, w, h, d, salt):
    for name, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        for x in range(x0, x0 + fw):
            for y in range(y0, y0 + fh):
                c = mix(ROOT_C, ROOT_DARK, noise(x, y, 1.0, 4.0, salt))
                if name == "top":
                    c = mix(c, STALK, 0.35)
                if noise(x, y, 1.0, 1.0, salt + 2) > 0.86:
                    c = mix(c, PALE, 0.45)                              # a knot
                if noise(x, y, 1.0, 1.0, salt + 4) > 0.93 and name != "bottom":
                    c = BONE                                            # a thorn's point
                img.putpixel((x, y), jitter(c, 5))


flesh(0, 0, 6, 14, 6, STALK, STALK_DARK, 61, ribbed=True)                # the stalk
flesh(0, 20, 14, 6, 14, mix(STALK, PALE, 0.12), STALK_DARK, 62, veined=0.07)   # the bulb
mouth(56, 20)
petal(0, 40)
for name, (x0, y0, fw, fh) in faces(26, 40, 1, 3, 1).items():           # a tooth: bone, dark at the root, yellow at the point
    for x in range(x0, x0 + fw):
        for y in range(y0, y0 + fh):
            t = (y - y0) / max(1, fh - 1) if name not in ("top", "bottom") else 0.0
            img.putpixel((x, y), jitter(mix(BONE, (120, 92, 70), t * 0.8), 3))
knotted(24, 0, 3, 2, 8, 63)
knotted(48, 0, 2, 2, 7, 64)
knotted(66, 0, 1, 1, 5, 65)

os.makedirs(OUT, exist_ok=True)
img.save(os.path.join(OUT, 'sewer_bloom.png'))
glow.save(os.path.join(OUT, 'sewer_bloom_glow.png'))
sheet = Image.new('RGBA', (128, 64), (255, 0, 255, 255))
sheet.alpha_composite(img)
os.makedirs(PREVIEW, exist_ok=True)
sheet.resize((1024, 512), Image.NEAREST).save(os.path.join(PREVIEW, 'bloom_sheet_x8.png'))
pv = Image.new('RGBA', (26, 13), (60, 64, 52, 255))                    # the petal, outside and inside, on the vault's dark
pv.alpha_composite(img.crop((1, 41, 13, 53)), (0, 0))
pv.alpha_composite(img.crop((14, 41, 26, 53)), (13, 0))
pv.resize((26 * 16, 13 * 16), Image.NEAREST).save(os.path.join(PREVIEW, 'bloom_petal_x16.png'))
print('wrote', os.path.join(OUT, 'sewer_bloom.png'))
