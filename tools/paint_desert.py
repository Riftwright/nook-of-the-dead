"""Paints the desert's creatures - from nothing of vanilla's: the infected scorpion's skin (64 x 32, client/ScorpionModel)
and the spawn eggs of the mutated husk, the infected camel and the infected scorpion (16 x 16).

Run: python tools/paint_desert.py
(writes textures/entity/infected_scorpion.png and textures/item/{mutated_husk,infected_camel,infected_scorpion}_spawn_egg.png)

The scorpion (the user, 2026-10-18: "感染サソリ"): the sand's colour so it is lost on it, its plates edged darker, a sick
pallor in blotches where the plague has it, the claws darker at their tips, the sting pale and wet at the point; small
black eyes on the carapace.

The UV boxes are ScorpionModel's: a cube w x h x d at (u, v) has top (u+d, v) w x d, bottom (u+d+w, v) w x d, and its sides
in a row at v+d, h high: right d, front w, left d, back w.
"""
import math
import os
import random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures')
rng = random.Random(1018)

CHITIN = (176, 146, 96)
CHITIN_DARK = (128, 100, 62)
CHITIN_DEEP = (86, 64, 40)
BELLY = (198, 176, 132)
PALLOR = (170, 168, 132)              # where the plague has it: a grey-green sick pallor
CLAW_TIP = (62, 46, 32)
STING = (214, 200, 168)
EYE = (16, 12, 10)


def jitter(c, amount=8):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, x + d)) for x in c[:3]) + (255,)


def noise(x, y, scale, salt):
    def h(ix, iy):
        return random.Random(ix * 73856093 ^ iy * 19349663 ^ salt).random()
    fx, fy = x / scale, y / scale
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


def box(img, u, v, w, h, d, base, salt, segments=0, segment_axis="y"):
    """A box in chitin: its upper faces the base, lighter on top, its belly paler; plates edged darker every
    {@code segments} texels; the plague's pallor in blotches."""
    for name, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        for x in range(x0, x0 + fw):
            for y in range(y0, y0 + fh):
                c = base
                if name == "top":
                    c = mix(base, (230, 210, 160), 0.18)
                elif name == "bottom":
                    c = BELLY
                elif fh > 1:
                    c = mix(base, CHITIN_DEEP, (y - y0) / (fh - 1) * 0.45)
                c = mix(c, CHITIN_DARK, (noise(x, y, 2.5, salt) - 0.5) * 0.7)
                if segments and name in ("top", "right", "left", "front", "back"):
                    along = (y - y0) if name == "top" else (x - x0)
                    if name in ("right", "left") and along % segments == segments - 1 or name == "top" and along % segments == segments - 1:
                        c = mix(c, CHITIN_DEEP, 0.6)           # the edge of a plate
                if noise(x, y, 3.0, salt + 7) > 0.72 and name != "bottom":
                    c = mix(c, PALLOR, 0.65)                  # the plague's pallor
                img.putpixel((x, y), jitter(c, 6))


def scorpion():
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    box(img, 0, 0, 6, 3, 9, CHITIN, 11, segments=2)          # the body, in plates
    box(img, 30, 0, 5, 2, 3, CHITIN_DARK, 12)                # the carapace
    x0, y0, w, h = faces(30, 0, 5, 2, 3)["top"]              # its eyes: two pairs, small and black
    for (dx, dy) in ((1, 2), (3, 2), (2, 1)):
        img.putpixel((x0 + dx, y0 + dy), EYE + (255,))
    box(img, 46, 0, 2, 2, 4, CHITIN, 13, segments=2)          # the arms
    box(img, 30, 6, 3, 2, 3, CHITIN_DARK, 14)                 # the claws
    for name, (x0, y0, w, h) in faces(42, 6, 1, 1, 3).items():    # the fingers, dark at the tip
        for x in range(x0, x0 + w):
            for y in range(y0, y0 + h):
                t = (x - x0) / max(1, w - 1) if name in ("right", "left") else 0.5
                img.putpixel((x, y), jitter(mix(CHITIN_DARK, CLAW_TIP, t), 5))
    box(img, 0, 12, 6, 1, 1, CHITIN_DARK, 15, segments=2)     # the legs, jointed
    box(img, 14, 12, 2, 2, 3, CHITIN, 16, segments=3)          # a tail segment
    for name, (x0, y0, w, h) in faces(24, 12, 1, 1, 2).items():   # the sting: dark at its root, pale and wet at the point
        for x in range(x0, x0 + w):
            for y in range(y0, y0 + h):
                t = (x - x0) / max(1, w - 1) if name in ("right", "left") else 1.0
                img.putpixel((x, y), jitter(mix(CHITIN_DEEP, STING, t), 4))
    out = os.path.join(ROOT, 'entity', 'infected_scorpion.png')
    img.save(out)
    img.resize((512, 256), Image.NEAREST).save(os.path.join(os.path.dirname(__file__), '..', 'build', 'scorpion_preview.png'))
    print(os.path.normpath(out))


def egg(name, shell, spots, outline, spot_share=0.16):
    """A spawn egg (16 x 16): the shell, speckled, a light on its upper left."""
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
            if not edge and r.random() < spot_share:
                c = spots
            if not edge and x <= 6 and y <= 6 and inside < 0.4:
                c = mix(shell, (255, 250, 230), 0.35)
            img.putpixel((x, y), jitter(c, 0 if edge else 5))
    out = os.path.join(ROOT, 'item', name + '_spawn_egg.png')
    img.save(out)
    print(os.path.normpath(out))


os.makedirs(os.path.join(os.path.dirname(__file__), '..', 'build'), exist_ok=True)
scorpion()
egg('mutated_husk', (150, 128, 90), (96, 120, 70), (64, 52, 36))
egg('infected_camel', (176, 150, 104), (110, 120, 92), (78, 62, 40), 0.12)
egg('infected_scorpion', (176, 146, 96), (62, 46, 32), (70, 52, 34), 0.22)
