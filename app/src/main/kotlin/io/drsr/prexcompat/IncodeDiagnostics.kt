package io.drsr.prexcompat

import io.github.libxposed.api.XposedModule
import java.lang.reflect.Member
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

// Diagnostic only: do not change arguments, return values, or SDK exceptions.
object IncodeDiagnostics {
    private const val CALLBACK = "com.incode.welcome_obf.aqV2788"
    private val installed = Collections.synchronizedSet(mutableSetOf<Class<*>>())
    private val armedLoaders = Collections.synchronizedSet(mutableSetOf<ClassLoader>())
    private val matches = AtomicInteger()
    private val mismatches = AtomicInteger()

    fun init(
        module: XposedModule,
        loader: ClassLoader,
    ) {
        try {
            val resolver = Class.forName("com.incode.welcome_obf.w", false, loader)
            module.hookClassInitializer(resolver).intercept { chain ->
                module.logi("Incode diagnostics: resolver initialization entered")
                val result = chain.proceed()
                module.logi("Incode diagnostics: resolver initialization completed")
                result
            }
            val methods =
                listOf(
                    resolver.getDeclaredMethod("b", Int::class.javaPrimitiveType),
                    resolver.getDeclaredMethod(
                        "d",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Char::class.javaPrimitiveType,
                    ),
                    resolver.getDeclaredMethod(
                        "c",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Char::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                        String::class.java,
                        emptyArray<Class<*>>().javaClass,
                    ),
                )
            methods.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    observe(module, result)
                    result
                }
            }
            module.logi("Incode diagnostics: resolver observers ready; original behavior retained")
        } catch (error: Throwable) {
            module.logi("Incode diagnostics: resolver setup failed (${error.javaClass.simpleName})")
        }
    }

    private fun observe(
        module: XposedModule,
        result: Any?,
    ) {
        try {
            val resolved =
                when (result) {
                    is Class<*> -> result
                    is Member -> result.declaringClass
                    else -> return
                }
            if (!resolved.name.startsWith("com.incode.welcome_obf.")) return
            val loader = resolved.classLoader ?: return
            if (loader in armedLoaders) return
            val callback = if (resolved.name == CALLBACK) resolved else Class.forName(CALLBACK, false, loader)
            synchronized(installed) {
                if (callback in installed) {
                    armedLoaders.add(loader)
                    return
                }
                val method = callback.getDeclaredMethod("c", Array<Any>::class.java)
                module.hook(method).intercept { chain ->
                    record(module, chain.args.firstOrNull())
                    chain.proceed()
                }
                installed.add(callback)
                armedLoaders.add(loader)
                module.logi("Incode diagnostics: callback armed; original behavior retained")
            }
        } catch (_: ClassNotFoundException) {
            // Some resolver results use a parent loader without the runtime DEX.
        } catch (error: Throwable) {
            module.logi("Incode diagnostics: callback setup failed (${error.javaClass.simpleName})")
        }
    }

    private fun record(
        module: XposedModule,
        argument: Any?,
    ) {
        try {
            val args = argument as? Array<*> ?: return
            val actual = (args.getOrNull(2) as? IntArray)?.firstOrNull()
            val expected = (args.getOrNull(3) as? IntArray)?.firstOrNull()
            val delta = if (actual != null && expected != null) actual xor expected else null
            val mismatch = actual == null || expected == null || actual != expected
            val event = (if (mismatch) mismatches else matches).incrementAndGet()
            val limit = if (mismatch) 32 else 4
            if (event == limit + 1) module.logi("Incode diagnostics: event cap reached mismatch=$mismatch")
            if (event > limit) return
            val strings = args.getOrNull(0) as? Array<*>
            val summary = strings?.take(8)?.joinToString { summarize(it) } ?: "none"
            module.logi(
                "Incode diagnostic mismatch=$mismatch event=$event actual=$actual expected=$expected xor=$delta " +
                    "strings=${strings?.size ?: 0} [$summary]",
            )
        } catch (error: Throwable) {
            module.logi("Incode diagnostics: record failed (${error.javaClass.simpleName})")
        }
    }

    private fun summarize(value: Any?): String {
        if (value !is String) return "non-string"
        // No raw strings, tokens, identifiers, or file paths go into logcat.
        val digest = MessageDigest.getInstance("SHA-256").digest(value.take(4096).toByteArray())
        val fingerprint = digest.take(6).joinToString("") { "%02x".format(it) }
        return "length=${value.length},sha256-prefix4096=$fingerprint"
    }
}
