# ART View Sync

A Magisk service that gives an app a copy of `libart.so` matching its live
ART memory. This lets Incode’s file comparison pass when LSPosed has
modified ART entry points.

Currently targets `air.Prex`; other apps and SDK versions are untested.
See the main [compatibility list](../README.md#tested-configurations).

The service reads executable mappings through `/proc/<pid>/mem`, copies them
into a library file, and bind-mounts that file inside the app’s mount namespace.
It checks the mounted file against memory and unmounts it if verification fails.
SDK code, process memory, and verification results are unchanged.

## Files

- `module.prop` — module metadata and version.
- `service.sh` — Magisk daemon; accepts `once` for a single sync attempt.
- `src/artviewsync.c` — helper source. Address arithmetic lives in C because
  the device shell truncates 64-bit addresses.
- `bin/artviewsync` — static ARM64 helper.
- `uninstall.sh` — removes the working directory.

## Build

From the repository root, build an installable Magisk ZIP:

```sh
make magisk
# Output: build/art-view-sync.zip
# Override the NDK location if needed:
make magisk ANDROID_NDK_HOME=/path/to/android-ndk
```

Requires the Android NDK and `zip`. The default NDK location is
`/opt/android-ndk`, with host toolchain `linux-x86_64` (override with
`NDK_HOST_TAG`). The ARM64 helper is compiled from source for each build.

## Install

In the Magisk app, choose **Modules → Install from storage**, select
`build/art-view-sync.zip`, and reboot.

After a reboot Magisk starts `service.sh` automatically. Without reboot, or
for validation, run it manually:

```sh
adb shell su -c 'sh /data/adb/modules/artviewsync/service.sh once'
```

## Behavior

The daemon checks the app every five seconds and resyncs after process restarts
or changes to ART memory. In `once` mode, it waits up to 30 seconds for hooks
before syncing.

Working files and `service.log` live in `/data/adb/artviewsync`. The mounted copy
inherits the library’s permissions and SELinux label. Mounts are confined to the
app’s namespace; other processes keep their original library view.
