/*
 * hci-echo - the K2a probe.
 *
 * Runs as the first agent, after the payload has produced uid 0. It proves
 * bidirectional control of the controller: bind the HCI user channel, read the
 * local version, reset, and read the command complete. Every stage prints a
 * line so a failure can be attributed to the exploit or to the policy.
 *
 * This is not product code. See docs/06-roadmap.md, Phase 0.
 * Build with tools/hci-echo/build.sh (needs ANDROID_NDK_HOME).
 */

#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#ifndef AF_BLUETOOTH
#define AF_BLUETOOTH 31
#endif

#define BTPROTO_HCI 1
#define HCI_CHANNEL_USER 1

/* Linux bluetooth ioctls, declared here so the probe needs no Linux headers. */
#define HCIDEVUP _IOW('H', 201, int)
#define HCIDEVDOWN _IOW('H', 202, int)

struct sockaddr_hci {
    sa_family_t hci_family;
    unsigned short hci_dev;
    unsigned short hci_channel;
};

#define HCI_COMMAND_PKT 0x01
#define HCI_EVENT_PKT 0x04
#define EVT_CMD_COMPLETE 0x0e

#define OGF_HOST_CTL 0x03
#define OCF_RESET 0x0003
#define OGF_INFO_PARAM 0x04
#define OCF_READ_LOCAL_VERSION 0x0001

static int failures = 0;

static uint16_t make_opcode(uint8_t ogf, uint16_t ocf) {
    return (uint16_t)((ogf << 10) | ocf);
}

static void stage(const char *name, int err) {
    printf("K2A stage=%s status=%s errno=%d (%s)\n", name, err ? "fail" : "ok",
           err, err ? strerror(err) : "ok");
    if (err) {
        failures++;
    }
    fflush(stdout);
}

static void print_text_file(const char *path) {
    FILE *file = fopen(path, "r");
    if (!file) {
        printf("K2A file=%s status=unreadable errno=%d (%s)\n", path, errno,
               strerror(errno));
        return;
    }
    char buf[256] = {0};
    size_t n = fread(buf, 1, sizeof(buf) - 1, file);
    fclose(file);
    while (n > 0 && (buf[n - 1] == '\n' || buf[n - 1] == '\0')) {
        buf[--n] = 0;
    }
    printf("K2A file=%s value=%s\n", path, buf);
}

static void print_context(void) {
    printf("K2A uid=%d euid=%d gid=%d pid=%d\n", getuid(), geteuid(), getgid(),
           getpid());
    print_text_file("/proc/self/attr/current");
    print_text_file("/sys/fs/selinux/enforce");
}

