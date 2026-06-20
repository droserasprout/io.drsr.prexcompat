package io.drsr.prexcompat

import android.content.Context
import android.provider.Settings
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.security.MessageDigest

object FirebaseFix {
    private const val PLUGIN = "com.getcapacitor.community.firebaseanalytics.FirebaseAnalytics"

    @Volatile
    private var cachedId: String? = null

    fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cl = lpparam.classLoader
        try {
            val plugin = XposedHelpers.findClass(PLUGIN, cl)
            val jsObjectCls = XposedHelpers.findClass("com.getcapacitor.JSObject", cl)
            XposedBridge.hookAllMethods(
                plugin,
                "getAppInstanceId",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val id = instanceId(param.thisObject)
                        if (id == null) {
                            XLog.log("no per-device id available; not patching this call")
                            return
                        }
                        val call = param.args[0]
                        val obj = XposedHelpers.newInstance(jsObjectCls)
                        XposedHelpers.callMethod(obj, "put", "instanceId", id)
                        XposedHelpers.callMethod(call, "resolve", obj)
                        param.result = null
                    }
                },
            )
            XLog.log("firebase getAppInstanceId patched")
        } catch (e: Throwable) {
            XLog.log("FirebaseFix failed: $e")
        }
    }

    // Stable, per-device, unique-looking id derived from ANDROID_ID.
    // Deterministic, salted, cached. Returns null if it can't be derived.
    private fun instanceId(plugin: Any): String? {
        cachedId?.let { return it }
        val androidId =
            try {
                val context = XposedHelpers.callMethod(plugin, "getContext") as Context
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Throwable) {
                XLog.log("ANDROID_ID read failed: $e")
                null
            }
        if (androidId.isNullOrEmpty()) return null
        return sha256Hex("prex:$androidId").take(32).also { cachedId = it }
    }

    private fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
