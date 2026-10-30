**Hush Till Six: characters & animation porting map** (read-only)

c2 = /home/user/office-dreams/public/games/nightshift/chars2.js; sc/en/c1 = scenes.js/engine.js/chars.js beside it; GLBs in …/nightshift/models/. GLB inspector: /tmp/claude-0/-home-user/0fa0b1a0-e4f5-529b-a67e-693eb4ab78d1/scratchpad/glbinfo.js.

## 1. Cast and assembly
Load (c2:21-45): v3 only if anims.glb + 6 humans + MeshoptDecoder all load (c2:31); else v2 = Quaternius male/female + Hair_*/Eyebrows_* (c2:35, makeSkinned 464-499); else v1 box people with canvas faces (c1:76-78,147). Port only v3.

castV3 (c2:2095-2140), legacy keys:
- temych = Ethan (ethan.glb); ethanThing = his double (grey, deadEyes, jerk, headMax 2.8)
- smenshik = day guard (dayguard.glb + code-built cap, c2:506)
- mihalych = Frank (frank.glb, marker `sheet`, c2:530)
- cleaner = Gus (gus.glb, mop); victim = Gus corpse (dead)
- katya = Lily (lily.glb, sweater tinted yellow, wet, noBlink, lipLag .12); katyaSmile (grey, smile 1.5); katyaThing (grey, smile 2.1, deadEyes, jerk); martha (lily + lathe dress, c2:518)
- monster = Tenant (tenant.glb, nude, long hair): h 2.4, bone scales arm 1.1/forearm 1.08/hand 1.25/neck 1.18, grey, deadEyes, tilt .45, jerk, headMax 2.3, stopmo 1/7 s, shroud + gown.

GLBs: one 53-joint UE-named skeleton (Root, pelvis, spine_01-03, neck_01, head, clavicle/upperarm/lowerarm/hand, thumb/index/middle/ring/pinky_01-03, thigh/calf/foot/ball, _l/_r). Root is rotated −90°X, so the skeleton is Z-up and pelvis height sits in .z (ethan .959, lily .849, tenant 1.341). Meshes: skin, eyes, brows, lashes, teeth, tongue, hair, cloth_*. No eye/jaw bones. EXT_meshopt_compression + KHR_mesh_quantization are required. Built by tools/chars/gen.py (MPFB2/Blender, CC0; ARKit + Meta visemes; bones aligned to the male.glb T-pose and applied as rest), then build.sh runs meshopt.

makeHuman (c2:1767-1840):
- Renames head→Head and Root→root (1774).
- Re-bases rests to the Quaternius world rotations QW (c2:41), keeping joint positions, and recomputes boneInverses (1776-1786). Clips and hard-coded bone axes then apply as-is.
- humanMat is chosen by mesh name (1796). Eye centres and mouth come from geometry (1807-1815).
- ss = height/1.81 scales the IK/pose constants (1822). The pelvis track is multiplied by the pelvis-height ratio (clipsFor 1756).
- Tenant extras (1828, 1836-1838); props in addProps/fitProps (502-571).

humanMat (1645-1746):
- Luma tint {part: hex|[hex,gain,mix]}, dim, grey, wet, gore, uRip wounds (1710), sockets, mouth-depth darkening, face-shadow softening.
- Cine-only custom lighting: skin wrap + dual GGX, hair strand highlight, cloth sheen (1617-1644).
- Hair uses alpha-test + alpha-to-coverage.

## 2. Skeleton and clips
- anims.glb = male.glb skeleton + 42 Quaternius UAL1/2 clips (tools/anim/retarget.py; in-place; fingers ~static).
- anims2.glb = 14 mocap clips (Rokoko "Rk_" with fingers, CMU mop; tools/anim/mocap.py).
- male.glb clips: v2 only.
- The loader keeps only quaternion tracks + pelvis.position (c2:36-40). A clip loops if its name ends in _Loop.