static int open_user_channel(int dev, int skip_down) {
    int fd = socket(AF_BLUETOOTH, SOCK_RAW, BTPROTO_HCI);
    if (fd < 0) {
        stage("socket", errno);
        return -1;
    }
    int fd_flags = fcntl(fd, F_GETFD);
    if (fd_flags >= 0) {
        (void)fcntl(fd, F_SETFD, fd_flags | FD_CLOEXEC);
    }
    stage("socket", 0);

    if (!skip_down) {
        int id = dev;
        if (ioctl(fd, HCIDEVDOWN, &id) < 0) {
            /* ENODEV means the device was already down, which is fine. */
            stage("device_down", errno == ENODEV ? 0 : errno);
        } else {
            stage("device_down", 0);
        }
    }

    struct sockaddr_hci addr;
    memset(&addr, 0, sizeof(addr));
    addr.hci_family = AF_BLUETOOTH;
    addr.hci_dev = (unsigned short)dev;
    addr.hci_channel = HCI_CHANNEL_USER;

    if (bind(fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        stage("bind_user_channel", errno);
        close(fd);
        return -1;
    }
    stage("bind_user_channel", 0);
    return fd;
}

static int send_command(int fd, uint16_t op, const uint8_t *params, size_t len) {
    uint8_t packet[260];
    if (len > 255) {
        return -1;
    }
    packet[0] = HCI_COMMAND_PKT;
    packet[1] = (uint8_t)(op & 0xff);
    packet[2] = (uint8_t)(op >> 8);
    packet[3] = (uint8_t)len;
    if (len) {
        memcpy(packet + 4, params, len);
    }
    ssize_t written = write(fd, packet, 4 + len);
    return written == (ssize_t)(4 + len) ? 0 : -1;
}

/* Waits for a Command Complete for the expected opcode. Returns the status. */
static int wait_complete(int fd, uint16_t expected, int timeout_ms) {
    struct pollfd pfd;
    pfd.fd = fd;
    pfd.events = POLLIN;

    int ready = poll(&pfd, 1, timeout_ms);
    if (ready <= 0) {
        return -1;
    }

    uint8_t buf[512];
    ssize_t n = read(fd, buf, sizeof(buf));
    if (n < 7) {
        return -1;
    }

    printf("K2A event=");
    for (ssize_t i = 0; i < n; i++) {
        printf("%02x", buf[i]);
    }
    printf("\n");

    if (buf[0] != HCI_EVENT_PKT || buf[1] != EVT_CMD_COMPLETE) {
        return -1;
    }
    uint16_t got = (uint16_t)(buf[4] | (buf[5] << 8));
    if (got != expected) {
        return -1;
    }
    return buf[6];
}

/* Reads recent kernel log lines that mention avc, so the evidence file can
 * name the denial without adb root. */
static void dump_avc(int max_lines) {
    int fd = open("/dev/kmsg", O_RDONLY | O_NONBLOCK);
    if (fd < 0) {
        printf("K2A file=/dev/kmsg status=unreadable errno=%d (%s)\n", errno,
               strerror(errno));
        return;
    }
    char buf[1024];
    int shown = 0;
    for (int i = 0; i < 500 && shown < max_lines; i++) {
        ssize_t n = read(fd, buf, sizeof(buf) - 1);
        if (n <= 0) {
            break;
        }
        buf[n] = 0;
        char *avc = strstr(buf, "avc:");
        if (avc) {
            printf("K2A avc=%s\n", avc);
            shown++;
        }
    }
    close(fd);
    printf("K2A avc_lines=%d\n", shown);
}

static void run_command(int fd, const char *name, uint16_t op, int timeout_ms) {
    if (send_command(fd, op, NULL, 0) < 0) {
        stage(name, errno);
        return;
    }
    int status = wait_complete(fd, op, timeout_ms);
    if (status == 0) {
        stage(name, 0);
    } else {
        stage(name, status < 0 ? ETIMEDOUT : status);
    }
}

int main(int argc, char **argv) {
    int dev = 0;
    int skip_down = 0;
    int timeout_ms = 2000;

    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--dev") == 0 && i + 1 < argc) {
            dev = atoi(argv[++i]);
        } else if (strcmp(argv[i], "--skip-down") == 0) {
            skip_down = 1;
        } else if (strcmp(argv[i], "--timeout") == 0 && i + 1 < argc) {
            timeout_ms = atoi(argv[++i]);
        } else if (strcmp(argv[i], "--help") == 0) {
            printf("usage: %s [--dev N] [--skip-down] [--timeout MS]\n", argv[0]);
            return 0;
        } else {
            fprintf(stderr, "unknown argument: %s\n", argv[i]);
            return 2;
        }
    }

    printf("K2A probe start dev=%d skip_down=%d timeout_ms=%d\n", dev, skip_down,
           timeout_ms);
    print_context();

    int fd = open_user_channel(dev, skip_down);
    if (fd < 0) {
        dump_avc(200);
        printf("K2A_RESULT status=fail stage=bind failures=%d\n", failures);
        return 2;
    }

    run_command(fd, "read_local_version",
                make_opcode(OGF_INFO_PARAM, OCF_READ_LOCAL_VERSION), timeout_ms);
    run_command(fd, "reset", make_opcode(OGF_HOST_CTL, OCF_RESET), timeout_ms);

    close(fd);
    dump_avc(200);

    if (failures == 0) {
        printf("K2A_RESULT status=pass\n");
        return 0;
    }
    printf("K2A_RESULT status=fail failures=%d\n", failures);
    return 2;
}
