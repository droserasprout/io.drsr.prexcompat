#!/system/bin/sh
# Mount an ART copy matching live memory inside the target app's namespace.
# Magisk starts the daemon at boot; use "once" for a single attempt.

LIB=${LIB:-/apex/com.android.art/lib64/libart.so}
PKG=${PKG:-air.Prex}
STORE=${STORE:-/data/adb/artviewsync}
HELPER=${HELPER:-/data/adb/modules/artviewsync/bin/artviewsync}
PRISTINE=$STORE/libart.pristine.so
LOG=$STORE/service.log

# Paths are overridable for host dry-runs against captured state.
PROC=${PROC:-/proc}

BB=$(command -v busybox 2>/dev/null || true)
[ -x "$BB" ] || BB=/data/adb/magisk/busybox

log() { echo "$(date '+%F %T') $*" >>"$LOG"; }

nsenter_run() {
    if command -v nsenter >/dev/null 2>&1; then
        nsenter "$@"
    else
        "$BB" nsenter "$@"
    fi
}

pid_main() { pidof "$PKG" 2>/dev/null | tr ' ' '\n' | head -n 1; }

# What the app itself would currently read at the library path.
app_view() { echo "$PROC/$1/root$LIB"; }

# pid -> 0 iff the app-visible file equals live executable memory.
view_ok() { "$HELPER" diff "$1" "$(app_view "$1")" >>"$LOG" 2>&1; }

# Wait for LSPosed's trampolines: any live/pristine difference in the
# executable range (helper diff exits nonzero when memory differs).
wait_hooks() {
    i=0
    while [ "$i" -lt 30 ]; do
        if ! "$HELPER" diff "$1" "$PRISTINE" >/dev/null 2>&1; then
            return 0
        fi
        i=$((i + 1))
        sleep 1
    done
    return 1
}

# Unmount if the app-visible copy fails verification.
attempt_sync() {
    pid=$1
    synced=$STORE/libart.synced.$pid.so

    if ! "$HELPER" copy "$pid" "$PRISTINE" "$synced" >>"$LOG" 2>&1; then
        log "pid $pid: synced copy failed"
        rm -f "$synced"
        return 1
    fi

    # Preserve permissions and SELinux access for the app.
    mode=$(stat -c %a "$LIB" 2>/dev/null)
    [ -n "$mode" ] && chmod "$mode" "$synced" 2>>"$LOG"
    label=$(ls -Zd "$LIB" 2>/dev/null | awk '{print $1}')
    if [ -n "$label" ] && [ "$label" != "$LIB" ]; then
        chcon "$label" "$synced" 2>>"$LOG" ||
            log "pid $pid: WARNING chcon failed; app may not read the view"
    fi

    if ! nsenter_run -t "$pid" -m -- mount --bind "$synced" "$LIB" 2>>"$LOG"; then
        log "pid $pid: bind mount failed"
        return 1
    fi
    if ! view_ok "$pid"; then
        log "pid $pid: through-mount verification failed; rolling back"
        nsenter_run -t "$pid" -m umount "$LIB" 2>>"$LOG"
        return 1
    fi
    log "pid $pid: synced ART view mounted and verified"
    return 0
}

prepare() {
    mkdir -p "$STORE"
    # Refresh after system updates that may replace libart.
    if ! cat "$LIB" >"$PRISTINE" 2>>"$LOG"; then
        log "pristine copy of $LIB failed"
        return 1
    fi
    # Drop synced copies left over from dead processes.
    for old in "$STORE"/libart.synced.*.so; do
        [ -f "$old" ] || continue
        oldpid=${old##*libart.synced.}
        oldpid=${oldpid%.so}
        [ -d "$PROC/$oldpid" ] || rm -f "$old"
    done
    return 0
}

attempt_once() {
    pid=$(pid_main)
    if [ -z "$pid" ]; then
        echo "not-running"
        return 1
    fi
    if view_ok "$pid"; then
        log "pid $pid: view already mounted and in sync"
        echo "already-synced pid=$pid"
        return 0
    fi
    wait_hooks "$pid"
    if attempt_sync "$pid"; then
        echo "mounted pid=$pid"
        return 0
    fi
    echo "sync-failed pid=$pid"
    return 1
}

main_loop() {
    log "art-view-sync service started (daemon, pid $$)"
    while :; do
        sleep 5
        pid=$(pid_main)
        [ -z "$pid" ] && continue
        if view_ok "$pid"; then
            continue
        fi
        if ! attempt_sync "$pid" >/dev/null 2>&1; then
            sleep 10
        fi
    done
}

mkdir -p "$STORE"
case "${1:-}" in
    once)
        prepare || exit 1
        attempt_once
        ;;
    '')
        prepare || exit 1
        main_loop
        ;;
    *)
        echo "usage: service.sh [once]" >&2
        exit 2
        ;;
esac
