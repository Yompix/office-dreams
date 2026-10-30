# Hush Till Six — 3D world and staging porting map

W = `public/games/nightshift/world.js`, S = `scenes.js` (same folder). Nothing was edited.

The whole world is built in code: only the characters come from GLB files. The main pain points for the port are: about 225 shots whose staging is a JavaScript function of time; three custom shaders (facade glass, CRT monitors, post-processing) that the glTF exporter cannot carry; and material and light settings that have to be refit by hand. My recommendation: export the geometry and textures automatically, rewrite the shaders and APIs by hand, and bake the deterministic staging into animations.

## 1. How the world is built
- **Graphics modes** (W:53-86):
  - `FINE` means PC.
  - `GFX` is retro, cine or ultra. It comes from `?gfx=`, from localStorage `ns1_gfx`, or by default from the GPU tier (W:64-82). `CINE` means cine or ultra.
  - Internal resolution (W:86, 95-100): retro is 270×480 (or 480×270) × HD (1 or 2). CINE uses the screen's long side, capped at 1280 on phone, 1920 on PC, 3840 in Ultra, then multiplied by `rs`. `rs` is set automatically between .55 and 1 by `perfTick` (S:2956).
  - There is no FSR or DLSS. Instead there is dynamic resolution plus a CAS-style sharpen, Ultra only.
- **Global ShaderChunk patches** (W:15-47): fewer unrolled point-light loops and a NaN clamp. Godot doesn't need them.
- **`ps1()` vertex snap** (W:102): effectively switched off (`SNAP=1e5`, W:99). Drop it.
- **Materials, `mat(color,map,o)`** (W:132, cached):
  - `o.basic` gives MeshBasic. CINE gives MeshStandard with `pbr()` (W:113: roughness per texture kind, metalness, env .7, bump and roughness maps). Retro gives MeshLambert.
  - Options: emissive, transparent, side, `decal` (polygonOffset −1/−4, W:124), `fog:false`, `glow:k`, rough, metal. `litMat` (W:121) uses the same split.
  - `glow` (W:127) multiplies the final colour by k, so only lamps bloom.
- **Textures**:
  - Every texture is drawn on a canvas. `ctex()` (W:154) draws 4× larger in CINE (up to 1024 px, linear filtering, mipmaps, 8/16× anisotropy). Retro keeps native size with Nearest filtering.
  - `TX` (W:173-323) has about 45 entries: floors, walls, facade (plus emissive and dark variants), rack LEDs, salt, signs, journal note/note2 (text at W:339-346), skies, grime, logo, puddle, sheen, smear, clock, badges and the torch cookie.
  - In CINE, `hiTex` (W:387-483) replaces 13 surfaces with fbm-noise maps that add height and roughness.
  - About 25 more canvases are created inline in S: notice board (S:144), vending machine, photo, book, smudges, palm shadow, footprints, AO strip (S:26), door plates, lift indicators.
  - All text goes through `tr()`, so it is baked in the current language.
- **Geometry helpers** (W:487-504): `box` (cached per size), `plane`, `cyl`, `room` (a box seen from inside via a BackSide material clone, never scale −1), and `label`/`relabel` (a text plane that can be redrawn).
- **Retro post-processing** (S:3005-3035): barrel distortion, VHS row jitter, head-switch band, tape crease every 13 s, RGB split, gamma 1.25, cold/warm grade, CCTV desaturation, IR mode, scanlines. Last comes a **4×4 Bayer dither and quantise to 22 levels**. The result is upscaled with nearest filtering.
- **CINE pipeline** (S:2758-2953):
  - Passes: HalfFloat render target with MSAA×4, then a ¼-resolution downsample, then two Gaussian blur passes.
  - Composite: barrel distortion, CCTV jitter, scare smear, chromatic aberration, sharpen, edge blur. Then bloom (max(b−.6,0)·1.15) plus halation and ACES tone mapping (exposure .68 corridor, 1.05 server, .92 elsewhere). Last: gamma 1.15, split-tone with skin protection, vignette, grain. SSAO is coded but switched off.
  - `cineLoc` (S:2806) adds:
    - light-shaft cones under lamps above 2.3 m (S:2762);
    - dust lit by the beams (S:2778);
    - one PMREM environment map per location (S:2835).
  - `cineTick` (S:2847): only 2 point lights cast shadows per frame.
  - `lightRig` (S:2789): pads every scene to the same light count with dummy lights at y −60, and adds the `rimC` and torch spotlights.