Used clips:
- Quaternius: Idle_Loop 2.5 s (base; 'monster' pose = .3×), Idle_Talking_Loop 2.93, Sitting_Idle_Loop 1.67, Sitting_Talking_Loop 2.93, Walk_Loop 1.33, Push_Loop 2.67 (door), Crouch_Idle_Loop 2.93 (eat), Crouch_Fwd_Loop 2.0 (crawl), Fixing_Kneeling 5.2 (Frank), LayToIdle 1.53 (frame 0 = lying), Death01 2.4 (gore deaths), Sprint_Loop .67, Zombie_Idle_Loop/Zombie_Walk_Fwd_Loop 1.33 (Tenant), Idle_Torch_Loop 1.27, Idle_TalkingPhone_Loop 2.93.
- Story reactions: Yes/Idle_No_Loop/Idle_FoldArms_Loop 2.5. Living people nod/shake with head code instead.
- Finger-pose sources only: Farm_Harvest/Interact/Consume.
- Mocap: Rk_Watching_Loop 24.5, Rk_LookAround_Loop 29.9 (idles), Rk_Chatting_Loop 22.9, Rk_Chatting2_Loop 36.9, Rk_Conversation_Loop 23.5 (talk), Rk_SitIdle_Loop 24.0, Rk_SitTalk2_Loop 16.0 (Ethan seated), Rk_Walk_Loop 3.47 (time ×0.74), Cmu_Mop_Loop 12.0 (Gus).

Unused: Driving, Hit_Chest, Hit_Head, PickUp_Table, Punch_Jab, Sitting_Enter/Exit, Walk_Formal, Chest_Open, ClimbUp_1m, Farm_PlantSeed/Watering, Hit_Knockback, Idle_Lantern, Idle_Rail_Call/Loop, OverhandThrow, Walk_Carry, Zombie_Scratch, Jog_Fwd, Rk_Arguing, Rk_Pointing, Rk_SitIdle2, Rk_SitTalk, Rk_Phone.

Mixing has two modes:
- **String pose** → POSE map (683-688). A change crossfades over .35 s, or switches hard on a cut (1315-1322). timeScale = stride (back = −stride) (1339).
- **Pose object** (act3Body 1113-1175): clips [[name, time, weight]] at times the scene gives (so it can scrub), weights normalised, timeScale 0.
- Automatic layers on pose objects:
  - talk fade Idle→Talking (3/s);
  - reaction clip (4/s);
  - natClip swaps in per-actor mocap with phase offsets (1074-1086);
  - ambClock keeps idles moving while scene time waits for a tap (1099-1112).
- bonesRestore/Snap (1064) is a workaround for three.js writing only changed tracks.
- A cut is the flag, a gap over 0.5 s, or time going backwards (1292).

Root motion: none. sc sets root position/yaw every frame (sc:3088). Stride follows ground speed (c2:1330-1336; sc:1134 time = distance/cycle).

Ground fitting:
- footLock (750-767): while moving, the lowest foot within 2 cm locks its world XZ via IK. Fades in at 14/s, out at 3.5–9/s, releases beyond 16 cm, and changes state only when dt>0.
- Others: footFloor (691), heelFloor (698), feetIn + stance narrowing (1055, 1350), legs 'plant' + pelvis offset (1144), scripted steps P.feet (1153; sc:1328), seatFeet (1089), kneel (733).
- Scene side: solidPass pushes actors out of geometry and re-poses at dt=0 (sc:3495); contact shadows (sc:3146); gLift (sc:1870).

## 3. Face
53 skin shapes: viseme_aa CH DD E FF I kk nn O PP RR SS TH U; browDownL/R, browInnerUp, browOuterUpL/R, cheekSquintL/R, eyeBlinkL/R, eyeLookDown/In/Out/Up L/R, eyeSquintL/R, eyeWideL/R, jawOpen, mouthClose, mouthFrownL/R, mouthFunnel, mouthLowerDownL/R, mouthPressL/R, mouthPucker, mouthSmileL/R, mouthStretchL/R, mouthUpperUpL/R, noseSneerL/R.
- Brows, lashes, hair and teeth/tongue (9 each) carry subsets. Eyes have only the 8 eyeLook shapes, so gaze is morph-driven.
- Unused: mouthClose/Funnel/Pucker.
- faceV3 (1997-2091) builds one weight dict per frame and writes it to every part mesh by name. The tongue only follows jaw/vowels (2087).

Lip-sync combines loudness and text:
- Loudness: en:65 analyser RMS → talkV (en:1842).
- Text: visemeAt (en:434-448) takes the letter sounding now in the current word (word starts from timing_<lang>.json `w`, English `ew`) and maps it to a viseme. It returns 'sil' after ~75 ms per letter.
- sc:3213-3220 feeds the speaker talk/vis and gives listeners hear.
- In c2: mouth attack 22/s, release 9/s (1524); lipLag (1522); 60 ms viseme crossfade, gain .4+.9·loudness, visibility table VW, jaw table JAW (1748-1754, 2044).
- With no text, visemes are random, scaled by loudness (2052). Lips press after phrases (2076).

