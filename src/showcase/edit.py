import os
import random
import subprocess
import sys
import wave

import imageio_ffmpeg
import numpy as np

FF = imageio_ffmpeg.get_ffmpeg_exe()
HERE = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[2] if len(sys.argv) > 2 else HERE
CLIPS = os.path.join(HERE, "clips")
SOUNDS = r"C:\Users\Alten\IdeaProjects\Squall\src\main\resources\assets\squall\sounds"
RATE = 48000
FADE = 0.8

SHOTS = ["01-arrival", "03-close", "05-through", "06-storm", "07-outro"]
SKIP = {"01-arrival": 3.5, "06-storm": 2.0}
CUT = {"01-arrival": 1.5, "03-close": 1.5, "05-through": 3.0}

def duration(path):
    out = subprocess.run([FF, "-i", path], capture_output=True, text=True).stderr
    h, m, s = out.split("Duration: ")[1].split(",")[0].split(":")
    return int(h) * 3600 + int(m) * 60 + float(s)

lengths = [duration(os.path.join(CLIPS, s + ".mp4")) - SKIP.get(s, 0.0) - CUT.get(s, 0.0) for s in SHOTS]
starts = []
t = 0.0
for n in lengths:
    starts.append(t)
    t += n - FADE
TOTAL = starts[-1] + lengths[-1]
S = dict(zip(SHOTS, starts))
print("shots start at", [round(s, 2) for s in starts], "total", round(TOTAL, 2))

N = int(TOTAL * RATE) + RATE
mix = np.zeros((N, 2), dtype=np.float32)

def load(name):
    raw = subprocess.run([FF, "-loglevel", "error", "-i", os.path.join(SOUNDS, name + ".ogg"), "-f", "f32le", "-ac", "2",
                          "-ar", str(RATE), "-"], capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).copy()

def loop(sound, offset=0.0):
    reps = N // len(sound) + 2
    whole = np.tile(sound, (reps, 1))
    skip = int(offset * RATE) % len(sound)
    return whole[skip:skip + N]

def envelope(points):
    times = np.arange(N) / RATE
    return np.interp(times, [p[0] for p in points], [p[1] for p in points]).astype(np.float32)[:, None]

def band(sound, low, high):
    spectrum = np.fft.rfft(sound, axis=0)
    freqs = np.fft.rfftfreq(len(sound), 1.0 / RATE)
    gain = 1.0 / np.sqrt(1.0 + (freqs / high) ** 4)
    if low > 0:
        gain *= 1.0 / np.sqrt(1.0 + (low / np.maximum(freqs, 1.0)) ** 4)
    spectrum *= gain[:, None]
    return np.fft.irfft(spectrum, n=len(sound), axis=0).astype(np.float32)

def place(sound, at, gain=1.0, pan=0.0):
    i = int(at * RATE)
    if i >= N:
        return
    piece = sound[: N - i] * gain
    piece = piece * np.array([1.0 - max(0.0, pan), 1.0 + min(0.0, pan)], dtype=np.float32)
    mix[i:i + len(piece)] += piece

rng = np.random.default_rng(11)
noise = rng.standard_normal((N, 2)).astype(np.float32)
times = np.arange(N) / RATE
gust = (0.55 + 0.25 * np.sin(times * 0.37) + 0.15 * np.sin(times * 1.13 + 1.7) + 0.08 * np.sin(times * 2.9 + 0.4))
wind = band(noise, 120.0, 520.0) * gust.astype(np.float32)[:, None]
wind /= np.max(np.abs(wind))
whistle = band(noise, 700.0, 1400.0) * (gust ** 3).astype(np.float32)[:, None]
whistle /= np.max(np.abs(whistle))

storm_in = S["06-storm"]
calm = envelope([(0, 1), (storm_in - 0.2, 1), (storm_in + FADE, 0.55), (TOTAL + 1, 0.55)])
rainy = envelope([(0, 0), (storm_in - 0.2, 0), (storm_in + 1.2, 1), (TOTAL - 4, 0.9), (TOTAL + 1, 0.7)])
mix += wind * 0.32 * (calm + rainy * 0.8) + whistle * 0.05
leaves = loop(band(load("rain/leaves"), 0, 2500.0), 3.0)
mix += leaves * 0.06 * calm
rain = loop(load("rain/stone")) * 0.5 + loop(load("rain/leaves"), 4.0) * 0.45
mix += rain * rainy

