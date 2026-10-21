#!/usr/bin/env python3
"""
Erzeugt die Texturen und Sounds des Lichtschwerts prozedural (keine fremden Assets).

Benötigt: Python 3, numpy, Pillow und ffmpeg (mit libvorbis) im PATH.
Aufruf:   python3 tools/generate_assets.py
"""
import os
import subprocess
import tempfile
import wave

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "swmod")
TEXTURES = os.path.join(ASSETS, "textures", "item")
SOUNDS = os.path.join(ASSETS, "sounds", "lightsaber")
FORCE_ICONS = os.path.join(ASSETS, "textures", "gui", "sprites", "force")
PARTICLE_TEXTURES = os.path.join(ASSETS, "textures", "particle")
ENTITY_TEXTURES = os.path.join(ASSETS, "textures", "entity")
FORCE_SOUNDS = os.path.join(ASSETS, "sounds", "force")
HOLOCRON_SOUNDS = os.path.join(ASSETS, "sounds", "holocron")

SR = 44100
rng = np.random.default_rng(1977)


# =============================================================================================
# Audio-Bausteine
# =============================================================================================

def samples(seconds):
    return int(SR * seconds)


def t_axis(n):
    return np.arange(n) / SR


def saw(phase, harmonics=16, rolloff=1.15):
    """Bandbegrenzter Sägezahn aus der Phase (in Perioden)."""
    out = np.zeros_like(phase)
    for k in range(1, harmonics + 1):
        out += np.sin(2 * np.pi * k * phase) / (k ** rolloff)
    return out * 0.6


def phase_of(freq):
    """Phase (in Perioden) aus einem Frequenzverlauf (Hz pro Sample)."""
    return np.cumsum(freq) / SR


def noise_band(n, lo, hi, periodic=False):
    """Rauschen in einem Frequenzband. periodic=True ergibt eine nahtlose Schleife."""
    freqs = np.fft.rfftfreq(n, 1 / SR)
    if periodic:
        spectrum = np.zeros(len(freqs), complex)
        band = (freqs >= lo) & (freqs <= hi)
        spectrum[band] = np.exp(1j * rng.uniform(0, 2 * np.pi, band.sum()))
    else:
        spectrum = np.fft.rfft(rng.standard_normal(n))
        spectrum[(freqs < lo) | (freqs > hi)] = 0
    x = np.fft.irfft(spectrum, n)
    return x / (np.abs(x).max() + 1e-9)


def crackle(n, rate_start, rate_end, lo=900, hi=9000):
    """Elektrisches Knistern: zufällige Impulse, deren Dichte abnimmt."""
    rate = np.linspace(rate_start, rate_end, n) / SR
    impulses = (rng.random(n) < rate) * rng.uniform(0.4, 1.0, n) * rng.choice([-1, 1], n)
    kernel = np.exp(-np.arange(int(0.004 * SR)) / (0.0008 * SR))
    x = np.convolve(impulses, kernel)[:n]
    spectrum = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(n, 1 / SR)
    spectrum[(freqs < lo) | (freqs > hi)] = 0
    x = np.fft.irfft(spectrum, n)
    return x / (np.abs(x).max() + 1e-9)


def decay(n, seconds):
    return np.exp(-t_axis(n) / seconds)


def bell(n, peak=0.4, width=0.22):
    t = np.linspace(0, 1, n)
    return np.exp(-((t - peak) / width) ** 2)


def fade(x, fade_in=0.004, fade_out=0.02):
    n = len(x)
    a = min(n, samples(fade_in))
    b = min(n, samples(fade_out))
    x = x.copy()
    if a > 0:
        x[:a] *= np.linspace(0, 1, a)
    if b > 0:
        x[-b:] *= np.linspace(1, 0, b)
    return x


def soft_clip(x, drive=1.6):
    return np.tanh(x * drive) / np.tanh(drive)


def hum_voice(freq, buzz=0.12):
    """Der Kern des Lichtschwertklangs: zwei leicht verstimmte Motor-Brummer plus Bildröhren-Summen."""
    n = len(freq)
    a = saw(phase_of(freq), 16)
    b = saw(phase_of(freq * 1.0054), 10)
    c = np.sin(2 * np.pi * phase_of(freq * 1.5))
    x = 0.55 * a + 0.35 * b + 0.14 * c
    x += buzz * noise_band(n, 1800, 5200)
    return soft_clip(x, 1.3)


def metal_ring(n, partials, decays, amps):
    t = t_axis(n)
    x = np.zeros(n)
    for f, d, a in zip(partials, decays, amps):
        x += a * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) * np.exp(-t / d)
    return x


def normalize(x, peak=0.89):
    return x / (np.abs(x).max() + 1e-9) * peak


def write_ogg(name, x, folder=SOUNDS):
    os.makedirs(folder, exist_ok=True)
    data = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, name + ".wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(data.tobytes())
        out = os.path.join(folder, name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-ac", "1",
                        "-c:a", "libvorbis", "-q:a", "6", out], check=True)
    print("  sound:", name)


# =============================================================================================
# Sounds
# =============================================================================================

def sound_hum():
    # Exakt 2 s mit ganzzahligen Perioden -> nahtlose Schleife.
    n = samples(2.0)
    t = t_axis(n)
    f = 92.0
    x = (0.55 * saw(f * t, 16) + 0.35 * saw(92.5 * t, 10) + 0.14 * np.sin(2 * np.pi * 138.0 * t)
         + 0.11 * noise_band(n, 1800, 5200, periodic=True))
    x = soft_clip(x, 1.3)
    x *= 1.0 + 0.06 * np.sin(2 * np.pi * 0.5 * t) + 0.03 * np.sin(2 * np.pi * 1.5 * t + 1.0)
    return normalize(x, 0.7)


def sound_ignite():
    n = samples(1.0)
    t = t_axis(n)
    sweep = 30 * (92 / 30) ** np.clip(t / 0.28, 0, 1)
    body = hum_voice(sweep) * np.clip(t / 0.05, 0, 1)
    body *= np.where(t > 0.75, np.clip(1 - (t - 0.75) / 0.25, 0, 1), 1.0)
    hiss = noise_band(n, 1500, 7500) * decay(n, 0.12) * 0.55
    snap = noise_band(n, 2500, 9000) * decay(n, 0.012) * 1.2
    whoom = np.sin(2 * np.pi * phase_of(np.linspace(520, 140, n))) * decay(n, 0.18) * 0.5
    return normalize(fade(body * 0.9 + hiss + snap + whoom, 0.001, 0.05))


