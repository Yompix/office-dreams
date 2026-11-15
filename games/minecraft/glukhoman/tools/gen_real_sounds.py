#!/usr/bin/env python3
"""Настоящие записи вместо синтеза: берёт звуки BigSoundBank (лицензия «как CC0», см. docs/SOUND_CREDITS.md)
из tools/sound_src/NNNN.ogg, обрабатывает (обрезка, EQ, тон, эхо, слои с нашими звуками) и пишет:
  - в мод: src/main/resources/assets/glukhoman/sounds/*.ogg (те же имена, что были у синтеза);
  - для трейлера/пробы: /tmp/claude-0/glukhoman-trailer2/sfx_real/*.wav (подхватывает tools/make_proof.py).
Сырьё в git не кладём (tools/sound_src/ в .gitignore). Запуск: python3 tools/gen_real_sounds.py
"""
import os, subprocess

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, 'sound_src')
SND = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'glukhoman', 'sounds')
REAL = '/tmp/claude-0/glukhoman-trailer2/sfx_real'


def ff(inputs, fc, out, dur=None):
    """inputs: [(id, start, len)], fc: filter_complex с выходом [o]."""
    cmd = ['ffmpeg', '-y', '-loglevel', 'error']
    for i, s, d in inputs:
        cmd += ['-ss', str(s), '-t', str(d), '-i', os.path.join(SRC, f'{i}.ogg')] if not i.startswith('/') else ['-i', i]
    cmd += ['-filter_complex', fc, '-map', '[o]', '-ac', '1', '-ar', '44100']
    if dur:
        cmd += ['-t', str(dur)]
    if out.endswith('.ogg'):
        cmd += ['-c:a', 'libvorbis', '-q:a', '4']
    cmd.append(out)
    subprocess.run(cmd, check=True)


def mod(name):
    p = os.path.join(SND, name.replace('.', '/') + '.ogg') if name.startswith('amb.') else os.path.join(SND, name + '.ogg')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


def main():
    os.makedirs(REAL, exist_ok=True)
    norm = 'loudnorm=I=-20:TP=-2'
    # --- фон леса: ветер в соснах + ночной лес + далёкая сова, мягкие края для петли
    ff([('0904', 0, 32), ('0555', 0, 32), ('1764', 0, 4)],
       '[0]lowpass=f=4000,volume=1.0[a];[1]lowpass=f=3000,volume=0.7[b];[2]lowpass=f=1800,aecho=0.6:0.5:400:0.3,volume=0.25,adelay=14000[c];'
       '[a][b][c]amix=inputs=3:normalize=0,afade=t=in:d=1.5,afade=t=out:st=30:d=2,' + norm + '[o]', mod('amb.forest'), 32)
    ff([('0155', 0, 5)], '[0]lowpass=f=3500,afade=t=in:d=0.8,afade=t=out:st=3.5:d=1.5,' + norm + '[o]', mod('wind_gust'))
    # --- шаги: близкий хруст снега и далёкие шаги по сучьям
    ff([('0208', 0, 0.6)], '[0]highpass=f=80,afade=t=out:st=0.4:d=0.2,loudnorm=I=-18:TP=-2[o]', mod('steps_near'))
    ff([('3363', 0, 3.5), ('1301', 0, 3.5)], '[0][1]amix=inputs=2:normalize=0,lowpass=f=1800,aecho=0.7:0.6:180:0.3,volume=0.8,' + norm + '[o]', mod('steps_far'))
    ff([('1299', 0, 1.4)], '[0]aecho=0.7:0.6:300|700:0.3|0.15,' + norm + '[o]', mod('branch_snap'))
    # --- дерево: двери и скрип пола (Жердь «ступает» скрипом)
    ff([('3211', 0, 2.2)], '[0]highpass=f=60,' + norm + '[o]', mod('door_creak'))
    ff([('0518', 0, 0.9)], '[0]asetrate=44100*0.8,aresample=44100,lowpass=f=2500,' + norm + '[o]', mod('pole_step'))
    ff([('0320', 0, 1.5)], '[0]lowpass=f=1600,equalizer=f=180:t=q:w=1:g=6,aecho=0.6:0.4:60:0.3,' + norm + '[o]', mod('knock'))
    # --- дыхание и голоса тварей
    ff([('2195', 0, 3)], '[0]highpass=f=120,' + norm + '[o]', mod('breath'))
    ff([('1103', 0, 2)], '[0]highpass=f=120,volume=1.2,' + norm + '[o]', REAL + '/breath.wav')
    ff([('2107', 0, 2.2)], '[0]asetrate=44100*1.25,aresample=44100,highpass=f=300,aecho=0.6:0.5:120:0.3,' + norm + '[o]', mod('listener_scream'))
    ff([('2107', 0, 4), ('0486', 0, 4)],
       '[0]asetrate=44100*0.62,aresample=44100,chorus=0.6:0.8:50|70:0.4|0.3:0.25|0.4:2|1.5[a];[1]asetrate=44100*0.7,aresample=44100[b];'
       '[a][b]amix=inputs=2:normalize=0,lowpass=f=3500,aecho=0.8:0.7:500|900:0.4|0.25,' + norm + '[o]', mod('choir_roar'))
    ff([('2107', 0, 6)], '[0]asetrate=44100*0.5,aresample=44100,lowpass=f=900,chorus=0.7:0.9:60|90|110:0.4|0.35|0.3:0.3|0.4|0.5:1|1.4|2,'
       'aecho=0.8:0.8:800:0.5,afade=t=in:d=1,afade=t=out:st=4:d=2,' + norm + '[o]', mod('choir_hum'))
    # --- плоть и кости (расправа)
    ff([('2593', 0, 1.5), ('1409', 0, 1.5)], '[0][1]amix=inputs=2:normalize=0,lowpass=f=6000,' + norm + '[o]', mod('choir_hurt'))
    # --- рация, сердце, крик вдалеке
    ff([('0312', 0, 3)], '[0]highpass=f=200,' + norm + '[o]', mod('radio_static'))
    ff([('0218', 0, 2)], '[0]lowpass=f=500,volume=2,' + norm + '[o]', mod('heartbeat'))
    ff([('1662', 0, 3)], '[0]lowpass=f=1400,aecho=0.8:0.8:900|1600:0.4|0.25,volume=0.6,afade=t=out:st=3:d=1.5,' + norm + '[o]', mod('distant_scream'), 4.5)
    # --- удары скримеров: скрежет металла + хруст костей + наш низкий удар
    synth = os.path.join(SND, 'stinger.ogg')
    ff([('0303', 0, 1.6), ('0596', 0, 1.2), (synth, 0, 0)],
       '[0]asetrate=44100*0.85,aresample=44100,highpass=f=200,volume=0.9[a];[1]volume=1.2[b];[2]lowpass=f=300,volume=1.3[c];'
       '[a][b][c]amix=inputs=3:normalize=0,alimiter=limit=0.9,' + norm.replace('-20', '-14') + '[o]', mod('jumpscare'), 3.5)
    # --- для пробного ролика/трейлера: настоящие версии тех же звуков
    for name, src in (('wind', 'amb.forest'), ('step', 'steps_near'), ('creak', 'door_creak'), ('tap', 'knock'), ('hit', 'jumpscare'),
                      ('swish', 'branch_snap')):
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', mod(src), REAL + f'/{name}.wav'], check=True)
    ff([('0555', 2, 24)], '[0]lowpass=f=500,volume=1.5,' + norm + '[o]', REAL + '/room.wav')
    print('ok')


if __name__ == '__main__':
    main()