- **`World.draw(g,W,H,id,o)`** (S:3204, called from engine.js:1534):
  - `o` carries: t (shot time), T (global time), cx/cy/z (zoom), sx/sy (shake), talk, react, props, scare, cctv, vhs, creep, torch.
  - Order of work:
    1. `init` (S:2978).
    2. `stage` (S:3071): reset the location, apply `sh.face`, run `sh.set(o,t,sh)` to get the actor list, apply `sh.fog`, place the actors.
    3. `update`, then `solidPass`, then `tick`, then `post`.
    4. Blob shadows under bodies (S:3146).
    5. Monitor feeds (S:3126): 96×72 render targets, updated every 3rd frame.
    6. `aim` (S:3051): in portrait, tan(FOV/2) is multiplied by 1.9; zoom is half dolly, half lens.
    7. Auto rim light (S:3252), torch, `placeCreep` (S:3175).
    8. Lobby planar reflection (S:3109): the camera is mirrored in the plane z=6; actors marked `'refl'` appear only in this pass.
    9. Render target, then post-processing, then `g.drawImage` (flipped if `sh.mirror`).

## 2. Locations (`L`, S:2993)
Every location has FogExp2 with colour 0x0b0f14, `reset()`, `tick(t)`, a movable `faceL` point light and an IR light `irL`.

- **lobby** (S:35-389, fog .055): a hall 16×12×4.2 m (x ±8, z −6..6), a glass facade at z=6, and the street beyond it.
  - Lights:
    - ambient and hemisphere;
    - point lights l1 at (−1,3.8,0), l3, l2 (unreliable) and l4;
    - desk lamp 0xffc46a at (5.95,1.05,−.12);
    - boothLight 0x9fe0c0;
    - canopy light at (0,3.1,7.2);
    - sodium street lamp 0xff9a3a at (2.8,5.6,10.4);
    - cabLight.
  - Objects: ceiling panels, columns, turnstiles, two lifts (`setLift`, S:390) with a cab (S:93), a palm and its shadow, a clock (`setClock`, S:126), a vending machine.
  - **Booth** (x 3.6–7.8, z −1.55..0.45, S:167-305): 4 CRT monitors (`crt`, S:517) and Frank's jacket on a chair. On the desk: lamp, journal or tied book, keys, radio, mug, phone, drawer. Also: chairs, instanced salt, glass panes, the badge, a reflection pane, and the booth door hinged at (6.63,0,.45).
  - **Facade** (S:403-511): ShaderMaterial glass (reflection, grime, rain, palm print `uPrint`); door leaves hinged at x=±1 (`leafAng`, S:1143); writing in the condensation (`fogWrite`, S:1377); footprints.
  - Street: road, canopy, lamp, cat, car, a 30×36 m facade with a ghost window, buildings, a BackSide sky, rain as LineSegments (S:381). `setDawn` (S:395) and `props(P)` (S:361) switch states.
- **corridor** (S:527-724, fog .05). The same set plays the 7th floor, the 10th floor, CAM13 and "−2".
  - Size 2.6×26×3 m (z 0..−26). 7 tube lights at z = −1.8 − 3.6i, controlled by `setLamps`/`lampSet` (S:750, 947). Lamp 4 is unreliable.
  - Doors: 707 is hinged, with a black room behind it and fingers (`setFingers`, S:601). 713 has a light slit under it. Door plates switch between 7xx and 10xx (`setFloor`). Door10 has its own states.
  - `cleanSet(c)` (S:705) takes `{bucket:[x,z,ry,tip], mop:[x,z,ry,k], sign, wet, spill, smear:[x0,z0,x1,z1], door7, fingers, splat, blood}`. Presets are CLEAN and AFTER (S:943).
- **server** (S:756-802, fog .085): 8×7×3 m, with racks, a salt circle, candles, cold 0x7fd0b0 lights (one unreliable), a red alarm light and the CAM13 box.
- **stairs** (S:810-853, fog .06): 3 levels of 3 m each, with flights, rails, doors, `setFloor(k,n)` and 6 lamps.
- **basement** (S:856-923, fog .075): 2.4×14×2.6 m. It has 3 bulbs (the middle one unreliable), `sdoor` hinged at x=.47, a glowing slit, a salt line, a room with the circle, hand prints and `etorch`.
- **Flicker**:
  - `unreliable(t,seed)` (S:13) is deterministic. Each 15 s window has one event: 15% of the time a blackout of about 1.5 s, otherwise 2–6 drops of 40–120 ms. There is also `stut` (S:948).
  - Bug: the vending-machine flicker code sits in the corridor's tick (S:719), so it never runs.