def sound_retract():
    n = samples(0.8)
    t = t_axis(n)
    sweep = 92 * (24 / 92) ** np.clip(t / 0.6, 0, 1)
    body = hum_voice(sweep) * np.clip(1 - t / 0.7, 0, 1) ** 1.5
    hiss = noise_band(n, 1200, 6000) * bell(n, 0.35, 0.25) * 0.35
    click = noise_band(n, 2000, 8000) * np.exp(-np.abs(t - 0.68) / 0.006) * 0.6
    return normalize(fade(body + hiss + click))


def sound_swing(peak_pitch, peak_at, length):
    n = samples(length)
    shape = bell(n, peak_at, 0.2)
    freq = 92 + (peak_pitch - 92) * shape
    body = hum_voice(freq, buzz=0.18) * (0.25 + 0.75 * shape)
    whoosh = noise_band(n, 250, 2600) * bell(n, peak_at, 0.16) * 0.45
    return normalize(fade(body + whoosh, 0.01, 0.06), 0.8)


def sound_clash(ring_base, length=0.7, heavy=1.0):
    n = samples(length)
    crack = crackle(n, 9000, 300) * decay(n, 0.22) * 0.9
    ring = metal_ring(n, [ring_base, ring_base * 1.52, ring_base * 2.21, ring_base * 2.93, ring_base * 4.3],
                      [0.18, 0.14, 0.11, 0.08, 0.05], [0.6, 0.45, 0.35, 0.25, 0.15])
    burst = hum_voice(np.full(n, 105.0), buzz=0.4) * decay(n, 0.09) * 1.4 * heavy
    thump = np.sin(2 * np.pi * 70 * t_axis(n)) * decay(n, 0.05) * 0.7 * heavy
    snap = noise_band(n, 1500, 10000) * decay(n, 0.015) * 1.1
    return normalize(fade(soft_clip(crack + ring * 0.6 + burst + thump + snap, 1.4), 0.0005, 0.05))


def sound_block(ring_base):
    return sound_clash(ring_base, 0.45, 0.7)


def sound_parry():
    n = samples(1.0)
    base = np.zeros(n)
    clash = sound_clash(1250, 1.0, 1.1)
    shine = metal_ring(n, [2900, 4400, 6100, 7300], [0.55, 0.45, 0.35, 0.25], [0.5, 0.4, 0.3, 0.2])
    zing = np.sin(2 * np.pi * phase_of(np.linspace(1800, 4200, n))) * bell(n, 0.12, 0.1) * 0.35
    base += clash * 0.8 + shine * 0.5 + zing
    return normalize(fade(base, 0.0005, 0.08))


def sound_guard_break():
    n = samples(0.9)
    t = t_axis(n)
    clash = sound_clash(900, 0.9, 1.5)
    boom = np.sin(2 * np.pi * phase_of(np.linspace(60, 35, n))) * decay(n, 0.25) * 0.9
    fall = np.sin(2 * np.pi * phase_of(600 * (200 / 600) ** (t / 0.9))) * decay(n, 0.35) * 0.35
    return normalize(fade(soft_clip(clash + boom + fall, 1.3), 0.0005, 0.08))


def sound_deflect(start, end):
    n = samples(0.38)
    pew = np.sin(2 * np.pi * phase_of(np.geomspace(start, end, n))) * decay(n, 0.09)
    crack = crackle(n, 6000, 200) * decay(n, 0.06) * 0.6
    swell = hum_voice(np.full(n, 110.0), buzz=0.25) * bell(n, 0.25, 0.3) * 0.5
    return normalize(fade(pew + crack + swell, 0.0005, 0.04))


def sound_hit(seed_shift):
    n = samples(0.6)
    sizzle = noise_band(n, 1200, 9500) * decay(n, 0.22) * 0.7
    crack = crackle(n, 5000 + seed_shift, 400) * decay(n, 0.3) * 0.7
    burst = hum_voice(np.full(n, 98.0 + seed_shift * 0.002), buzz=0.35) * decay(n, 0.12) * 0.9
    return normalize(fade(soft_clip(sizzle + crack + burst, 1.5), 0.0005, 0.05))


def sound_throw():
    n = samples(0.6)
    shape = bell(n, 0.35, 0.25)
    body = hum_voice(92 + 45 * shape, buzz=0.2) * (0.3 + 0.7 * shape)
    whoosh = noise_band(n, 200, 2200) * bell(n, 0.3, 0.2) * 0.6
    return normalize(fade(body + whoosh, 0.01, 0.06), 0.85)


def sound_spin():
    # 0,5 s, ganzzahlige Perioden für nahtloses "Wumm-Wumm-Wumm".
    n = samples(0.5)
    t = t_axis(n)
    x = (0.55 * saw(92.0 * t, 16) + 0.35 * saw(94.0 * t, 10) + 0.12 * noise_band(n, 1500, 5000, periodic=True))
    x = soft_clip(x, 1.4)
    am = 0.35 + 0.65 * np.sin(np.pi * 6.0 * t) ** 2
    pitchy = 0.08 * np.sin(2 * np.pi * 6.0 * t) * saw(184.0 * t, 6)
    return normalize(x * am + pitchy, 0.75)


def sound_catch():
    n = samples(0.45)
    t = t_axis(n)
    click = noise_band(n, 2000, 7000) * decay(n, 0.008) * 0.9
    tick = np.sin(2 * np.pi * 3200 * t) * decay(n, 0.03) * 0.5
    swell = hum_voice(np.full(n, 92.0)) * bell(n, 0.3, 0.3) * 0.6
    return normalize(fade(click + tick + swell, 0.0005, 0.06))


