/* JNI bridge between Downlink and librtlsdr. The app opens the USB device with Android's
   UsbManager and passes the file descriptor; samples arrive as unsigned 8-bit I/Q pairs. */
#include <jni.h>
#include <pthread.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include "rtl-sdr.h"

#define TAG "DownlinkSDR"
#define LOG(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

extern int rtlsdr_android_fd;

typedef struct {
    rtlsdr_dev_t *dev;
    JavaVM *vm;
    jobject sink;
    jmethodID onSamples;
    jbyteArray buf;
    pthread_t thread;
    volatile int running;
} handle_t;

static void cb(unsigned char *data, uint32_t len, void *ctx) {
    handle_t *h = (handle_t *)ctx;
    if (!h->running) { rtlsdr_cancel_async(h->dev); return; }
    JNIEnv *env;
    if ((*h->vm)->GetEnv(h->vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return;
    if (!h->buf || (*env)->GetArrayLength(env, h->buf) < (jsize)len) {
        if (h->buf) (*env)->DeleteGlobalRef(env, h->buf);
        jbyteArray a = (*env)->NewByteArray(env, len);
        h->buf = (*env)->NewGlobalRef(env, a);
        (*env)->DeleteLocalRef(env, a);
    }
    (*env)->SetByteArrayRegion(env, h->buf, 0, len, (const jbyte *)data);
    (*env)->CallVoidMethod(env, h->sink, h->onSamples, h->buf, (jint)len);
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
}

static void *reader(void *arg) {
    handle_t *h = (handle_t *)arg;
    JNIEnv *env;
    (*h->vm)->AttachCurrentThread(h->vm, &env, NULL);
    rtlsdr_reset_buffer(h->dev);
    rtlsdr_read_async(h->dev, cb, h, 12, 65536);
    h->running = 0;
    if (h->buf) { (*env)->DeleteGlobalRef(env, h->buf); h->buf = NULL; }
    (*h->vm)->DetachCurrentThread(h->vm);
    return NULL;
}

#define FN(name) Java_io_github_professorengineergit_downlink_sdr_RtlNative_##name

JNIEXPORT jlong JNICALL FN(open)(JNIEnv *env, jclass c, jint fd) {
    handle_t *h = calloc(1, sizeof(handle_t));
    rtlsdr_android_fd = fd;
    int r = rtlsdr_open(&h->dev, 0);
    rtlsdr_android_fd = -1;
    if (r < 0 || !h->dev) { LOG("open failed %d", r); free(h); return 0; }
    (*env)->GetJavaVM(env, &h->vm);
    return (jlong)(intptr_t)h;
}

JNIEXPORT void JNICALL FN(close)(JNIEnv *env, jclass c, jlong p) {
    handle_t *h = (handle_t *)(intptr_t)p; if (!h) return;
    if (h->running) { h->running = 0; rtlsdr_cancel_async(h->dev); pthread_join(h->thread, NULL); }
    if (h->sink) (*env)->DeleteGlobalRef(env, h->sink);
    rtlsdr_close(h->dev);
    free(h);
}

JNIEXPORT jint JNICALL FN(tunerType)(JNIEnv *env, jclass c, jlong p) { return rtlsdr_get_tuner_type(((handle_t *)(intptr_t)p)->dev); }
JNIEXPORT jint JNICALL FN(setFrequency)(JNIEnv *env, jclass c, jlong p, jint hz) { return rtlsdr_set_center_freq(((handle_t *)(intptr_t)p)->dev, (uint32_t)hz); }
JNIEXPORT jint JNICALL FN(setSampleRate)(JNIEnv *env, jclass c, jlong p, jint sps) { return rtlsdr_set_sample_rate(((handle_t *)(intptr_t)p)->dev, (uint32_t)sps); }
JNIEXPORT jint JNICALL FN(setPpm)(JNIEnv *env, jclass c, jlong p, jint ppm) {
    int r = rtlsdr_set_freq_correction(((handle_t *)(intptr_t)p)->dev, ppm); return r == -2 ? 0 : r; }
JNIEXPORT jint JNICALL FN(setBiasTee)(JNIEnv *env, jclass c, jlong p, jboolean on) { return rtlsdr_set_bias_tee(((handle_t *)(intptr_t)p)->dev, on ? 1 : 0); }
JNIEXPORT jint JNICALL FN(setGain)(JNIEnv *env, jclass c, jlong p, jint tenthDb) {
    rtlsdr_dev_t *d = ((handle_t *)(intptr_t)p)->dev;
    if (tenthDb < 0) { rtlsdr_set_agc_mode(d, 1); return rtlsdr_set_tuner_gain_mode(d, 0); }
    rtlsdr_set_agc_mode(d, 0);
    rtlsdr_set_tuner_gain_mode(d, 1);
    return rtlsdr_set_tuner_gain(d, tenthDb);
}
JNIEXPORT jintArray JNICALL FN(gains)(JNIEnv *env, jclass c, jlong p) {
    rtlsdr_dev_t *d = ((handle_t *)(intptr_t)p)->dev;
    int n = rtlsdr_get_tuner_gains(d, NULL); if (n < 0) n = 0;
    int *g = calloc(n > 0 ? n : 1, sizeof(int)); if (n > 0) rtlsdr_get_tuner_gains(d, g);
    jintArray a = (*env)->NewIntArray(env, n); (*env)->SetIntArrayRegion(env, a, 0, n, g); free(g);
    return a;
}
JNIEXPORT jstring JNICALL FN(product)(JNIEnv *env, jclass c, jlong p) {
    char m[256] = {0}, pr[256] = {0}, s[256] = {0}, out[800];
    rtlsdr_get_usb_strings(((handle_t *)(intptr_t)p)->dev, m, pr, s);
    snprintf(out, sizeof out, "%s|%s|%s", m, pr, s);
    return (*env)->NewStringUTF(env, out);
}

JNIEXPORT jboolean JNICALL FN(start)(JNIEnv *env, jclass c, jlong p, jobject sink) {
    handle_t *h = (handle_t *)(intptr_t)p; if (!h || h->running) return JNI_FALSE;
    if (h->sink) (*env)->DeleteGlobalRef(env, h->sink);
    h->sink = (*env)->NewGlobalRef(env, sink);
    jclass k = (*env)->GetObjectClass(env, sink);
    h->onSamples = (*env)->GetMethodID(env, k, "onSamples", "([BI)V");
    h->running = 1;
    if (pthread_create(&h->thread, NULL, reader, h) != 0) { h->running = 0; return JNI_FALSE; }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL FN(stop)(JNIEnv *env, jclass c, jlong p) {
    handle_t *h = (handle_t *)(intptr_t)p; if (!h || !h->running) return;
    h->running = 0; rtlsdr_cancel_async(h->dev); pthread_join(h->thread, NULL);
}
