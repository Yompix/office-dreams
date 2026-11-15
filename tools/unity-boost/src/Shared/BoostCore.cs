// Unity Boost RU — общая логика пресетов (одинакова для BepInEx 5 / Mono и BepInEx 6 / IL2CPP).
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.Text;
using BepInEx.Configuration;
using BepInEx.Logging;

namespace UnityBoost
{
    public enum BoostPreset { WeakLaptop, Medium, Off }
    public enum WeakShadowMode { Off, Low }

    public sealed class BoostCore
    {
        public const string Guid = "UnityBoost"; // => BepInEx/config/UnityBoost.cfg
        public const string Name = "Unity Boost RU";
        public const string Version = "1.0.0";

        private readonly ManualLogSource _log;

        private readonly ConfigEntry<BoostPreset> _preset;
        private readonly ConfigEntry<int> _fpsLimit;
        private readonly ConfigEntry<float> _weakScale;
        private readonly ConfigEntry<float> _mediumScale;
        private readonly ConfigEntry<WeakShadowMode> _weakShadows;
        private readonly ConfigEntry<bool> _weakPost;
        private readonly ConfigEntry<bool> _changeResolution;
        private readonly ConfigEntry<float> _fixedStep;
        private readonly ConfigEntry<string> _presetKey;
        private readonly ConfigEntry<string> _fpsKey;
        private readonly ConfigEntry<bool> _showFps;

        // ---------- запомненные исходные значения игры (чтобы «Выкл» всё вернул) ----------
        private sealed class StaticSetting
        {
            public string Label;
            public string TypeName;
            public string[] Props;     // альтернативные имена в разных версиях Unity
            public string UsedProp;
            public bool Captured;
            public object Original;
            public double OriginalNum = double.NaN;
            public bool Missing;
        }

        private readonly List<StaticSetting> _settings = new List<StaticSetting>();
        private readonly Dictionary<string, StaticSetting> _byLabel = new Dictionary<string, StaticSetting>();

        // Изменения на объектах (камеры, ассет URP): объект, член, старое значение.
        private sealed class ObjectChange { public object Obj; public string Member; public object Old; }
        private readonly List<ObjectChange> _objectChanges = new List<ObjectChange>();

        private int _origW, _origH;
        private int _ourW, _ourH;
        private bool _resolutionChanged;

        // ---------- состояние ----------
        private int _frame;
        private bool _started;
        private readonly Stopwatch _clock = Stopwatch.StartNew();
        private double _nextCheck;
        private string _sceneSig;
        private bool _keyPresetWas, _keyFpsWas;
        private int _vkPreset, _vkFps;

        private int _fpsFrames;
        private double _fpsT0;
        private float _fps;
        private string _toast;
        private double _toastUntil;

        private bool _guiBroken;
        private bool _titleMode;