Blink (2006): 70 ms close / 120 ms open.
- Interval: 1.8–4.2 s talking, 2.8–6.5 s listening, 4–9 s afraid.
- 12% double blinks, 20% partial.
- Forced on gaze jumps and phrase edges. The Tenant and the fakes never blink.

Gaze and head (1448-1494):
- Target is C.look. Aversions in gazeLife (1253): eyes move fully, head 35%. Clamped by headMax.
- The head follows a critically damped spring, ω 8 (1247), and the eyes lead it.
- Eye angle relative to Head drives eyeLook*, plus state-dependent microsaccades (2017-2036).
- headLife adds drift, onset nods, listener nods and yes/no reactions (1268-1287).
- Split: torso takes 30% (≤.42 rad), then neck_01 45%, Head 90%.
- Tenant tilt/snaps/shake (1490, 1499).

Brows: browU (up/in) from face + loudness + flicks (1527) → browInnerUp/OuterUp/Down (2038).

Expressions:
- faceSet fields: smile, wide, browUp, browIn, dart, mouth, press, frown, tense, squint, scream, dead. Changes ease over ~0.3 s (2003).
- Named sets: base, smile, fear, worry, plead, alert, hush, gasp, terror, calm, sad, grim, warm, dread, photo, grin, scream (2109-2137), plus per-shot sets written by sc.
- smile>1 gives a stretched, inhuman grin (2065). Scream opens the jaw ×1.75 on the Tenant (2079).

## 4. Code-driven overrides
Primitives: aimBone (705), analytic two-bone ik2 with pole (718-731), keepWorld (744), setWorldQ (878), rotChar (1191), wiggle (771), leanTo (922).
- knock/press/slam (1364-1381): left palm IK on the glass (glassD), right fist IK, 3 hits per 1.9 s → C.knockHit. press/slam put both palms on the glass.
- give (1396): arm aimed forward, fist .55, keys on hand_r.
- paper/sitpaper (1398-1406): both arms aimed. Frank's sheet is centred between the grip points and turned to the look target (1540).
- reach, mop, eat, embrace, hug, smoke, weak (1382-1447, 616-679).
- reachArm (904-921): IKs the wrist so the contact point `by` (palm/knuck/tip/thumb/pinch, handPt 868) hits the target, in 3 passes.
  - orientHand (888) points fingers along fdir and the palm along pdir; twistShare (880) gives 60% of the twist to the forearm.
  - With weight<1 it solves the full pose, then blends. `at` may be a body-relative function (sc:1265).
- breathe, painLife, fingerLife, gestBeat (1199-1238).
- Sitting: root at the chair (sc:970). For mocap sits, seatFeet lifts the pelvis 4.5 cm and IKs the feet to the floor.
- Lying: LayToIdle frame 0 + struggle/twitch (1435) + layFloor (1041-1053). If a joint is below floor+radius, its parent rotates up about a horizontal axis. sc gLift then raises the root (sc:1870-1876).
- Fingers: HANDP (812-846) is built from clip frames, mirrored as (x,−y,−z,w).
  - Poses: relax, grip, open, flat, phone, cup; amplified fist/claw/hook/pinch/point; soft = average of Rk_Watching.
  - setFingers slerps (857); a procedural fist bends about local X (779); the Tenant uses hookHand (848).
- Props: prop3 meshes (931), GRIP3 in the hand frame (989-1003), holdProp eases 0.22 s between hands (1005), wearProp (1177), propAt/propAim (1025).

## 5. Capsules, shaders
Capsules (Tenant only):
- CAP_DEF (1935): 14 bone segments, percentile q from .55 (torso) to .85 (legs).
- capsBuild (1942): skin verts are grouped by dominant bone. Each end's radius is the q-percentile distance, so the capsules are really cones.
- capsOn (1954-1964): CAP_VS runs after skinning and pushes vertices inside a capsule onto its surface. Endpoints are moved into mesh space each onBeforeRender. Padding is .014·ss for the gown and .008·ss for hair.

Other Tenant tricks:
- Void eyes are a black glossy material with no map (1652), plus darkened sockets.
- clawHands edits vertices and adds an aNail attribute (1845).
- The shroud is painted on the skin, with inflation before skinning (1572-1612).
- skinGown builds a skinned mesh at runtime, with a torn-hem shader (1886-1994).
- The smile and scream are morphs.

PS1 vertex snap (c2:280) is disabled (world.js:99). v2-only tricks: cloth drawn by a zone shader (49-170) + shell mesh (309), UV-painted mouth/needle teeth/tears (171-259), blink via eye emissive (1513).

