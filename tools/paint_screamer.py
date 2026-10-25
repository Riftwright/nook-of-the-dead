"""Paints the screamer's skin (64 x 64, the zombie model's layout) - from nothing of vanilla's.

Run: python tools/paint_screamer.py  (writes src/main/resources/assets/nookofthedead/textures/entity/screamer.png)

The user (2026-10-17): the screamer should be told apart by its look - "普通のゾンビってスティーブの服着てるから、アレックス
ライクな服装のゾンビにしてみたら？". So: a girl's look, as the game's other player wears it, gone the way of the dead -
long hair the dead's green as the rest of it, a pale green tunic torn at the hem, brown leggings, worn boots; the skin the dead's
green, the eyes sunk, the mouth open (it screams). All of it in the dead's dull greens, as the others are (the user: nothing on it should stand out by its colour -
"そこだけ彩度が高くて…浮いて見える"; "普通のゾンビみたいに緑っぽくていい"): told apart by its shape - the hair over its
head, the tunic and leggings - not by a brighter colour.

The UV boxes are the zombie model's (HumanoidModel.createMesh, 64 x 64): head 8x8x8 at (0, 0) and its hat at (32, 0),
body 8x12x4 at (16, 16), an arm 4x12x4 at (40, 16) and a leg 4x12x4 at (0, 16) (the left ones the right ones mirrored).
A cube w x h x d at (u, v) has top (u+d, v) w x d, bottom (u+d+w, v) w x d, and its sides in a row at v+d, h high:
right d, front w, left d, back w.
"""
import os
import random
import sys
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures', 'entity',
                   'screamer.png')
rng = random.Random(1017)
img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))

SKIN = (90, 128, 60)
SKIN_DARK = (70, 102, 48)
HAIR = (60, 90, 46)
HAIR_DARK = (46, 72, 36)
TUNIC = (118, 136, 98)
TUNIC_DARK = (98, 116, 82)
TRIM = (70, 66, 50)
PANTS = (84, 80, 58)
PANTS_DARK = (68, 64, 48)
BOOT = (64, 66, 54)
EYE = (24, 30, 22)
MOUTH = (40, 20, 22)
BLOOD = (80, 46, 40)

# The desert's screamer (the mutated husk's, Desert): the same look gone the husk's way - sun-dried, sand-coloured, the
# tunic bleached; told apart from the other husks by its shape as the town's one is.
#   python tools/paint_screamer.py --husk   (writes husk_screamer.png)
if "--husk" in sys.argv:
    OUT = os.path.join(os.path.dirname(OUT), 'husk_screamer.png')
    SKIN = (152, 132, 96)
    SKIN_DARK = (124, 106, 76)
    HAIR = (120, 100, 70)
    HAIR_DARK = (96, 80, 56)
    TUNIC = (172, 156, 122)
    TUNIC_DARK = (146, 130, 100)
    TRIM = (98, 86, 64)
    PANTS = (118, 102, 78)
    PANTS_DARK = (96, 82, 62)
    BOOT = (84, 74, 58)
    EYE = (36, 28, 20)
    MOUTH = (48, 24, 22)
    BLOOD = (90, 50, 40)


def jitter(c, amount=8):
    d = rng.randint(-amount, amount)
    return tuple(max(0, min(255, x + d)) for x in c) + (255,)


def px(x, y, colour, amount=6):
    img.putpixel((x, y), jitter(colour, amount))


def fill(x0, y0, w, h, colour, amount=8):
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            px(x, y, colour, amount)