        public BoostCore(ConfigFile cfg, ManualLogSource log)
        {
            _log = log;

            _preset = cfg.Bind("1. Пресет", "Preset", BoostPreset.WeakLaptop,
                "Режим ускорения.\n" +
                "WeakLaptop = «Слабый ноутбук» (максимум FPS, картинка проще),\n" +
                "Medium = «Средний» (умеренно),\n" +
                "Off = «Выкл» (всё как задумала игра).\n" +
                "Во время игры переключается клавишей F9.");
            _fpsLimit = cfg.Bind("1. Пресет", "FpsLimit", 60,
                "Ограничение кадров в секунду: 60 или 30 (30 — ноутбук меньше греется). 0 = без ограничения.");
            _weakScale = cfg.Bind("2. Тонкая настройка", "WeakResolutionScale", 0.75f,
                "Масштаб разрешения в «Слабом ноутбуке» (0.5–1.0). 0.75 = рисуем 75% пикселей по каждой стороне.");
            _mediumScale = cfg.Bind("2. Тонкая настройка", "MediumResolutionScale", 1.0f,
                "Масштаб разрешения в «Среднем» (0.5–1.0). 1.0 = не менять.");
            _weakShadows = cfg.Bind("2. Тонкая настройка", "WeakShadows", WeakShadowMode.Off,
                "Тени в «Слабом ноутбуке»: Off = выключить, Low = простые и близкие.");
            _weakPost = cfg.Bind("2. Тонкая настройка", "WeakDisablePostProcessing", true,
                "Выключать тяжёлую постобработку (размытие, свечение и т. п.) в «Слабом ноутбуке».");
            _changeResolution = cfg.Bind("2. Тонкая настройка", "ChangeScreenResolution", true,
                "Можно ли уменьшать разрешение экрана в полноэкранном режиме (если игра не на URP). false = не трогать разрешение.");
            _fixedStep = cfg.Bind("2. Тонкая настройка", "FixedTimestep", 0f,
                "Шаг физики Time.fixedDeltaTime. 0 = не трогать (рекомендуется). Например 0.03333 = физика 30 раз в секунду.");
            _presetKey = cfg.Bind("3. Клавиши", "PresetKey", "F9", "Клавиша переключения пресета (F1–F12).");
            _fpsKey = cfg.Bind("3. Клавиши", "FpsKey", "F10", "Клавиша показа счётчика FPS (F1–F12).");
            _showFps = cfg.Bind("3. Клавиши", "ShowFps", false, "Показывать счётчик FPS в углу.");

            _vkPreset = Win32.ParseKey(_presetKey.Value, 0x78);
            _vkFps = Win32.ParseKey(_fpsKey.Value, 0x79);

            const string Q = "UnityEngine.QualitySettings";
            Add("pixelLightCount", Q, "pixelLightCount");
            Add("antiAliasing", Q, "antiAliasing");
            Add("anisotropicFiltering", Q, "anisotropicFiltering");
            Add("lodBias", Q, "lodBias");
            Add("textureLimit", Q, "globalTextureMipmapLimit", "masterTextureLimit");
            Add("softParticles", Q, "softParticles");
            Add("realtimeReflectionProbes", Q, "realtimeReflectionProbes");
            Add("particleRaycastBudget", Q, "particleRaycastBudget");
            Add("skinWeights", Q, "skinWeights", "blendWeights");
            Add("shadows", Q, "shadows");
            Add("shadowResolution", Q, "shadowResolution");
            Add("shadowDistance", Q, "shadowDistance");
            Add("shadowCascades", Q, "shadowCascades");
            Add("vSyncCount", Q, "vSyncCount");
            Add("targetFrameRate", "UnityEngine.Application", "targetFrameRate");
            Add("fixedDeltaTime", "UnityEngine.Time", "fixedDeltaTime");

            _log.LogInfo(Name + " " + Version + " загружен. Пресет: " + PresetRu(_preset.Value) +
                         ". F9 — сменить пресет, F10 — счётчик FPS.");
        }

        private void Add(string label, string type, params string[] props)
        {
            StaticSetting s = new StaticSetting { Label = label, TypeName = type, Props = props };
            _settings.Add(s);
            _byLabel[label] = s;
        }

        public static string PresetRu(BoostPreset p)
        {
            switch (p)
            {
                case BoostPreset.WeakLaptop: return "Слабый ноутбук";
                case BoostPreset.Medium: return "Средний";
                default: return "Выкл";
            }
        }

        private double Now { get { return _clock.Elapsed.TotalSeconds; } }

        // ======================= вызывается каждый кадр =======================
        public void Update()
        {
            try { UpdateInner(); }
            catch (Exception e) { SafeLog("Ошибка в Update: " + e.Message); }
        }

