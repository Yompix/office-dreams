# Hush Till Six: engine and story porting map
E = engine.js, S = story.js, SC = scenes.js (all in public/games/nightshift/). I only read files; nothing was edited.

Before the details, four things to know:
- **Cams are real 3D camera moves**, not 2D crops of a picture.
- **The real "camera minigame" is the shift mode** (`then.shift`). `camgame` is dead code.
- **The `door` minigame and the fx `lunge`, `glitch` and `strobe` exist in the engine, but the story never uses them.**
- **Music is not generated at runtime.** It is mp3 files composed offline in Python.

## 1. Data model
- `window.STORY` (S:12) = `{start:'intro', scenes(98), shift{hourDeath, memo, frank, frankPre, lines, who, an(39), segs}, endings(4), overs(14)}`.
- It is pure data plus the `L1` text constant (S:11). `global.window={};require('./story.js')` dumps it to JSON.
- Scene = `{music, time, beats[], then}`. The engine also sets flags `opened`/`mih` itself, by scene-id regex (E:489–490).

**Beat fields** (E:500–563, 1815–1946):
- `img`: shot id in SC. `null` plus `phone` shows the phone screen.
- `cams`: `[[cx,cy,z0,z1,(altShot)]]`, plans played in order.
  - cx can be a world point instead: `[[x,y,z],dy,z0,z1]` (S:286).
  - Plans split the beat equally, or switch on words matched by `cutAt` regexes (S:58, E:531).
- `fx` (see §3), `label` (camera caption), `hud{date,at,tag}` (fake recording timestamp, S:120).
- `say`: voice keys (S:19). `sayAt`: start offset (S:491). `text`: typed line without voice. `text:''` marks a silent beat (S:107).
- `sfx`: `[[name,sec,{gain,cut,off,lp,pan,rate,once,bus}]]` (S:22). `pad`: tail after the last line, default .3 s.
- `phone{kind:lock|call|out|talk|msg, time, notes[{from,text,at,ago}], name, t0}` (lock S:23, call S:70, out S:275, talk S:121).
- `rule` (S:93), `time` (S:109), `chapter[big,small]` and `wait` tap prompt (S:20), `set`/`if` (S:33/51).
- `react{WHO:[gesture, sec | [key,wordRegex,frac], dur]}` (S:33), `props` (S:359), `photo{smudge,back,backCam}` (S:59), `noise{at,w,h}` (S:182).
- Scare/death fields: `hush:true|'soft'` (S:207/408), `heart` (S:112), `scare` and `hit` (S:502), `snow[[a,b]]` and `black` (S:408).
- The engine supports these, but the story never uses them: `sms` (E:541), `subl` (E:559), `creep` (E:548, which also gates the one-per-night false alarm), `scareGap`, `light`, `floors`, `who`, `auto`.
- `fin` is created by the engine (E:1460). `cctv` is not a field: fx `'cctv'` passes `cctv:1` to World.draw.

**`then` kinds** (E:675):
- `goto` (S:34).
- `choice` with `opts[{t,go,if,set}]`, `timeout`, `def` (S:28).
- `mg` plus params: tape S:114, cord S:162, wipe S:173, hold S:229/328, run S:250, torch S:303. `door`: no example in the story.
- `shift:'S1', fresh` (S:63).
- `explore{img,cams,hint,spots[{t,go,at,set,if,once}],leave{t,go,need,set}}` (S:53).
- `talk{img,cams,title,topics,bye}` (S:73).
- `back:'hub'` (S:58): re-runs the hub's `then` without replaying its beats (E:695).
- `ending,title,sub[,bg,bgCam,bgFx]` (S:245).
- `over,sub,retry:'@save'|sceneId` (S:411).
- `demo` (itch.io build only).
- `camgame` (E:757) is dead: it needs `S.cams`, which doesn't exist anywhere.

**Story flow** (`[ ]` = minigame or hub; `k#` = the death on that step):
- intro → keys_take → booth[explore] → S1 → dg_call[talk] → round1[explore] → S2 → S3 → round2[talk] → ly_call[talk] → S4 → journal → floor7[tape] → floor7b(k2) → radio(k3) → lift → cam13[cord, k4] → glass[wipe] → glass2, which branches:
- **Open:** branch_a →
  - hide[hold, k5] → A1 → hide2 = ending INSIDE
  - run[run, k6] → run2 = ending SISTER
