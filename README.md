> ⚠️ Installing unknown patches for banking apps is usually a very bad idea. Read and understand the code!

# Prex Compat

<img src=oops.png width=200 align=right alt="Algo salió mal screen">

Xposed module to make the [Prex Uruguay](https://play.google.com/store/apps/details?id=air.Prex) app work on de-googled / rooted devices.

## Hooks

- **RootHook** — forces RootBeer (`com.scottyab.rootbeer`) and IRoot (`de.cyberkatze.iroot`) checks to "not rooted", suppressing "Dispositivo está rooteado" rejection on startup.
- **FirebaseFix** — makes the Capacitor Firebase plugin's `getAppInstanceId` resolve a stable non-empty string, so the `Device-Firebase-Instance-Id` request header isn't null. Without this, the login POST is rejected client-side and the app shows "Algo salió mal" (likely a bug, introduced around `10.70.00`).

### KYC

Incode’s device checks can reject rooted devices or crash when LSPosed is loaded.

- **IncodeHook** skips the legacy environment gate and reads
  `SessionConfig.externalToken` directly to avoid a crash in its protected getter.
  It preserves the token, including null.
- **[ART View Sync](art-view-sync/README.md)** is a companion Magisk module.
  It gives the app a copy of `libart.so` matching its live ART memory, allowing
  Incode’s file comparison to pass with LSPosed loaded.

Document capture, liveness checks, uploads, and server verification are unchanged.

## Tested configurations

Tested on Android 15/16/17 (LineageOS) with Magisk, Zygisk, and LSPosed.
Additional root hiding tools such as Shamiko are not required.

Active app versions:

- `10.80.00` - Working with PrexCompat.
- `10.79.00` - Working with PrexCompat. Selfie KYC verified.

Older versions are disabled by Prex.

Not tested on other regional variants — [Argentina](https://play.google.com/store/apps/details?id=air.PrexArgentina), [Chile](https://play.google.com/store/apps/details?id=air.PrexChile), [Peru](https://play.google.com/store/apps/details?id=air.PrexPeru). PRs are welcome.

## Build

```sh
# app/build/outputs/apk/debug/app-debug.apk
make build

# build/art-view-sync.zip
make magisk
```

Requires JDK 21 and Android SDK 36. Build ART View Sync separately using
its [build instructions](art-view-sync/README.md#build).

## Install

Install the APK and enable the module in LSPosed, then force-stop the app. Scope is fixed to `air.Prex` (`staticScope=true` via `META-INF/xposed/scope.list`), so no manual scope selection is needed. If you're running Magisk with "Enforce DenyList" enabled — remove Prex from this list to allow Xposed injection.

If app requests selfie verification to pass KYC:

- Install ART View Sync module, reboot
- Pass the KYC in Prex
- Disable or uninstall module, reboot

## License

[GPL-3.0](LICENSE)

<img src=linus.png width=150 height=75 alt="Linus Torvalds showing Prex the middle finger">
