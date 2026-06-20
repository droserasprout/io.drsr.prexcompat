package io.drsr.prexcompat

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

private const val TARGET_PACKAGE = "air.Prex"

class PrexCompatModule : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE || lpparam.processName != TARGET_PACKAGE) return
        RootHook.init(lpparam)
        FirebaseFix.init(lpparam)
    }
}