        private void UpdateInner()
        {
            _frame++;
            double now = Now;

            // FPS
            _fpsFrames++;
            if (now - _fpsT0 >= 0.5)
            {
                _fps = (float)(_fpsFrames / (now - _fpsT0));
                _fpsFrames = 0;
                _fpsT0 = now;
                if (_titleMode) Win32.SetTitleSuffix(_showFps.Value ? "FPS " + Math.Round(_fps) : null);
            }

            // Первое применение — когда игра уже прогрузила первый кадр.
            if (!_started)
            {
                if (_frame < 30 || now < 2.0) return;
                _started = true;
                Apply(true, "запуск");
                _sceneSig = SceneSignature();
                _nextCheck = now + 1.0;
            }

            // Клавиши (только когда окно игры активно).
            bool focus = Win32.GameHasFocus();
            bool kp = focus && Win32.IsDown(_vkPreset);
            bool kf = focus && Win32.IsDown(_vkFps);
            if (kp && !_keyPresetWas) CyclePreset();
            if (kf && !_keyFpsWas)
            {
                _showFps.Value = !_showFps.Value;
                if (_titleMode && !_showFps.Value) Win32.SetTitleSuffix(null);
            }
            _keyPresetWas = kp;
            _keyFpsWas = kf;

            // Смена сцены / уровня качества / новые камеры — применяем заново (без смены разрешения).
            if (now >= _nextCheck)
            {
                _nextCheck = now + 1.0;
                string sig = SceneSignature();
                if (sig != _sceneSig)
                {
                    _sceneSig = sig;
                    if (_preset.Value != BoostPreset.Off) Apply(false, "смена сцены");
                }
            }
        }

        private void CyclePreset()
        {
            BoostPreset next = _preset.Value == BoostPreset.WeakLaptop ? BoostPreset.Medium
                : _preset.Value == BoostPreset.Medium ? BoostPreset.Off : BoostPreset.WeakLaptop;
            _preset.Value = next; // сохраняется в UnityBoost.cfg
            Apply(true, "F9");
            ShowToast("Unity Boost: " + PresetRu(next));
        }

        private void ShowToast(string s)
        {
            _toast = s;
            _toastUntil = Now + 3.0;
            if (_titleMode) Win32.SetTitleSuffix(s);
        }

        private string SceneSignature()
        {
            StringBuilder sb = new StringBuilder();
            try
            {
                Type sm = U.T("UnityEngine.SceneManagement.SceneManager", "UnityEngine.CoreModule", "UnityEngine");
                object scene = U.CallStatic(sm, "GetActiveScene");
                object h;
                if (scene != null && (U.TryGet(scene, "handle", out h) || U.TryGet(scene, "m_Handle", out h))) sb.Append(h);
            }
            catch { }
            sb.Append('|');
            try { sb.Append(U.GetStatic(U.Core("UnityEngine.Camera"), "allCamerasCount")); } catch { }
            sb.Append('|');
            try { sb.Append(U.CallStatic(U.Core("UnityEngine.QualitySettings"), "GetQualityLevel")); } catch { }
            return sb.ToString();
        }

        // ======================= применение пресета =======================