## 3. Shots
- **Shot id**: one of three forms.
  - A key in `SH` (S:969-1108 plus 147 `SH.x=` lines, about 225 in total).
  - An `ALIAS` f01–f12 (S:2750).
  - `gore@<base>:<kind>[|c]` (S:1663). Kind is eat|drag|chest|neck|throat|face|eyes|hang|heart|limb, with an optional suffix L, D or F (who kills / who dies). The engine builds these ids at engine.js:397-406.
- **Shot fields**: `{loc,pos,look,fov,set(o,t,sh)→acts,face,fog,cctv,feeds,feedMap,mirror,torch,roll,noRim,noDolly,hum,ghost,post,tl}`.
- **Actor entry** (S:925, 3084): `[actor,x,y,z,ry,pose,look 'cam'|[x,y,z]|null,'refl']`.
  - A pose is either a chars2 preset name or a spec: `{clips:[[clip,t,w]],legs,pelvis,spine,arm{l|r:{at,by,fdir,pdir,w,pole}},fing,hold,look,wig,post}`.
  - `set` changes the location's state and can move the camera by writing `sh.pos`/`sh.look` (S:1501, 2240).
- **Helpers**: walkPath (S:929), stride/walker/shuffle/backAway (S:1111-1141, 1490), turnSteps (S:1328), lunge (S:951), keyAt (S:1775). The CCTV shots (S:2350-2431) read the engine's anomaly flags from `curProps.sh`.
- **Examples**:
  1. `guard` (S:985): static, one actor looking at the camera.
  2. `corridor_off` (S:1008): lamps go out in pairs. The monster then appears closer after each flash (z −18, −13, −8.5, −5) and is hidden during the strobe.
  3. `gore@hush_face:neck`. `GPLACE.hush_face='seat'` routes it to seatSet/seatPost with this timeline:
     - `GTS.neck {d 5.8,touch .35,hit .9,kill 1.75,rel 2.35,cam [[0,'w'],[1.45,'c'],[2.35,'w'],[3.1,'e']],kp,sfx}`.
     - seatCam cuts with a push-in of up to 14 cm.
     - seatVic: the victim freezes, jolts, struggles, slumps.
     - seatPost: the fingertips crawl, grip the temples, and twistHead turns the head 2.75 rad in .18 s.
- **Death timelines**:
  - GTS (death in the booth chair, S:1920), GTH (dragged from under the desk, S:2046), GTL (standing neck/hang/chest, S:1763).
  - They drive poses, IK contacts, lights, camera cuts (S:1865) and effects. The engine also reads `d`, `hit` (red flash), `splat` and `sfx` from them (engine.js:611, 1918).
  - The GTL chain runs in this order:
    1. gFit (S:1789) searches for a free spot with line of sight.
    2. gStage (S:1783).
    3. gSet, which calls gVic, gMon and gCam.
    4. gPost: reachArm, twistHead, layFloor plus gLift (S:1871).
  - Other kinds use goreFit/goreSet/gorePost (S:1644-1754).
  - Effects: guts as TubeGeometry (S:1548), spray (S:1595), and `wound`, which drives the skin's `uRip` uniforms (S:1575).
  - reachArm and layFloor live in chars2.js (lines 904 and 1044).

## 4. Pure data vs code
- **Can go to JSON as-is**:
  - The static shot fields via `JSON.stringify(World.shots)`. Moving shots overwrite `pos`/`look` while playing, so export before playback.
  - ALIAS, GTS/GTH/GTL/GSK/GPLACE, CLEAN/AFTER.
  - Staging coordinates, CREEP (S:3169), G_LIFT, SOL_SEG/TOL.
  - Journal text, light values and fog values.
- **Must be rewritten**: about 225 `set` closures, the pose builders, the gore solvers, the location APIs, the shaders, reflections and feeds, `aim`, `stage`, and the solid world. The location builders are code, but what they produce can be exported.
- **Baking option**:
  - Sample each shot at 30 fps for the length of its longest beat (timing_*.json).
  - Record actor roots, bones and the camera (hook `__W3core.R.render`).
  - Lip sync, creep/stare, CCTV anomalies and props stay live.

## 5. Can GLTFExporter export the static geometry?
Yes, with known losses.

- **Reaching the scene graph** after `World.init()`:
  - `window.__dbgL` is `L` (for example `L.lobby.S` is a scene).
  - `__dbgA` or `World.actors` hold the actors.
  - `window.__W3core` holds TX, R and RT.
  - `World.shots`, `World.solids` and `World.clips` are also exposed.
  - The exporter is not bundled; `lib/` has three r159 and GLTFLoader only. Load the r159 ESM build through an import-map shim that points at `window.THREE`.
