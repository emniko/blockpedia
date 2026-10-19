#include <jni.h>
#include <stdlib.h>

#include "seedmap_core.h"
#include "cubiomes/util.h"

#define FN(name) Java_com_emniko_blockpedia_seed_SeedNative_##name

static SeedMap *H(jlong h) { return (SeedMap *) (intptr_t) h; }

JNIEXPORT jlong JNICALL FN(nativeCreate)(JNIEnv *env, jobject thiz, jlong seed, jint mc, jint edition, jint dim)
{
    (void) env; (void) thiz;
    return (jlong) (intptr_t) sm_create((uint64_t) seed, mc, edition, dim);
}

JNIEXPORT void JNICALL FN(nativeFree)(JNIEnv *env, jobject thiz, jlong h)
{
    (void) env; (void) thiz;
    sm_free(H(h));
}

JNIEXPORT jboolean JNICALL FN(nativeBiomes)(JNIEnv *env, jobject thiz, jlong h, jint scale,
                                            jint x, jint z, jint w, jint hgt, jint y, jintArray out)
{
    (void) thiz;
    if ((*env)->GetArrayLength(env, out) < w * hgt) return JNI_FALSE;
    jint *buf = (*env)->GetIntArrayElements(env, out, NULL);
    if (!buf) return JNI_FALSE;
    int err = sm_biomes(H(h), scale, x, z, w, hgt, y, (int *) buf);
    (*env)->ReleaseIntArrayElements(env, out, buf, 0);
    return err == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL FN(nativeBiomeAt)(JNIEnv *env, jobject thiz, jlong h, jint x, jint z, jint y)
{
    (void) env; (void) thiz;
    return sm_biome_at(H(h), x, z, y);
}

JNIEXPORT jint JNICALL FN(nativePoints)(JNIEnv *env, jobject thiz, jlong h, jint layer,
                                        jint x0, jint z0, jint x1, jint z1, jintArray out)
{
    (void) thiz;
    jsize len = (*env)->GetArrayLength(env, out);
    jint *buf = (*env)->GetIntArrayElements(env, out, NULL);
    if (!buf) return -1;
    int n = sm_points(H(h), layer, x0, z0, x1, z1, (int *) buf, len / 4);
    (*env)->ReleaseIntArrayElements(env, out, buf, 0);
    return n;
}

JNIEXPORT jint JNICALL FN(nativeClusters)(JNIEnv *env, jobject thiz, jlong h, jint layer,
                                          jint x0, jint z0, jint x1, jint z1, jintArray out)
{
    (void) thiz;
    jsize len = (*env)->GetArrayLength(env, out);
    jint *buf = (*env)->GetIntArrayElements(env, out, NULL);
    if (!buf) return -1;
    int n = sm_clusters(H(h), layer, x0, z0, x1, z1, (int *) buf, len / 10);
    (*env)->ReleaseIntArrayElements(env, out, buf, 0);
    return n;
}

JNIEXPORT jint JNICALL FN(nativeVariant)(JNIEnv *env, jobject thiz, jlong h, jint type, jint x, jint z, jintArray yOut)
{
    (void) thiz;
    int y;
    int flags = sm_variant(H(h), type, x, z, &y);
    jint yv = y;
    (*env)->SetIntArrayRegion(env, yOut, 0, 1, &yv);
    return flags;
}

JNIEXPORT jint JNICALL FN(nativeRavines)(JNIEnv *env, jobject thiz, jlong h, jint cx0, jint cz0,
                                         jint cw, jint ch, jshortArray out)
{
    (void) thiz;
    if ((*env)->GetArrayLength(env, out) < cw * 16 * ch * 16) return -1;
    jshort *buf = (*env)->GetShortArrayElements(env, out, NULL);
    if (!buf) return -1;
    int r = sm_ravines(H(h), cx0, cz0, cw, ch, (short *) buf);
    (*env)->ReleaseShortArrayElements(env, out, buf, 0);
    return r;
}

JNIEXPORT jboolean JNICALL FN(nativeHeights)(JNIEnv *env, jobject thiz, jlong h, jint scale,
                                             jint x, jint z, jint w, jint hgt, jintArray out)
{
    (void) thiz;
    if ((*env)->GetArrayLength(env, out) < w * hgt) return JNI_FALSE;
    jint *buf = (*env)->GetIntArrayElements(env, out, NULL);
    if (!buf) return JNI_FALSE;
    int err = sm_heights(H(h), scale, x, z, w, hgt, (int *) buf);
    (*env)->ReleaseIntArrayElements(env, out, buf, 0);
    return err == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL FN(nativeLoot)(JNIEnv *env, jobject thiz, jlong h, jint type, jint x, jint z)
{
    (void) thiz;
    char *buf = (char *) malloc(32768);
    if (!buf) return NULL;
    int chests = sm_loot(H(h), type, x, z, buf, 32768);
    jstring s = chests > 0 ? (*env)->NewStringUTF(env, buf) : NULL;
    free(buf);
    return s;
}

JNIEXPORT jint JNICALL FN(nativeLootCount)(JNIEnv *env, jobject thiz, jlong h, jint type, jint x, jint z, jstring item)
{
    (void) thiz;
    const char *s = (*env)->GetStringUTFChars(env, item, NULL);
    if (!s) return 0;
    int n = sm_loot_count(H(h), type, x, z, s);
    (*env)->ReleaseStringUTFChars(env, item, s);
    return n;
}

JNIEXPORT jint JNICALL FN(nativeStructures)(JNIEnv *env, jobject thiz, jlong h, jint type,
                                            jint x0, jint z0, jint x1, jint z1, jintArray out)
{
    (void) thiz;
    jsize len = (*env)->GetArrayLength(env, out);
    jint *buf = (*env)->GetIntArrayElements(env, out, NULL);
    if (!buf) return -1;
    int n = sm_structures(H(h), type, x0, z0, x1, z1, (int *) buf, len / 2);
    (*env)->ReleaseIntArrayElements(env, out, buf, 0);
    return n;
}

JNIEXPORT jboolean JNICALL FN(nativeSpawn)(JNIEnv *env, jobject thiz, jlong h, jintArray out)
{
    (void) thiz;
    int x, z;
    if (sm_spawn(H(h), &x, &z) != 0) return JNI_FALSE;
    jint xz[2] = { x, z };
    (*env)->SetIntArrayRegion(env, out, 0, 2, xz);
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL FN(nativeStrongholds)(JNIEnv *env, jobject thiz, jlong h, jintArray out)
{
    (void) thiz;
    jsize len = (*env)->GetArrayLength(env, out);
    jint *buf = (*env)->GetIntArrayElements(env, out, NULL);
    if (!buf) return 0;
    int n = sm_strongholds(H(h), (int *) buf, len / 2);
    (*env)->ReleaseIntArrayElements(env, out, buf, 0);
    return n;
}

JNIEXPORT jint JNICALL FN(nativeVersionId)(JNIEnv *env, jobject thiz, jstring name)
{
    (void) thiz;
    const char *s = (*env)->GetStringUTFChars(env, name, NULL);
    if (!s) return 0;
    int mc = str2mc(s);
    (*env)->ReleaseStringUTFChars(env, name, s);
    return mc;
}

JNIEXPORT jstring JNICALL FN(nativeBiomeName)(JNIEnv *env, jobject thiz, jint mc, jint id)
{
    (void) thiz;
    const char *s = biome2str(mc, id);
    return s ? (*env)->NewStringUTF(env, s) : NULL;
}

JNIEXPORT void JNICALL FN(nativeBiomeColors)(JNIEnv *env, jobject thiz, jintArray out)
{
    (void) thiz;
    unsigned char colors[256][3];
    initBiomeColors(colors);
    jint argb[256];
    for (int i = 0; i < 256; i++)
        argb[i] = (jint) (0xFF000000u | ((uint32_t) colors[i][0] << 16) | ((uint32_t) colors[i][1] << 8) | colors[i][2]);
    (*env)->SetIntArrayRegion(env, out, 0, 256, argb);
}
