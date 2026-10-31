"""Уровни звуков WHAT THE LAKE KEEPS: меряет громкость каждого snd/*.ogg (EBU R128) и пишет snd/levels.json —
сколько дБ добавить, чтобы звук звучал «нормально» (разовые −18 LUFS, петли −21 LUFS, пик не выше −1 дБ).
В игре 0 дБ = нормальная громкость любого звука. Запускать после каждого нового звука:
  python3 tools/lake/snd_levels.py"""
import glob, json, os, re, subprocess

SND = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../godot/lake/snd')
LOOPS = {'car_interior', 'wind_forest', 'station_hum', 'shop_room', 'blizzard', 'drone', 'deer_breath', 'snow_steps',
         'underwater', 'radio_hiss'}
out = {}
for f in sorted(glob.glob(os.path.join(SND, '*.ogg'))):
    name = os.path.basename(f)[:-4]
    r = subprocess.run(['ffmpeg', '-nostdin', '-hide_banner', '-i', f, '-af', 'ebur128=peak=true', '-f', 'null', '-'],
                       capture_output=True, text=True).stderr
    i = float(re.findall(r'I:\s+(-?[\d.]+) LUFS', r)[-1])
    p = float(re.findall(r'Peak:\s+(-?[\d.]+) dBFS', r)[-1])
    target = -21.0 if name in LOOPS else -18.0
    out[name] = round(min(target - i, -1.0 - p), 1)
    print('%-16s I %6.1f  peak %5.1f  → %+5.1f дБ' % (name, i, p, out[name]))
with open(os.path.join(SND, 'levels.json'), 'w') as fh:
    json.dump(out, fh, indent=1, ensure_ascii=False)
