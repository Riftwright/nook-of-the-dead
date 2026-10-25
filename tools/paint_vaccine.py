"""Paints the vaccine's apparatus (16 x 16 faces) - from nothing of vanilla's.

Run: python tools/paint_vaccine.py   (writes textures/block/vaccine_*.png, and build/vaccine_preview.png, each face x16)

The laboratory's last machine (the user, 2026-10-20, of the trailer: the dispersal machine looked poor beside the rest -
"他がよい分、余計にしょぼく見える"). It was one cube, a little cabinet; now the console is a desk (models/block/vaccine.json:
a body, a sloped panel, a riser of lamps and a gauge, the release lever to one side), and behind it against the window
the vessel the vaccine waits in (vaccine_tank, in copper - world/SkyBase#apparatus), glowing, the sky through it; when
it is let go the vessel and the tanks either side go empty. Nothing on the console reads as a face: no pair of dials or
lamps over a slot (the first one had a screen over two red buttons).
"""
import os
import random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DIR = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures', 'block')
PREVIEW = os.path.join(HERE, '..', 'build')
rng = random.Random(1103)

ENAMEL = (214, 216, 212)
STEEL = (92, 97, 104)
STEEL_DARK = (52, 55, 61)
STEEL_LIGHT = (150, 156, 162)
VACCINE = (142, 214, 240)
VACCINE_DEEP = (64, 150, 200)
COPPER = (196, 110, 70)
RED = (178, 40, 34)
AMBER = (236, 170, 60)
GREEN = (110, 210, 120)
HAZARD = (222, 178, 40)
LIQUID = (64, 200, 236)                  # the vaccine in its vessel, lit
LIQUID_DEEP = (22, 120, 190)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def jitter(c, a=4, alpha=255):
    d = rng.randint(-a, a)
    return tuple(max(0, min(255, x + d)) for x in c) + (alpha,)


def worn(c):
    return mix(c, (168, 158, 140), 0.45) if rng.random() < 0.05 else c


def body():
    """The desk's body, sides and front: a steel frame, an enamel panel, louvres low down where it breathes."""
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = worn(ENAMEL)
            if x in (0, 15) or y in (0, 15):
                c = STEEL_DARK
            elif x in (1, 14) or y == 1:
                c = STEEL
            elif 9 <= y <= 13 and 3 <= x <= 12:
                c = STEEL_DARK if y % 2 == 1 else STEEL           # the louvres
            elif 3 <= y <= 5 and 3 <= x <= 8:
                c = mix(ENAMEL, STEEL, 0.25) if y == 4 and x < 8 else c   # a plate, its line of print
            img.putpixel((x, y), jitter(c))
    return img


def panel():
    """The sloped panel: a row of switches; to the right, the release under a hazard-striped guard; a lit strip."""
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = worn(mix(ENAMEL, STEEL_LIGHT, 0.2))
            if x in (0, 15) or y in (0, 15):
                c = STEEL
            elif y in (3, 4) and 2 <= x <= 7:
                c = STEEL_DARK if x % 2 == 0 else (c if y == 3 else STEEL)   # toggles, alternate thrown
                if y == 3 and x % 2 == 0 and x in (2, 6):
                    c = STEEL_LIGHT
            elif 9 <= x <= 13 and 4 <= y <= 11:                      # the guard round the release
                edge = x in (9, 13) or y in (4, 11)
                c = (HAZARD if (x + y) % 4 < 2 else STEEL_DARK) if edge else STEEL_DARK
            elif y == 8 and 2 <= x <= 7:
                c = VACCINE                                          # the strip: ready
            elif y in (11, 12) and 2 <= x <= 6:
                c = mix(ENAMEL, STEEL, 0.3) if (x + y) % 3 else c    # a written card, taped down
            img.putpixel((x, y), jitter(c))
    return img


def gauge():
    """The riser: lamps in a row, a long level window onto the vaccine."""
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = STEEL
            if x in (0, 15) or y in (0, 15):
                c = STEEL_DARK
            elif y in (2, 3) and x in (2, 5, 8, 11):
                c = (GREEN, GREEN, AMBER, GREEN)[(x - 2) // 3]
            elif y in (2, 3) and x == 13:
                c = RED
            elif 6 <= y <= 11 and 2 <= x <= 13:
                if x in (2, 13) or y in (6, 11):
                    c = STEEL_DARK
                elif y == 7:
                    c = mix(STEEL_DARK, VACCINE, 0.25)                # the head of the glass, above its level (no marks
                else:                                                 # along it: they read as teeth)
                    c = mix(VACCINE, VACCINE_DEEP, (y - 8) / 3)
            elif y == 13 and 2 <= x <= 13:
                c = STEEL_DARK if x % 2 else STEEL
            img.putpixel((x, y), jitter(c))
    return img


def parts():
    """Small parts by colour, 4 x 4 each: dark steel, red, copper, bright steel; the glow."""
    img = Image.new('RGBA', (16, 16))
    swatches = [STEEL_DARK, RED, COPPER, STEEL_LIGHT, VACCINE, ENAMEL, STEEL, HAZARD]
    for y in range(16):
        for x in range(16):
            k = (y // 4) * 4 + x // 4
            c = swatches[k] if k < len(swatches) else STEEL
            if x % 4 == 0 or y % 4 == 0:
                c = mix(c, (0, 0, 0), 0.18)
            img.putpixel((x, y), jitter(c, 3))
    return img


def tank(full):
    """The vessel: the vaccine itself, seamless every way (the vessel is stacked three wide and four high: no frame on a
    block, or it reads as a grid of cubes; no bubbles either - the same in every block, they lined up like snow). Empty, a
    faint glass with the dried run of it."""
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            if full:
                # deep and saturated, or against the lab's white it read as ice (in game, 2026-10-20); a few small bubbles
                t = ((x * 7 + y * 3) % 5) / 4
                c = mix(LIQUID, LIQUID_DEEP, 0.35 + 0.3 * t + rng.uniform(-0.08, 0.08))
                a = 214
            else:
                c = mix((200, 220, 228), VACCINE_DEEP, 0.15)
                a = 56
                if x in (4, 11) and rng.random() < 0.6:
                    c, a = mix(VACCINE, (180, 190, 190), 0.5), 110    # where it ran down the glass and dried
            img.putpixel((x, y), jitter(c, 3, a))
    return img


def main():
    os.makedirs(DIR, exist_ok=True)
    faces = {'vaccine_body': body(), 'vaccine_panel': panel(), 'vaccine_gauge': gauge(), 'vaccine_parts': parts(),
             'vaccine_tank': tank(True), 'vaccine_tank_empty': tank(False)}
    for name, img in faces.items():
        img.save(os.path.join(DIR, name + '.png'))
    os.makedirs(PREVIEW, exist_ok=True)
    sheet = Image.new('RGBA', (len(faces) * 272, 256), (40, 40, 40, 255))
    for i, img in enumerate(faces.values()):
        bg = Image.new('RGBA', (16, 16), (40, 60, 80, 255))
        sheet.paste(Image.alpha_composite(bg, img).resize((256, 256), Image.NEAREST), (i * 272, 0))
    sheet.save(os.path.join(PREVIEW, 'vaccine_preview.png'))
    print('ok')


if __name__ == '__main__':
    main()