        private void Apply(bool full, string reason)
        {
            BoostPreset p = _preset.Value;
            List<string> done = new List<string>();
            List<string> failed = new List<string>();

            // Сначала один раз запоминаем исходные значения игры.
            foreach (StaticSetting s in _settings) Capture(s);

            if (p == BoostPreset.Off)
            {
                foreach (StaticSetting s in _settings) Restore(s, done, failed);
                RestoreObjects(done);
                if (full) RestoreResolution(done);
                Report(p, reason, done, failed);
                return;
            }

            // Если игра сама поменяла настройку (меню, смена уровня качества) — новое исходное берём от неё.
            foreach (StaticSetting s in _settings) Recapture(s);
            // Настройки камер и URP каждый раз ставим заново от исходных значений.
            RestoreObjects(null);

            bool weak = p == BoostPreset.WeakLaptop;
            bool shadowsOff = weak && _weakShadows.Value == WeakShadowMode.Off;

            // --- QualitySettings ---
            SetCap("pixelLightCount", weak ? 1 : 2, done, failed);
            SetTo("antiAliasing", 0, done, failed);
            if (weak) SetTo("anisotropicFiltering", 0, done, failed);            // Disable
            else SetCap("anisotropicFiltering", 1, done, failed);                // не выше Enable
            SetCap("lodBias", weak ? 0.6 : 1.0, done, failed);
            SetFloor("textureLimit", weak ? 1 : 0, done, failed);                // 1 = текстуры в 2 раза меньше
            SetTo("softParticles", false, done, failed);
            SetTo("realtimeReflectionProbes", false, done, failed);
            SetCap("particleRaycastBudget", weak ? 16 : 64, done, failed);
            SetCap("skinWeights", weak ? 2 : 4, done, failed);
            if (shadowsOff)
            {
                SetTo("shadows", 0, done, failed);                               // Disable
                SetCap("shadowDistance", 15, done, failed);
            }
            else
            {
                SetCap("shadows", 1, done, failed);                              // только жёсткие
                SetCap("shadowResolution", weak ? 0 : 1, done, failed);          // Low / Medium
                SetCap("shadowDistance", weak ? 20 : 50, done, failed);
                SetCap("shadowCascades", 2, done, failed);
            }

            // --- FPS ---
            SetTo("vSyncCount", 0, done, failed);
            int fps = _fpsLimit.Value;
            SetTo("targetFrameRate", fps > 0 ? fps : -1, done, failed);

            // --- физика: только если пользователь сам попросил ---
            if (_fixedStep.Value > 0.001f) SetTo("fixedDeltaTime", _fixedStep.Value, done, failed);

            // --- конвейер рендера (URP/HDRP) и масштаб разрешения ---
            float scale = Clamp(weak ? _weakScale.Value : _mediumScale.Value, 0.5f, 1f);
            bool urpScaled = ApplyPipeline(weak, shadowsOff, scale, done);
            if (full)
            {
                if (!urpScaled && _changeResolution.Value && scale < 0.999f) ApplyResolution(scale, done);
                else if (scale >= 0.999f) RestoreResolution(done);
            }

            // --- постобработка на камерах ---
            ApplyCameras(weak && _weakPost.Value, done);

            Report(p, reason, done, failed);
        }

        private void Report(BoostPreset p, string reason, List<string> done, List<string> failed)
        {
            StringBuilder sb = new StringBuilder();
            sb.Append("Пресет «").Append(PresetRu(p)).Append("» применён (").Append(reason).Append("). ");
            sb.Append(done.Count == 0 ? "Изменений нет." : "Изменено: " + string.Join("; ", done.ToArray()));
            SafeLog(sb.ToString());
            if (failed.Count > 0)
                SafeLog("В этой версии Unity недоступно (пропущено): " + string.Join(", ", failed.ToArray()));
        }

        private void SafeLog(string s)
        {
            try { _log.LogInfo(s); } catch { }
        }

        private static float Clamp(float v, float a, float b) { return v < a ? a : (v > b ? b : v); }

        // ---------- статические настройки ----------

        private static Type TypeOf(StaticSetting s)
        {
            return U.Core(s.TypeName);
        }

        private void Capture(StaticSetting s)
        {
            if (s.Captured || s.Missing) return;
            Type t = TypeOf(s);
            if (t == null) { s.Missing = true; return; }
            foreach (string prop in s.Props)
            {
                object v;
                if (U.TryGetStatic(t, prop, out v) && v != null)
                {
                    s.UsedProp = prop;
                    s.Original = v;
                    s.OriginalNum = U.ToDouble(v);
                    s.Captured = true;
                    return;
                }
            }
            s.Missing = true;
        }

        // Игра могла сама сменить значение (например, через меню настроек) — если текущее
        // не равно тому, что ставили мы, считаем его новым «исходным».
        private readonly Dictionary<string, double> _ourValues = new Dictionary<string, double>();

        private void Recapture(StaticSetting s)
        {
            if (!s.Captured) return;
            object v;
            if (!U.TryGetStatic(TypeOf(s), s.UsedProp, out v) || v == null) return;
            double cur = U.ToDouble(v);
            double ours;
            if (_ourValues.TryGetValue(s.Label, out ours) && Math.Abs(ours - cur) < 1e-4) return;
            s.Original = v;
            s.OriginalNum = cur;
        }

