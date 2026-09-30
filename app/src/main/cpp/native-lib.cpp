#include <jni.h>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_nativecppapp_MainActivity_helloFromCpp(
        JNIEnv* env,
        jobject /* this */) {

    return env->NewStringUTF("Hello from C++!");
}