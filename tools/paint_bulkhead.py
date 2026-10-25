"""Paints the sealed bulkhead (nookofthedead:bulkhead, 16 x 16) - from nothing of vanilla's.

Run: python tools/paint_bulkhead.py
writes textures/block/bulkhead.png, and build/bulkhead_tiled.png (3 x 3 of it, x8, to see how it joins).

What it is: the stone the last of them sealed their refuge in, all round it in the rock (world/Refuge#hull). The look
(the user, 2026-10-19: a riveted steel plate "ださい" - "バニラのブロックを参考に作り直し。なんなら石ブロックの色違いでいい"):
a stone as the game's own stones are made - a few close shades in small irregular clumps, no lines, no symbols - in a
cold blue-black darker than the deepslate round it. It has to pass at a glance for something one could mine, and then
not give.
"""
import os
import random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'nookofthedead', 'textures', 'block')
PREVIEW = os.path.join(HERE, '..', 'build')

SHADES = [(30, 32, 40), (38, 41, 50), (46, 50, 60), (55, 59, 70), (66, 71, 83)]   # dark to light


def main():
    # random texels, softened once with their neighbours (wrapping, so the block tiles without a seam), cut into four
    # shades: clumps of one to three texels, as the game's own stone
    rng = random.Random(1907)
    raw = [[rng.random() for _ in range(16)] for _ in range(16)]
    img = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            v = sum(raw[(y + dy) % 16][(x + dx) % 16] * (2 if dx == dy == 0 else 1)
                    for dx in (-1, 0, 1) for dy in (-1, 0, 1)) / 10
            i = 0 if v < 0.42 else 1 if v < 0.50 else 2 if v < 0.58 else 3 if v < 0.66 else 4
            img.putpixel((x, y), SHADES[i] + (255,))
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, 'bulkhead.png'))
    os.makedirs(PREVIEW, exist_ok=True)
    tiled = Image.new('RGBA', (48, 48))
    for i in range(3):
        for j in range(3):
            tiled.paste(img, (i * 16, j * 16))
    tiled.resize((384, 384), Image.NEAREST).save(os.path.join(PREVIEW, 'bulkhead_tiled.png'))
    print('ok')


if __name__ == '__main__':
    main()
