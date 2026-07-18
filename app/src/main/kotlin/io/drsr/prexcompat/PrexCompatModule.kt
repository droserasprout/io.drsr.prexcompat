package io.drsr.prexcompat

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

private const val TARGET_PACKAGE = "air.Prex"

// Modern Xposed API (>=101) entry point. Registered via META-INF/xposed/java_init.list;
// the framework instantiates it once per scoped process using the no-arg constructor.
class PrexCompatModule : XposedModule() {
    // onPackageReady only exposes the package name, so capture the process name here (the
    // classic module keyed off lpparam.processName to run in the main process only).
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
