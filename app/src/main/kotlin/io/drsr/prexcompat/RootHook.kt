package io.drsr.prexcompat

import io.github.libxposed.api.XposedModule

object RootHook {
    private val ROOTBEER_METHODS =
        listOf(
            "isRooted", "isRootedWithoutBusyBoxCheck", "isRootedWithBusyBoxCheck",
            "detectTestKeys", "detectRootManagementApps", "detectPotentiallyDangerousApps",
            "detectRootCloakingApps", "checkForSuBinary", "checkForMagiskBinary",
            "checkForBusyBoxBinary", "checkForDangerousProps", "checkForRWPaths",
            "checkSuExists", "checkForRootNative", "checkForNativeLibraryReadAccess",
        )
    private val IROOT_METHODS = listOf("isRooted", "isRootedWithEmulator", "isRunningOnEmulator")

    fun init(
        module: XposedModule,
        cl: ClassLoader,
    ) {
        forceResult(module, cl, "com.scottyab.rootbeer.RootBeer", ROOTBEER_METHODS, false)
        forceResult(module, cl, "de.cyberkatze.iroot.InternalRootDetection", IROOT_METHODS, false)
        // RootBeerNative.checkForRoot returns int; the classic hook forced it to 0.
        forceResult(module, cl, "com.scottyab.rootbeer.RootBeerNative", listOf("checkForRoot"), 0)
        module.logi("root detectors neutralized")
    }

    // Replaces every overload named in `methods` on `className` with one that returns `result`
    // without invoking the original (return-without-proceed skips the real method).
    private fun forceResult(
        module: XposedModule,
        cl: ClassLoader,
        className: String,
        methods: List<String>,
        result: Any?,
    ) {
        try {
            val clazz = Class.forName(className, false, cl)
            clazz.declaredMethods
                .filter { it.name in methods }
                .forEach { m -> module.hook(m).intercept { result } }
        } catch (e: Throwable) {
            module.logi("$className not found: $e")
        }
    }
}
