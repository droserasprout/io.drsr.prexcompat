package io.drsr.prexcompat

import android.util.Log
import io.github.libxposed.api.XposedInterface

const val LOG_TAG = "PrexCompat"

// The modern API routes logging through the module's XposedInterface instead of the
// global XposedBridge.log. Expose it as an extension so hook code reads the same as before.
fun XposedInterface.logi(msg: String) = log(Log.INFO, LOG_TAG, msg)
