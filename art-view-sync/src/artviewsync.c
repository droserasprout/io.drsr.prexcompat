/* Build and verify an ART file copy using the target process's executable mappings.
 * C handles addresses that the device shell would truncate to 32 bits.
 *
 * copy PID PRISTINE OUT: copy the file, replace executable ranges, and verify.
 * diff PID FILE: compare executable ranges against live memory.
 * PROC and LIB override the proc root and library path for host dry-runs.
 * Exit codes: 0 success/equal, 1 error, 2 usage, 3 difference (diff).
 */
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#define LIB_DEFAULT "/apex/com.android.art/lib64/libart.so"
#define PROC_DEFAULT "/proc"
#define CHUNK (1u << 20)
#define PATHBUF 512

static const char *g_lib;
static const char *g_proc;

typedef struct {
    uint64_t start;
    uint64_t end;
    uint64_t off;
} Range;

static void die(const char *what)
{
    fprintf(stderr, "artviewsync: %s: %s\n", what, strerror(errno));
    exit(1);
}

/* Executable file mappings of g_lib, adjacent mappings merged. */
static Range *parse_maps(const char *pid, size_t *count)
{
    char path[PATHBUF];
    Range *r = NULL;
    size_t n = 0, cap = 0;
    FILE *f;
    char line[8192];

    snprintf(path, sizeof path, "%s/%s/maps", g_proc, pid);
    f = fopen(path, "r");
    if (!f)
        die("open maps");
    while (fgets(line, sizeof line, f)) {
        unsigned long long s = 0, e = 0, o = 0, inode = 0;
        char perms[8] = {0}, dev[64] = {0}, fpath[4096] = {0};
        int m = sscanf(line, "%llx-%llx %7s %llx %63s %llu %4095s",
                       &s, &e, perms, &o, dev, &inode, fpath);
        if (m != 7)
            continue;
        if (strcmp(fpath, g_lib) != 0)
            continue;
        if (!strchr(perms, 'x'))
            continue;
        if (n && r[n - 1].end == s &&
            r[n - 1].off + (r[n - 1].end - r[n - 1].start) == o) {
            r[n - 1].end = e; /* contiguous in both memory and file */
        } else {
            if (n == cap) {
                Range *tmp;
                cap = cap ? cap * 2 : 8;
                tmp = realloc(r, cap * sizeof *tmp);
                if (!tmp) {
                    free(r);
                    fclose(f);
                    die("realloc");
                }
                r = tmp;
            }
            r[n].start = s;
            r[n].end = e;
            r[n].off = o;
            n++;
        }
    }
    fclose(f);
    *count = n;
    return r;
}

static int pread_all(int fd, uint64_t pos, void *buf, size_t len)
{
    size_t done = 0;
    while (done < len) {
        ssize_t r = pread(fd, (char *)buf + done, len - done,
                          (off_t)(pos + done));
        if (r < 0)
            return -1;
        if (r == 0) {
            errno = EIO; /* short file: EOF inside a mapped range */
            return -1;
        }
        done += (size_t)r;
    }
    return 0;
}

static int pwrite_all(int fd, uint64_t pos, const void *buf, size_t len)
{
    size_t done = 0;
    while (done < len) {
        ssize_t w = pwrite(fd, (const char *)buf + done, len - done,
                           (off_t)(pos + done));
        if (w <= 0)
            return -1;
        done += (size_t)w;
    }
    return 0;
}

static int open_mem(const char *pid)
{
    char path[PATHBUF];
    int fd;
    snprintf(path, sizeof path, "%s/%s/mem", g_proc, pid);
    fd = open(path, O_RDONLY);
    if (fd < 0)
        die("open mem");
    return fd;
}

