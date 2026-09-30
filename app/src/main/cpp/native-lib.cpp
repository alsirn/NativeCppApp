#include <jni.h>

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_nativecppapp_MainActivity_addNumbers(
        JNIEnv* env,
        jobject /* this */,
        jint a,
        jint b) {

    return a + b;
}