- **Step away:** branch_b → server →
  - down[torch] → basement(k7) → knock3[hold, k8] → srv_wait → srv_talk → S6 → dawn = ending DAWN
  - stay → S5 → stay2 → call540(k9) → morning = ending NEWENTRY

## 2. Runtime loop
- **Main loop:** one requestAnimationFrame `frame()` (E:1805) switches on `mode`. A `gen` counter kills stale timers. The game clock stops on pause and advances at most 4 s per frame.
- **Line timing** (E:500):
  - The first line starts at `sayAt` (default .15 s); each next one starts .3 s after the previous ends.
  - Languages shown as subtitles over English audio also wait for reading time (E:522).
  - Typed text runs at 14 chars/s (ja 5, zh 8, ko 11).
- **Beat length:** `bDur = max(end + pad, 1.2)`. Death scenes extend it past `black` (E:528).
- **Audio start:** sound is scheduled on the first drawn frame (E:1947). Lines that load late catch up (E:569).
- **Camera:**
  - SC:3051 `aim()` turns the shot camera toward (cx,cy), and zoom narrows the field of view.
  - Non-CCTV shots get half the zoom as a real dolly (up to 30 cm). CCTV and scare shots use only the lens (SC:3246).
  - Portrait widens the field of view (tan ×1.9, SC:3050), because the scripts were authored for 9:16 (`scrX` E:33).
  - z is eased within each plan.
  - If the next beat uses the same `img`, its animation time continues (E:509) and the camera glides for 0.85 s (E:2057).
  - Handheld noise, mouse/tilt look and shake are added on top (E:1852, 2050).
  - Only fx `photo` is a 2D crop of a cached render (E:1873).
  - Shots must be deterministic in time t, because the tape minigame scrubs it.
- **Cuts** are hard. A 90 ms black frame marks a plan change, a scare and the end of a pre-frame (E:1883, 2013). If a scare comes less than 1.2 s into a beat, the previous frame freezes and sinks to black first (E:544, 1759).
- **End of beat:** it advances by itself if the beat is silent, has `auto`, or the Auto setting is on. Otherwise it holds and shows a hint after 2.2 s (E:1819).
- **Tap/Space** jumps to the end of the beat and cuts the voice. It is blocked during the pre-frame and until 1 s after a scare (E:627).
- **Voice files:** `snd/<key>.mp3` for Russian, `snd/<lang>/` otherwise (E:72).
- **Timing file:** `timing[_lang].json` maps each key to `{t, dur, w:[[sec,word]], who, fx, g, ew}`.
  - `ew` holds English word times, for languages subtitled over English audio.
  - Lines are decoded per scene; 32 stay in memory (E:91).
- **Subtitles:** a speaker name plate (colours in SPK, E:426) plus the whole line. Words brighten at their timestamps (E:1937, 1798).
- **Lips:** World.draw receives `{who, v, vis}`.
  - `v` = smoothed loudness of the voice bus ×6 (E:65, 1842).
  - `vis` = mouth shape (viseme) for the letter being spoken (E:434–447).
  - Only lines spoken aloud move lips: non-Ethan lines, or text starting with "—". Ethan's narration keeps his mouth closed (E:432).
- **sfx** are scheduled at their time. `knock`/`glass_tap` instead sync to Katya's animated hand hits (E:582, 346).

## 3. fx (E:1884–1904; post-process shader SC:2901–2939)
- **cctv:**
  - Look: fisheye, line jitter, soft optics, green monochrome tint, scanlines and grain from the shader; infrared mode is black and white.
  - Behaviour: the picture updates at 12 fps (E:1526) and the camera is locked.
  - HUD (E:1598): corner marks, blinking REC, IR, label, local-format date and clock, a MIC level meter during the shift, image tears every 2–7 s.
