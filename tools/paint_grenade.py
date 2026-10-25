"""Paints the makeshift grenade's item picture (16 x 16) - from nothing of vanilla's.

Run: python tools/paint_grenade.py  (writes src/main/resources/assets/nookofthedead/textures/item/grenade.png)

A squat bottle of dark glass bound round with iron wire, a rag stuffed in its neck for a fuse: made by hand from what
was found, as the world's survivors would have made one.
"""
import os
import random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures', 'item',
                   'grenade.png')
rng = random.Random(1017)
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))

OUTLINE = (30, 34, 30)
GLASS = (62, 78, 64)
GLASS_LIGHT = (96, 116, 96)
GLASS_DARK = (44, 56, 46)
WIRE = (150, 150, 146)
WIRE_DARK = (104, 104, 100)
RAG = (198, 186, 160)
RAG_DARK = (150, 136, 110)
CORK = (120, 86, 52)


def px(x, y, c, amount=5):
    d = rng.randint(-amount, amount)
    img.putpixel((x, y), tuple(max(0, min(255, v + d)) for v in c) + (255,))


# the body: a squat round bottle, rows 6..14, widest at 9..12
widths = {6: (6, 9), 7: (5, 10), 8: (4, 11), 9: (3, 12), 10: (3, 12), 11: (3, 12), 12: (3, 12), 13: (4, 11), 14: (5, 10)}
for y, (x0, x1) in widths.items():
    for x in range(x0, x1 + 1):
        edge = x in (x0, x1) or y in (6, 14)
        if edge:
            px(x, y, OUTLINE, 3)
        elif x <= x0 + 1 and 8 <= y <= 11:
            px(x, y, GLASS_LIGHT)
        elif x >= x1 - 1 or y == 13:
            px(x, y, GLASS_DARK)
        else:
            px(x, y, GLASS)
# the wire bound round it, two turns
for y in (9, 12):
    x0, x1 = widths[y]
    for x in range(x0, x1 + 1):
        px(x, y, WIRE if (x + y) % 3 else WIRE_DARK, 4)
# the neck and the cork
for y in (4, 5):
    for x in (6, 7, 8, 9):
        px(x, y, OUTLINE if x in (6, 9) else (CORK if y == 5 else GLASS_DARK), 3)
# the rag stuffed in it, hanging over the side
for x, y in ((7, 3), (8, 3), (7, 2), (8, 1), (9, 2), (10, 2), (10, 3), (11, 4), (11, 5)):
    px(x, y, RAG if (x + y) % 2 else RAG_DARK, 6)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print("wrote", os.path.normpath(OUT))
