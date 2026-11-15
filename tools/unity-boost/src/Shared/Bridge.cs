// Unity Boost RU — доступ к Unity API через reflection.
// Один и тот же код работает и в Mono-играх (BepInEx 5), и в IL2CPP-играх (BepInEx 6):
// в IL2CPP типы UnityEngine.* — это прокси-классы из BepInEx/interop с теми же именами.
// Каждый вызов может не сработать (нет API в этой версии Unity, метод вырезан стриппингом) —
// поэтому всё возвращает bool/null и никогда не бросает исключения наружу.
using System;
using System.Collections;
using System.Collections.Generic;
using System.Reflection;
using System.Runtime.InteropServices;

namespace UnityBoost
{
    internal static class U
    {
        private const BindingFlags Inst = BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic;
        private const BindingFlags Stat = BindingFlags.Static | BindingFlags.Public;

        private static readonly Dictionary<string, Type> TypeCache = new Dictionary<string, Type>();

        // Где искать типы: имя сборки в новых Unity (модули) и старое UnityEngine.
        public static Type T(string fullName, params string[] assemblies)
        {
            Type t;
            if (TypeCache.TryGetValue(fullName, out t)) return t;
            t = null;
            try
            {
                foreach (Assembly a in AppDomain.CurrentDomain.GetAssemblies())
                {
                    try { t = a.GetType(fullName, false); } catch { t = null; }
                    if (t != null) break;
                }
                if (t == null)
                {
                    foreach (string asm in assemblies)
                    {
                        try { t = Type.GetType(fullName + ", " + asm, false); } catch { t = null; }
                        if (t != null) break;
                    }
                }
            }
            catch { t = null; }
            // Ненайденные типы не кэшируем навсегда: сборка (например, URP) может загрузиться позже.
            if (t != null) TypeCache[fullName] = t;
            return t;
        }

        public static Type Core(string fullName)
        {
            return T(fullName, "UnityEngine.CoreModule", "UnityEngine");
        }

        public static object Convert(object value, Type target)
        {
            if (value == null || target == null) return value;
            if (target.IsInstanceOfType(value)) return value;
            if (target.IsEnum) return Enum.ToObject(target, System.Convert.ToInt32(value));
            return System.Convert.ChangeType(value, target, System.Globalization.CultureInfo.InvariantCulture);
        }

        public static double ToDouble(object v)
        {
            if (v == null) return double.NaN;
            if (v is bool) return ((bool)v) ? 1 : 0;
            try { return System.Convert.ToDouble(v, System.Globalization.CultureInfo.InvariantCulture); }
            catch { return double.NaN; }
        }

        // ---------- статические свойства (QualitySettings, Application, Screen, Time) ----------

        public static PropertyInfo StaticProp(Type t, string name)
        {
            if (t == null) return null;
            try { return t.GetProperty(name, Stat); } catch { return null; }
        }

        public static bool TryGetStatic(Type t, string name, out object value)
        {
            value = null;
            PropertyInfo p = StaticProp(t, name);
            if (p != null && p.CanRead)
            {
                try { value = p.GetValue(null, null); return true; } catch { }
            }
#if IL2CPP
            return Il2CppICall.TryGet(t, name, p != null ? p.PropertyType : null, out value);
#else
            return false;
#endif
        }

        public static object GetStatic(Type t, string name)
        {
            object v;
            return TryGetStatic(t, name, out v) ? v : null;
        }

        public static bool SetStatic(Type t, string name, object value)
        {
            PropertyInfo p = StaticProp(t, name);
            if (p != null && p.CanWrite)
            {
                try { p.SetValue(null, Convert(value, p.PropertyType), null); return true; } catch { }
            }
#if IL2CPP
            // Если сеттер вырезан при сборке игры — зовём родную функцию движка напрямую.
            return Il2CppICall.TrySet(t, name, value);
#else
            return false;
#endif
        }

        public static object CallStatic(Type t, string name, params object[] args)
        {
            if (t == null) return null;
            foreach (MethodInfo m in t.GetMethods(Stat))
            {
                if (m.Name != name || m.IsGenericMethodDefinition) continue;
                ParameterInfo[] ps = m.GetParameters();
                if (ps.Length != args.Length) continue;
                try
                {
                    object[] conv = new object[args.Length];
                    for (int i = 0; i < args.Length; i++) conv[i] = Convert(args[i], ps[i].ParameterType);
                    return m.Invoke(null, conv);
                }
                catch { }
            }
            return null;
        }

        // ---------- члены объектов (свойство или поле; в IL2CPP поля тоже видны как свойства) ----------

        public static bool TryGet(object o, string name, out object value)
        {
            value = null;
            if (o == null) return false;
            Type t = o.GetType();
            try
            {
                PropertyInfo p = t.GetProperty(name, Inst);
                if (p != null && p.CanRead && p.GetIndexParameters().Length == 0) { value = p.GetValue(o, null); return true; }
                FieldInfo f = t.GetField(name, Inst);
                if (f != null) { value = f.GetValue(o); return true; }
            }
            catch { }
            return false;
        }