- **static:** the same look, redrawn every frame. After 1.2 s, heavy grain and white flicker, with a static sound loop.
- **rain:** 2D rain streaks only when the camera is outside. The background rain sound switches to "behind glass".
- **flicker:** faint white flicker for 0.5 s out of every 4 s.
- **lamp:** an 80 ms dip to 35% darker every 2.9 s.
- **dark:** ramps to 85% black over the beat.
- **dawn:** orange soft-light plus a brown ramp.
- **photo:** faded and yellowed, with a smudged face at a world point, scratches and a border. A `backCam` plan shows handwriting on the back (E:1626).
- **lunge** (unused):
  - Sound: a breath 1 s before, a neck crack .42 s before, a three-layer scream.
  - Picture: ghosting, a red subliminal face, negative flashes, black at .8 s (E:591, 1900).
- **glitch** (unused): a 0.8 s frozen frame with a "CONNECTION LOST" box. Audio drops to true silence, then static (E:661, 1767).
- **strobe:** unused.
- **Always:** vignette.
- **Every scare:** a black frame, a ×1.1 zoom punch, a red smear from the shader (`uScare`) and vibration.

## 4. Choices and flags
- **Choice UI** (E:735):
  - Layout: heading, numbered options (those passing `if`), a red timer bar. Music switches to tension.
  - Timer: ticks in the last 3 s. On timeout it picks `def` if visible, otherwise a random option.
  - Input: keys 1–4.
- **Consequences:**
  - A pick goes to the option's scene and sets its `o.set` flags.
  - Non-death picks are recorded in `run.picks` (E:476).
  - Choices with two or more non-death options can be returned to from the end screen (E:482).
- **Flags** are stored in localStorage `flags` (E:413–416). `!x` means "not set"; an array means all must hold.
  - asked: set S:33, read S:51–52
  - salt: set S:54, read S:96
  - photo: set S:54, read S:535–536
  - fphone: set S:55, read S:799–802 and as a scene prop (E:465)
  - heard: set S:515, read S:294, S:525–526
  - warned: set S:539, read S:111
  - jread: set S:88, read E:462 and E:1145
  - fpcall: set S:801, read S:95
  - opened: set E:489, read as the jacket prop
  - mih: set E:490, read as the scratched-badge prop
  - `v:<scene>`: set on every visit (E:713), read S:75/342/516/534–539; it also hides visited items (E:694) and gates `leave.need`.
- **Shift state** is kept outside the flags: `shk{door, lift, lights, log, st{gus, gusGone, blackout}, c13}`.

## 5. Minigames (E:787–945; each exits to its `win` or `lose` scene)
- **hold:**
  - Air drains by min(.085, .8/dur) per second while held and refills .25/s when released.
  - Letting go between 2.5 s and `dur`, or running out of air, loses.
  - After `dur` the text "ТИХО…" appears; releasing then wins.
  - Sound: barefoot steps circle around, or the `calls` voice lines play with subtitles.
- **run:** n rounds. Each round darkens a random side; press the other arrow within max(1.3, 2.6 − .2·round) s.
- **cord:** the CAM 13 shot zooms over 3.2 s. Dragging the plug down more than 45% wins; time running out loses.
- **wipe:** rub a fogged glass clear while it slowly fogs again. At 45% clear or 15 s, the `slam` image and a glass slam play, then it wins. There is no lose.
- **torch:** a lagging spotlight follows the finger. An optional `mon` figure vanishes when lit. Holding the light .7 s on `goal` wins. An optional `img3` adds a jumpscare.
- **tape:** the knob scrubs the shot's time. Going below `goal` makes the tape play forward to `stop`, then it wins.
- **door** (unused): button-mash. Each press adds .075 to a meter that drains faster over time, with door slams and shake. Empty loses; surviving 12 s wins.
- **camgame** (dead):
  - A grid of `S.cams` thumbnails; the player views 3 cameras, each about 2.6 s with an Ethan line or note and an optional jumpscare, then "continue".
  - CAM 13: the first tap warns, the second goes to `over_cam13`, a scene that doesn't exist.

