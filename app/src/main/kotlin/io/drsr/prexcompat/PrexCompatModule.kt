package io.drsr.prexcompat

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

private const val TARGET_PACKAGE = "air.Prex"

class PrexCompatModule : XposedModule() {
    // onPackageReady lacks the process name; save it to exclude child processes.
    private var processName: String = ""

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != TARGET_PACKAGE || processName != TARGET_PACKAGE) return
        val cl = param.classLoader
        RootHook.init(this, cl)
        FirebaseFix.init(this, cl)
        IncodeHook.init(this, cl)
    }
}
