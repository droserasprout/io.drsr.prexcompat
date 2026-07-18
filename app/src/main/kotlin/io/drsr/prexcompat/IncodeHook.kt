package io.drsr.prexcompat

import io.github.libxposed.api.XposedModule

// Incode Welcome SDK's anti-tamper layer throws DeviceEnvironmentException after a
// document is captured, failing KYC with "No se pudo validar tu identidad". The gate is
// one void method fed four detection booleans (emulator, hooking framework, virtual
// environment, root); no-op it and all four pass. Obfuscated names are version-specific;
// verified against Incode 5.41.1 (Prex 10.72).
object IncodeHook {
    private const val GATE_CLASS = "com.incode.welcome_sdk.commons.utils.n"
    private const val GATE_METHOD = "d"
    private const val VERDICT_CLASS = "com.incode.welcome_sdk.commons.utils.n\$d"

    fun init(
        module: XposedModule,
        cl: ClassLoader,
    ) {
        try {
            val gate = Class.forName(GATE_CLASS, false, cl)
            val verdict = Class.forName(VERDICT_CLASS, false, cl)
            val method = gate.getDeclaredMethod(GATE_METHOD, verdict)
            module.hook(method).intercept { null }
            module.logi("Incode environment gate neutralized")
        } catch (e: Throwable) {
            module.logi("Incode environment gate not found: $e")
        }
    }
}