**Shift: the real camera game** (E:948–1241, S:700–830)
- **Hours:** S1 21:10–22:00, S2 22:20–23:59, S3 00:00–00:59, S4 01:20–02:50. Then one per branch: S5 04:20–05:35 (stay), S6 04:30–05:58 (with Frank), A1 04:00–05:45 (hiding).
- **Speed:** `spm` is real seconds per game minute, 2.2–4, so an hour lasts 3–6.3 real minutes.
- **Events** fire at set game minutes: `an`, `say`, `radio`, `sms` (with replies), `state`, `c13`, `task`, `call`, `hold`, `sfx`. `rnd` adds n random anomalies.
- **An anomaly** has:
  - World `props`, or a sound shown on the MIC meter.
  - `fix`: log, `light:N`, lift, door or none.
  - `win`: the time window, ×7 real seconds.
- **Player tools:**
  - Camera buttons: switching plays a static burst.
  - Log: 7 kinds. The right kind on the current camera resolves the anomaly; a wrong log costs nothing.
  - Panel: door, lift and 13 floor lights.
  - Journal: memo, Frank's notes, the player's own log.
- **Misses and deaths:**
  - A missed anomaly or missed task plays heartbeat and steps; 3 misses lead to `over_come`.
  - Watching a "stare" anomaly over 2.6 s gives a warning, over 5 s kills. The figure approaches in 4 jumps.
  - Leaving the door unlocked 28 s with Lily there, or unlocking it for her, leads to `over_door`.
  - Tapping CAM 13 a second time leads to `over_k1`.
  - Answering "yes" on a call, or picking up Frank's phone, kills.
  - Releasing a hold for more than .9 s kills.
- **Retry consistency:** snapshots `shk0`/`shk` and `doorLock` (E:966).

## 6. Phone, SMS, rules, clock, tutorial, journal, endings, deaths
- **Phone** (E:1655–1743): drawn on canvas, with a fixed camera.
  - lock: a code-drawn crayon wallpaper, the time, notifications sliding in.
  - call: incoming, with a pulsing green button.
  - out: "calling…".
  - talk: a 00:SS timer.
- **SMS banner** (E:651): buzz and vibration, shown 3.5 s. During the shift an SMS can offer 2 replies (E:1152).
- **Rule:** a beige paper note on screen. Each one seen is stored (localStorage `rules2`) and listed by the Rules HUD button (E:641).
- **Clock:** set by `scene.time`/`beat.time`, with seconds running in real time (E:467). In-world clocks freeze at 03:00 (E:464).
- **Tutorial** (E:1157–1204):
  - First S1 only; game time stops.
  - Dispatcher Vera's lines t01–t09 play, with ring-and-arrow targets on the UI.
  - A fake `tut13` anomaly is added for practice.
  - Continue/Skip buttons; "?" replays it.
- **Journal:** in the shift, the Journal button (memo, Frank's notes, the player's log). After an ending, a duty-log page (below).
- **Endings:** inside S:245, sister S:262, dawn S:362, newentry S:396.
  - The last shot keeps running for about 8.5 s while darkening, with music `end_<id>`; the title types in letter by letter (E:1456).
  - Then the duty-log page (E:1470): stats, decisions, pen-scratch sounds, a stamp, the endings gallery, and back to choice / restart / menu.
- **Deaths:** 14 names (S:832) spread over 22 `over_*` scenes; some deaths remap to variants (S:702, E:1060).
  - Triggers: death options (S:124/136/313/385), minigame losses (S:162/229/250/329), shift rules (E:1058/1081/1084/1087/1113/1137/1221/1239).
  - Sequence:
    1. At `black`, a 0.75 s `death_scream` zoom with a red flash and a three-layer scream (E:606, 1911).
    2. A gore finale shot `gore@<base>:<kind>` (E:397–408, timing from World.goreTL, default 3.4 s) with blood splats.
    3. The death screen (E:1503): black, then the frozen last frame fades in under the title.
  - Retry restarts at the decision beat, not at the start of the scene (E:1514).

## 7. Sound
- **Mixing** (E:142–181):
  - master → compressor → volume. A direct bus skips the compressor, for screams.
  - Music sits at .55, ducked to .22 under voice.
  - Voice and sfx send to a reverb built from a generated impulse.
  - Filter buses for muffled, radio and phone voices. A "phone-speaker" overtone layer (E:158) keeps bass audible on phones.
