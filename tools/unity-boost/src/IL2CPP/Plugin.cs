// Сборка для IL2CPP-игр: BepInEx 6 (bleeding edge) Unity.IL2CPP.
using System;
using BepInEx;
using BepInEx.Unity.IL2CPP;
using UnityEngine;

namespace UnityBoost
{
    [BepInPlugin(BoostCore.Guid, BoostCore.Name, BoostCore.Version)]
    public class UnityBoostPlugin : BasePlugin
    {
        internal static BoostCore Core;

        public override void Load()
        {
            try
            {
                Core = new BoostCore(Config, Log);
                AddComponent<UnityBoostBehaviour>(); // регистрирует класс в IL2CPP (ClassInjector) и вешает на объект BepInEx
            }
            catch (Exception e) { Log.LogError("Unity Boost не запустился: " + e); }
        }
    }

    // MonoBehaviour, внедрённый в IL2CPP: Unity сама вызывает Update/OnGUI каждый кадр.
    public class UnityBoostBehaviour : MonoBehaviour
    {
        public UnityBoostBehaviour(IntPtr ptr) : base(ptr) { }

        public void Update()
        {
            if (UnityBoostPlugin.Core != null) UnityBoostPlugin.Core.Update();
        }

        public void OnGUI()
        {
            if (UnityBoostPlugin.Core != null) UnityBoostPlugin.Core.OnGUI();
        }
    }
}
