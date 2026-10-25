"""The thin-air zombie's model and skin, from one table of parts (client/ThinAirModel) - from nothing of vanilla's.

Run: python tools/paint_thin_air.py
writes textures/entity/thin_air_zombie.png (128 x 128) and thin_air_zombie_eyes.png (for an EyesLayer), rewrites the
generated createBodyLayer() in client/ThinAirModel.java from the same table, and build/thin_air_views.png (the model at
rest, front, side, back and three-quarter, to look at).

The look (the user, 2026-10-19, from a picture they gave: "こんなイメージで。テクスチャ以外にも、いじってええよ" - after ten
texture-only looks and a plain lab coat, "流石にシンプルすぎやしないか"): one of the base's researchers, dead and up again.
The zombie's own frame, the head hung to one side, lank dark hair falling round it; eyes with a dull amber light in them;
the jaw slack and bloodied, blood down the chin and over the chest. Scrubs of dark teal, torn. Over them a lab coat gone
the grey-green of old ice, open at the front, its lapels hanging, its collar up; long, in tatters past the knees, the
tails moving as it walks and in the wind up there; the sleeves to the wrist, rags hanging under them as it reaches; blood
on the right cuff. A respirator hanging at its neck on one strap. Bare, filthy feet. Flat colour in a few steps, lit
from above; no grain.

Model space: y down, the ground at 24, the front towards -z, the creature's right towards -x. A box (x, y, z, w, h, d)
at texture (u, v) has its top at (u+d, v) w x d, its bottom at (u+d+w, v), and its sides in a row at v+d, h high: right
(-x) d, front w, left (+x) d, back w. A plane is a box with no depth (or no width): only its two broad faces show.
"""
import math
import os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures', 'entity')
JAVA = os.path.join(HERE, '..', 'src', 'main', 'java', 'dev', 'nookofthedead', 'client', 'ThinAirModel.java')
PREVIEW = os.path.join(HERE, '..', 'build')
TW, TH = 128, 128

# --- the parts: name, parent, pivot (in the parent's frame), boxes (x, y, z, w, h, d, tag, grow) ------------------------
# The humanoid's own parts keep their names and places (the zombie's animation moves them); the rest hang off them.
PARTS = [
    ('head', None, (0, 0, 0), [(-4, -8, -4, 8, 8, 8, 'head', 0)]),
    ('hat', 'head', (0, 0, 0), [(-4, -8, -4, 8, 8, 8, 'hat', 0.5)]),
    ('hair_r', 'head', (-4.6, -8, 0), [(0, 0, -3, 0, 10, 6, 'hair', 0)]),
    ('hair_l', 'head', (4.6, -8, 0), [(0, 0, -3, 0, 10, 6, 'hair', 0)]),
    ('hair_b', 'head', (0, -8, 4.6), [(-4, 0, 0, 8, 11, 0, 'hair', 0)]),
    ('body', None, (0, 0, 0), [(-4, 0, -2, 8, 12, 4, 'body', 0)]),
    ('coat', 'body', (0, 0, 0), [(-4, 0, -2, 8, 12, 4, 'coat', 0.4)]),
    ('collar', 'body', (0, 0, 0), [(-4.5, -1.5, -2.6, 9, 2, 5, 'collar', 0.15)]),
    ('lapel_r', 'body', (-4.3, 0, -2.5), [(0, 0, 0, 3, 7, 0, 'lapel', 0)]),
    ('lapel_l', 'body', (4.3, 0, -2.5), [(-3, 0, 0, 3, 7, 0, 'lapel', 0)]),
    ('respirator', 'body', (2.2, 0.5, -2.6), [(-1.5, 0, -1.2, 3, 3, 1, 'mask', 0), (-0.5, 2.5, -1.6, 2, 2, 2, 'canister', 0)]),
    ('tail_fr', 'body', (-4.4, 12, -2.5), [(0, 0, 0, 4, 11, 0, 'tail', 0)]),
    ('tail_fl', 'body', (0.4, 12, -2.5), [(0, 0, 0, 4, 11, 0, 'tail', 0)]),
    ('tail_b', 'body', (-4.5, 12, 2.5), [(0, 0, 0, 9, 12, 0, 'tail', 0)]),
    ('tail_sr', 'body', (-4.5, 12, -2.5), [(0, 0, 0, 0, 10, 5, 'tail', 0)]),
    ('tail_sl', 'body', (4.5, 12, -2.5), [(0, 0, 0, 0, 10, 5, 'tail', 0)]),
    ('right_arm', None, (-5, 2, 0), [(-3, -2, -2, 4, 12, 4, 'arm', 0)]),
    ('sleeve_r', 'right_arm', (0, 0, 0), [(-3, -2, -2, 4, 10, 4, 'sleeve_r', 0.35)]),
    ('rags_r', 'right_arm', (-1, 2, 2.4), [(0, 0, 0, 0, 8, 4, 'rags', 0)]),
    ('left_arm', None, (5, 2, 0), [(-1, -2, -2, 4, 12, 4, 'arm', 0)]),
    ('sleeve_l', 'left_arm', (0, 0, 0), [(-1, -2, -2, 4, 10, 4, 'sleeve_l', 0.35)]),
    ('rags_l', 'left_arm', (1, 2, 2.4), [(0, 0, 0, 0, 8, 4, 'rags', 0)]),
    ('right_leg', None, (-1.9, 12, 0), [(-2, 0, -2, 4, 12, 4, 'leg', 0)]),
    ('left_leg', None, (1.9, 12, 0), [(-2, 0, -2, 4, 12, 4, 'leg', 0)]),
]

