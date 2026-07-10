package io.drsr.prexcompat

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

// Incode Welcome SDK's anti-tamper layer throws DeviceEnvironmentException after a
// document is captured, failing KYC with "No se pudo validar tu identidad". The gate is
// one void method fed four detection booleans (emulator, hooking framework, virtual
// environment, root); no-op it and all four pass. Obfuscated names are version-specific;
// verified against Incode 5.41.1 (Prex 10.72).
object IncodeHook {
    private const val GATE_CLASS = "com.incode.welcome_sdk.commons.utils.n"
    private const val GATE_METHOD = "d"
    private const val VERDICT_CLASS = "com.incode.welcome_sdk.commons.utils.n\$d"

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                GATE_CLASS,
                lpparam.classLoader,
                GATE_METHOD,
                VERDICT_CLASS,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = null
                    }
                },
            )
            XLog.log("Incode environment gate neutralized")
        } catch (e: Throwable) {
            XLog.log("Incode environment gate not found: $e")
        }
    }
}
