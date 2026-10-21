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


def write_ogg(name, x):
    os.makedirs(SOUNDS, exist_ok=True)
    data = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, name + ".wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(data.tobytes())
        out = os.path.join(SOUNDS, name + ".ogg")
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


if __name__ == "__main__":
    generate_textures()
    generate_sounds()
    print("Fertig.")