- **Voice effects** come from the timing file's `fx` (E:125). Fake voices and the monster get a pitched-down ghost layer.
- **Place ambience** is never silent (E:250): room-tone and rain loops filtered per place (E:453), plus a synthesized 100 Hz lamp hum. It ducks under voice.
- **Random distant sounds** every 8–20 s, per place and randomized, held back near voice and scares (E:302).
- **Silence before scares** (E:241, 1828):
  - Music and ambience nearly mute, a tinnitus loop starts, and a fridge-hum stop plays.
  - It starts automatically 1.5 s before a `scare`, then a reverse swell and the hit sounds play (E:588).
- **Signature before the monster:** a neck crack and a close breath 2.5 s before it first appears in a shot (E:362–381, 553).
- **Music:**
  - mp3 loops with crossfades (E:223), chosen per scene or mode.
  - The theme, shift and ending tracks were composed offline in code, by tools/music/compose.py.
  - At runtime only the hums, drips and UI thumps are synthesized (E:263, 283).

## 8. Saves, pause, settings, demo, QA
- **localStorage**, all keys prefixed `ns1_`: flags, save, saveT, seen, run, rules2, shk, shk0, doorLock, alt_*, tutDone, endings, overs, calm, land3, voice, vol, mute, auto, look, vhs, gfx, gfxAuto, res, lang, qa.
- **sessionStorage** `ns1_resume` (E:1349).
- **Continue** restarts the saved scene and re-applies the `set` flags of beats it skips (E:495).
- **Pause** suspends the audio and the clock (E:1325).
- **Settings** (E:1381) include "calm" (fewer flashes). Hotkeys at E:2076; gamepad support in pad.js.
- **Demo:** `NS_DEMO` cuts the story after the glass choice and shows `demoEnd` (E:1494).
- **QA hooks:**
  - `window.__ns` (E:2114): startScene, seek, skipBeat, flags, shSpeed, shift actions.
  - `?qa=1` loads qa.js; `?cs=` sets the canvas scale.
  - Under automated browsers (webdriver) the title gate opens by itself (E:1306).

## 9. Also needed for the same feel
- **Startup:** studio splash, then a tap gate that unlocks audio.
- **Title:** the live `door_katya` shot slowly pushing in, with a flickering logo (E:1269).
- **Explore hubs:** spots follow world points via World.project every frame, and labels are placed to avoid overlap (E:717).
- **Talk hubs:** topics on keys 1–9 (E:2085).
- **Platform:** vibration, both screen orientations (E:2027), and the per-run stats shown on the end page.

## Recommended Godot structure
This fits the existing `godot/nightshift/` (assets, data/timing*, main.tscn).
- **Autoloads:**
  - `StoryDB`: loads data/*.json.
  - `Save`: same keys as localStorage.
  - `Audio`: buses mirroring E:142; a pool of AudioStreamPlayers behind `play(name,{when,off,cut,rate,pan,lp,rev,bus})`; a level/spectrum reading for lips.
  - `Flags`.
- **`main.tscn`:**
  - `Game.gd`: mode state machine plus the gen counter.
  - `BeatPlayer.gd`: port of nextBeat, beatAudio and frame.
  - `World`: a SubViewport with `aim()`, `draw(id,{cx,cy,z,t,T,sx,sy,sr,talk,react,props,cctv,scare,torch})` and `project`.
  - `PostFX`: the post-process shader.
  - `Overlay`: fx, CCTV HUD, phone, image tears via the screen texture.
  - `UI`: a CanvasLayer for all menus and text.
  - `MiniGames/*`.
  - `Shift/`: game, UI, tutorial.
- **Data to export:**
  - `story_<lang>.json`: STORY after `trStory` translation.
  - `tables.json`: engine constants — GORE_KIND/BASE, SIG_AT/SKIP, DEATH_ALT, AMBP, BEDS, RAINS, SEND, SPK, VOICE_WHO, CAMS, KINDS, TUT_DO, STARE_T/K, VIS_CH, CPS and timing constants.
  - `timing_*.json`: copy as-is.
