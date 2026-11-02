package io.drsr.prexcompat

import android.content.Context
import android.provider.Settings
import io.github.libxposed.api.XposedModule
import java.security.MessageDigest

object FirebaseFix {
    private const val PLUGIN = "com.getcapacitor.community.firebaseanalytics.FirebaseAnalytics"

    @Volatile
    private var cachedId: String? = null

    fun init(
        module: XposedModule,
        cl: ClassLoader,
    ) {
        try {
            val plugin = Class.forName(PLUGIN, false, cl)
            val jsObjectCls = Class.forName("com.getcapacitor.JSObject", false, cl)
            plugin.declaredMethods
                .filter { it.name == "getAppInstanceId" }
                .forEach { m ->
                    module.hook(m).intercept { chain ->
                        val id = instanceId(module, chain.thisObject)
                        if (id == null) {
                            module.logi("no per-device id available; not patching this call")
                            return@intercept chain.proceed()
                        }
                        val call = chain.args[0]
                        val obj = jsObjectCls.getDeclaredConstructor().newInstance()
                        jsObjectCls.getMethod("put", String::class.java, String::class.java)
                            .invoke(obj, "instanceId", id)
                        call.javaClass.getMethod("resolve", jsObjectCls).invoke(call, obj)
                        null
                    }
                }
            module.logi("firebase getAppInstanceId patched")
        } catch (e: Throwable) {
            module.logi("FirebaseFix failed: $e")
        }
    }

    // Use a stable device-derived ID when Firebase cannot supply one.
    private fun instanceId(
        module: XposedModule,
        plugin: Any,
    ): String? {
        cachedId?.let { return it }
        val androidId =
            try {
                val context = plugin.javaClass.getMethod("getContext").invoke(plugin) as Context
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            } catch (e: Throwable) {
                module.logi("ANDROID_ID read failed: $e")
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
