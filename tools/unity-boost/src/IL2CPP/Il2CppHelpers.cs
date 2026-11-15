// Только для IL2CPP-сборки (BepInEx 6).
using System;
using System.Collections.Generic;
using System.Reflection;
using System.Runtime.InteropServices;
using Il2CppInterop.Runtime;
using Il2CppInterop.Runtime.InteropTypes;

namespace UnityBoost
{
    internal static class Il2CppHelpers
    {
        private static readonly MethodInfo TryCastDef =
            typeof(Il2CppObjectBase).GetMethod("TryCast", BindingFlags.Instance | BindingFlags.Public);

        public static object TryCast(object o, Type t)
        {
            try
            {
                if (TryCastDef == null || !(o is Il2CppObjectBase) || !typeof(Il2CppObjectBase).IsAssignableFrom(t)) return null;
                return TryCastDef.MakeGenericMethod(t).Invoke(o, null);
            }
            catch { return null; }
        }

        public static object Il2CppTypeOf(Type t)
        {
            try { return Il2CppType.From(t); } catch { return null; }
        }
    }

    // Прямой вызов внутренних функций движка ("UnityEngine.QualitySettings::set_shadowDistance").
    // Нужен, когда в IL2CPP-игре сеттер вырезан стриппингом и прокси-свойства нет.
    internal static class Il2CppICall
    {
        [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate void SetI(int v);
        [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate void SetF(float v);
        [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate void SetB(byte v);
        [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate int GetI();
        [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate float GetF();
        [UnmanagedFunctionPointer(CallingConvention.Cdecl)] private delegate byte GetB();

        // Типы значений, которые мы знаем заранее (если прокси-свойства нет совсем).
        private static readonly Dictionary<string, char> Kinds = new Dictionary<string, char>
        {
            { "pixelLightCount", 'i' }, { "antiAliasing", 'i' }, { "anisotropicFiltering", 'i' },
            { "lodBias", 'f' }, { "globalTextureMipmapLimit", 'i' }, { "masterTextureLimit", 'i' },
            { "softParticles", 'b' }, { "realtimeReflectionProbes", 'b' }, { "particleRaycastBudget", 'i' },
            { "skinWeights", 'i' }, { "blendWeights", 'i' }, { "shadows", 'i' }, { "shadowResolution", 'i' },
            { "shadowDistance", 'f' }, { "shadowCascades", 'i' }, { "vSyncCount", 'i' },
            { "targetFrameRate", 'i' }, { "fixedDeltaTime", 'f' },
        };

        private static IntPtr Resolve(Type t, string accessor)
        {
            if (t == null) return IntPtr.Zero;
            try { return IL2CPP.il2cpp_resolve_icall(t.FullName + "::" + accessor); } catch { return IntPtr.Zero; }
        }

        private static char KindOf(string name, Type propType)
        {
            if (propType != null)
            {
                if (propType == typeof(float)) return 'f';
                if (propType == typeof(bool)) return 'b';
                if (propType == typeof(int) || propType.IsEnum) return 'i';
            }
            char k;
            return Kinds.TryGetValue(name, out k) ? k : '\0';
        }

        public static bool TrySet(Type t, string name, object value)
        {
            char k = KindOf(name, null);
            if (k == '\0') return false;
            IntPtr fn = Resolve(t, "set_" + name);
            if (fn == IntPtr.Zero) return false;
            try
            {
                if (k == 'i') Marshal.GetDelegateForFunctionPointer<SetI>(fn)(System.Convert.ToInt32(value));
                else if (k == 'f') Marshal.GetDelegateForFunctionPointer<SetF>(fn)(System.Convert.ToSingle(value));
                else Marshal.GetDelegateForFunctionPointer<SetB>(fn)((byte)(System.Convert.ToBoolean(value) ? 1 : 0));
                return true;
            }
            catch { return false; }
        }

        public static bool TryGet(Type t, string name, Type propType, out object value)
        {
            value = null;
            char k = KindOf(name, propType);
            if (k == '\0') return false;
            IntPtr fn = Resolve(t, "get_" + name);
            if (fn == IntPtr.Zero) return false;
            try
            {
                if (k == 'i') value = Marshal.GetDelegateForFunctionPointer<GetI>(fn)();
                else if (k == 'f') value = Marshal.GetDelegateForFunctionPointer<GetF>(fn)();
                else value = Marshal.GetDelegateForFunctionPointer<GetB>(fn)() != 0;
                return true;
            }
            catch { return false; }
        }
    }
}
