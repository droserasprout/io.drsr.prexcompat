package io.drsr.prexcompat

import android.util.Log
import io.github.libxposed.api.XposedInterface

const val LOG_TAG = "PrexCompat"

fun XposedInterface.logi(msg: String) = log(Log.INFO, LOG_TAG, msg)
