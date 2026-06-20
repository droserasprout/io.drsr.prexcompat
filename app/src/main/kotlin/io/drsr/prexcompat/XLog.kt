package io.drsr.prexcompat

import de.robv.android.xposed.XposedBridge

object XLog {
    fun log(msg: String) = XposedBridge.log("PrexCompat: $msg")
}