## 6. Interface
- Object (makeHuman 1771): root, inner, o, bone{}, ss, uniforms U/HU, M (morph meshes), mesh/brows/lashes/hairM/eyeM, headFwd, mouthL, faceSets, addFace(name,set), update(t,dt).
- Per-frame inputs from sc (3076-3093, 3213-3220): pose, look (Vector3/null/'cam'), talk, vis, hear, hearOk, react ('yes'|'no'|'arms'[+face]), faceSet, cut, smDone, gore, scream, shake, struggle, twitch, no, actT, tiltNow, eatHold, glassD, HU.uRip*/uSocket, eyeM.emissive.
- Output: knockHit.
- String poses: stand, talk, sit, sittalk, sitfloor, knock, press, give, paper, monster, reach, walk, back, push, hug, eat, lay, mop, slam, sitpaper, smoke, embrace, weak, kneel.
- Pose-object keys (785-794): clips, fing, arm, spine, hold, legs, pelvis, feet, still, talk, talkK, look, listen, wig, twist, gest, heels, toes, after, post, wear, sheet, br, pain.
- Exports (2181): load, cast, propAt, propAim, propsFrame, HANDP, prop3, handPt, reachArm, layFloor, hasClip.

## Godot 4.7 recommendation
1. **Assets:** re-export the humans from gen.py without the meshopt/quantize step. Godot's built-in importer can't decode EXT_meshopt_compression, which these files require.
   - Use root/Head names and bake the QW rest re-base (port c2:1776-1786 to Blender or an EditorScenePostImport script).
   - Import anims*.glb as AnimationLibrary, rotations + pelvis position only.
   - No BoneMap is needed. Avoid "Overwrite Axis", which would break fist(), wiggle and the HANDP mirroring.
   - Pelvis scaling: Skeleton3D.motion_scale, or a multiply in ClipStack.
   - Godot, like three.js, is Y-up right-handed with models facing +Z, so c2 constants and (x,y,z,w) quaternions port verbatim.
2. **Nodes:** Actor → Inner (scale) → Skeleton3D + one MeshInstance3D per part.
3. **Base pose:** a ClipStack script that samples Animation.rotation_track_interpolate/position_track_interpolate at scene-given times, with normalised weights and its own .35 s crossfade.
   - This reproduces act3Body, string poses and ambClock.
   - It maps tracks by bone name, so library paths needn't match; AnimationPlayer only stores the libraries.
4. **Overrides:** a custom SkeletonModifier3D chain in c2 order: feet → breathing → fingers → spine → arms (ported ik2/reachArm) → head → layFloor/props.
   - Optional: LookAtModifier3D for neck/head (influence ≈.45/.9), with a script-moved target so the spring and aversions stay.
   - TwoBoneIK3D (4.6+, verify) could replace ik2, but not reachArm's contact passes.
5. **Face:** cache find_blend_shape_by_name per mesh and call set_blend_shape_value from one dict.
6. **Lip-sync:** port visemeAt. Precompute RMS envelopes into the timing JSON so it is deterministic, seekable and safe on web.
7. **Shaders:** one ShaderMaterial per part kind; light() for skin/hair/cloth; capsule push in vertex(), with arrays refreshed on skeleton_updated.

## Risks
- In Godot, VERTEX is already skinned. Everything that uses rest-pose positions (vB: gore, wounds, sockets, shroud, hem discard, claws) needs those positions copied into CUSTOM0 at import. Deformations done before skinning (shroud/hem inflation, claws, gown) must be baked into the mesh; meshes are shared resources.
- Ordering: sc reads bones right after update (solidPass re-pose at dt=0, bodyAt, gore post-steps, contact shadows). Deferred mixer/modifier processing would give stale poses, so drive the actors manually from one director and keep the dt=0 re-run safe.
- Additive writes (fist, wiggle, fingerLife) need a fresh base every frame. The Tenant's bone scales must survive animation.
- Blend weights go above 1 (stretch ×2.9, jaw ×1.75); check Godot doesn't clamp them.
- light() can't separate shadow from ATTENUATION. The web (Compatibility) renderer has no SSS; check alpha-to-coverage there.
- Web performance: 53-shape skins (8.6–14.5k verts) × ~8 meshes × 4 actors, and the capsule loop also runs in shadow passes.
- A seeded global PRNG (world.js:88) drives blinks and saccades.
- The QA probes (tools/anim/clip_check.js, hold_probe.js, life_probe.js) need Godot equivalents to keep the owner's no-penetration standard.
