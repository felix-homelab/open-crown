#include <android/binder_ibinder.h>
#include <android/binder_ibinder_jni.h>
#include <android/binder_parcel.h>
#include <android/log.h>
#include <jni.h>

#define TAG "BinderProbeNative"

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