/* Count differing bytes between live memory and FILE over every range. */
static int compare_all(int memfd, size_t n, const Range *r, const char *file,
                       uint64_t *diff_bytes)
{
    int fd = open(file, O_RDONLY);
    char *live, *disk;
    uint64_t diffs = 0;
    size_t i;

    if (fd < 0)
        die("open compare file");
    live = malloc(CHUNK);
    disk = malloc(CHUNK);
    if (!live || !disk)
        die("malloc");
    for (i = 0; i < n; i++) {
        uint64_t len = r[i].end - r[i].start;
        uint64_t p;
        for (p = 0; p < len; p += CHUNK) {
            size_t w = len - p > CHUNK ? CHUNK : (size_t)(len - p);
            if (pread_all(memfd, r[i].start + p, live, w))
                die("pread live");
            if (pread_all(fd, r[i].off + p, disk, w))
                die("pread file");
            {
                size_t k;
                for (k = 0; k < w; k++)
                    if (live[k] != disk[k])
                        diffs++;
            }
        }
    }
    free(live);
    free(disk);
    close(fd);
    *diff_bytes = diffs;
    return 0;
}

static int do_copy(const char *pid, const char *pristine, const char *out)
{
    size_t n = 0;
    Range *r = parse_maps(pid, &n);
    uint64_t copied = 0, diffs = 0;
    struct stat st;
    char *buf;
    int memfd, in, of;
    uint64_t total, done;
    size_t i;

    if (!n) {
        fprintf(stderr, "artviewsync: no executable mappings of %s\n", g_lib);
        return 1;
    }
    memfd = open_mem(pid);
    in = open(pristine, O_RDONLY);
    if (in < 0)
        die("open pristine");
    if (fstat(in, &st))
        die("fstat pristine");
    of = open(out, O_RDWR | O_CREAT | O_TRUNC, 0600);
    if (of < 0)
        die("open out");
    if (fchmod(of, st.st_mode & 0777))
        die("fchmod out");

    /* Base copy of the pristine file. */
    buf = malloc(CHUNK);
    if (!buf)
        die("malloc");
    total = (uint64_t)st.st_size;
    done = 0;
    while (done < total) {
        size_t want = total - done > CHUNK ? CHUNK : (size_t)(total - done);
        ssize_t rd = pread(in, buf, want, (off_t)done);
        if (rd <= 0)
            die("read pristine");
        if (pwrite_all(of, done, buf, (size_t)rd))
            die("write base");
        done += (uint64_t)rd;
    }

    /* Overwrite each executable range with the live bytes. */
    for (i = 0; i < n; i++) {
        uint64_t len = r[i].end - r[i].start;
        uint64_t p;
        for (p = 0; p < len; p += CHUNK) {
            size_t w = len - p > CHUNK ? CHUNK : (size_t)(len - p);
            if (pread_all(memfd, r[i].start + p, buf, w))
                die("pread live");
            if (pwrite_all(of, r[i].off + p, buf, w))
                die("pwrite synced");
        }
        copied += len;
    }
    free(buf);

    /* Verify the produced copy against live memory. */
    if (compare_all(memfd, n, r, out, &diffs))
        return 1;
    printf("copy ranges=%zu map_bytes=%llu verify_diff_bytes=%llu\n",
           n, (unsigned long long)copied, (unsigned long long)diffs);
    return diffs == 0 ? 0 : 1;
}

static int do_diff(const char *pid, const char *file)
{
    size_t n = 0;
    Range *r = parse_maps(pid, &n);
    uint64_t diffs = 0;
    int memfd;

    if (!n) {
        fprintf(stderr, "artviewsync: no executable mappings of %s\n", g_lib);
        return 1;
    }
    memfd = open_mem(pid);
    if (compare_all(memfd, n, r, file, &diffs))
        return 1;
    printf("diff ranges=%zu diff_bytes=%llu\n",
           n, (unsigned long long)diffs);
    return diffs == 0 ? 0 : 3;
}

int main(int argc, char **argv)
{
    g_proc = getenv("PROC");
    if (!g_proc || !*g_proc)
        g_proc = PROC_DEFAULT;
    g_lib = getenv("LIB");
    if (!g_lib || !*g_lib)
        g_lib = LIB_DEFAULT;

    if (argc == 5 && strcmp(argv[1], "copy") == 0)
        return do_copy(argv[2], argv[3], argv[4]);
    if (argc == 4 && strcmp(argv[1], "diff") == 0)
        return do_diff(argv[2], argv[3]);
    fprintf(stderr,
            "usage: %s copy PID PRISTINE OUT\n"
            "       %s diff PID FILE\n",
            argv[0], argv[0]);
    return 2;
}