def generate_sounds():
    print("Erzeuge Sounds ...")
    write_ogg("hum", sound_hum())
    write_ogg("ignite", sound_ignite())
    write_ogg("retract", sound_retract())
    write_ogg("swing1", sound_swing(150, 0.38, 0.5))
    write_ogg("swing2", sound_swing(132, 0.45, 0.55))
    write_ogg("swing3", sound_swing(170, 0.33, 0.45))
    write_ogg("hit1", sound_hit(0))
    write_ogg("hit2", sound_hit(900))
    write_ogg("clash1", sound_clash(1130))
    write_ogg("clash2", sound_clash(980))
    write_ogg("clash3", sound_clash(1320))
    write_ogg("block1", sound_block(1050))
    write_ogg("block2", sound_block(1210))
    write_ogg("parry", sound_parry())
    write_ogg("guard_break", sound_guard_break())
    write_ogg("deflect1", sound_deflect(2600, 700))
    write_ogg("deflect2", sound_deflect(3100, 900))
    write_ogg("throw", sound_throw())
    write_ogg("spin", sound_spin())
    write_ogg("catch", sound_catch())


# =============================================================================================
# Texturen
# =============================================================================================

def rect(img, x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            img.putpixel((x, y), color)


def hilt_texture():
    """32x32-Textur für das 3D-Griffmodell. 1 UV-Einheit im Modell = 2 Pixel."""
    img = Image.new("RGBA", (32, 32), (120, 122, 128, 255))

    # Knauf (Seiten + Deckel): dunkles Metall
    rect(img, 0, 0, 8, 4, (72, 74, 80, 255))
    rect(img, 0, 0, 8, 1, (110, 112, 118, 255))
    rect(img, 0, 3, 8, 4, (48, 50, 55, 255))
    rect(img, 0, 4, 8, 12, (88, 90, 96, 255))
    rect(img, 2, 6, 6, 10, (52, 54, 60, 255))

    # Griff: schwarz mit Rillen
    rect(img, 8, 0, 14, 14, (24, 24, 27, 255))
    for y in range(1, 14, 2):
        rect(img, 8, y, 14, y + 1, (44, 44, 49, 255))
    rect(img, 9, 0, 10, 14, (36, 36, 40, 255))

    # T-Schienen
    rect(img, 14, 0, 16, 12, (58, 60, 66, 255))
    rect(img, 14, 0, 15, 12, (125, 128, 135, 255))

    # Ring: hell poliert
    rect(img, 16, 0, 24, 2, (212, 215, 222, 255))
    rect(img, 16, 1, 24, 2, (160, 163, 170, 255))

    # Körper: Chrom mit Längsreflex und schwarzem Fenster
    for x in range(16, 24):
        shade = [150, 175, 205, 235, 220, 190, 165, 145][x - 16]
        rect(img, x, 2, x + 1, 10, (shade, shade + 2, shade + 8, 255))
    rect(img, 19, 4, 21, 8, (20, 20, 24, 255))
    rect(img, 16, 9, 24, 10, (110, 112, 120, 255))

    # Schalterkasten + roter Knopf
    rect(img, 24, 0, 28, 6, (46, 46, 52, 255))
    rect(img, 24, 0, 28, 1, (95, 97, 104, 255))
    rect(img, 28, 0, 32, 4, (196, 28, 28, 255))
    rect(img, 28, 0, 30, 2, (255, 120, 110, 255))

    # Emitter: Chrom mit Lüftungsschlitzen
    for x in range(0, 8):
        shade = [165, 190, 220, 238, 225, 200, 175, 158][x]
        rect(img, x, 12, x + 1, 18, (shade, shade + 3, shade + 9, 255))
    for x in (1, 4, 7):
        rect(img, x, 14, x + 1, 17, (30, 30, 35, 255))
    rect(img, 0, 12, 8, 13, (120, 122, 130, 255))

    # Emitter-Öffnung (Deckel): Chromring, dunkles Loch
    rect(img, 8, 16, 16, 24, (200, 203, 210, 255))
    rect(img, 9, 17, 15, 23, (90, 92, 100, 255))
    rect(img, 10, 18, 14, 22, (14, 14, 18, 255))
    return img


def icon_layers():
    """16x16-Inventar-Icon: Griff unten links, Klinge diagonal nach oben rechts."""
    hilt = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    glow = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    core = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    crystal = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    outline = (18, 18, 22, 255)
    colors = [
        ((88, 90, 98, 255), (58, 60, 66, 255)),     # Knauf
        ((30, 30, 34, 255), (50, 50, 56, 255)),     # Griff
        ((30, 30, 34, 255), (50, 50, 56, 255)),     # Griff
        ((214, 217, 224, 255), (150, 153, 160, 255)),  # Ring
        ((228, 231, 238, 255), (160, 163, 172, 255)),  # Emitter
    ]

    # Umriss zuerst, dann die eigentlichen Pixel.
    for i in range(5):
        x, y = 2 + i, 13 - i
        for dx, dy in ((-1, 0), (2, 0), (0, 1), (1, 1), (0, -1), (1, -1)):
            px, py = x + dx, y + dy
            if 0 <= px < 16 and 0 <= py < 16:
                hilt.putpixel((px, py), outline)
    for i, (light, dark) in enumerate(colors):
        x, y = 2 + i, 13 - i
        hilt.putpixel((x, y), light)
        hilt.putpixel((x + 1, y), dark)
    hilt.putpixel((4, 12), (200, 30, 30, 255))  # Knopf

    for x in range(7, 15):
        y = 15 - x
        core.putpixel((x, y), (255, 255, 255, 255))
        for gx, gy, alpha in ((x + 1, y, 230), (x, y + 1, 230), (x - 1, y, 120), (x, y - 1, 120),
                              (x + 1, y + 1, 90), (x - 1, y - 1, 60)):
            if 0 <= gx < 16 and 0 <= gy < 16 and core.getpixel((gx, gy))[3] == 0:
                old = glow.getpixel((gx, gy))[3]
                glow.putpixel((gx, gy), (235, 235, 235, max(old, alpha)))
    # Kern über dem Glühen nicht doppelt einfärben
    for x in range(7, 15):
        glow.putpixel((x, 15 - x), (0, 0, 0, 0))

    crystal.putpixel((7, 8), (235, 235, 235, 255))
    return hilt, glow, core, crystal


def generate_textures():
    print("Erzeuge Texturen ...")
    os.makedirs(TEXTURES, exist_ok=True)
    hilt_texture().save(os.path.join(TEXTURES, "lightsaber_hilt.png"))
    hilt, glow, core, crystal = icon_layers()
    hilt.save(os.path.join(TEXTURES, "lightsaber_icon_hilt.png"))
    glow.save(os.path.join(TEXTURES, "lightsaber_icon_glow.png"))
    core.save(os.path.join(TEXTURES, "lightsaber_icon_core.png"))
    crystal.save(os.path.join(TEXTURES, "lightsaber_icon_crystal.png"))
    print("  textures: lightsaber_hilt, lightsaber_icon_*")


# =============================================================================================
# Machtfähigkeiten: Symbole, Partikel, Aura, Holocron
# =============================================================================================

from PIL import ImageDraw, ImageFilter  # noqa: E402

ICON_SS = 256  # Zeichenauflösung der Symbole, wird auf 32x32 verkleinert

FORCE_COLORS = {
    "push": (0x7C, 0xC8, 0xFF),
    "pull": (0x9A, 0xDF, 0xFF),
    "grab": (0x58, 0xB7, 0xFF),
    "speed": (0xFF, 0xE2, 0x7A),
    "heal": (0x6C, 0xFF, 0x9A),
    "lightning": (0xB9, 0x8C, 0xFF),
    "grip": (0xFF, 0x3B, 0x3B),
}


def _line(d, points, w):
    d.line(points, fill=255, width=w, joint="curve")
    r = w / 2
    for x, y in (points[0], points[-1]):
        d.ellipse([x - r, y - r, x + r, y + r], fill=255)


def _arc(d, cx, cy, r, a0, a1, w):
    d.arc([cx - r, cy - r, cx + r, cy + r], start=a0, end=a1, fill=255, width=w)


def _star(d, cx, cy, r, w):
    _line(d, [(cx - r, cy), (cx + r, cy)], max(2, w // 2))
    _line(d, [(cx, cy - r), (cx, cy + r)], max(2, w // 2))


def draw_push(d, w):
    d.ellipse([40, 112, 72, 144], fill=255)
    for r in (52, 88, 124):
        _arc(d, 56, 128, r, -40, 40, w)


def draw_pull(d, w):
    d.ellipse([22, 112, 54, 144], fill=255)
    _line(d, [(100, 92), (68, 128), (100, 164)], w)
    for r in (140, 182, 224):
        _arc(d, 330, 128, r, 158, 202, w)


def draw_grab(d, w):
    d.ellipse([90, 54, 166, 130], outline=255, width=w)
    d.ellipse([116, 80, 140, 104], fill=255)
    d.ellipse([34, 74, 222, 112], outline=255, width=max(3, w // 2))
    _line(d, [(84, 196), (128, 164), (172, 196)], w)
    _line(d, [(84, 234), (128, 202), (172, 234)], w)


def draw_speed(d, w):
    for x0 in (96, 140, 184):
        _line(d, [(x0 - 26, 68), (x0 + 22, 128), (x0 - 26, 188)], w)
    for y, x0, x1 in ((96, 12, 56), (128, 4, 60), (160, 12, 56)):
        _line(d, [(x0, y), (x1, y)], max(3, w // 2))


def draw_heal(d, w):
    big = int(w * 1.5)
    _line(d, [(128, 58), (128, 198)], big)
    _line(d, [(58, 128), (198, 128)], big)
    d.ellipse([20, 20, 236, 236], outline=255, width=max(3, w // 3))
    _star(d, 206, 50, 18, w)
    _star(d, 50, 206, 14, w)


def draw_lightning(d, w):
    # Klassische Blitzform, gefüllt; die Strichbreite macht sie für Glühen und Kern dicker oder dünner.
    bolt = [(158, 16), (84, 138), (128, 138), (98, 240), (182, 104), (136, 104), (178, 16)]
    d.polygon(bolt, fill=255, outline=255, width=max(1, w // 2 - 6))


def draw_grip(d, w):
    # Eine Klaue aus drei Fingern greift von oben um eine Kehle.
    d.ellipse([102, 132, 154, 184], outline=255, width=w)
    for a0, a1 in ((188, 232), (248, 292), (308, 352)):
        _arc(d, 128, 158, 96, a0, a1, w)
    for angle in (232, 292, 352):
        a = np.radians(angle)
        x0, y0 = 128 + np.cos(a) * 96, 158 + np.sin(a) * 96
        a2 = np.radians(angle + 14)
        x1, y1 = 128 + np.cos(a2) * 62, 158 + np.sin(a2) * 62
        _line(d, [(x0, y0), (x1, y1)], w)
    a = np.radians(188)
    _line(d, [(128 + np.cos(a) * 96, 158 + np.sin(a) * 96), (60, 222)], w)


FORCE_ICON_SHAPES = {
    "push": draw_push, "pull": draw_pull, "grab": draw_grab, "speed": draw_speed,
    "heal": draw_heal, "lightning": draw_lightning, "grip": draw_grip,
}


def _mask(draw_fn, width, blur):
    img = Image.new("L", (ICON_SS, ICON_SS), 0)
    draw_fn(ImageDraw.Draw(img), width)
    if blur > 0:
        img = img.filter(ImageFilter.GaussianBlur(blur))
    return np.asarray(img, dtype=np.float64) / 255.0


def _over(dst_rgb, dst_a, rgb, a):
    """Alpha-Compositing (vormultipliert): legt Farbe rgb mit Deckkraft a über das Bild."""
    dst_rgb = dst_rgb * (1 - a[..., None]) + np.asarray(rgb)[None, None, :] * a[..., None]
    dst_a = dst_a * (1 - a) + a
    return dst_rgb, dst_a


def _downsample(rgb_premul, alpha, size):
    rgba = np.dstack([rgb_premul, alpha])
    img = Image.fromarray((np.clip(rgba, 0, 1) * 255).astype(np.uint8), "RGBA")
    small = np.asarray(img.resize((size, size), Image.LANCZOS), dtype=np.float64) / 255.0
    a = small[..., 3]
    rgb = np.where(a[..., None] > 1e-4, small[..., :3] / np.maximum(a[..., None], 1e-4), 0)
    out = np.dstack([np.clip(rgb, 0, 1), a])
    return Image.fromarray((out * 255).round().astype(np.uint8), "RGBA")


def force_icon(name, size=32):
    """Leuchtendes Symbol: farbiges Glühen, hellerer Strich, weißer Kern."""
    shape = FORCE_ICON_SHAPES[name]
    color = np.array(FORCE_COLORS[name]) / 255.0
    light = color + (1 - color) * 0.45
    glow = _mask(shape, 34, 13) * 0.85
    body = _mask(shape, 18, 1.5)
    core = _mask(shape, 7, 1.0)
    rgb = np.zeros((ICON_SS, ICON_SS, 3))
    a = np.zeros((ICON_SS, ICON_SS))
    rgb, a = _over(rgb, a, color, glow)
    rgb, a = _over(rgb, a, light, body)
    rgb, a = _over(rgb, a, (1.0, 1.0, 1.0), core * 0.95)
    return _downsample(rgb, a, size)


def _radial(size, sigma):
    y, x = np.mgrid[0:size, 0:size]
    c = (size - 1) / 2
    return np.exp(-((x - c) ** 2 + (y - c) ** 2) / (2 * sigma ** 2))


def _white(alpha):
    alpha = np.clip(alpha, 0, 1)
    rgba = np.dstack([np.ones_like(alpha), np.ones_like(alpha), np.ones_like(alpha), alpha])
    return Image.fromarray((rgba * 255).round().astype(np.uint8), "RGBA")


def particle_textures():
    """Weiße Partikeltexturen; die Farbe kommt erst im Spiel dazu."""
    s = 128
    y, x = np.mgrid[0:s, 0:s]
    c = (s - 1) / 2
    dx, dy = np.abs(x - c) / c, np.abs(y - c) / c
    r = np.sqrt(dx ** 2 + dy ** 2)

    def shrink(alpha, size):
        img = _white(alpha)
        return img.resize((size, size), Image.LANCZOS)

    # Vierzackiger Stern mit weichem Kern
    star = np.maximum(np.clip(1 - (dx * 6 + dy * 0.9), 0, 1), np.clip(1 - (dy * 6 + dx * 0.9), 0, 1)) ** 1.5
    sparkle = np.clip(star + np.exp(-r ** 2 / 0.02) + 0.35 * np.exp(-r ** 2 / 0.08), 0, 1)
    mote = np.exp(-r ** 2 / 0.12) * 0.9 + np.exp(-r ** 2 / 0.015) * 0.3
    spark = np.exp(-r ** 2 / 0.025)
    ring = np.exp(-((r - 0.78) ** 2) / 0.004) + 0.12 * np.exp(-((r - 0.7) ** 2) / 0.03)
    streak = np.exp(-(dy ** 2) / 0.006) * np.clip(1 - dx ** 1.6, 0, 1)
    # Rauch: weiche, unregelmäßige Wolke (deterministisch)
    noise_rng = np.random.default_rng(66)
    blob = np.zeros((s, s))
    for _ in range(9):
        bx, by = noise_rng.uniform(0.3, 0.7, 2) * s
        br = noise_rng.uniform(0.12, 0.28) * s
        blob += np.exp(-(((x - bx) ** 2 + (y - by) ** 2) / (2 * br ** 2)))
    smoke = np.clip(blob / blob.max(), 0, 1) ** 1.3 * np.clip(1.2 - r, 0, 1)
    rays = np.clip(1 - np.minimum(dx * 9 + dy * 0.6, dy * 9 + dx * 0.6), 0, 1) * np.clip(1 - r, 0, 1)
    flash = np.clip(np.exp(-r ** 2 / 0.06) + 0.5 * np.exp(-r ** 2 / 0.3) + 0.6 * rays, 0, 1)

    return {
        "force_sparkle": shrink(sparkle, 16),
        "force_mote": shrink(mote, 16),
        "force_spark": shrink(spark, 16),
        # Große, weiche Formen höher aufgelöst, damit sie nah vor der Kamera nicht pixelig wirken.
        "force_ring": shrink(ring, 64),
        "force_streak": shrink(streak, 32),
        "force_smoke": shrink(smoke, 64),
        "force_flash": shrink(flash, 64),
    }


def aura_texture(size=64):
    """Kachelbare Energie-Textur für die leuchtende Hülle (wandert im Spiel über das Modell)."""
    y, x = np.mgrid[0:size, 0:size] / size * 2 * np.pi
    texture_rng = np.random.default_rng(1138)
    field = np.zeros((size, size))
    for _ in range(7):
        fx, fy = texture_rng.integers(1, 4, 2)
        phase = texture_rng.uniform(0, 2 * np.pi)
        field += np.sin(fx * x + fy * y + phase + 0.8 * np.sin(fy * x - fx * y))
    field /= np.abs(field).max()
    bands = np.abs(np.sin(field * 3.2)) ** 6  # schmale, helle Energiefäden
    glow = 0.25 + 0.75 * bands
    value = np.clip(glow, 0, 1)
    rgba = np.dstack([value, value, value, np.ones_like(value)])
    return Image.fromarray((rgba * 255).round().astype(np.uint8), "RGBA")


def holocron_textures():
    """Seite: dunkler Rahmen, leuchtendes blaues Feld mit Ornament. Ecke: goldenes Metall."""
    side = np.zeros((16, 16, 4))
    for py in range(16):
        for px in range(16):
            d = min(px, py, 15 - px, 15 - py)
            if d == 0:
                col = (0.07, 0.09, 0.14)
            elif d == 1:
                col = (0.45, 0.62, 0.85)
            else:
                cdist = max(abs(px - 7.5), abs(py - 7.5)) / 6.0
                base = np.array((0.10, 0.36, 0.86)) * (1 - cdist) + np.array((0.05, 0.16, 0.45)) * cdist
                col = tuple(base)
                if d == 4:
                    col = (0.70, 0.90, 1.0)
                if abs(px - 7.5) + abs(py - 7.5) <= 2.0:
                    col = (0.95, 0.99, 1.0)
            side[py, px] = (*col, 1.0)
    for px, py in ((3, 7), (12, 8), (7, 3), (8, 12)):
        side[py, px, :3] = (0.75, 0.92, 1.0)

    corner = np.zeros((16, 16, 4))
    for py in range(16):
        for px in range(16):
            shade = 0.75 + 0.25 * np.cos((px + py) * 0.5)
            corner[py, px] = (0.86 * shade, 0.66 * shade, 0.24 * shade, 1.0)
    corner[0, :, :3] = (1.0, 0.92, 0.6)
    corner[:, 0, :3] = (1.0, 0.88, 0.5)
    corner[15, :, :3] = (0.45, 0.32, 0.1)
    corner[:, 15, :3] = (0.5, 0.36, 0.12)

    def img(a):
        return Image.fromarray((np.clip(a, 0, 1) * 255).round().astype(np.uint8), "RGBA")

    return img(side), img(corner)


def generate_force_textures():
    print("Erzeuge Machtsymbole, Partikel und Holocron ...")
    os.makedirs(FORCE_ICONS, exist_ok=True)
    for name in FORCE_ICON_SHAPES:
        force_icon(name).save(os.path.join(FORCE_ICONS, name + ".png"))
        print("  icon:", name)
    os.makedirs(PARTICLE_TEXTURES, exist_ok=True)
    for name, image in particle_textures().items():
        image.save(os.path.join(PARTICLE_TEXTURES, name + ".png"))
        print("  particle:", name)
    os.makedirs(ENTITY_TEXTURES, exist_ok=True)
    aura_texture().save(os.path.join(ENTITY_TEXTURES, "force_aura.png"))
    side, corner = holocron_textures()
    side.save(os.path.join(TEXTURES, "holocron_side.png"))
    corner.save(os.path.join(TEXTURES, "holocron_corner.png"))
    print("  textures: force_aura, holocron_side, holocron_corner")


# ---------------------------------------------------------------------------------------------
# Machtklänge
# ---------------------------------------------------------------------------------------------

def ar(n, attack, release):
    """Hüllkurve: linear an, exponentiell ab."""
    t = t_axis(n)
    return np.minimum(1.0, t / max(attack, 1e-4)) * np.exp(-np.maximum(0.0, t - attack) / release)


def sweep_noise(n, f_start, f_end, width=0.6, curve=1.0):
    """Rauschen, dessen Frequenzband von f_start nach f_end wandert (Wusch, Sog)."""
    hop, win = 512, 2048
    x = rng.standard_normal(n + win)
    out = np.zeros(n + win)
    window = np.hanning(win)
    freqs = np.fft.rfftfreq(win, 1 / SR)
    for start in range(0, n, hop):
        u = (start / max(1, n)) ** curve
        fc = f_start * (f_end / f_start) ** u
        spectrum = np.fft.rfft(x[start:start + win] * window)
        spectrum *= np.exp(-(np.log2(np.maximum(freqs, 1.0) / fc) / width) ** 2)
        out[start:start + win] += np.fft.irfft(spectrum, win) * window
    out = out[:n]
    return out / (np.abs(out).max() + 1e-9)


def chime(freq, n, length=0.8, partials=((1.0, 1.0), (2.0, 0.45), (2.76, 0.3), (5.4, 0.15))):
    """Glockenartiger Ton aus unharmonischen Teiltönen."""
    t = t_axis(n)
    x = np.zeros(n)
    for ratio, amp in partials:
        x += amp * np.sin(2 * np.pi * freq * ratio * t) * np.exp(-t / (length / ratio ** 0.6))
    return x * np.clip(t / 0.002, 0, 1)


def delayed(x, seconds, n):
    out = np.zeros(n)
    start = samples(seconds)
    if start < n:
        part = x[:n - start]
        out[start:start + len(part)] = part
    return out


def reverb(x, seconds=0.8, mix=0.25):
    """Einfacher Hall: Faltung mit abklingendem Rauschen."""
    n = len(x)
    m = samples(seconds)
    impulse = rng.standard_normal(m) * np.exp(-t_axis(m) / (seconds / 4))
    impulse /= np.sqrt(np.sum(impulse ** 2)) + 1e-9
    size = 1 << int(np.ceil(np.log2(n + m)))
    wet = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(impulse, size), size)[:n]
    return x * (1 - mix) + wet * mix * 1.8


def seamless(make, seconds, fade_seconds=0.25):
    """Erzeugt eine nahtlose Schleife: das Ende wird in den Anfang übergeblendet."""
    n, f = samples(seconds), samples(fade_seconds)
    x = make(n + f)
    out = x[:n].copy()
    ramp = np.linspace(0, 1, f)
    out[:f] = x[:f] * np.sqrt(ramp) + x[n:n + f] * np.sqrt(1 - ramp)
    return out


def chord_pad(freqs, n, attack=0.3, release=1.0, detune=0.004):
    t = t_axis(n)
    x = np.zeros(n)
    for f in freqs:
        for d in (-detune, 0.0, detune):
            x += np.sin(2 * np.pi * f * (1 + d) * t + rng.uniform(0, 6.28))
    return x / (3 * len(freqs)) * ar(n, attack, release)


def sound_force_push():
    n = samples(1.1)
    thump = np.sin(2 * np.pi * phase_of(np.linspace(95, 30, n))) * ar(n, 0.004, 0.22)
    whoosh = sweep_noise(n, 3400, 260, 0.75) * ar(n, 0.012, 0.42)
    air = noise_band(n, 60, 500) * ar(n, 0.003, 0.12)
    x = soft_clip(thump * 0.95 + whoosh * 0.75 + air * 0.5, 1.4)
    return normalize(fade(reverb(x, 0.9, 0.22), 0.001, 0.08))


def sound_force_pull():
    n = samples(1.0)
    t = t_axis(n)
    gate = np.where(t < 0.62, (np.clip(t / 0.6, 0, 1)) ** 2, np.exp(-(t - 0.62) / 0.04))
    suck = sweep_noise(n, 260, 3200, 0.6, curve=1.5) * gate
    tone = np.sin(2 * np.pi * phase_of(np.geomspace(180, 620, n))) * gate * 0.25
    thump = delayed(np.sin(2 * np.pi * phase_of(np.linspace(130, 50, n))) * ar(n, 0.003, 0.14), 0.6, n)
    x = soft_clip(suck * 0.8 + tone + thump * 0.9, 1.3)
    return normalize(fade(reverb(x, 0.7, 0.2), 0.005, 0.06))


def sound_force_grab():
    n = samples(0.9)
    x = np.zeros(n)
    for i, f in enumerate((880.0, 1318.5, 1760.0)):
        x += delayed(chime(f, n, 0.5), i * 0.05, n) * 0.35
    sparkle = crackle(n, 300, 2500, 5000, 11000) * ar(n, 0.2, 0.25) * 0.35
    swell = np.sin(2 * np.pi * 220 * t_axis(n)) * ar(n, 0.15, 0.4) * 0.3
    whoosh = sweep_noise(n, 500, 2500, 0.6) * ar(n, 0.1, 0.25) * 0.4
    return normalize(fade(reverb(x + sparkle + swell + whoosh, 1.0, 0.3), 0.002, 0.1), 0.75)


def sound_force_grab_loop():
    def make(n):
        t = t_axis(n)
        hum = 0.5 * np.sin(2 * np.pi * 110 * t) + 0.3 * np.sin(2 * np.pi * 165 * t) + 0.15 * np.sin(2 * np.pi * 220.5 * t)
        hum *= 1 + 0.25 * np.sin(2 * np.pi * 2 * t)
        shimmer = noise_band(n, 4000, 9000) * (0.5 + 0.5 * np.sin(2 * np.pi * 5 * t)) * 0.18
        return hum + shimmer
    return normalize(seamless(make, 2.0), 0.55)


def sound_force_throw():
    n = samples(0.55)
    whoosh = sweep_noise(n, 2600, 380, 0.7) * ar(n, 0.02, 0.22)
    low = noise_band(n, 80, 400) * ar(n, 0.01, 0.15) * 0.5
    return normalize(fade(whoosh + low, 0.002, 0.05), 0.8)


def sound_force_speed():
    n = samples(1.0)
    t = t_axis(n)
    rise = sweep_noise(n, 300, 4500, 0.6, curve=0.7) * ar(n, 0.25, 0.25)
    vibrato = 1 + 0.02 * np.sin(2 * np.pi * 7 * t)
    zing = np.sin(2 * np.pi * phase_of(np.geomspace(500, 2400, n) * vibrato)) * bell(n, 0.3, 0.2) * 0.35
    boom = np.sin(2 * np.pi * phase_of(np.linspace(80, 40, n))) * ar(n, 0.003, 0.15) * 0.8
    return normalize(fade(reverb(rise * 0.8 + zing + boom, 0.6, 0.2), 0.002, 0.08))


def sound_force_speed_end():
    n = samples(0.7)
    fall = sweep_noise(n, 3000, 250, 0.6) * ar(n, 0.02, 0.3)
    tone = np.sin(2 * np.pi * phase_of(np.geomspace(1500, 300, n))) * ar(n, 0.01, 0.2) * 0.25
    return normalize(fade(fall + tone, 0.002, 0.06), 0.7)


def sound_force_heal():
    n = samples(1.9)
    x = np.zeros(n)
    for i, f in enumerate((1046.5, 1318.5, 1568.0, 2093.0)):
        x += delayed(chime(f, n, 0.9), i * 0.07, n) * 0.3
    pad = chord_pad((523.25, 659.25, 783.99), n, 0.35, 0.9) * 0.6
    shimmer = crackle(n, 600, 100, 6000, 12000) * ar(n, 0.1, 0.6) * 0.25
    return normalize(fade(reverb(x + pad + shimmer, 1.3, 0.35), 0.002, 0.2), 0.8)


def sound_force_lightning():
    n = samples(0.75)
    t = t_axis(n)
    crack = noise_band(n, 1000, 12000) * ar(n, 0.0005, 0.012) * 1.4
    sizzle = crackle(n, 14000, 900) * ar(n, 0.002, 0.35)
    buzz = np.sign(np.sin(2 * np.pi * 118 * t)) * noise_band(n, 5, 60) * ar(n, 0.005, 0.3) * 0.5
    return normalize(fade(soft_clip(reverb(crack + sizzle + buzz, 0.5, 0.15), 1.5), 0.0005, 0.06))


def sound_force_lightning_loop():
    def make(n):
        t = t_axis(n)
        crack = crackle(n, 5200, 5200)
        buzz = (np.sign(np.sin(2 * np.pi * 100 * t)) * 0.4 + np.sin(2 * np.pi * 200 * t) * 0.3) * (0.6 + 0.4 * noise_band(n, 3, 30))
        zaps = np.zeros(n)
        for _ in range(int(n / SR * 7)):
            start = rng.integers(0, max(1, n - samples(0.08)))
            m = samples(0.07)
            zap = np.sin(2 * np.pi * phase_of(np.geomspace(rng.uniform(2500, 4000), 600, m))) * decay(m, 0.02)
            zaps[start:start + m] += zap * rng.uniform(0.3, 0.7)
        return soft_clip(crack * 0.8 + buzz * 0.35 + zaps * 0.5, 1.4)
    return normalize(seamless(make, 2.0), 0.7)


def sound_force_lightning_hit():
    n = samples(0.35)
    zap = np.sin(2 * np.pi * phase_of(np.geomspace(3200, 700, n))) * ar(n, 0.001, 0.07)
    burst = crackle(n, 9000, 500) * ar(n, 0.001, 0.09)
    click = noise_band(n, 2000, 10000) * ar(n, 0.0003, 0.006)
    return normalize(fade(zap * 0.6 + burst + click, 0.0005, 0.04), 0.8)


def sound_force_grip():
    n = samples(1.2)
    t = t_axis(n)
    thoom = soft_clip(np.sin(2 * np.pi * phase_of(np.linspace(58, 36, n))) * ar(n, 0.01, 0.5) * 1.6, 2.0)
    creak = sweep_noise(n, 320, 520, 0.18) * (0.5 + 0.5 * np.sin(2 * np.pi * 3.5 * t)) * ar(n, 0.15, 0.5) * 0.45
    low = noise_band(n, 30, 200) * ar(n, 0.05, 0.4) * 0.5
    return normalize(fade(reverb(thoom + creak + low, 0.9, 0.25), 0.005, 0.12))


def sound_force_grip_loop():
    def make(n):
        t = t_axis(n)
        brown = np.cumsum(rng.standard_normal(n))
        brown = brown - np.convolve(brown, np.ones(4096) / 4096, mode="same")
        rumble = brown / (np.abs(brown).max() + 1e-9)
        drone = np.sin(2 * np.pi * 55 * t) + 0.8 * np.sin(2 * np.pi * 55.7 * t) + 0.3 * np.sin(2 * np.pi * 110.4 * t)
        creak = noise_band(n, 600, 950) * np.clip(np.sin(2 * np.pi * 0.5 * t), 0, 1) ** 4 * 0.5
        return soft_clip(rumble * 0.6 + drone * 0.35 + creak, 1.3)
    return normalize(seamless(make, 2.0), 0.65)


def sound_force_impact():
    n = samples(0.6)
    thud = np.sin(2 * np.pi * phase_of(np.linspace(85, 38, n))) * ar(n, 0.002, 0.12)
    body = noise_band(n, 50, 400) * ar(n, 0.001, 0.08)
    crunch = noise_band(n, 800, 3200) * ar(n, 0.001, 0.045) * 0.7
    return normalize(fade(soft_clip(reverb(thud + body + crunch, 0.5, 0.2), 1.6), 0.0005, 0.05))


def sound_force_fail():
    n = samples(0.45)
    t = t_axis(n)
    sputter = noise_band(n, 2000, 8000) * ar(n, 0.003, 0.12) * (0.4 + 0.6 * (np.sin(2 * np.pi * 30 * t) > 0))
    blip = np.sin(2 * np.pi * phase_of(np.geomspace(600, 180, n))) * ar(n, 0.002, 0.1) * 0.6
    return normalize(fade(sputter * 0.6 + blip, 0.001, 0.05), 0.6)


def sound_wheel_open():
    n = samples(0.35)
    swell = sweep_noise(n, 600, 3200, 0.6) * ar(n, 0.12, 0.12) * 0.6
    ding = delayed(chime(1760.0, n, 0.25), 0.08, n) * 0.3
    return normalize(fade(swell + ding, 0.002, 0.05), 0.6)


def sound_wheel_tick():
    n = samples(0.07)
    tick = np.sin(2 * np.pi * 2400 * t_axis(n)) * decay(n, 0.012)
    click = noise_band(n, 3000, 9000) * decay(n, 0.003) * 0.4
    return normalize(fade(tick + click, 0.0003, 0.01), 0.5)


def sound_wheel_select():
    n = samples(0.32)
    x = chime(880.0, n, 0.2) * 0.5 + delayed(chime(1320.0, n, 0.25), 0.06, n) * 0.5
    return normalize(fade(x, 0.001, 0.05), 0.65)


def sound_holocron_open():
    n = samples(1.8)
    pad = chord_pad((220.0, 261.63, 329.63, 440.0), n, 0.5, 0.8)
    shimmer = crackle(n, 400, 150, 5000, 11000) * ar(n, 0.4, 0.6) * 0.25
    swell = sweep_noise(n, 200, 1200, 0.5) * ar(n, 0.5, 0.4) * 0.3
    return normalize(fade(reverb(pad + shimmer + swell, 1.4, 0.35), 0.01, 0.25), 0.7)


def choir(freqs, n, attack, release):
    """Chorähnlicher Klang: Sägezahn durch Formantfilter ("aah")."""
    t = t_axis(n)
    x = np.zeros(n)
    for f in freqs:
        x += saw(f * t * (1 + 0.003 * np.sin(2 * np.pi * 5 * t)), 24)
    spectrum = np.fft.rfft(x)
    freqs_axis = np.fft.rfftfreq(n, 1 / SR)
    formants = sum(a * np.exp(-((freqs_axis - fc) / bw) ** 2) for fc, bw, a in ((750, 120, 1.0), (1150, 150, 0.7), (2500, 300, 0.35)))
    x = np.fft.irfft(spectrum * formants, n)
    return x / (np.abs(x).max() + 1e-9) * ar(n, attack, release)


def sound_holocron_learn():
    n = samples(2.8)
    boom = np.sin(2 * np.pi * phase_of(np.linspace(62, 34, n))) * ar(n, 0.005, 0.45) * 0.9
    x = np.zeros(n)
    for i, f in enumerate((523.25, 659.25, 783.99, 1046.5, 1318.5, 1568.0, 2093.0)):
        x += delayed(chime(f, n, 1.0), i * 0.06, n) * 0.28
    chord = delayed(chord_pad((261.63, 329.63, 392.0, 523.25), n, 0.3, 1.2) * 0.55 + choir((261.63, 392.0, 523.25), n, 0.35, 1.1) * 0.4,
                    0.38, n)
    shimmer = crackle(n, 700, 80, 6000, 12000) * ar(n, 0.4, 0.9) * 0.3
    return normalize(fade(reverb(boom + x + chord + shimmer, 1.6, 0.35), 0.002, 0.3), 0.85)


def sound_holocron_upgrade():
    n = samples(1.5)
    x = np.zeros(n)
    for i, f in enumerate((783.99, 1046.5, 1318.5, 1568.0)):
        x += delayed(chime(f, n, 0.8), i * 0.07, n) * 0.3
    pad = delayed(chord_pad((392.0, 523.25, 659.25), n, 0.2, 0.7) * 0.45, 0.25, n)
    shimmer = crackle(n, 500, 100, 6000, 12000) * ar(n, 0.2, 0.5) * 0.25
    return normalize(fade(reverb(x + pad + shimmer, 1.2, 0.3), 0.002, 0.2), 0.8)


def generate_force_sounds():
    global rng
    saved = rng
    rng = np.random.default_rng(2187)  # eigener Zufall: Lichtschwertklänge bleiben unverändert
    print("Erzeuge Machtklänge ...")
    for name, make in (
            ("push", sound_force_push), ("pull", sound_force_pull), ("grab", sound_force_grab),
            ("grab_loop", sound_force_grab_loop), ("throw", sound_force_throw), ("speed", sound_force_speed),
            ("speed_end", sound_force_speed_end), ("heal", sound_force_heal), ("lightning", sound_force_lightning),
            ("lightning_loop", sound_force_lightning_loop), ("lightning_hit", sound_force_lightning_hit),
            ("grip", sound_force_grip), ("grip_loop", sound_force_grip_loop), ("impact", sound_force_impact),
            ("fail", sound_force_fail), ("wheel_open", sound_wheel_open), ("wheel_tick", sound_wheel_tick),
            ("wheel_select", sound_wheel_select)):
        write_ogg(name, make(), FORCE_SOUNDS)
    for name, make in (("open", sound_holocron_open), ("learn", sound_holocron_learn), ("upgrade", sound_holocron_upgrade)):
        write_ogg(name, make(), HOLOCRON_SOUNDS)
    rng = saved


if __name__ == "__main__":
    import sys

    # "python3 tools/generate_assets.py force" erzeugt nur die Macht-Assets.
    if "force" not in sys.argv[1:]:
        generate_textures()
        generate_sounds()
    generate_force_textures()
    generate_force_sounds()
    print("Fertig.")
