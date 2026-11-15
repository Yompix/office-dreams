// Сборка для Mono-игр: BepInEx 5.4.x.
using BepInEx;

namespace UnityBoost
{
    [BepInPlugin(BoostCore.Guid, BoostCore.Name, BoostCore.Version)]
    public class UnityBoostPlugin : BaseUnityPlugin
    {
        private BoostCore _core;

        private void Awake()
        {
            try { _core = new BoostCore(Config, Logger); }
            catch (System.Exception e) { Logger.LogError("Unity Boost не запустился: " + e); }
        }

        private void Update()
        {
            if (_core != null) _core.Update();
        }

        private void OnGUI()
        {
            if (_core != null) _core.OnGUI();
        }
    }
}
