#!/usr/bin/env python3
"""Animated, tileable ground textures for Ember Garden (scorched + molten ground).

Usage: python3 tools/generate_ground.py
Writes 64x64 block textures with 8 animation frames (vertical strip + .mcmeta, interpolated)
under assets/skysdomains/textures/block. Replace 1:1 with hand-made art if you like.
"""
import json, math, os
import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'skysdomains')
TEX = os.path.join(ROOT, 'textures', 'block')
SIZE = 64
FRAMES = 8


def torus_noise(size, cells, seed, octaves=4):
    """Seamlessly tileable value noise: random grid wrapped on both axes, bilinear upsampled."""
    r = np.random.default_rng(seed)
    total = np.zeros((size, size), np.float32)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        c = cells * 2 ** o
        grid = r.random((c, c), dtype=np.float32)
        y, x = np.mgrid[0:size, 0:size].astype(np.float32) * c / size
        x0, y0 = np.floor(x).astype(int), np.floor(y).astype(int)
        fx, fy = x - x0, y - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        g = lambda yy, xx: grid[yy % c, xx % c]
        v = (g(y0, x0) * (1 - fx) * (1 - fy) + g(y0, x0 + 1) * fx * (1 - fy)
             + g(y0 + 1, x0) * (1 - fx) * fy + g(y0 + 1, x0 + 1) * fx * fy)
        total += amp * v
        norm += amp
        amp *= 0.5
    return total / norm


def crack_field(size, points, seed):
    """Toroidal Voronoi: returns (F2-F1 edge distance, index of nearest seed)."""
    r = np.random.default_rng(seed)
    seeds = r.random((points, 2), dtype=np.float32) * size
    y, x = np.mgrid[0:size, 0:size].astype(np.float32)
    d = np.full((points, size, size), 1e9, np.float32)
    for i, (sx, sy) in enumerate(seeds):
        dx = np.abs(x - sx)
        dy = np.abs(y - sy)
        dx = np.minimum(dx, size - dx)
        dy = np.minimum(dy, size - dy)
        d[i] = np.sqrt(dx * dx + dy * dy)
    order = np.sort(d, axis=0)
    nearest = np.argmin(d, axis=0)
    return order[1] - order[0], nearest


def ground(kind):
    rock = torus_noise(SIZE, 4, 11 if kind == 'scorched' else 23)
    fine = torus_noise(SIZE, 16, 5 if kind == 'scorched' else 29, octaves=2)
    if kind == 'scorched':
        edge, cell = crack_field(SIZE, 14, 3)
        width, glow_reach = 1.1, 4.5
        base_dark = np.array([0.07, 0.05, 0.05])
        base_light = np.array([0.22, 0.17, 0.15])
        hot = np.array([1.0, 0.55, 0.12])
        core = np.array([1.0, 0.92, 0.55])
        glow_amount = 1.0
    else:
        edge, cell = crack_field(SIZE, 9, 17)
        width, glow_reach = 2.6, 7.0
        base_dark = np.array([0.10, 0.03, 0.02])
        base_light = np.array([0.26, 0.09, 0.05])
        hot = np.array([1.0, 0.42, 0.06])
        core = np.array([1.0, 0.85, 0.45])
        glow_amount = 1.3
    rng = np.random.default_rng(99)
    phase = rng.random(cell.max() + 1, dtype=np.float32) * 2 * math.pi
    phase_map = phase[cell]
    crack = np.clip(1.0 - edge / width, 0, 1) ** 1.3           # 1 in the crack centre
    glow = np.clip(1.0 - edge / glow_reach, 0, 1) ** 2.0        # soft halo around cracks
    rockmix = rock * 0.7 + fine * 0.3
    frames = []
    for f in range(FRAMES):
        t = f / FRAMES * 2 * math.pi
        pulse = 0.72 + 0.28 * np.sin(t + phase_map)              # per-cell breathing glow
        pulse = pulse[..., None]
        base = base_dark + (base_light - base_dark) * rockmix[..., None]
        heat = (glow * glow_amount)[..., None] * pulse
        col = base * (1 - heat * 0.9) + hot * heat
        c = crack[..., None]
        col = col * (1 - c) + (hot + (core - hot) * c * pulse) * c
        frames.append(np.clip(col, 0, 1))
    strip = np.concatenate(frames, axis=0)
    img = Image.fromarray((strip * 255).astype(np.uint8), 'RGB').convert('RGBA')
    os.makedirs(TEX, exist_ok=True)
    name = f'{kind}_ground'
    img.save(os.path.join(TEX, name + '.png'), optimize=True)
    with open(os.path.join(TEX, name + '.png.mcmeta'), 'w') as fh:
        json.dump({"animation": {"frametime": 5, "interpolate": True}}, fh, indent=2)
        fh.write('\n')
    print('wrote', name, img.size)


if __name__ == '__main__':
    ground('scorched')
    ground('molten')