# --- colours: ramps, shadow to light -----------------------------------------------------------------------------------
COAT = [(76, 96, 98), (108, 130, 130), (146, 168, 166), (184, 202, 198), (212, 226, 222)]   # the grey-green of old ice
SCRUB = [(24, 38, 40), (34, 52, 54), (46, 68, 70), (60, 86, 86)]
SKIN = [(50, 68, 68), (70, 92, 90), (94, 118, 114), (120, 144, 138)]
HAIR = [(16, 22, 24), (28, 36, 38), (42, 52, 54)]
BLOOD = [(66, 14, 16), (104, 26, 24), (142, 40, 34)]
RUBBER = [(30, 34, 34), (44, 50, 50), (60, 68, 66)]
METAL = [(96, 104, 104), (140, 148, 146)]
EYE = (236, 174, 84)
SOCKET = (20, 24, 26)
MOUTH = (34, 12, 16)
DIRT = (40, 44, 40)

LIGHT = {'top': 3.0, 'front': 2.2, 'right': 1.7, 'left': 1.5, 'back': 1.1, 'bottom': 0.2}


def hs(*k):
    n = 2166136261
    for v in k:
        for ch in str(v):
            n = ((n ^ ord(ch)) * 16777619) & 0xffffffff
    return (n % 100003) / 100003


def st(ramp, lv):
    return ramp[max(0, min(len(ramp) - 1, int(round(lv))))]


def pack():
    boxes = [(pi, bi, b) for pi, p in enumerate(PARTS) for bi, b in enumerate(p[3])]
    order = sorted(boxes, key=lambda t: (-(t[2][5] + t[2][4]), -(t[2][5] + t[2][3]), t[0], t[1]))
    uv, x, y, shelf = {}, 0, 0, 0
    for pi, bi, b in order:
        w, h, d = b[3], b[4], b[5]
        bw, bh = 2 * (d + w), d + h
        if x + bw > TW:
            x, y, shelf = 0, y + shelf, 0
        uv[(pi, bi)] = (x, y)
        x += bw
        shelf = max(shelf, bh)
    assert y + shelf <= TH, 'the parts do not fit the sheet'
    return uv


UV = pack()


def world_of(pi):
    x = y = z = 0.0
    while pi is not None:
        name, parent, off, _ = PARTS[pi]
        x, y, z = x + off[0], y + off[1], z + off[2]
        pi = next((i for i, p in enumerate(PARTS) if p[0] == parent), None) if parent else None
    return x, y, z


