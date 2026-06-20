> ⚠️ Installing unknown patches for banking apps is usually a very bad idea. Read and understand the code!

# Prex Compat

<img src=oops.png width=200 align=right alt="Algo salió mal screen">

Xposed module to make the [Prex Uruguay](https://play.google.com/store/apps/details?id=air.Prex) app work on de-googled / rooted devices. Two patches:

- **RootHook** — forces RootBeer (`com.scottyab.rootbeer`) and IRoot (`de.cyberkatze.iroot`) checks to "not rooted", suppressing "Dispositivo está rooteado" rejection on startup.
- **FirebaseFix** — makes the Capacitor Firebase plugin's `getAppInstanceId` resolve a stable non-empty string, so the `Device-Firebase-Instance-Id` request header isn't null. Without this, the login POST is rejected client-side and the app shows "Algo salió mal" (likely a bug, introduced around `10.70.00`).

Tested on Android 15 + Magisk + Zygisk + LSPosed, should work on other configurations.

NOT tested on other regional variants — [Argentina](https://play.google.com/store/apps/details?id=air.PrexArgentina), [Chile](https://play.google.com/store/apps/details?id=air.PrexChile), [Peru](https://play.google.com/store/apps/details?id=air.PrexPeru).

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