def faces(u, v, w, h, d):
    """The faces of a cube as rectangles: top, bottom, right, front, left, back."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def rows(face, top, colour, count, amount=8):
    """Paint {@code count} rows of {@code face} from row {@code top} down."""
    x0, y0, w, h = face
    fill(x0, y0 + top, w, min(count, h - top), colour, amount)


# --- the head: hair over the top, the back and down the sides; the face green, the eyes sunk, the mouth open ---------
head = faces(0, 0, 8, 8, 8)
for name, f in head.items():
    fill(*f, SKIN)
fill(*head["top"], HAIR)
fill(*head["back"], HAIR)
rows(head["right"], 0, HAIR, 8)
rows(head["left"], 0, HAIR, 8)
for name in ("right", "left"):                        # the ear's cheek showing at the front edge of each side
    x0, y0, w, h = head[name]
    edge = x0 + w - 1 if name == "right" else x0
    for y in range(y0 + 3, y0 + 7):
        px(edge, y, SKIN)
fx, fy, fw, fh = head["front"]
rows(head["front"], 0, HAIR, 2)                       # the fringe, swept to one side
for x in range(fx, fx + 5):
    px(x, fy + 2, HAIR_DARK)
px(fx, fy + 3, HAIR_DARK)
px(fx + fw - 1, fy + 2, HAIR)
for x in (fx + 1, fx + 2, fx + 5, fx + 6):            # sunk eyes, a darker brow over them
    px(x, fy + 4, EYE, 3)
    px(x, fy + 3, SKIN_DARK)
px(fx + 3, fy + 5, SKIN_DARK)                          # the nose's shadow
for x in range(fx + 2, fx + 6):                        # the mouth open in the scream
    px(x, fy + 6, MOUTH, 3)
px(fx + 3, fy + 7, MOUTH, 3)
px(fx + 4, fy + 7, MOUTH, 3)
px(fx + 6, fy + 7, BLOOD, 4)
# no hat layer: hair on it stood off the head and looked to float (the user, 2026-10-17: "髪の毛がちょっと浮いちゃう");
# the hair is all on the head itself

# --- the body: the tunic, a belt, the leggings' top; torn at the hem, the skin showing ------------------------------
body = faces(16, 16, 8, 12, 4)
for name, f in body.items():
    fill(*f, TUNIC)
    rows(f, 0, TUNIC_DARK, 1) if name in ("front", "back", "right", "left") else None
for name in ("front", "back", "right", "left"):
    rows(body[name], 8, TRIM, 1)
    rows(body[name], 9, PANTS, 3)
bx, by, bw, bh = body["front"]
for x in range(bx + 2, bx + 6):                        # the collar open at the neck
    px(x, by, SKIN_DARK)
px(bx + 3, by + 1, SKIN_DARK)
for x, y in ((bx + 1, by + 7), (bx + 5, by + 6), (bx + 6, by + 7), (bx + 2, by + 4)):   # torn through
    px(x, y, SKIN)
px(bx + 5, by + 3, BLOOD, 4)
px(bx + 5, by + 4, BLOOD, 4)
fill(*body["bottom"], PANTS_DARK)

# --- the arms: short sleeves, the rest bare and dead -----------------------------------------------------------------
arm = faces(40, 16, 4, 12, 4)
for name, f in arm.items():
    fill(*f, SKIN)
for name in ("front", "back", "right", "left"):
    rows(arm[name], 0, TUNIC, 4)
    rows(arm[name], 4, TUNIC_DARK, 1)
    rows(arm[name], 10, SKIN_DARK, 2)                  # the hands, darker
fill(*arm["top"], TUNIC)
fill(*arm["bottom"], SKIN_DARK)
ax, ay, aw, ah = arm["front"]
px(ax + 1, ay + 7, SKIN_DARK)
px(ax + 2, ay + 8, BLOOD, 4)

# --- the legs: leggings, worn boots ------------------------------------------------------------------------------------
leg = faces(0, 16, 4, 12, 4)
for name, f in leg.items():
    fill(*f, PANTS)
for name in ("front", "back", "right", "left"):
    rows(leg[name], 0, PANTS_DARK, 1)
    rows(leg[name], 9, BOOT, 3)
fill(*leg["top"], PANTS_DARK)
fill(*leg["bottom"], BOOT)
lx, ly, lw, lh = leg["front"]
px(lx + 1, ly + 5, SKIN)                               # a knee through a tear

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
img.resize((512, 512), Image.NEAREST).save(os.path.join(os.path.dirname(__file__), 'screamer_preview.png'))
print("wrote", os.path.normpath(OUT))
