package io.drsr.prexcompat

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

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

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cl = lpparam.classLoader
        val returnFalse =
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = false
                }
            }
        hookMethods(cl, "com.scottyab.rootbeer.RootBeer", ROOTBEER_METHODS, returnFalse)
        hookMethods(cl, "de.cyberkatze.iroot.InternalRootDetection", IROOT_METHODS, returnFalse)
        try {
            val native = XposedHelpers.findClass("com.scottyab.rootbeer.RootBeerNative", cl)
            XposedBridge.hookAllMethods(
                native,
                "checkForRoot",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.result = 0
                    }
                },
            )
        } catch (e: Throwable) {
            XLog.log("RootBeerNative not found: $e")
        }
        XLog.log("root detectors neutralized")
    }

    private fun hookMethods(
        cl: ClassLoader,
        className: String,
        methods: List<String>,
        hook: XC_MethodHook,
    ) {
        try {
            val clazz = XposedHelpers.findClass(className, cl)
            methods.forEach { XposedBridge.hookAllMethods(clazz, it, hook) }
        } catch (e: Throwable) {
            XLog.log("$className not found: $e")
        }
    }
}
