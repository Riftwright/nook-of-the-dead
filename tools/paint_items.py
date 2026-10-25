"""Paints the three 0.3 spawn eggs, from nothing of vanilla's.

Run: python tools/paint_items.py
writes textures/item/sentry_golem_spawn_egg.png, thin_air_zombie_spawn_egg.png, sewer_bloom_spawn_egg.png
(16 x 16 each).

- the eggs keep tools/paint_bloom.py's egg() shape and light; only their colours and marks change:
  - Sentry Golem: the creature's own colours (the draft skin's gunmetal, white enamel flecks, one red lamp dot)
    instead of light grey with rust, which sat next to the game's own iron golem egg;
  - thin-air zombie: the cold blue-grey shell, but the spots dark harness-grey and frost white instead of the
    plague green, which read as a vanilla-ish "stray" egg;
  - sewer bloom: darker olive (it was nearly the zombie egg's green), the pink spots kept, and a dark red base.
"""
import math
import os
import random
import sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures', 'item')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'build')


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def egg(name, shell, spots, outline, spot_share=0.16, base=None, mark=None, spots2=None):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    r = random.Random(name)
    for y in range(16):
        for x in range(16):
            ry = 6.6
            rx = 4.6 + (y - 3) * 0.12 if y > 3 else 4.2
            inside = ((x + 0.5 - 8) / rx) ** 2 + ((y + 0.5 - 8.6) / ry) ** 2
            if inside > 1.0:
                continue
            edge = inside > 0.72
            c = outline if edge else shell
            if not edge and base and y >= 12:
                c = mix(shell, base, 0.6 if y == 12 else 0.85)
            roll = r.random()
            if not edge and roll < spot_share:
                c = spots
            elif not edge and spots2 and roll < spot_share + 0.06:
                c = spots2
            if not edge and x <= 6 and y <= 6 and inside < 0.4:
                c = mix(shell, (255, 250, 230), 0.35)
            d = 0 if edge else r.randint(-5, 5)
            img.putpixel((x, y), tuple(max(0, min(255, v + d)) for v in c) + (255,))
    if mark:
        for (x, y), c in mark.items():
            img.putpixel((x, y), c + (255,))
    img.save(os.path.join(OUT, name + '_spawn_egg.png'))
    return img



os.makedirs(OUT, exist_ok=True)
new = [
    egg('sentry_golem', (62, 68, 76), (186, 188, 182), (24, 26, 30), 0.12, base=(36, 40, 46),
        mark={(8, 5): (226, 48, 36), (7, 5): (150, 30, 26)}),
    egg('thin_air_zombie', (184, 202, 198), (24, 38, 40), (76, 96, 98), 0.16, spots2=(104, 26, 24)),   # the lab coat, the scrubs, blood
    egg('sewer_bloom', (74, 88, 50), (206, 150, 140), (34, 40, 26), 0.18, base=(110, 36, 44)),
]
print('wrote items to', OUT)
