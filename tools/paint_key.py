"""Paints the vaccine console's launch key (16 x 16) - from nothing of vanilla's.

Run: python tools/paint_key.py  (writes src/main/resources/assets/nookofthedead/textures/item/launch_key.png)

A control-panel key, not a door's: a red moulded grip with a white band and a hole for its lanyard, a short flat steel
blade cut on one side, lying corner to corner. Red and steel keep it apart from the game's trial keys, which are gold.
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures', 'item',
                   'launch_key.png')
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))

OUTLINE = (40, 22, 22)
RED = (178, 40, 36)
RED_LIGHT = (214, 82, 70)
RED_DARK = (118, 24, 24)
WHITE = (232, 230, 220)
STEEL = (176, 182, 186)
STEEL_LIGHT = (222, 226, 228)
STEEL_DARK = (104, 110, 116)
STEEL_EDGE = (58, 62, 68)


def put(x, y, c):
    img.putpixel((x, y), c + (255,))


# the grip: a rounded square, rows 1..8, columns 1..8
for y in range(1, 9):
    for x in range(1, 9):
        if x in (1, 8) and y in (1, 8):
            continue
        edge = x in (1, 8) or y in (1, 8)
        if edge:
            put(x, y, OUTLINE)
        elif x + y == 12:
            put(x, y, WHITE)                                          # the white band, across the grip by the blade
        elif x + y <= 6:
            put(x, y, RED_LIGHT)
        elif x + y >= 13:
            put(x, y, RED_DARK)
        else:
            put(x, y, RED)
for (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4)):                       # the lanyard's hole
    img.putpixel((x, y), (0, 0, 0, 0))

# the blade: from the grip's corner to the bottom right, three wide, lit along its upper edge
for k in range(0, 7):
    x, y = 8 + k, 8 + k
    put(x, y, STEEL)
    put(x + 1, y, STEEL_LIGHT)
    put(x, y + 1, STEEL_DARK)
put(15, 15, STEEL_EDGE)
put(15, 14, STEEL_DARK)
# its cuts, on the under side: two teeth standing out below the blade
for (x, y) in ((9, 11), (12, 14)):
    put(x, y, STEEL_DARK)
    put(x, y + 1, STEEL_EDGE)
# the collar where the blade meets the grip
put(8, 8, STEEL_EDGE)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
img.resize((256, 256), Image.NEAREST).save(os.path.join(os.path.dirname(__file__), '..', 'build', 'launch_key_preview.png'))
print('wrote', OUT)
