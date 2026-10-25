"""Paints the infected rat's skin (32 x 32) for client/RatModel - from nothing of vanilla's.

Run: python tools/paint_rat.py  (writes src/main/resources/assets/nookofthedead/textures/entity/infected_rat.png)
The UV boxes are RatModel's: a cube w x h x d at (u, v) has top (u+d, v) w x d, bottom (u+d+w, v) w x d, and its sides
in a row at v+d, h high: right d, front w, left d, back w.
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures', 'entity',
                   'infected_rat.png')
rng = random.Random(1016)
img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))

FUR = (74, 64, 55)
FUR_DARK = (52, 45, 40)
BELLY = (128, 116, 102)
SKIN = (150, 112, 106)
SKIN_DARK = (118, 86, 82)
MANGE = (142, 100, 94)
EYE = (156, 34, 30)
NOSE = (96, 52, 52)


def jitter(c, amount=10):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, x + d)) for x in c) + (255,)


def fill(x0, y0, w, h, colour, amount=10):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            img.putpixel((x, y), jitter(colour, amount))


def box(u, v, w, h, d, side, top=None, bottom=None, front=None, back=None):
    fill(u + d, v, w, d, top or side)
    fill(u + d + w, v, w, d, bottom or side)
    fill(u, v + d, d, h, side)
    fill(u + d, v + d, w, h, front or side)
    fill(u + d + w, v + d, d, h, side)
    fill(u + 2 * d + w, v + d, w, h, back or side)


# the body: a darker back, a pale belly, fur streaked; raw patches where the plague took the fur
box(0, 0, 5, 4, 9, FUR, top=FUR_DARK, bottom=BELLY)
for x in range(0, 28):                       # the lower row of the sides paler, towards the belly
    if img.getpixel((x, 12))[3]:
        img.putpixel((x, 12), jitter(BELLY, 6))
for _ in range(5):
    x, y = rng.choice([(rng.randint(9, 13), rng.randint(0, 8)), (rng.randint(0, 8), rng.randint(9, 11)),
                       (rng.randint(14, 22), rng.randint(9, 11))])
    img.putpixel((x, y), jitter(MANGE, 8))
# the skull: fur, two dull red eyes at the front's top corners
box(0, 13, 4, 3, 4, FUR, top=FUR_DARK, bottom=BELLY)
img.putpixel((4, 17), EYE + (255,))
img.putpixel((7, 17), EYE + (255,))
# the snout: bare at the tip, a dark nose
box(16, 13, 2, 2, 2, FUR, bottom=BELLY, front=SKIN)
img.putpixel((18, 15), NOSE + (255,))
img.putpixel((19, 15), NOSE + (255,))
# the ears: thin skin, darker at the rim
box(24, 13, 2, 2, 1, SKIN_DARK, front=SKIN)
# the legs: bare skin to the toes
box(0, 20, 1, 3, 1, SKIN_DARK)
# the tail: bare, ringed
box(8, 20, 1, 1, 6, SKIN)
for x in range(8, 22, 2):
    for y in (26,):
        if img.getpixel((x, y))[3]:
            img.putpixel((x, y), jitter(SKIN_DARK, 4))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print(os.path.normpath(OUT))

# its spawn egg (16 x 16): an egg of the rat's brown, speckled with its bare skin
EGG = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures', 'item',
                   'infected_rat_spawn_egg.png')
egg = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
OUTLINE = (34, 28, 24)
for y in range(16):
    for x in range(16):
        # an egg: narrower at the top
        cy = 8.6
        ry = 6.6
        rx = 4.6 + (y - 3) * 0.12 if y > 3 else 4.2
        inside = ((x + 0.5 - 8) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
        if inside <= 1.0:
            edge = inside > 0.72
            colour = OUTLINE if edge else FUR if (x + y) % 5 else FUR_DARK
            if not edge and rng.random() < 0.13:
                colour = SKIN
            if not edge and x <= 6 and y <= 6 and inside < 0.4:
                colour = BELLY                    # the light on it
            egg.putpixel((x, y), jitter(colour, 5 if not edge else 0))
os.makedirs(os.path.dirname(EGG), exist_ok=True)
egg.save(EGG)
print(os.path.normpath(EGG))
