> ⚠️ Installing unknown patches for banking apps is usually a very bad idea. Read and understand the code!

# Prex Compat

<img src=oops.png width=200 align=right alt="Algo salió mal screen">

Xposed module to make the [Prex Uruguay](https://play.google.com/store/apps/details?id=air.Prex) app work on de-googled / rooted devices.

## Hooks

- **RootHook** — forces RootBeer (`com.scottyab.rootbeer`) and IRoot (`de.cyberkatze.iroot`) checks to "not rooted", suppressing "Dispositivo está rooteado" rejection on startup.
- **FirebaseFix** — makes the Capacitor Firebase plugin's `getAppInstanceId` resolve a stable non-empty string, so the `Device-Firebase-Instance-Id` request header isn't null. Without this, the login POST is rejected client-side and the app shows "Algo salió mal" (likely a bug, introduced around `10.70.00`).

### KYC

> Does not touch the document classifier or anything uploaded to Incode — only disables the local root/hook check. Your real ID is scanned and sent unchanged.

Around `10.72.00` Prex added ID verification via the Incode Welcome SDK, whose anti-tamper layer detects Xposed and throws `DeviceEnvironmentException` right after capture ("No se pudo validar tu identidad") — tripping on the very module running the hooks above.

- **IncodeHook** — no-ops the SDK's environment gate (`com.incode.welcome_sdk.commons.utils.n#d`), which throws on emulator / hooking framework / virtual environment / root. Obfuscated name is version-specific.

## Tested configurations

Tested on Android 15/16 (LineageOS) + Magisk + Zygisk + LSPosed, should work on other configurations. Additional root hiding solutions like Shamiko are not required, but don't interfere either.

App versions:

- `10.69.01` - one of the latest without broken Firebase. Not working, disabled by Prex.
- `10.72.00` - Active, tested with PrexCompat.
- `10.74.00` - Latest. NOT TESTED.

Also not tested on other regional variants — [Argentina](https://play.google.com/store/apps/details?id=air.PrexArgentina), [Chile](https://play.google.com/store/apps/details?id=air.PrexChile), [Peru](https://play.google.com/store/apps/details?id=air.PrexPeru). PRs are welcome.

## Build

```sh
# app/build/outputs/apk/debug/app-debug.apk
make build
```

Requires JDK 21 + Android SDK.

## Install

Install the APK, enable the module in LSPosed with scope `air.Prex`, force-stop the app. If you're running Magisk with "Enforce DenyList" enabled — remove Prex from this list to allow Xposed injection.

## License

[GPL-3.0](LICENSE)

<img src=linus.png width=150 height=75 alt="Linus Torvalds showing Prex the middle finger">