        private bool Write(StaticSetting s, object value, List<string> done, List<string> failed)
        {
            if (s == null || !s.Captured) { if (s != null && failed.IndexOf(s.Label) < 0) failed.Add(s.Label); return false; }
            Type t = TypeOf(s);
            object cur;
            U.TryGetStatic(t, s.UsedProp, out cur);
            double curNum = U.ToDouble(cur);
            double newNum = U.ToDouble(value);
            if (!double.IsNaN(curNum) && Math.Abs(curNum - newNum) < 1e-4)
            {
                _ourValues[s.Label] = newNum;
                return true; // уже так
            }
            if (U.SetStatic(t, s.UsedProp, value))
            {
                _ourValues[s.Label] = newNum;
                done.Add(s.UsedProp + " " + Fmt(curNum) + "→" + Fmt(newNum));
                return true;
            }
            failed.Add(s.UsedProp);
            return false;
        }

        private static string Fmt(double d)
        {
            if (double.IsNaN(d)) return "?";
            return Math.Round(d, 3).ToString(CultureInfo.InvariantCulture);
        }

        private void SetTo(string label, object value, List<string> done, List<string> failed)
        {
            StaticSetting s;
            if (!_byLabel.TryGetValue(label, out s)) return;
            Write(s, value, done, failed);
        }

        // Не выше указанного (качество только понижаем, не повышаем).
        private void SetCap(string label, double max, List<string> done, List<string> failed)
        {
            StaticSetting s;
            if (!_byLabel.TryGetValue(label, out s)) return;
            if (!s.Captured) { Write(s, max, done, failed); return; }
            double v = double.IsNaN(s.OriginalNum) ? max : Math.Min(s.OriginalNum, max);
            Write(s, v, done, failed);
        }

        // Не ниже указанного (для лимита текстур: больше = хуже качество = быстрее).
        private void SetFloor(string label, double min, List<string> done, List<string> failed)
        {
            StaticSetting s;
            if (!_byLabel.TryGetValue(label, out s)) return;
            if (!s.Captured) { Write(s, min, done, failed); return; }
            double v = double.IsNaN(s.OriginalNum) ? min : Math.Max(s.OriginalNum, min);
            Write(s, v, done, failed);
        }

        private void Restore(StaticSetting s, List<string> done, List<string> failed)
        {
            if (!s.Captured) return;
            Write(s, s.Original, done, failed);
            _ourValues.Remove(s.Label);
        }

        // ---------- объекты: запоминаем старое значение и меняем ----------

        private bool SetObj(object obj, string[] members, object value, string label, List<string> done)
        {
            if (obj == null) return false;
            foreach (string m in members)
            {
                object old;
                if (!U.TryGet(obj, m, out old)) continue;
                if (old != null && Math.Abs(U.ToDouble(old) - U.ToDouble(value)) < 1e-4) return true; // уже так
                if (!U.TrySet(obj, m, value)) continue;
                bool already = false;
                foreach (ObjectChange c in _objectChanges)
                    if (ReferenceEquals(c.Obj, obj) && c.Member == m) { already = true; break; }
                if (!already) _objectChanges.Add(new ObjectChange { Obj = obj, Member = m, Old = old });
                done.Add(label + " " + Fmt(U.ToDouble(old)) + "→" + Fmt(U.ToDouble(value)));
                return true;
            }
            return false;
        }

        private void RestoreObjects(List<string> done)
        {
            int n = 0;
            foreach (ObjectChange c in _objectChanges)
            {
                try { if (U.IsAlive(c.Obj) && U.TrySet(c.Obj, c.Member, c.Old)) n++; } catch { }
            }
            _objectChanges.Clear();
            if (n > 0 && done != null) done.Add("возвращено настроек камер/рендера: " + n);
        }

        // ---------- URP / HDRP ----------

