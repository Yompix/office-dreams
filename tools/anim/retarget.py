#!/usr/bin/env python3
"""Клипы Quaternius Universal Animation Library 1 и 2 (CC0) → models/anims.glb под скелет models/male.glb.

  python3 tools/anim/retarget.py UAL1_Standard.glb UAL2_Standard.glb   (файлы без _RM — без смещения корня)

Скачать: quaternius.itch.io/universal-animation-library и …-2 (Standard, бесплатно, CC0), папка Unreal-Godot.
Перенос: мировой поворот кости = поворот в клипе × (покой манекена)⁻¹ × покой male.glb — позы покоя у обоих «Т»,
отличаются только осями и пропорциями. Таз — смещение клипа × (высота таза male / манекена). Персонажи MakeHuman в игре
получают покой male.glb (chars2.js, QW), поэтому клипы встают на всех как есть, с пальцами и носками.
Потом сжать (сырой файл пишется в $TMPDIR, по умолчанию /tmp):
  gltf-transform resample /tmp/anims_raw.glb /tmp/anims_rs.glb --tolerance 0.0001
  gltf-transform meshopt /tmp/anims_rs.glb public/games/nightshift/models/anims.glb --level medium"""
import sys, os, numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gltfio import Skel, qmul, qinv, qnorm, qrot
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MODELS = os.path.join(ROOT, 'public/games/nightshift/models')
# что берём (бой, магия, плавание, паркур — не нужны)
WANT = {
  'UAL1': ['Crouch_Fwd_Loop', 'Crouch_Idle_Loop', 'Death01', 'Driving_Loop', 'Fixing_Kneeling', 'Hit_Chest', 'Hit_Head', 'Idle_Loop',
           'Idle_Talking_Loop', 'Idle_Torch_Loop', 'Interact', 'Jog_Fwd_Loop', 'PickUp_Table', 'Push_Loop', 'Punch_Jab', 'Sitting_Enter',
           'Sitting_Exit', 'Sitting_Idle_Loop', 'Sitting_Talking_Loop', 'Sprint_Loop', 'Walk_Formal_Loop', 'Walk_Loop'],
  'UAL2': ['Chest_Open', 'ClimbUp_1m', 'Consume', 'Farm_Harvest', 'Farm_PlantSeed', 'Farm_Watering', 'Hit_Knockback', 'Idle_FoldArms_Loop',
           'Idle_Lantern_Loop', 'Idle_No_Loop', 'Idle_Rail_Call', 'Idle_Rail_Loop', 'Idle_TalkingPhone_Loop', 'LayToIdle', 'OverhandThrow',
           'Walk_Carry_Loop', 'Yes', 'Zombie_Idle_Loop', 'Zombie_Scratch', 'Zombie_Walk_Fwd_Loop'],
}

def main(paths):
    tgt = Skel(os.path.join(MODELS, 'male.glb'))
    bones = [n for n in tgt.name if n not in ('Eyebrows', 'Eyes', 'SuperHero_Male')]
    WTt, WRt, _ = tgt.world()
    clips = {}
    for p in paths:
        src = Skel(p); lib = 'UAL2' if 'UAL2' in os.path.basename(p) else 'UAL1'
        WTs, WRs, _ = src.world()
        hs, ht = WTs[src.idx['pelvis'], 1], WTt[tgt.idx['pelvis'], 1]
        for name in WANT[lib]:
            ts, T, R, S = src.sample(name, 30)
            F = len(ts)
            _, WRa, _ = src.world(T, R, S)
            # мировые повороты целевых костей
            WRo = np.repeat(WRt[None], F, 0)
            for b in bones:
                if b in ('Armature', 'root') or b not in src.idx: continue
                i, k = src.idx[b], tgt.idx[b]
                WRo[:, k] = qnorm(qmul(qmul(WRa[:, i], qinv(WRs[i])[None]), WRt[k][None]))
            tracks = {}
            for b in bones:
                k = tgt.idx[b]
                if b in ('Armature', 'root') or b.endswith('_leaf_l') or b.endswith('_leaf_r'): continue
                pk = tgt.parent[k]
                loc = qnorm(qmul(qinv(WRo[:, pk]), WRo[:, k]))
                for f in range(1, F):   # без скачков знака — ровная интерполяция
                    if (loc[f] * loc[f - 1]).sum() < 0: loc[f] *= -1
                tracks[k] = {'rotation': loc}
            # таз: смещение клипа в пространстве корня, масштаб по высоте таза
            i, k = src.idx['pelvis'], tgt.idx['pelvis']
            tracks[k]['translation'] = T[:, i] * (ht / hs)
            clips[name] = (ts, tracks)
            print(f'{lib} {name:24s} {F:4d} кадров {ts[-1]:5.2f} с')
    from gltfio import save_anim_glb
    # сырой файл — во временную папку: в игру идёт только сжатый models/anims.glb (см. шапку)
    out = os.path.join(os.environ.get('TMPDIR', '/tmp'), 'anims_raw.glb')
    save_anim_glb(out, tgt, set(bones), clips)
    print('→', out, os.path.getsize(out) // 1024, 'КБ')

if __name__ == '__main__':
    main(sys.argv[1:])
