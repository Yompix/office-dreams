#!/usr/bin/env python3
"""18+ версия трейлера: карточка «18+» + основной трейлер, в который вставлены сцены жестокости
(изба охотника с останками и смерть от Жерди). Берёт готовый trailer_16x9.mp4 и клипы h_tr_gore / h_death_pole.

  python3 tools/make_trailer18.py
"""
import os, subprocess, tempfile

OUT = '/tmp/claude-0/glukhoman-trailer'
SND = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'glukhoman', 'sounds')
FONT = '/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf'
FONT2 = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
# точки вставки в основном трейлере (по монтажному листу make_trailer.py): после «просеки» и перед тишиной
CUT1, CUT2 = 38.4, 56.7


def run(c):
    subprocess.run(c, check=True)


def enc(args, out):
    run(['ffmpeg', '-y', '-loglevel', 'error'] + args + ['-c:v', 'libx264', '-crf', '21', '-preset', 'slow', '-pix_fmt', 'yuv420p', '-r', '30',
                                                         '-c:a', 'aac', '-b:a', '192k', '-ar', '48000', '-ac', '2', out])


def main():
    with tempfile.TemporaryDirectory(dir='/tmp/claude-0') as t:
        main_ = f'{OUT}/trailer_16x9.mp4'
        card = f'{t}/c.mp4'
        vf = (f"drawtext=fontfile={FONT}:text='18+':fontsize=200:fontcolor=0xC02020:x=(w-text_w)/2:y=h/2-230,"
              f"drawtext=fontfile={FONT2}:text='Насилие, кровь, расчленёнка':fontsize=56:fontcolor=white:x=(w-text_w)/2:y=h/2+20,"
              f"drawtext=fontfile={FONT2}:text='Версия трейлера только для взрослых':fontsize=40:fontcolor=0xA0A0A0:x=(w-text_w)/2:y=h/2+110,"
              "fade=t=in:st=0:d=0.4,fade=t=out:st=3.1:d=0.4")
        enc(['-f', 'lavfi', '-i', 'color=c=black:s=1920x1080:r=30:d=3.5', '-f', 'lavfi', '-i', 'anullsrc=r=48000:cl=stereo', '-t', '3.5', '-vf', vf], card)
        parts = [card]
        for i, (a, b) in enumerate(((0, CUT1), (CUT1, CUT2), (CUT2, None))):
            p = f'{t}/m{i}.mp4'
            enc(['-ss', str(a)] + (['-to', str(b)] if b else []) + ['-i', main_], p)
            parts.append(p)
            if i == 0:
                g = f'{t}/g.mp4'
                lb = 'drawbox=x=0:y=0:w=iw:h=110:color=black:t=fill,drawbox=x=0:y=ih-110:w=iw:h=110:color=black:t=fill'
                enc(['-ss', '0', '-t', '5.5', '-i', f'{OUT}/clips/h_tr_gore.mp4', '-i', f'{SND}/amb/dread.ogg', '-i', f'{SND}/breath_close.ogg', '-i', f'{SND}/stinger_soft.ogg',
                     '-filter_complex', f'[0:v]{lb},fade=t=in:st=0:d=0.3,fade=t=out:st=5.2:d=0.3[v];[1:a]atrim=0:5.5[a1];[2:a]adelay=800|800[a2];[3:a]adelay=3600|3600[a3];'
                                        '[a1][a2][a3]amix=inputs=3:normalize=0,volume=1.6[a]', '-map', '[v]', '-map', '[a]', '-t', '5.5'], g)
                parts.append(g)
            if i == 1:
                d = f'{t}/d.mp4'
                enc(['-ss', '0', '-t', '3.6', '-i', f'{OUT}/clips/h_death_pole.mp4', '-i', f'{SND}/stinger.ogg', '-i', f'{SND}/choir_hurt.ogg', '-i', f'{SND}/jumpscare.ogg',
                     '-filter_complex', '[0:v]fade=t=out:st=3.2:d=0.4[v];[1:a]volume=0.8[a1];[2:a]adelay=1200|1200[a2];[3:a]adelay=2200|2200[a3];'
                                        '[a1][a2][a3]amix=inputs=3:normalize=0[a]', '-map', '[v]', '-map', '[a]', '-t', '3.6'], d)
                parts.append(d)
        n = len(parts)
        ins = sum((['-i', p] for p in parts), [])
        fc = ''.join(f'[{i}:v][{i}:a]' for i in range(n)) + f'concat=n={n}:v=1:a=1[v][a];[a]loudnorm=I=-14:TP=-2:LRA=11[an]'
        run(['ffmpeg', '-y', '-loglevel', 'error'] + ins + ['-filter_complex', fc, '-map', '[v]', '-map', '[an]', '-c:v', 'libx264', '-crf', '21',
                                                         '-preset', 'slow', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k', '-ar', '48000',
                                                         '-movflags', '+faststart', f'{OUT}/trailer_16x9_18plus.mp4'])
        print('ok trailer_16x9_18plus.mp4')


if __name__ == '__main__':
    main()