        private static readonly string[] UrpTypes =
        {
            "UnityEngine.Rendering.Universal.UniversalRenderPipelineAsset, Unity.RenderPipelines.Universal.Runtime",
            "UnityEngine.Rendering.LWRP.LightweightRenderPipelineAsset, Unity.RenderPipelines.Lightweight.Runtime",
        };

        private object CurrentPipelineAsset()
        {
            Type gs = U.T("UnityEngine.Rendering.GraphicsSettings", "UnityEngine.CoreModule", "UnityEngine");
            object rp = U.GetStatic(gs, "currentRenderPipeline");
            if (rp == null) rp = U.GetStatic(U.Core("UnityEngine.QualitySettings"), "renderPipeline");
            if (rp == null) rp = U.GetStatic(gs, "renderPipelineAsset");
            return rp;
        }

        private static Type TypeByQualifiedName(string qualified)
        {
            int comma = qualified.IndexOf(',');
            return U.T(qualified.Substring(0, comma).Trim(), qualified.Substring(comma + 1).Trim());
        }

        // true — если масштаб разрешения применён через URP renderScale.
        private bool ApplyPipeline(bool weak, bool shadowsOff, float scale, List<string> done)
        {
            object rp;
            try { rp = CurrentPipelineAsset(); } catch { rp = null; }
            if (rp == null) return false; // встроенный конвейер

            object urp = null;
            foreach (string qn in UrpTypes)
            {
                Type t = TypeByQualifiedName(qn);
                if (t == null) continue;
                urp = U.Cast(rp, t);
                if (urp != null) break;
            }
            if (urp == null)
            {
                if (!_hdrpNoted)
                {
                    _hdrpNoted = true;
                    SafeLog("Игра на HDRP/своём конвейере: масштаб и тени конвейера не трогаю, работают только общие настройки.");
                }
                return false;
            }

            bool scaled = false;
            try
            {
                object cur;
                string used = U.FirstReadable(urp, new[] { "renderScale", "m_RenderScale" }, out cur);
                if (used != null)
                {
                    float target = (float)Math.Min(U.ToDouble(cur), scale);
                    if (SetObj(urp, new[] { "renderScale", "m_RenderScale" }, target, "URP renderScale", done)) scaled = true;
                }
                object sd;
                string sdName = U.FirstReadable(urp, new[] { "shadowDistance", "m_ShadowDistance" }, out sd);
                if (sdName != null)
                {
                    SetObj(urp, new[] { "shadowDistance", "m_ShadowDistance" }, (float)Math.Min(U.ToDouble(sd), weak ? 20 : 50), "URP shadowDistance", done);
                }
                SetObj(urp, new[] { "msaaSampleCount", "m_MSAA" }, 1, "URP MSAA", done);
                if (shadowsOff)
                {
                    SetObj(urp, new[] { "m_MainLightShadowsSupported" }, false, "URP тени солнца", done);
                    SetObj(urp, new[] { "m_AdditionalLightShadowsSupported" }, false, "URP тени ламп", done);
                }
                if (weak) SetObj(urp, new[] { "m_SoftShadowsSupported" }, false, "URP мягкие тени", done);
            }
            catch (Exception e) { SafeLog("URP: " + e.Message); }
            return scaled;
        }

        private bool _hdrpNoted;

        // ---------- разрешение экрана (встроенный конвейер) ----------