- **Export from `?gfx=cine` or `?gfx=ultra`**: Lambert materials export with metallic and roughness both at .5.
- **Lost in export**:
  - Shader materials: meshes using ShaderMaterial get no material and import white (the facade glass). The `glow` and `crt` tweaks are lost too.
  - Maps and offsets: bumpMap is dropped (convert it with `Image.bump_map_to_normal_map`), and so is polygonOffset.
  - Rendering modes: BackSide (use `cull_mode=FRONT` in Godot) and additive blending.
  - Lights: Ambient and Hemisphere lights are not exported, and the light units are legacy (S:2984).
  - Dynamic content: rain, dust, dynamic canvases, and text in other languages.
  - Instancing: for InstancedMesh, check whether Godot imports EXT_mesh_gpu_instancing; otherwise use MultiMesh.
- **Kept**:
  - Geometry with its custom UVs, PNG images, emissive and roughness maps.
  - Texture repeat (via KHR_texture_transform), alphaTest, DoubleSide and names.
  - `userData` exported as extras. Strip the labels' `tex` and `draw` first. Godot 4.3+ should read extras as metadata; check this on 4.7.
- **Rebuilding in Godot**: don't rebuild meshes as BoxMesh/CylinderMesh, because their UVs differ. Coordinates map 1:1: Y up, −Z forward, metres, vertical FOV.

## 6. Solid world (S:3350-3558)
- **Colliders** (`solidCols`, S:3361):
  - Box becomes an OBB; Cylinder becomes a cylinder.
  - A plane becomes a slab. A single-sided plane of at least 1×1.2 m counts as a wall, 0.3 m thick behind the face. Glass is 2 cm thick.
  - Skipped: `userData.solid===false`, BackSide, invisible transparent objects, decals, skinned and instanced meshes.
  - Explicit colliders: `userData.solid=[[cx,cy,cz,w,h,d,name,isWall]]` (S:49, 114, 469, 616).
  - At runtime (`solidNow`, S:3383) it ignores horizontal planes, tops lower than 8 cm and objects smaller than 15 cm.
- **Bodies**:
  - Spheres per bone segment, fitted from the skin (S:3439).
  - Push-out is horizontal only, at most 4 iterations and capped at 15 cm; then the pose is solved again (S:3495).
  - The same data feeds gSolid and the QA tools (`World.clips`, `World.solids`). `World.solids()` returns no rotation, so dump the OBB axes yourself.
- **Floor offsets**: LAYY .085 (S:8), FRANKY .07, and the gLift radii.
- **Z-fighting fixes worth keeping**:
  - DECAL polygonOffset.
  - Wall panels set 5–10 mm off the walls (S:55, 539).
  - Stairs panel and stringers (S:816, 830).
  - Vending-machine lid (S:157, 162), cooler (S:784), booth panel (S:282).
  - Canopy face moved behind the glass (S:315).
  - Basement slit (S:891), book text (S:256).
  - Stacked heights for floor decals.
  - Corridor walls built without T-junctions (S:543).

  Godot has no polygon offset, so keep the millimetre offsets, or use Decal nodes or a depth-push shader.

## Recommendation
1. **Export automatically with Playwright, `?gfx=ultra`.**
   - For each location: call `reset()` and detach the actors, padding lights, beams, dust, blobs, effects, rain and sky. Then export a `.glb`.
   - Write a sidecar JSON per location with lights, the pivots of doors, leaves, lifts, drawer, fingers and clock, OBB colliders, fog, probe points and CREEP.
   - Dump TX and the bump/roughness maps as PNG, and dump shots, timelines and constants as JSON.
2. **Write a Godot import script.**
   - Materials: turn glow into emission energy, keep additive blending, use front culling where three used BackSide.
   - Lights: refit the falloff, because three's legacy falloff is `(1−d/range)^decay`.
   - Collisions and reflections: StaticBody3D colliders and ReflectionProbes.
   - Facade reflection: a SubViewport with a mirrored camera, plus a cull layer for `'refl'` actors.
   - Monitor feeds: a SubViewport that uses the corridor's World3D.
   - Fog: refit it, because FogExp2 is exp(−(ρd)²) while Godot's fog is exp(−ρd).
   - Volumetric fog can replace the beams and dust.
   - Renderers: Forward+ on PC, and Compatibility with the retro post-processing on the web.
3. **Rewrite by hand**: the glass, the CRT, both post-processing chains, the flicker, the location APIs, `aim`, `stage` and the push-out. Godot's FSR 2 can replace `perfTick` and the sharpen pass.
4. **Staging**: bake shots and deaths into an AnimationLibrary wherever they are deterministic. Port `set` and the gore solvers to GDScript only where they depend on runtime state.
