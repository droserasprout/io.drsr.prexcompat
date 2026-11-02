package io.drsr.prexcompat

import io.github.libxposed.api.XposedModule

// Obfuscated gate names vary by SDK; retain the legacy hook for older apps.
object IncodeHook {
    private const val GATE_CLASS = "com.incode.welcome_sdk.commons.utils.n"
    private const val GATE_METHOD = "d"
    private const val VERDICT_CLASS = "com.incode.welcome_sdk.commons.utils.n\$d"

    fun init(
        module: XposedModule,
        cl: ClassLoader,
    ) {
        fixExternalToken(module, cl)
        IncodeDiagnostics.init(module, cl)
        hookLegacyGate(module, cl)
    }

    // The protected getter can allocate an int[] larger than the heap.
    // Read the backing field instead, preserving the supplied token and null.
    private fun fixExternalToken(
        module: XposedModule,
        cl: ClassLoader,
    ) {
        try {
            Class.forName("com.incode.welcome_obf.w", false, cl)
            val config = Class.forName("com.incode.welcome_sdk.SessionConfig", false, cl)
            val token = config.getDeclaredField("externalToken")
            val getter = config.getDeclaredMethod("getExternalToken")
            check(token.type == String::class.java && getter.returnType == String::class.java)
            token.isAccessible = true
            module.hook(getter).intercept { chain -> token.get(chain.thisObject) }
            module.logi("Incode externalToken getter patched (original token preserved)")
        } catch (_: ClassNotFoundException) {
            module.logi("Incode protected SessionConfig not present; trying legacy gate")
        } catch (e: Throwable) {
            module.logi("Incode externalToken fix failed: $e")
        }
    }

    private fun hookLegacyGate(
        module: XposedModule,
        cl: ClassLoader,
    ) {
        try {
            val gate = Class.forName(GATE_CLASS, false, cl)
            val verdict = Class.forName(VERDICT_CLASS, false, cl)
            val method = gate.getDeclaredMethod(GATE_METHOD, verdict)
            module.hook(method).intercept { null }
            module.logi("Incode environment gate neutralized")
        } catch (_: ClassNotFoundException) {
            module.logi("Incode legacy environment gate not present")
        } catch (e: Throwable) {
            module.logi("Incode environment gate hook failed: $e")
        }
    }
}