        private void ApplyResolution(float scale, List<string> done)
        {
            try
            {
                Type screen = U.Core("UnityEngine.Screen");
                object fs = U.GetStatic(screen, "fullScreen");
                if (!(fs is bool) || !(bool)fs) return; // в окне не трогаем — окно бы сжалось
                int w = System.Convert.ToInt32(U.GetStatic(screen, "width"));
                int h = System.Convert.ToInt32(U.GetStatic(screen, "height"));
                if (!_resolutionChanged || w != _ourW || h != _ourH)
                {
                    // Игрок сам сменил разрешение, или это первый раз — запоминаем как исходное.
                    _origW = w; _origH = h;
                }
                int tw = ((int)Math.Round(_origW * scale)) & ~1;
                int th = ((int)Math.Round(_origH * scale)) & ~1;
                if (tw < 640 || th < 360) { tw = Math.Max(tw, 640); th = Math.Max(th, 360); }
                if (tw == w && th == h) return;
                if (SetResolution(screen, tw, th))
                {
                    _ourW = tw; _ourH = th; _resolutionChanged = true;
                    done.Add("разрешение " + _origW + "x" + _origH + "→" + tw + "x" + th);
                }
            }
            catch (Exception e) { SafeLog("Разрешение: " + e.Message); }
        }

        private void RestoreResolution(List<string> done)
        {
            if (!_resolutionChanged) return;
            try
            {
                Type screen = U.Core("UnityEngine.Screen");
                int w = System.Convert.ToInt32(U.GetStatic(screen, "width"));
                int h = System.Convert.ToInt32(U.GetStatic(screen, "height"));
                if (w == _ourW && h == _ourH && SetResolution(screen, _origW, _origH))
                    done.Add("разрешение возвращено " + _origW + "x" + _origH);
            }
            catch { }
            _resolutionChanged = false;
        }

        private static bool SetResolution(Type screen, int w, int h)
        {
            if (screen == null) return false;
            object mode = U.GetStatic(screen, "fullScreenMode");
            foreach (System.Reflection.MethodInfo m in screen.GetMethods(System.Reflection.BindingFlags.Static | System.Reflection.BindingFlags.Public))
            {
                if (m.Name != "SetResolution") continue;
                System.Reflection.ParameterInfo[] ps = m.GetParameters();
                if (ps.Length != 3) continue;
                try
                {
                    if (ps[2].ParameterType.IsEnum && mode != null)
                    {
                        m.Invoke(null, new object[] { w, h, U.Convert(mode, ps[2].ParameterType) });
                        return true;
                    }
                }
                catch { }
            }
            // Старые версии Unity: SetResolution(int, int, bool).
            foreach (System.Reflection.MethodInfo m in screen.GetMethods(System.Reflection.BindingFlags.Static | System.Reflection.BindingFlags.Public))
            {
                if (m.Name != "SetResolution") continue;
                System.Reflection.ParameterInfo[] ps = m.GetParameters();
                if (ps.Length != 3 || ps[2].ParameterType != typeof(bool)) continue;
                try { m.Invoke(null, new object[] { w, h, true }); return true; } catch { }
            }
            return false;
        }

        // ---------- постобработка на камерах ----------

        private void ApplyCameras(bool disablePost, List<string> done)
        {
            if (!disablePost) return;
            object cams = U.GetStatic(U.Core("UnityEngine.Camera"), "allCameras");
            if (cams == null) return;

            Type ppLayer = U.T("UnityEngine.Rendering.PostProcessing.PostProcessLayer", "Unity.Postprocessing.Runtime");
            Type urpCam = U.T("UnityEngine.Rendering.Universal.UniversalAdditionalCameraData", "Unity.RenderPipelines.Universal.Runtime");
            int n = 0;
            foreach (object cam in U.Enumerate(cams))
            {
                try
                {
                    if (ppLayer != null)
                    {
                        object layer = U.GetComponent(cam, ppLayer);
                        if (layer != null && SetObj(layer, new[] { "enabled" }, false, "PostProcessLayer", new List<string>())) n++;
                    }
                    if (urpCam != null)
                    {
                        object data = U.GetComponent(cam, urpCam);
                        if (data != null)
                        {
                            if (SetObj(data, new[] { "renderPostProcessing", "m_RenderPostProcessing" }, false, "URP постобработка", new List<string>())) n++;
                            SetObj(data, new[] { "antialiasing", "m_Antialiasing" }, 0, "URP сглаживание", new List<string>());
                        }
                    }
                }
                catch { }
            }
            if (n > 0) done.Add("постобработка выключена на камерах: " + n);
        }

