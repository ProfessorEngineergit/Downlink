// JNI bridge for the LRPT decoder: decodes a recorded .s8 baseband file into channel images.
#include <jni.h>
#include <cstdio>
#include <vector>
#include "lrpt/lrpt.h"

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_professorengineergit_downlink_sdr_MeteorDecoder_decodeFile(JNIEnv* env, jclass, jstring jpath, jdouble rate, jobject progress) {
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    FILE* f = std::fopen(path, "rb");
    env->ReleaseStringUTFChars(jpath, path);
    jclass chCls = env->FindClass("io/github/professorengineergit/downlink/sdr/LrptChannel");
    if (!f) return env->NewObjectArray(0, chCls, nullptr);
    std::fseek(f, 0, SEEK_END); long size = std::ftell(f); std::fseek(f, 0, SEEK_SET);
    jmethodID onProgress = nullptr;
    if (progress) {
        onProgress = env->GetMethodID(env->GetObjectClass(progress), "onProgress", "(FJJF)V");
        if (env->ExceptionCheck()) { env->ExceptionClear(); onProgress = nullptr; }
    }
    lrpt::Decoder dec(rate);
    std::vector<int8_t> buf(1 << 18);
    size_t n; long done = 0; int tick = 0;
    while ((n = std::fread(buf.data(), 1, buf.size(), f)) > 0) {
        dec.feed(buf.data(), n / 2);
        done += (long)n;
        if (onProgress && (++tick % 4 == 0)) {
            auto s = dec.stats();
            env->CallVoidMethod(progress, onProgress, (jfloat)(done / (double)size), (jlong)s.frames, (jlong)s.packets, (jfloat)s.snr);
            if (env->ExceptionCheck()) { env->ExceptionClear(); break; }
        }
    }
    std::fclose(f);
    auto& chs = dec.channels();
    jmethodID ctor = env->GetMethodID(chCls, "<init>", "(III[B)V");
    if (env->ExceptionCheck() || !ctor) { env->ExceptionClear(); return nullptr; }
    jobjectArray out = env->NewObjectArray((jsize)chs.size(), chCls, nullptr);
    int i = 0;
    for (auto& [apid, c] : chs) {
        jbyteArray px = env->NewByteArray((jsize)c.pixels.size());
        env->SetByteArrayRegion(px, 0, (jsize)c.pixels.size(), (const jbyte*)c.pixels.data());
        jobject o = env->NewObject(chCls, ctor, (jint)apid, (jint)c.width, (jint)c.rows, px);
        env->SetObjectArrayElement(out, i++, o);
        env->DeleteLocalRef(px); env->DeleteLocalRef(o);
    }
    return out;
}
