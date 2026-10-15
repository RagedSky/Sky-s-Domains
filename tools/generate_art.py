#!/usr/bin/env python3
"""High-resolution procedural art for Sky's Domains (impact frames + scene textures).

Usage:  python3 tools/generate_art.py [--frames 1440|1080|2160]

Impact frames are 16:9 (default 2560x1440; 2160 = true 4K, roughly 4x the jar size and VRAM).
Scene textures: 1024x512 equirectangular sphere skins, 512x512 planets, 512x512 sprites.
Everything is written under src/main/resources/assets/skysdomains/textures and can be replaced 1:1.
"""
import argparse, json, math, os, random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'skysdomains')
TEX = os.path.join(ROOT, 'textures')
rng = np.random.default_rng(7)
random.seed(7)


# ----------------------------------------------------------------------------- helpers
def save(img, *path):
    p = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p, optimize=True)


def to_img(rgb, alpha=None):
    rgb = np.clip(rgb, 0, 1)
    arr = (rgb * 255).astype(np.uint8)
    if alpha is None:
        return Image.fromarray(arr, 'RGB').convert('RGBA')
    a = (np.clip(alpha, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(np.dstack([arr, a]), 'RGBA')


def value_noise(h, w, cells, seed=None):
    """Smooth value noise by bicubic upscaling of a random grid."""
    r = np.random.default_rng(seed) if seed is not None else rng
    grid = r.random((max(2, cells * h // max(w, 1)) + 1, cells + 1), dtype=np.float32)
    im = Image.fromarray((grid * 255).astype(np.uint8), 'L').resize((w, h), Image.BICUBIC)
    return np.asarray(im, dtype=np.float32) / 255.0


def fbm(h, w, cells=4, octaves=6, gain=0.5, seed=None):
    total = np.zeros((h, w), np.float32)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        total += amp * value_noise(h, w, cells * 2 ** o, None if seed is None else seed + o)
        norm += amp
        amp *= gain
    return total / norm


def tileable_fbm(h, w, cells=4, octaves=6, seed=None):
    """Horizontally tileable (for equirectangular sphere skins): blend two noises across the seam."""
    a = fbm(h, w, cells, octaves, seed=seed)
    b = np.roll(a, w // 2, axis=1)
    x = np.linspace(0, 1, w, dtype=np.float32)[None, :]
    wgt = 0.5 - 0.5 * np.cos(x * 2 * np.pi)
    return a * (1 - wgt) + b * wgt


def radial(h, w, cx=0.5, cy=0.5, aspect=True):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    x = x / w - cx
    y = y / h - cy
    if aspect:
        x *= w / h
    return np.sqrt(x * x + y * y), np.arctan2(y, x)


def grad(h, w, c0, c1, t):
    c0 = np.asarray(c0, np.float32) / 255
    c1 = np.asarray(c1, np.float32) / 255
    return c0[None, None, :] * (1 - t[..., None]) + c1[None, None, :] * t[..., None]


def blur(arr, radius):
    im = Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8), 'L').filter(ImageFilter.GaussianBlur(radius))
    return np.asarray(im, np.float32) / 255


def lines_layer(w, h, draw_fn, blur_radius=0):
    im = Image.new('L', (w, h), 0)
    draw_fn(ImageDraw.Draw(im))
    if blur_radius:
        im = im.filter(ImageFilter.GaussianBlur(blur_radius))
    return np.asarray(im, np.float32) / 255


def vignette(h, w, strength=0.6, power=2.2):
    r, _ = radial(h, w)
    return 1 - strength * np.clip(r / 0.9, 0, 1) ** power


def grain(h, w, amount=0.04):
    return 1 + (rng.random((h, w), dtype=np.float32) - 0.5) * 2 * amount


def crack_walks(d, w, h, count, length, step, width, fill, start='center'):
    for _ in range(count):
        if start == 'center':
            x, y = w / 2 + random.uniform(-w * 0.05, w * 0.05), h / 2 + random.uniform(-h * 0.05, h * 0.05)
            ang = random.uniform(0, math.tau)
        else:
            x, y = random.uniform(0, w), random.uniform(0, h)
            ang = random.uniform(0, math.tau)
        for i in range(length):
            ang += random.uniform(-0.6, 0.6)
            nx, ny = x + math.cos(ang) * step, y + math.sin(ang) * step
            wd = max(1, int(width * (1 - i / length)))
            d.line([(x, y), (nx, ny)], fill=fill, width=wd)
            if random.random() < 0.08:
                crack_walks(d, w, h, 1, length // 3, step * 0.7, width * 0.6, fill, start='here' if False else 'center') if False else None
            x, y = nx, ny


def burst_rays(d, w, h, count, inner, fill, width_range=(1, 5), jitter=0.4):
    cx, cy = w / 2, h / 2
    far = math.hypot(w, h)
    for i in range(count):
        a = i / count * math.tau + random.uniform(-jitter, jitter) / count
        wd = random.randint(*width_range)
        d.line([(cx + math.cos(a) * inner, cy + math.sin(a) * inner), (cx + math.cos(a) * far, cy + math.sin(a) * far)], fill=fill, width=wd)


# ----------------------------------------------------------------------------- impact frames
def frame_frost(w, h, variant):
    r, ang = radial(h, w)
    base = grad(h, w, (236, 250, 255), (120, 200, 240), np.clip(r * 1.1, 0, 1))
    frost = fbm(h, w, 6, 6, seed=10 + variant)
    base *= (0.85 + 0.3 * frost)[..., None]
    cracks = lines_layer(w, h, lambda d: crack_walks(d, w, h, 18 + variant * 6, 60, h / 40, 9, 255), 0)
    glow = blur(cracks, h / 60)
    rays = lines_layer(w, h, lambda d: burst_rays(d, w, h, 40 + variant * 20, h * 0.06, 255, (1, 4)), h / 300) if variant == 2 else 0
    cyan = np.array([0.25, 0.85, 1.0], np.float32)
    white = np.array([1, 1, 1], np.float32)
    img = base * (1 - glow[..., None] * 0.8) + cyan[None, None, :] * glow[..., None] * 0.8
    img = img * (1 - cracks[..., None]) + white[None, None, :] * cracks[..., None]
    img = img * (1 - np.asarray(rays)[..., None] * 0.5) + white * np.asarray(rays)[..., None] * 0.5 if variant == 2 else img
    img *= vignette(h, w, 0.35)[..., None] * grain(h, w, 0.02)[..., None]
    return to_img(img)


def frame_umbral(w, h, variant):
    noise = fbm(h, w, 3, 5, seed=20 + variant)
    iso = np.abs(((noise * 14) % 1.0) - 0.5)
    contours = np.clip(1 - iso / 0.035, 0, 1)
    r, ang = radial(h, w)
    rings = np.clip(1 - np.abs(((r * 9 + variant * 0.3) % 1.0) - 0.5) / 0.03, 0, 1) * (r < 0.95)
    eye = np.clip(1 - np.abs(r - 0.12) / 0.01, 0, 1)
    layer = np.maximum(contours * (0.5 + 0.5 * (r > 0.2)), rings * 0.9 if variant != 1 else contours)
    layer = np.maximum(layer, eye)
    glow = blur(layer, h / 120) * 0.5
    img = np.zeros((h, w, 3), np.float32)
    white = np.array([1, 1, 1], np.float32)
    violet = np.array([0.55, 0.3, 0.9], np.float32)
    img += glow[..., None] * violet[None, None, :]
    img = img * (1 - layer[..., None]) + white * layer[..., None]
    img *= grain(h, w, 0.03)[..., None]
    return to_img(img)


def voronoi_edges(h, w, seeds, scale=4):
    sh, sw = h // scale, w // scale
    pts = rng.random((seeds, 2), dtype=np.float32) * np.array([sw, sh], np.float32)
    y, x = np.mgrid[0:sh, 0:sw].astype(np.float32)
    d = np.full((sh, sw), 1e9, np.float32)
    d2 = np.full((sh, sw), 1e9, np.float32)
    for px, py in pts:
        dist = np.sqrt((x - px) ** 2 + (y - py) ** 2)
        closer = dist < d
        d2 = np.where(closer, d, np.minimum(d2, dist))
        d = np.where(closer, dist, d)
    edge = np.clip(1 - (d2 - d) / 1.6, 0, 1)
    im = Image.fromarray((edge * 255).astype(np.uint8), 'L').resize((w, h), Image.BILINEAR)
    return np.asarray(im, np.float32) / 255


def frame_glass(w, h, variant):
    r, ang = radial(h, w)
    base = grad(h, w, (255, 250, 236), (240, 215, 150), np.clip(r, 0, 1))
    edges = voronoi_edges(h, w, 60 + variant * 40)
    rays = lines_layer(w, h, lambda d: burst_rays(d, w, h, 90, h * 0.02, 255, (1, 3)), h / 200)
    gold = np.array([0.8, 0.62, 0.2], np.float32)
    white = np.array([1, 1, 1], np.float32)
    img = base * (1 - rays[..., None] * 0.35) + white * rays[..., None] * 0.35
    img = img * (1 - edges[..., None]) + gold * edges[..., None]
    glow = blur(edges, h / 150)
    img += glow[..., None] * np.array([0.3, 0.2, 0.0], np.float32)
    if variant == 2:
        flare = np.clip(1 - r / 0.35, 0, 1) ** 2
        img = img * (1 - flare[..., None]) + white * flare[..., None]
    img *= vignette(h, w, 0.3)[..., None] * grain(h, w, 0.02)[..., None]
    return to_img(img)


def frame_ember(w, h, variant):
    y = np.linspace(0, 1, h, dtype=np.float32)[:, None]
    turb = fbm(h, w, 5, 6, seed=30 + variant)
    flame = np.clip((y * 1.4 - 0.25) + (turb - 0.5) * 0.9, 0, 1)
    flame = flame ** 1.6
    black = np.array([0.03, 0.0, 0.0], np.float32)
    red = np.array([0.85, 0.08, 0.02], np.float32)
    orange = np.array([1.0, 0.55, 0.1], np.float32)
    img = black[None, None, :] * (1 - flame[..., None]) + red * flame[..., None]
    hot = np.clip((flame - 0.6) / 0.4, 0, 1)
    img = img * (1 - hot[..., None]) + orange * hot[..., None]
    rays = lines_layer(w, h, lambda d: burst_rays(d, w, h, 60, h * 0.05, 255, (1, 6)), h / 250)
    img = img * (1 - rays[..., None] * 0.6) + red * rays[..., None] * 0.6
    if variant == 1:
        flare = np.clip(1 - radial(h, w)[0] / 0.4, 0, 1) ** 2
        img = img * (1 - flare[..., None]) + np.array([1, 0.9, 0.7], np.float32) * flare[..., None]
    img *= grain(h, w, 0.03)[..., None]
    return to_img(img)


def frame_stasis(w, h, variant):
    r, ang = radial(h, w)
    base = grad(h, w, (62, 62, 66), (28, 28, 30), np.clip(r, 0, 1))
    rings = np.clip(1 - np.abs(((r * 6) % 1.0) - 0.5) / 0.02, 0, 1) * (r < 0.8)
    ticks = lines_layer(w, h, lambda d: burst_rays(d, w, h, 60, h * 0.3, 255, (1, 2)), 0) * (r < 0.36)[...] * (r > 0.3)
    hands = lines_layer(w, h, lambda d: (d.line([(w / 2, h / 2), (w / 2, h / 2 - h * 0.3)], fill=255, width=int(h / 90)),
                                          d.line([(w / 2, h / 2), (w / 2 + h * 0.2 * math.cos(0.4 + variant), h / 2 + h * 0.2 * math.sin(0.4 + variant))], fill=255, width=int(h / 70))), 0)
    layer = np.maximum(np.maximum(rings * 0.8, ticks), hands)
    halftone = ((np.mgrid[0:h, 0:w][0] // max(1, h // 180) + np.mgrid[0:h, 0:w][1] // max(1, h // 180)) % 2) * 0.05
    light = np.array([0.92, 0.92, 0.95], np.float32)
    img = base * (1 - layer[..., None]) + light * layer[..., None]
    img *= (1 - halftone)[..., None] * grain(h, w, 0.06)[..., None]
    if variant == 1:
        img = 1 - img
    return to_img(img)


def frame_collapse(w, h, variant):
    r, ang = radial(h, w)
    invert = variant == 1
    speed = lines_layer(w, h, lambda d: burst_rays(d, w, h, 160, h * 0.08, 255, (1, 7), jitter=1.5), 0)
    cracks = lines_layer(w, h, lambda d: crack_walks(d, w, h, 10, 50, h / 30, 7, 255), 0)
    stars = (rng.random((h, w), dtype=np.float32) > 0.9993).astype(np.float32)
    stars = blur(stars, 1.2) * 6
    core = np.clip(1 - r / 0.1, 0, 1)
    layer = np.clip(np.maximum(speed, cracks) + core, 0, 1)
    img = np.ones((h, w, 3), np.float32)
    img = img * (1 - layer[..., None])
    img += stars[..., None] * 0.6
    img *= vignette(h, w, 0.5, 1.5)[..., None]
    if invert:
        img = 1 - img
    img *= grain(h, w, 0.02)[..., None]
    return to_img(img)


FRAMES = {
    'frost_vault': frame_frost, 'umbral_sea': frame_umbral, 'glass_heaven': frame_glass,
    'ember_garden': frame_ember, 'stasis': frame_stasis, 'structural_collapse': frame_collapse,
}


# ----------------------------------------------------------------------------- scene textures
def sphere_skin(w, h, c0, c1, c2, cells=3, stars=0, cracks=False, seed=0, streaks=False):
    n = tileable_fbm(h, w, cells, 6, seed=seed)
    t = np.clip((n - 0.3) / 0.45, 0, 1)
    img = grad(h, w, c0, c1, t)
    hi = np.clip((n - 0.62) / 0.2, 0, 1) ** 1.5
    img = img * (1 - hi[..., None]) + (np.asarray(c2, np.float32) / 255) * hi[..., None]
    if streaks:
        s = tileable_fbm(h, w, 24, 2, seed=seed + 50)
        img *= (0.85 + 0.3 * s)[..., None]
    if stars:
        st = (rng.random((h, w), dtype=np.float32) > 1 - stars / (w * h)).astype(np.float32)
        big = blur(st, 1.5) * 5
        img = np.clip(img + big[..., None], 0, 1)
    if cracks:
        cr = lines_layer(w, h, lambda d: crack_walks(d, w, h, 40, 30, h / 25, 3, 255, start='rand'), 0)
        img = img * (1 - cr[..., None] * 0.9) + cr[..., None] * 0.9
    return to_img(img)


def planet_tex(w, h, bands, turb=0.4, cells=3, spots=0, craters=0, polar=None, seed=0):
    y = np.linspace(0, 1, h, dtype=np.float32)[:, None].repeat(w, 1)
    n = tileable_fbm(h, w, cells, 5, seed=seed)
    yy = np.clip(y + (n - 0.5) * turb, 0, 1)
    img = np.zeros((h, w, 3), np.float32)
    k = len(bands)
    pos = yy * (k - 1)
    i0 = np.clip(np.floor(pos).astype(int), 0, k - 2)
    f = pos - i0
    cols = np.asarray(bands, np.float32) / 255
    img = cols[i0] * (1 - f[..., None]) + cols[i0 + 1] * f[..., None]
    detail = tileable_fbm(h, w, 12, 3, seed=seed + 7)
    img *= (0.88 + 0.24 * detail)[..., None]
    if spots:
        for _ in range(spots):
            cx, cy, rr = rng.random() * w, h * (0.3 + 0.4 * rng.random()), w * (0.03 + 0.05 * rng.random())
            d = np.sqrt(((np.arange(w)[None, :] - cx) * 1.0) ** 2 + ((np.arange(h)[:, None] - cy) * 1.8) ** 2)
            m = np.clip(1 - d / rr, 0, 1) ** 0.7
            img = img * (1 - m[..., None] * 0.6) + np.asarray(bands[-1], np.float32) / 255 * 0.9 * m[..., None] * 0.6
    if craters:
        for _ in range(craters):
            cx, cy, rr = rng.random() * w, rng.random() * h, w * (0.01 + 0.03 * rng.random())
            d = np.sqrt((np.arange(w)[None, :] - cx) ** 2 + (np.arange(h)[:, None] - cy) ** 2)
            rim = np.clip(1 - np.abs(d - rr) / (rr * 0.3), 0, 1)
            floor = np.clip(1 - d / rr, 0, 1)
            img *= (1 - floor * 0.35 + rim * 0.25)[..., None]
    if polar:
        p = np.clip((np.abs(y - 0.5) - 0.38) / 0.12, 0, 1)
        img = img * (1 - p[..., None]) + np.asarray(polar, np.float32) / 255 * p[..., None]
    return to_img(np.clip(img, 0, 1))


def earth_tex(w, h):
    n = tileable_fbm(h, w, 3, 7, seed=99)
    land = np.clip((n - 0.52) / 0.06, 0, 1)
    y = np.linspace(0, 1, h, dtype=np.float32)[:, None]
    ocean = grad(h, w, (12, 50, 140), (30, 110, 200), np.clip(1 - np.abs(y - 0.5) * 2, 0, 1).repeat(w, 1))
    veg = tileable_fbm(h, w, 8, 4, seed=5)
    landc = grad(h, w, (60, 120, 50), (150, 130, 80), veg)
    img = ocean * (1 - land[..., None]) + landc * land[..., None]
    ice = np.clip((np.abs(y - 0.5) - 0.4) / 0.08, 0, 1).repeat(w, 1)
    img = img * (1 - ice[..., None]) + ice[..., None] * 0.95
    clouds = np.clip((tileable_fbm(h, w, 5, 5, seed=42) - 0.5) / 0.25, 0, 1)
    img = img * (1 - clouds[..., None] * 0.8) + clouds[..., None] * 0.8
    return to_img(img)


def sun_tex(w, h):
    g = tileable_fbm(h, w, 10, 5, seed=3)
    img = grad(h, w, (255, 150, 20), (255, 240, 170), g)
    dark = tileable_fbm(h, w, 3, 3, seed=4)
    img *= (0.8 + 0.3 * dark)[..., None]
    return to_img(np.clip(img, 0, 1))


def disc(size, inner, outer, color, soft=0.1, rays=0, bands=False, alpha_pow=1.0):
    r, ang = radial(size, size, aspect=False)
    r = r * 2
    a = np.clip((r - inner) / max(soft, 1e-3), 0, 1) * np.clip((outer - r) / max(soft, 1e-3), 0, 1)
    if inner == 0:
        a = np.clip((outer - r) / max(soft, 1e-3), 0, 1)
    a = a ** alpha_pow
    if rays:
        a *= 0.55 + 0.45 * np.abs(np.sin(ang * rays + r * 6))
    if bands:
        a *= 0.6 + 0.4 * np.sin(r * 90) ** 2
    rgb = np.ones((size, size, 3), np.float32) * (np.asarray(color, np.float32) / 255)
    return to_img(rgb, a)


def shard(size, seed):
    random.seed(seed)
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    pts = [(size / 2 + math.cos(a) * size * random.uniform(0.2, 0.48), size / 2 + math.sin(a) * size * random.uniform(0.2, 0.48)) for a in np.linspace(0, math.tau, 6, endpoint=False)]
    d.polygon(pts, fill=(235, 245, 255, 190), outline=(255, 255, 255, 255))
    d.line([pts[0], pts[3]], fill=(255, 255, 255, 120), width=max(1, size // 64))
    return im


def petal(size):
    r, ang = radial(size, size, aspect=False)
    shape = np.clip(1 - (r * 2.2) ** 2 - 0.4 * np.sin(ang * 2) ** 2, 0, 1)
    a = np.clip(shape * 2, 0, 1)
    rgb = grad(size, size, (255, 200, 90), (255, 80, 20), np.clip(r * 2, 0, 1))
    return to_img(rgb, a)


def clock_face(size):
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = size / 2
    d.ellipse([size * 0.04, size * 0.04, size * 0.96, size * 0.96], outline=(220, 220, 230, 255), width=max(2, size // 60))
    d.ellipse([size * 0.1, size * 0.1, size * 0.9, size * 0.9], outline=(180, 180, 190, 160), width=max(1, size // 120))
    for i in range(60):
        a = i / 60 * math.tau
        ln = size * (0.08 if i % 5 == 0 else 0.04)
        d.line([(c + math.cos(a) * (c * 0.9 - ln), c + math.sin(a) * (c * 0.9 - ln)), (c + math.cos(a) * c * 0.9, c + math.sin(a) * c * 0.9)],
               fill=(230, 230, 240, 255), width=max(1, size // (80 if i % 5 == 0 else 200)))
    d.line([(c, c), (c, c - size * 0.35)], fill=(255, 255, 255, 255), width=max(2, size // 50))
    d.line([(c, c), (c + size * 0.22, c + size * 0.12)], fill=(255, 255, 255, 255), width=max(2, size // 40))
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--frames', type=int, default=1440, help='impact frame height: 1080, 1440 (default) or 2160')
    args = ap.parse_args()
    fh = args.frames
    fw = fh * 16 // 9

    # impact frames
    for domain, fn in FRAMES.items():
        for i in range(3):
            random.seed(100 + i)
            save(fn(fw, fh, i), 'impact', domain, f'frame_{i + 1:02d}.png')
        print('frames', domain)
    with open(os.path.join(ROOT, 'impact_frames.json'), 'w') as f:
        json.dump({'width': fw, 'height': fh}, f)

    # sphere skins (equirectangular, tileable horizontally)
    W, H = 1024, 512
    save(sphere_skin(W, H, (90, 90, 140), (170, 170, 230), (255, 255, 255), seed=1), 'scene', 'sphere_default.png')
    save(sphere_skin(W, H, (150, 215, 255), (225, 245, 255), (255, 255, 255), cracks=True, seed=2), 'scene', 'sphere_frost_vault.png')
    save(sphere_skin(W, H, (2, 2, 8), (14, 8, 40), (60, 30, 110), cells=2, seed=3, streaks=True), 'scene', 'sphere_umbral_sea.png')
    save(sphere_skin(W, H, (255, 245, 215), (255, 252, 240), (255, 255, 255), cracks=True, seed=4), 'scene', 'sphere_glass_heaven.png')
    save(sphere_skin(W, H, (120, 20, 5), (255, 110, 30), (255, 230, 150), cells=4, seed=5, streaks=True), 'scene', 'sphere_ember_garden.png')
    save(sphere_skin(W, H, (90, 90, 95), (170, 170, 175), (230, 230, 235), seed=6, streaks=True), 'scene', 'sphere_stasis.png')
    save(sphere_skin(W, H, (1, 1, 6), (8, 6, 30), (40, 30, 90), cells=2, stars=900, seed=8), 'scene', 'sphere_structural_collapse.png')
    save(sphere_skin(W, H, (0, 0, 3), (4, 4, 16), (20, 20, 60), cells=2, stars=1400, seed=9), 'scene', 'sky_space.png')
    save(to_img(np.zeros((H, W, 3), np.float32)), 'scene', 'sky_none.png')

    # planets
    P = 512
    save(planet_tex(P, P // 2, [(110, 105, 100), (150, 145, 140), (90, 85, 80), (140, 135, 130)], 0.2, craters=90, seed=11), 'scene', 'planet_mercury.png')
    save(planet_tex(P, P // 2, [(235, 205, 150), (250, 225, 175), (210, 175, 120), (240, 215, 160)], 0.5, cells=2, seed=12), 'scene', 'planet_venus.png')
    save(earth_tex(P, P // 2), 'scene', 'planet_earth.png')
    save(planet_tex(P, P // 2, [(200, 95, 55), (165, 75, 45), (215, 120, 70), (150, 70, 40)], 0.3, craters=40, polar=(240, 235, 230), seed=13), 'scene', 'planet_mars.png')
    save(planet_tex(P, P // 2, [(225, 200, 160), (180, 125, 90), (245, 220, 180), (160, 110, 80), (230, 205, 170), (190, 140, 100)], 0.12, cells=6, spots=1, seed=14), 'scene', 'planet_jupiter.png')
    save(planet_tex(P, P // 2, [(230, 210, 160), (205, 185, 135), (240, 225, 180), (215, 195, 150)], 0.1, cells=6, seed=15), 'scene', 'planet_saturn.png')
    save(planet_tex(P, P // 2, [(150, 220, 230), (130, 205, 220), (165, 230, 235)], 0.15, cells=2, seed=16), 'scene', 'planet_uranus.png')
    save(planet_tex(P, P // 2, [(45, 75, 220), (30, 55, 180), (70, 110, 240), (35, 60, 190)], 0.25, cells=3, spots=1, seed=17), 'scene', 'planet_neptune.png')
    save(planet_tex(P, P // 2, [(150, 150, 170), (120, 120, 140), (170, 170, 190)], 0.3, craters=30, seed=18), 'scene', 'planet_generic.png')
    save(sun_tex(P, P // 2), 'scene', 'sun.png')
    save(sphere_skin(P, P // 2, (235, 240, 255), (255, 255, 255), (255, 255, 255), cells=6, seed=19), 'scene', 'white_hole.png')

    # sprites
    S = 512
    save(disc(S, 0.55, 0.98, (225, 205, 160), soft=0.04, bands=True), 'scene', 'ring_saturn.png')
    save(disc(S, 0.0, 1.0, (255, 255, 255), soft=0.9, alpha_pow=2.2), 'scene', 'glow_soft.png')
    save(disc(S, 0.0, 1.0, (255, 225, 140), soft=1.0, rays=18, alpha_pow=1.6), 'scene', 'sun_corona.png')
    save(disc(S, 0.0, 1.0, (210, 230, 255), soft=1.0, rays=12, alpha_pow=1.3), 'scene', 'white_hole_rays.png')
    save(disc(S, 0.82, 1.0, (255, 255, 255), soft=0.08), 'scene', 'shockwave.png')
    save(disc(S, 0.0, 1.0, (255, 255, 255), soft=0.5, alpha_pow=3.0), 'scene', 'flash_white.png')
    save(clock_face(S), 'scene', 'clock_face.png')
    save(shard(S, 1), 'scene', 'glass_shard.png')
    save(petal(S), 'scene', 'ember_petal.png')
    save(disc(S, 0.0, 1.0, (8, 4, 20), soft=0.6, alpha_pow=1.2), 'scene', 'shadow_blob.png')
    ice = shard(S, 2)
    save(ice, 'scene', 'ice_crystal.png')
    print('scene textures done')


if __name__ == '__main__':
    main()