        // ======================= экран: FPS и подсказки =======================

        private Type _guiType, _rectType, _colorType;
        private System.Reflection.MethodInfo _label;
        private System.Reflection.PropertyInfo _guiColor;

        public void OnGUI()
        {
            if (_guiBroken) return;
            bool toast = _toast != null && Now < _toastUntil;
            if (!_showFps.Value && !toast) return;
            try
            {
                if (_label == null && !InitGui()) return;
                if (_showFps.Value) Label(8, 6, 120, 22, "FPS " + Math.Round(_fps));
                if (toast) Label(8, _showFps.Value ? 26 : 6, 400, 22, _toast);
            }
            catch (Exception e)
            {
                FallbackToTitle("OnGUI: " + e.Message);
            }
        }

        private bool InitGui()
        {
            _guiType = U.T("UnityEngine.GUI", "UnityEngine.IMGUIModule", "UnityEngine");
            _rectType = U.Core("UnityEngine.Rect");
            _colorType = U.Core("UnityEngine.Color");
            if (_guiType == null || _rectType == null) { FallbackToTitle("нет UnityEngine.GUI"); return false; }
            foreach (System.Reflection.MethodInfo m in _guiType.GetMethods(System.Reflection.BindingFlags.Static | System.Reflection.BindingFlags.Public))
            {
                if (m.Name != "Label") continue;
                System.Reflection.ParameterInfo[] ps = m.GetParameters();
                if (ps.Length == 2 && ps[0].ParameterType == _rectType && ps[1].ParameterType == typeof(string)) { _label = m; break; }
            }
            _guiColor = _guiType.GetProperty("color", System.Reflection.BindingFlags.Static | System.Reflection.BindingFlags.Public);
            if (_label == null) { FallbackToTitle("нет GUI.Label"); return false; }
            return true;
        }

        // Структуры Unity: через конструктор, а если его нет (бывает в IL2CPP-прокси) — через поля.
        private static object MakeStruct(Type t, string[] fields, float[] values)
        {
            if (t == null) return null;
            try { return Activator.CreateInstance(t, values[0], values[1], values[2], values[3]); } catch { }
            try
            {
                object o = Activator.CreateInstance(t);
                for (int i = 0; i < fields.Length; i++)
                {
                    System.Reflection.FieldInfo f = t.GetField(fields[i], System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.NonPublic);
                    if (f != null) f.SetValue(o, values[i]);
                    else if (!U.TrySet(o, fields[i], values[i])) return null;
                }
                return o;
            }
            catch { return null; }
        }

        private static readonly string[] RectFields = { "m_XMin", "m_YMin", "m_Width", "m_Height" };
        private static readonly string[] ColorFields = { "r", "g", "b", "a" };

        private object MakeRect(float x, float y, float w, float h)
        {
            return MakeStruct(_rectType, RectFields, new[] { x, y, w, h });
        }

        private object MakeColor(float r, float g, float b)
        {
            return MakeStruct(_colorType, ColorFields, new[] { r, g, b, 1f });
        }

        private void Label(float x, float y, float w, float h, string text)
        {
            object shadow = MakeColor(0, 0, 0), white = MakeColor(1, 1, 1);
            if (_guiColor != null && shadow != null)
            {
                try
                {
                    _guiColor.SetValue(null, shadow, null);
                    _label.Invoke(null, new object[] { MakeRect(x + 1, y + 1, w, h), text });
                    _guiColor.SetValue(null, white, null);
                }
                catch { }
            }
            object rect = MakeRect(x, y, w, h);
            if (rect == null) throw new InvalidOperationException("нет UnityEngine.Rect");
            _label.Invoke(null, new object[] { rect, text });
        }

        private void FallbackToTitle(string why)
        {
            _guiBroken = true;
            _titleMode = true;
            SafeLog("Счётчик на экране недоступен (" + why + "). FPS и пресет будут видны в заголовке окна.");
        }
    }
}