        public static bool TrySet(object o, string name, object value)
        {
            if (o == null) return false;
            Type t = o.GetType();
            try
            {
                PropertyInfo p = t.GetProperty(name, Inst);
                if (p != null && p.CanWrite && p.GetIndexParameters().Length == 0)
                {
                    p.SetValue(o, Convert(value, p.PropertyType), null);
                    return true;
                }
                FieldInfo f = t.GetField(name, Inst);
                if (f != null && !f.IsInitOnly)
                {
                    f.SetValue(o, Convert(value, f.FieldType));
                    return true;
                }
            }
            catch { }
            return false;
        }

        // Первый из списка имён, который удалось прочитать.
        public static string FirstReadable(object o, string[] names, out object value)
        {
            value = null;
            foreach (string n in names)
            {
                if (TryGet(o, n, out value)) return n;
            }
            return null;
        }

        // ---------- объекты Unity ----------

        public static IEnumerable Enumerate(object arr)
        {
            IEnumerable e = arr as IEnumerable;
            if (e == null) yield break;
            foreach (object x in e) if (x != null) yield return x;
        }

        // Приведение к конкретному типу (в IL2CPP объект приходит как базовый прокси-тип).
        public static object Cast(object o, Type t)
        {
            if (o == null || t == null) return null;
            if (t.IsInstanceOfType(o)) return o;
#if IL2CPP
            return Il2CppHelpers.TryCast(o, t);
#else
            return null;
#endif
        }

        public static object GetComponent(object component, Type t)
        {
            if (component == null || t == null) return null;
            try
            {
                foreach (MethodInfo m in component.GetType().GetMethods(BindingFlags.Instance | BindingFlags.Public))
                {
                    if (m.Name != "GetComponent" || m.IsGenericMethodDefinition) continue;
                    ParameterInfo[] ps = m.GetParameters();
                    if (ps.Length != 1 || ps[0].ParameterType == typeof(string)) continue;
#if IL2CPP
                    object typeArg = Il2CppHelpers.Il2CppTypeOf(t);
                    if (typeArg == null || !ps[0].ParameterType.IsInstanceOfType(typeArg)) continue;
#else
                    object typeArg = t;
                    if (ps[0].ParameterType != typeof(Type)) continue;
#endif
                    object r = m.Invoke(component, new object[] { typeArg });
                    return r == null ? null : Cast(r, t);
                }
            }
            catch { }
            return null;
        }

        public static bool IsAlive(object unityObject)
        {
            if (unityObject == null) return false;
            try
            {
                // У уничтоженного объекта Unity чтение name бросает исключение или возвращает null-объект.
                object n;
                return TryGet(unityObject, "name", out n);
            }
            catch { return false; }
        }
    }

    // ---------- Windows: клавиши и заголовок окна (работает без Unity Input) ----------
    internal static class Win32
    {
        [DllImport("user32.dll")] private static extern short GetAsyncKeyState(int vKey);
        [DllImport("user32.dll")] private static extern IntPtr GetForegroundWindow();
        [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern bool SetWindowTextW(IntPtr hWnd, string text);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)] private static extern int GetWindowTextW(IntPtr hWnd, System.Text.StringBuilder sb, int max);

        private static uint _pid;

        public static bool GameHasFocus()
        {
            try
            {
                if (_pid == 0) _pid = (uint)System.Diagnostics.Process.GetCurrentProcess().Id;
                uint pid;
                GetWindowThreadProcessId(GetForegroundWindow(), out pid);
                return pid == _pid;
            }
            catch { return true; }
        }

        public static bool IsDown(int vk)
        {
            try { return (GetAsyncKeyState(vk) & 0x8000) != 0; } catch { return false; }
        }

        // "F9" -> 0x78. Поддерживаются F1..F12.
        public static int ParseKey(string s, int fallback)
        {
            if (string.IsNullOrEmpty(s)) return fallback;
            s = s.Trim().ToUpperInvariant();
            if (s.Length >= 2 && s[0] == 'F')
            {
                int n;
                if (int.TryParse(s.Substring(1), out n) && n >= 1 && n <= 12) return 0x70 + n - 1;
            }
            return fallback;
        }

        private static IntPtr _wnd = IntPtr.Zero;
        private static string _origTitle;

        private static IntPtr MainWindow()
        {
            if (_wnd == IntPtr.Zero)
            {
                try { _wnd = System.Diagnostics.Process.GetCurrentProcess().MainWindowHandle; } catch { }
            }
            return _wnd;
        }

        public static void SetTitleSuffix(string suffix)
        {
            try
            {
                IntPtr w = MainWindow();
                if (w == IntPtr.Zero) return;
                if (_origTitle == null)
                {
                    System.Text.StringBuilder sb = new System.Text.StringBuilder(512);
                    GetWindowTextW(w, sb, 512);
                    _origTitle = sb.ToString();
                }
                SetWindowTextW(w, string.IsNullOrEmpty(suffix) ? _origTitle : _origTitle + "  |  " + suffix);
            }
            catch { }
        }
    }
}