def faces(u, v, b):
    x0, y0, z0, w, h, d = b[:6]
    return [
        ('top', (u + d, v, w, d), lambda c, r: (x0 + c + .5, y0, z0 + d - r - .5)),
        ('bottom', (u + d + w, v, w, d), lambda c, r: (x0 + c + .5, y0 + h, z0 + d - r - .5)),
        ('right', (u, v + d, d, h), lambda c, r: (x0, y0 + r + .5, z0 + d - c - .5)),
        ('front', (u + d, v + d, w, h), lambda c, r: (x0 + c + .5, y0 + r + .5, z0)),
        ('left', (u + d + w, v + d, d, h), lambda c, r: (x0 + w, y0 + r + .5, z0 + c + .5)),
        ('back', (u + 2 * d + w, v + d, w, h), lambda c, r: (x0 + w - c - .5, y0 + r + .5, z0 + d)),
    ]


img = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))
eyes = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))


class Face:
    def __init__(self, pi, b, name, rect, at):
        self.pi, self.b, self.name = pi, b, name
        self.part = PARTS[pi][0]
        self.u, self.v, self.w, self.h = rect
        self.at, self.tag = at, b[6]
        self.base = world_of(pi)

    def world(self, c, r):
        x, y, z = self.at(c, r)
        return x + self.base[0], y + self.base[1], z + self.base[2]

    def level(self, c, r):
        _, wy, _ = self.world(c, r)
        lv = LIGHT[self.name] + (4 - wy) / 26.0
        if self.name not in ('top', 'bottom') and self.h >= 4:
            if r == 0:
                lv += 0.5
            elif r == self.h - 1:
                lv -= 0.5
        return lv


# --- what each part is painted with --------------------------------------------------------------------------------------
def stain(salt, c, r, size=1.3):
    """A spatter of blood: a blob or two with drips below, not a pattern."""
    for i in range(2):
        cx, cy = hs('bx', salt, i) * 8, hs('by', salt, i) * 6
        if ((c - cx) / size) ** 2 + ((r - cy) / (size * 1.2)) ** 2 < 1:
            return BLOOD[1 if hs('b', salt, c, r) < 0.6 else 0]
        if abs(c - round(cx)) < 0.6 and cy < r < cy + 2 + hs('drip', salt, i) * 4:
            return BLOOD[0]
    return None


def ragged(f, c, r, rows, salt):
    """A torn edge over the last `rows` rows: strips of different lengths, some hanging long."""
    start = f.h - rows
    if r < start:
        return False
    k = hs('rag', salt, f.pi, f.name, c)
    return r >= start + int(k * (rows + 1))


