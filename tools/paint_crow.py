"""Paints the capital's crow (32 x 32, client/CrowModel) and its spawn egg - from nothing of vanilla's.

Run: python tools/paint_crow.py  (writes textures/entity/capital_crow.png and textures/item/capital_crow_spawn_egg.png)

A crow of the dead city: black, a blue-violet sheen along the back and the wings' coverts, the flight feathers dull and
ragged at their ends, the beak and legs grey-black, the eye clouded pale. Here and there feathers gone, the skin under
them showing grey-pink.
The UV boxes are CrowModel's: a cube w x h x d at (u, v) has top (u+d, v) w x d, bottom (u+d+w, v) w x d, and its sides
in a row at v+d, h high: right d, front w, left d, back w.
"""
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures')
rng = random.Random(1025)
img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))

BLACK = (24, 23, 28)
SHEEN = (44, 46, 66)
DULL = (34, 32, 34)
BEAK = (46, 44, 46)
EYE = (176, 182, 170)
SKIN = (120, 92, 96)


def jitter(c, amount=6):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, x + d)) for x in c) + (255,)


def fill(x0, y0, w, h, colour, amount=6, sheen=0.0):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            c = SHEEN if rng.random() < sheen else colour
            img.putpixel((x, y), jitter(c, amount))


def box(u, v, w, h, d, side, top=None, bottom=None, front=None, back=None, sheen=0.0):
    fill(u + d, v, w, d, top or side, sheen=sheen)
    fill(u + d + w, v, w, d, bottom or side)
    fill(u, v + d, d, h, side, sheen=sheen * 0.5)
    fill(u + d, v + d, w, h, front or side)
    fill(u + d + w, v + d, d, h, side, sheen=sheen * 0.5)
    fill(u + 2 * d + w, v + d, w, h, back or side)


# the body: the back glossed, the belly duller; a bare patch on one side
box(0, 0, 3, 3, 6, BLACK, bottom=DULL, sheen=0.35)
img.putpixel((2, 7), jitter(SKIN, 6))
img.putpixel((3, 7), jitter(SKIN, 6))
# the head: its eyes on the sides, clouded
box(0, 9, 3, 3, 3, BLACK, sheen=0.2)
img.putpixel((1, 13), EYE + (255,))
img.putpixel((7, 13), EYE + (255,))
# the beak
box(12, 9, 1, 1, 2, BEAK)
# the tail, the ends of its feathers ragged
box(0, 15, 3, 1, 4, BLACK, sheen=0.2)
for x in (4, 6):
    img.putpixel((x, 15), (0, 0, 0, 0))
# the wings: the coverts glossed, the flight feathers dull, ragged at the tips
box(0, 20, 4, 1, 4, BLACK, top=BLACK, bottom=DULL, sheen=0.4)
box(16, 20, 4, 1, 3, DULL, top=DULL, bottom=DULL)
for x in range(19, 23):                      # the hand of the wing's far edge, a feather gone here and there
    if rng.random() < 0.35:
        img.putpixel((x, 20), (0, 0, 0, 0))
os.makedirs(os.path.join(ROOT, 'entity'), exist_ok=True)
img.save(os.path.join(ROOT, 'entity', 'capital_crow.png'))

# the spawn egg: as tools/paint_items.py's eggs, black with the sheen, a pale eye-spot
egg = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
r = random.Random('capital_crow')
for y in range(16):
    for x in range(16):
        rx = 4.6 + (y - 3) * 0.12 if y > 3 else 4.2
        inside = ((x + 0.5 - 8) / rx) ** 2 + ((y + 0.5 - 8.6) / 6.6) ** 2
        if inside > 1.0:
            continue
        edge = inside > 0.72
        c = (10, 10, 14) if edge else (SHEEN if r.random() < 0.18 else BLACK)
        if not edge and x <= 6 and y <= 6 and inside < 0.4:
            c = (70, 72, 92)
        d = 0 if edge else r.randint(-4, 4)
        egg.putpixel((x, y), tuple(max(0, min(255, v + d)) for v in c) + (255,))
egg.putpixel((9, 9), EYE + (255,))
egg.putpixel((10, 9), (140, 146, 136, 255))
egg.save(os.path.join(ROOT, 'item', 'capital_crow_spawn_egg.png'))
print('wrote crow and egg')
