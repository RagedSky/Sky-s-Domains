#!/usr/bin/env python3
"""Generates the 16x16 item/block placeholder icons of Sky's Domains.

High-resolution impact frames and scene textures come from tools/generate_art.py; this script
only writes them when they are missing (--all forces everything).

Run from the repository root:  python3 tools/generate_placeholders.py
All outputs are plain PNGs under src/main/resources/assets/skysdomains/textures and can be
replaced 1:1 by hand-made art (see docs/ASSETS.md for the list and recommended sizes).
"""
import math, os, random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'skysdomains')
TEX = os.path.join(ROOT, 'textures')
random.seed(42)

def save(img, *path):
    p = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)

def noise_stone(size, base, vein, vein_density=0.12, deepslate=False):
    img = Image.new('RGBA', (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            n = random.randint(-14, 14)
            c = tuple(max(0, min(255, b + n)) for b in base)
            px[x, y] = c + (255,)
    # ore veins: small blobs
    for _ in range(int(size * size * vein_density / 3)):
        cx, cy = random.randrange(size), random.randrange(size)
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                if random.random() < 0.7:
                    x, y = (cx + dx) % size, (cy + dy) % size
                    n = random.randint(-25, 25)
                    px[x, y] = tuple(max(0, min(255, v + n)) for v in vein) + (255,)
    return img

def item_icon(size, color, shape='gem', accent=None, glow=False):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    accent = accent or tuple(min(255, c + 70) for c in color)
    dark = tuple(max(0, c - 70) for c in color)
    c = size // 2
    if shape == 'gem':
        d.polygon([(c, 1), (size - 2, c), (c, size - 2), (1, c)], fill=color, outline=dark)
        d.polygon([(c, 3), (c + 3, c - 1), (c, c + 2), (c - 3, c - 1)], fill=accent)
    elif shape == 'dust':
        for _ in range(26):
            x, y = random.randint(2, size - 3), random.randint(2, size - 3)
            d.point((x, y), fill=random.choice([color, accent, dark]))
        d.ellipse([3, 6, size - 4, size - 3], outline=dark)
    elif shape == 'shard':
        d.polygon([(4, size - 2), (size - 5, 2), (size - 2, 6), (7, size - 2)], fill=color, outline=dark)
        d.line([(6, size - 4), (size - 5, 4)], fill=accent)
    elif shape == 'orb':
        d.ellipse([2, 2, size - 3, size - 3], fill=color, outline=dark)
        d.ellipse([4, 4, c - 1, c - 1], fill=accent)
    elif shape == 'bottle':
        d.rectangle([c - 2, 1, c + 1, 4], fill=(90, 70, 50, 255))
        d.rounded_rectangle([3, 4, size - 4, size - 2], radius=3, fill=color, outline=dark)
        d.rectangle([5, 7, size - 6, size - 4], fill=accent)
    elif shape == 'talisman':
        d.rounded_rectangle([3, 1, size - 4, size - 2], radius=2, fill=color, outline=dark)
        d.ellipse([c - 3, c - 3, c + 2, c + 2], fill=accent, outline=dark)
        d.line([(c, 1), (c, 3)], fill=dark)
    elif shape == 'sigil':
        d.ellipse([1, 1, size - 2, size - 2], fill=color, outline=dark)
        d.polygon([(c, 3), (size - 4, c + 2), (c, size - 4), (3, c + 2)], outline=accent)
        d.line([(c, 3), (c, size - 4)], fill=accent)
    elif shape == 'template':
        d.rectangle([2, 1, size - 3, size - 2], fill=color, outline=dark)
        for i in range(3):
            d.line([(4, 4 + i * 3), (size - 5, 4 + i * 3)], fill=accent)
    elif shape == 'catalyst':
        d.polygon([(c, 1), (size - 2, size - 3), (1, size - 3)], fill=color, outline=dark)
        d.ellipse([c - 2, c - 1, c + 2, c + 3], fill=accent)
    if glow:
        for i in range(3):
            d.point((random.randint(1, size - 2), random.randint(1, size - 2)), fill=(255, 255, 255, 255))
    return img

def sphere_texture(size, base, streak, alpha=255, stars=0, crackle=False):
    img = Image.new('RGBA', (size, size), base + (alpha,))
    px = img.load()
    for y in range(size):
        for x in range(size):
            v = (math.sin(x * 0.11 + y * 0.07) + math.sin(y * 0.19 - x * 0.05)) * 0.25 + 0.5
            n = random.random() * 0.15
            t = max(0.0, min(1.0, v + n))
            px[x, y] = tuple(int(base[i] + (streak[i] - base[i]) * t) for i in range(3)) + (alpha,)
    if stars:
        for _ in range(stars):
            x, y = random.randrange(size), random.randrange(size)
            b = random.randint(160, 255)
            px[x, y] = (b, b, b, 255)
    if crackle:
        d = ImageDraw.Draw(img)
        for _ in range(size // 8):
            x, y = random.randrange(size), random.randrange(size)
            for _ in range(6):
                nx, ny = x + random.randint(-6, 6), y + random.randint(-6, 6)
                d.line([(x, y), (nx, ny)], fill=(255, 255, 255, alpha))
                x, y = nx, ny
    return img

def planet(size, colors, bands=True, spots=0):
    img = Image.new('RGBA', (size, size))
    px = img.load()
    for y in range(size):
        band = colors[(y * len(colors) // size) % len(colors)] if bands else colors[0]
        for x in range(size):
            n = random.randint(-10, 10)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in band) + (255,)
    d = ImageDraw.Draw(img)
    for _ in range(spots):
        x, y, r = random.randrange(size), random.randrange(size), random.randint(2, size // 8)
        d.ellipse([x - r, y - r, x + r, y + r], fill=random.choice(colors))
    return img

def ring(size, inner=0.45, outer=0.98, color=(220, 200, 160)):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    c = size / 2
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - c + 0.5, y - c + 0.5) / c
            if inner <= r <= outer:
                band = 0.6 + 0.4 * math.sin(r * 60)
                a = int(220 * band)
                px[x, y] = tuple(int(v * band) for v in color) + (a,)
    return img

def impact_frame(w, h, bg, fg, style):
    img = Image.new('RGBA', (w, h), bg + (255,))
    d = ImageDraw.Draw(img)
    cx, cy = w // 2, h // 2
    if style == 'cracks':
        for _ in range(14):
            x, y = cx, cy
            for _ in range(10):
                nx, ny = x + random.randint(-40, 40), y + random.randint(-25, 25)
                d.line([(x, y), (nx, ny)], fill=fg + (255,), width=random.randint(1, 3))
                x, y = nx, ny
    elif style == 'burst':
        for i in range(36):
            a = i / 36 * math.tau
            d.line([(cx, cy), (cx + math.cos(a) * w, cy + math.sin(a) * h)], fill=fg + (255,), width=random.randint(1, 4))
        d.ellipse([cx - 30, cy - 30, cx + 30, cy + 30], fill=bg + (255,))
    elif style == 'outline':
        for r in range(10, min(w, h) // 2, 14):
            d.ellipse([cx - r * 1.7, cy - r, cx + r * 1.7, cy + r], outline=fg + (255,), width=2)
    elif style == 'shards':
        for _ in range(40):
            x, y = random.randrange(w), random.randrange(h)
            pts = [(x + random.randint(-20, 20), y + random.randint(-20, 20)) for _ in range(3)]
            d.polygon(pts, outline=fg + (255,))
    elif style == 'clock':
        d.ellipse([cx - 60, cy - 60, cx + 60, cy + 60], outline=fg + (255,), width=3)
        d.line([(cx, cy), (cx, cy - 50)], fill=fg + (255,), width=4)
        d.line([(cx, cy), (cx + 35, cy + 20)], fill=fg + (255,), width=4)
    elif style == 'flames':
        for i in range(60):
            x = random.randrange(w)
            d.polygon([(x, h), (x + random.randint(-15, 15), h - random.randint(20, 90)), (x + 12, h)], fill=fg + (255,))
    return img

# ------------------------------------------------------------------ blocks
save(noise_stone(16, (125, 125, 125), (150, 90, 230), 0.14), 'block', 'arcane_ore.png')
save(noise_stone(16, (80, 80, 84), (150, 90, 230), 0.14), 'block', 'deepslate_arcane_ore.png')
save(noise_stone(16, (100, 50, 50), (40, 10, 60), 0.12), 'block', 'abyssal_ore.png')
save(noise_stone(16, (220, 222, 160), (20, 10, 40), 0.10), 'block', 'void_ore.png')

# ------------------------------------------------------------------ items
items = {
    'arcane_dust': ((160, 110, 240), 'dust'), 'abyssal_shard': ((60, 20, 90), 'shard'), 'void_crystal': ((20, 10, 40), 'gem', (120, 90, 200)),
    'cursed_fragment': ((70, 70, 80), 'shard', (200, 60, 60)), 'echoed_matter': ((20, 60, 70), 'orb', (90, 220, 230)),
    'domain_core': ((90, 60, 160), 'orb'), 'sealing_ink': ((40, 40, 80), 'bottle'), 'refinement': ((200, 170, 240), 'gem'),
    'sealing_template': ((110, 60, 60), 'template'), 'absolute_template': ((90, 80, 140), 'template'), 'transcendent_template': ((40, 90, 100), 'template'),
    'sealing_catalyst': ((110, 40, 70), 'catalyst'), 'absolute_catalyst': ((70, 60, 140), 'catalyst'), 'transcendent_catalyst': ((30, 110, 120), 'catalyst'),
    'fractured_talisman': ((170, 140, 100), 'talisman'), 'sealed_talisman': ((120, 80, 110), 'talisman'),
    'absolute_talisman': ((80, 70, 150), 'talisman'), 'transcendent_talisman': ((230, 230, 250), 'talisman'),
    'frost_sigil': ((150, 220, 255), 'sigil'), 'umbral_sigil': ((40, 20, 60), 'sigil'), 'glass_sigil': ((240, 220, 150), 'sigil'),
    'ember_sigil': ((240, 100, 40), 'sigil'), 'stasis_sigil': ((170, 170, 170), 'sigil'), 'collapse_sigil': ((20, 20, 40), 'sigil', (255, 255, 255)),
}
for name, spec in items.items():
    color, shape = spec[0], spec[1]
    accent = spec[2] if len(spec) > 2 else None
    save(item_icon(16, color, shape, accent, glow=name in ('echoed_matter', 'void_crystal', 'transcendent_talisman')), 'item', name + '.png')

import sys
FORCE = '--all' in sys.argv
if FORCE or not os.path.exists(os.path.join(TEX, 'scene', 'sun.png')):
  pass
else:
  print('scene/impact textures exist (generate_art.py) - skipping'); raise SystemExit
# ------------------------------------------------------------------ scene (stitched into the block atlas)
save(sphere_texture(64, (120, 120, 160), (220, 220, 255), 255), 'scene', 'sphere_default.png')
save(sphere_texture(64, (150, 210, 255), (255, 255, 255), 255, crackle=True), 'scene', 'sphere_frost_vault.png')
save(sphere_texture(64, (5, 5, 15), (30, 20, 60), 255), 'scene', 'sphere_umbral_sea.png')
save(sphere_texture(64, (250, 240, 210), (255, 255, 255), 255, crackle=True), 'scene', 'sphere_glass_heaven.png')
save(sphere_texture(64, (200, 60, 20), (255, 180, 60), 255), 'scene', 'sphere_ember_garden.png')
save(sphere_texture(64, (120, 120, 120), (200, 200, 200), 255), 'scene', 'sphere_stasis.png')
save(sphere_texture(64, (2, 2, 12), (10, 10, 40), 255, stars=140), 'scene', 'sphere_structural_collapse.png')
save(sphere_texture(64, (0, 0, 4), (6, 6, 20), 255, stars=200), 'scene', 'sky_space.png')
save(sphere_texture(64, (0, 0, 0), (0, 0, 0), 255), 'scene', 'sky_none.png')
planets = {
    'mercury': ([(120, 115, 110), (90, 85, 80)], 4), 'venus': ([(230, 200, 140), (200, 170, 110)], 2), 'earth': ([(40, 90, 200), (60, 150, 80), (40, 90, 200)], 6),
    'mars': ([(200, 90, 50), (160, 70, 40)], 3), 'jupiter': ([(220, 190, 150), (170, 120, 90), (240, 210, 170), (190, 140, 100)], 3),
    'saturn': ([(230, 210, 160), (200, 180, 130)], 2), 'uranus': ([(150, 220, 230), (120, 200, 220)], 1), 'neptune': ([(50, 80, 220), (30, 60, 180)], 2),
}
for name, (colors, spots) in planets.items():
    save(planet(32, colors, spots=spots), 'scene', f'planet_{name}.png')
save(planet(32, [(150, 150, 170)], bands=False, spots=4), 'scene', 'planet_generic.png')
save(planet(32, [(255, 230, 120), (255, 200, 60), (255, 240, 160)], spots=5), 'scene', 'sun.png')
save(sphere_texture(32, (240, 240, 255), (255, 255, 255), 255), 'scene', 'white_hole.png')
save(ring(64), 'scene', 'ring_saturn.png')
save(ring(64, 0.2, 0.95, (200, 200, 200)), 'scene', 'clock_face.png')
save(sphere_texture(32, (240, 250, 255), (255, 255, 255), 200, crackle=True), 'scene', 'glass_shard.png')
save(sphere_texture(32, (255, 140, 40), (255, 220, 120), 230), 'scene', 'ember_petal.png')
save(sphere_texture(32, (200, 240, 255), (255, 255, 255), 230, crackle=True), 'scene', 'ice_crystal.png')
save(sphere_texture(32, (10, 5, 20), (40, 20, 70), 230), 'scene', 'shadow_blob.png')
save(sphere_texture(32, (255, 255, 255), (255, 255, 255), 255), 'scene', 'flash_white.png')

# ------------------------------------------------------------------ impact frames (256x144, scaled to the screen)
frames = {
    'frost_vault': ((255, 255, 255), (80, 220, 255), ['cracks', 'cracks', 'burst']),
    'umbral_sea': ((0, 0, 0), (255, 255, 255), ['outline', 'outline', 'burst']),
    'glass_heaven': ((250, 245, 230), (200, 170, 60), ['shards', 'shards', 'burst']),
    'ember_garden': ((20, 0, 0), (255, 60, 20), ['flames', 'burst', 'flames']),
    'stasis': ((60, 60, 60), (230, 230, 230), ['clock', 'outline', 'clock']),
    'structural_collapse': ((255, 255, 255), (0, 0, 0), ['burst', 'cracks', 'outline']),
}
for domain, (bg, fg, styles) in frames.items():
    for i, style in enumerate(styles, start=1):
        save(impact_frame(256, 144, bg, fg, style), 'impact', domain, f'frame_{i:02d}.png')

# ------------------------------------------------------------------ icon
icon = Image.new('RGBA', (128, 128), (20, 16, 40, 255))
d = ImageDraw.Draw(icon)
d.ellipse([16, 16, 112, 112], outline=(170, 140, 255, 255), width=6)
d.ellipse([44, 44, 84, 84], fill=(230, 220, 255, 255))
icon.save(os.path.join(ROOT, 'icon.png'))
print('placeholders generated under', TEX)