random.seed(7)
creaks = [band(load("drip/soft%d" % i), 0, 1800.0) for i in range(1, 5)]
for at in (S["03-close"] + 1.6, S["03-close"] + 4.9, S["05-through"] + 1.4):
    place(random.choice(creaks), at, 0.12)

place(load("thunder/far1"), storm_in + 1.0, 0.55)
place(load("thunder/near2"), storm_in + 200 / 60.0 - SKIP["06-storm"] + 0.9, 0.9, -0.3)
place(load("thunder/far3"), S["07-outro"] + 380 / 60.0 + 1.4, 0.7, 0.3)

master = envelope([(0, 0), (1.8, 1), (TOTAL - 3.2, 1), (TOTAL, 0), (TOTAL + 1, 0)])
mix *= master
peak = np.max(np.abs(mix))
mix *= 0.89 / max(peak, 1e-6)
audio = os.path.join(HERE, "soundtrack.wav")
with wave.open(audio, "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes((np.clip(mix, -1, 1) * 32767).astype(np.int16).tobytes())
print("soundtrack written, peak", round(float(peak), 3))

inputs = []
for s, n in zip(SHOTS, lengths):
    inputs += ["-ss", str(SKIP.get(s, 0.0)), "-t", "%.3f" % n, "-i", os.path.join(CLIPS, s + ".mp4")]
inputs += ["-i", audio]
chain = []
last = "[0:v]"
for i in range(1, len(SHOTS)):
    out = "[x%d]" % i
    chain.append("%s[%d:v]xfade=transition=fade:duration=%.3f:offset=%.3f%s" % (last, i, FADE, starts[i], out))
    last = out
font = "C\\:/Windows/Fonts/georgia.ttf"
title_in = TOTAL - 6.0
grade = (
    "eq=contrast=1.07:brightness=0.01:saturation=1.08,"
    "colorbalance=rs=0.03:gs=0.01:bs=-0.03:rh=0.04:gh=0.015:bh=-0.04,"
    "vignette=angle=PI/5,"
    "drawbox=x=0:y=0:w=iw:h=138:color=black:t=fill,drawbox=x=0:y=ih-138:w=iw:h=138:color=black:t=fill,"
    "drawtext=fontfile='%s':text='SWINGING SIGNS':fontcolor=white:fontsize=96:x=(w-text_w)/2:y=h*0.40:"
    "alpha='clip((t-%.2f)/1.5,0,1)*clip((%.2f-t)/1.0,0,1)',"
    "drawtext=fontfile='%s':text='hanging signs that sway in the wind':fontcolor=0xD8D8D8:fontsize=30:"
    "x=(w-text_w)/2:y=h*0.40+120:alpha='clip((t-%.2f)/1.5,0,1)*clip((%.2f-t)/1.0,0,1)',"
    "fade=t=in:st=0:d=1.5,fade=t=out:st=%.2f:d=2.4"
    % (font, title_in, TOTAL - 0.4, font, title_in + 0.7, TOTAL - 0.4, TOTAL - 2.4)
)
chain.append("%s%s[v]" % (last, grade))
os.makedirs(OUT, exist_ok=True)
out = os.path.join(OUT, "swingingsigns-showcase.mp4")
cmd = [FF, "-hide_banner", "-loglevel", "error", "-y"] + inputs + [
    "-filter_complex", ";".join(chain), "-map", "[v]", "-map", "%d:a" % len(SHOTS),
    "-c:v", "h264_nvenc", "-preset", "p7", "-rc", "vbr", "-cq", "19", "-b:v", "0", "-pix_fmt", "yuv420p",
    "-c:a", "aac", "-b:a", "256k", "-shortest", "-movflags", "+faststart", out]
subprocess.run(cmd, check=True)
print("wrote", out)
small = os.path.join(OUT, "swingingsigns-showcase-small.mp4")
subprocess.run([FF, "-hide_banner", "-loglevel", "error", "-y", "-i", out, "-vf", "scale=1280:-2", "-c:v", "h264_nvenc",
                "-preset", "p7", "-rc", "vbr", "-cq", "27", "-b:v", "0", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a",
                "160k", "-movflags", "+faststart", small], check=True)
print("wrote", small)