def paint_face(f, c, r):
    t, n, lv = f.tag, f.name, f.level(c, r)
    if t == 'head':
        if n == 'front':
            if r <= 1:
                return st(HAIR, 1 if (c + r) % 3 else 0)
            if r == 2:
                return st(HAIR, 0) if c in (0, 1, 6) else st(SKIN, lv)
            if r == 3 and c in (1, 2, 5, 6):
                return st(SKIN, lv - 1.6)                    # the brow's shadow
            if r == 4 and c in (1, 2, 5, 6):
                return EYE if c in (2, 5) else SOCKET        # a dull amber light in each
            if r == 5:
                if c in (3, 4):
                    return st(SKIN, lv - 1.4)
                if c in (1, 6):
                    return st(SKIN, lv - 1)                  # sunk cheeks
            if r == 6 and 2 <= c <= 5:
                return MOUTH if c in (3, 4) else BLOOD[0]    # the jaw slack, bloodied
            if r == 7:
                if 2 <= c <= 5:
                    return BLOOD[1] if c != 4 else MOUTH
                if c == 1:
                    return BLOOD[0]
            return st(SKIN, lv)
        if n == 'top':
            return st(HAIR, 1 if hs('ht', c, r) < 0.7 else 2)
        if n == 'bottom':
            return BLOOD[0] if 2 <= c <= 5 and r <= 2 else st(SKIN, 0)
        if r <= 3 or n == 'back':
            return st(HAIR, 0 if r > 4 else 1)
        return st(SKIN, lv - 0.4)
    if t == 'hat':                                           # lank hair over the crown, a few strands over the face
        if n == 'bottom':
            return None
        if n == 'top':
            return st(HAIR, 1 + (hs('crown', c, r) < 0.3)) if hs('gap', c, r) > 0.15 else None
        if n == 'front':
            if r == 0:
                return st(HAIR, 1)
            if c in (0, 1, 6) and r <= 3 + int(hs('fr', c) * 3):
                return st(HAIR, 0 if c == 1 else 1)          # strands hanging over the brow
            return None
        if r <= 1 + int(hs('side', n, c) * 3) and (c + r) % 3 != 1:
            return st(HAIR, 1)
        return None
    if t == 'hair':                                          # long lank strands, uneven
        if n not in ('front', 'back', 'left', 'right'):
            return None
        if c % 2 == 1 and hs('h2', f.pi, c) < 0.5:
            return None
        length = 4 + int(hs('len', f.pi, c) * (f.h - 3))
        return st(HAIR, 1 if c % 3 else 0) if r < length else None
    if t == 'body':                                          # the scrubs, torn, blood down the chest
        if n == 'front':
            s = stain('chest', c - 2, r - 1, 1.6)
            if s:
                return s
            if (c, r) in ((2, 8), (3, 9), (5, 6)):
                return st(SKIN, lv)                          # torn to the skin
        return st(SCRUB, lv - (0.5 if r == 11 else 0))
    if t == 'coat':
        if n == 'bottom':
            return None
        if n == 'front':
            if 2 <= c <= 5 and r >= 1:
                return None                                  # hanging open
            if r >= 9 and hs('hem', c, r) < 0.25:
                return None
            s = stain('coatfront', c * 2, r - 4)
            if s and c >= 6:
                return s
        if n == 'top':
            return st(COAT, 3) if not (2 <= c <= 5) else None
        if n in ('right', 'left', 'back') and (c, r) == (1, 6):
            return None                                      # a rip
        fold = n == 'back' and c in (2, 5)
        return st(COAT, lv - (0.8 if fold else 0) - 0.4 * r / 12)
    if t == 'collar':
        if n == 'bottom':
            return None
        if n in ('front', 'top') and 2 <= c <= 6:
            return None
        return st(COAT, lv + 0.3)
    if t == 'lapel':
        if r == f.h - 1 and c == 1:
            return None
        return st(COAT, lv - (0.8 if c in (0, f.w - 1) else 0) + 0.2)
    if t == 'mask':
        if n == 'front':
            return st(RUBBER, 2) if (c + r) % 2 == 0 else st(RUBBER, 1)
        return st(RUBBER, 1)
    if t == 'canister':
        return METAL[1] if r == 0 else st(RUBBER, 0) if n == 'front' and (c, r) == (1, 1) else METAL[0]
    if t == 'tail':                                          # long, stained toward the hem, in tatters
        if n in ('top', 'bottom'):
            return None
        rows = 5 if f.h >= 11 else 4
        if ragged(f, c, r, rows, f.part):
            return None
        if hs('hole', f.pi, n, c, r // 2) < 0.03:
            return None
        lv2 = lv - 0.15 * r - (0.6 if c % 3 == 1 else 0)
        if r > f.h - rows - 3 and hs('grime', f.pi, n, c) < 0.5:
            return st(COAT, lv2 - 1)
        if f.part == 'tail_fr' and n == 'front':
            s = stain('tail', c * 2, r - 2)
            if s:
                return s
        return st(COAT, lv2)
    if t in ('sleeve_r', 'sleeve_l'):
        if n == 'bottom':
            return None
        if ragged(f, c, r, 2, t):
            return None
        if t == 'sleeve_r' and r >= f.h - 3 and hs('cuff', n, c, r) < 0.3:
            return BLOOD[1]                                  # blood on the right cuff
        return st(COAT, lv)
    if t == 'rags':
        if n not in ('right', 'left'):
            return None
        if ragged(f, c, r, f.h, 'rags' + f.part) and r > 1:
            return None
        return st(COAT, lv - 0.6 - (0.6 if c % 2 else 0))
    if t == 'arm':                                           # under the sleeves: the hands, the right one bloodied
        if n == 'bottom':                                    # the fingers' ends, as one sees them reaching
            if f.part == 'right_arm' and (c, r) in ((0, 1), (2, 2), (3, 0)):
                return BLOOD[1]
            return st(SKIN, 1.4 if (c + r) % 2 else 0.8)
        if r == 11:
            return SOCKET if c % 2 == 0 else st(SKIN, lv - 1)   # the nails, dark
        if f.part == 'right_arm' and r == 10 and c in (1, 2):
            return BLOOD[1]
        return st(SKIN, lv)
    if t == 'leg':                                           # the scrubs' trousers, torn; bare filthy feet
        if n == 'bottom':
            return DIRT
        if r >= 10:
            return st(SKIN, lv - 1.2) if r == 10 else DIRT
        if n == 'front' and r in (6, 7) and c in (1, 2):
            return st(SKIN, lv)                              # torn at the knee
        if (c, r) in ((2, 3), (1, 8)):
            return BLOOD[0]
        return st(SCRUB, lv - 0.4)
    return None


def paint():
    allf = []
    for (pi, bi), (u, v) in sorted(UV.items()):
        b = PARTS[pi][3][bi]
        for name, rect, at in faces(u, v, b):
            if rect[2] <= 0 or rect[3] <= 0:
                continue
            f = Face(pi, b, name, rect, at)
            for r in range(f.h):
                for c in range(f.w):
                    col = paint_face(f, c, r)
                    if col is not None:
                        img.putpixel((f.u + c, f.v + r), tuple(col) + (255,))
                        if tuple(col) == EYE:
                            eyes.putpixel((f.u + c, f.v + r), EYE + (255,))
            allf.append(f)
    return allf


# --- the Java: createBodyLayer() from the same table -----------------------------------------------------------------
def num(v):
    s = ('%.3f' % v).rstrip('0').rstrip('.')
    return (s if '.' in s else s + '.0') + 'F'


def java():
    lines = ['    public static LayerDefinition createBodyLayer() {',
             '        MeshDefinition mesh = new MeshDefinition();',
             '        PartDefinition root = mesh.getRoot();']
    for pi, (name, parent, off, boxes) in enumerate(PARTS):
        b = 'CubeListBuilder.create()'
        for bi, (x, y, z, w, hh, d, _, grow) in enumerate(boxes):
            u, v = UV[(pi, bi)]
            g = ', new CubeDeformation(%s)' % num(grow) if grow else ''
            b += '\n                .texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s%s)' % (u, v, num(x), num(y), num(z), num(w), num(hh), num(d), g)
        par = 'root' if parent is None else 'p_' + parent
        lines.append('        PartDefinition p_%s = %s.addOrReplaceChild("%s", %s,\n                PartPose.offset(%s, %s, %s));'
                     % (name, par, name, b, num(off[0]), num(off[1]), num(off[2])))
    lines.append('        return LayerDefinition.create(mesh, %d, %d);' % (TW, TH))
    lines.append('    }')
    src = open(JAVA, encoding='utf-8').read()
    a, b = '    // BEGIN GENERATED by tools/paint_thin_air.py\n', '    // END GENERATED\n'
    i, j = src.index(a) + len(a), src.index(b)
    src = src[:i] + '\n'.join(lines) + '\n' + src[j:]
    open(JAVA, 'w', encoding='utf-8', newline='\n').write(src)


def view(allf, yaw, scale=6):
    ca, sa = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    pts = []
    for f in allf:
        for r in range(f.h):
            for c in range(f.w):
                px = img.getpixel((f.u + c, f.v + r))
                if px[3] == 0:
                    continue
                x, y, z = f.world(c, r)
                pts.append((x * sa + z * ca, x * ca - z * sa, y, px))
    pts.sort(key=lambda p: -p[0])
    W, H = 30 * scale, 36 * scale
    out = Image.new('RGBA', (W, H), (200, 210, 220, 255))
    d = ImageDraw.Draw(out)
    for _, sx, y, px in pts:
        cx, cy = W / 2 - sx * scale, (y + 10) * scale
        d.rectangle([cx - scale * .55, cy - scale * .55, cx + scale * .55, cy + scale * .55], fill=px)
    return out


def main():
    allf = paint()
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, 'thin_air_zombie.png'))
    eyes.save(os.path.join(OUT, 'thin_air_zombie_eyes.png'))
    java()
    os.makedirs(PREVIEW, exist_ok=True)
    views = [view(allf, a) for a in (0, 90, 180, 35)]
    sheet = Image.new('RGBA', (sum(v.width for v in views), views[0].height))
    for i, v in enumerate(views):
        sheet.paste(v, (i * v.width, 0))
    sheet.save(os.path.join(PREVIEW, 'thin_air_views.png'))
    print('ok: %d colours' % len({p for p in img.get_flattened_data() if p[3]}))


if __name__ == '__main__':
    main()
