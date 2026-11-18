// XP seed search for the enchanting table predictor.
// Same check as EnchantmentCracker's seed cracker (MIT, Earthcomputer): replay the three
// cost rolls of java.util.Random for every candidate seed and keep the ones that match.
#include <jni.h>
#include <stdint.h>

#define MULT 0x5DEECE66DULL
#define MASK ((1ULL << 48) - 1)

static inline int next31(uint64_t *s) {
    *s = (*s * MULT + 0xBULL) & MASK;
    return (int) (*s >> 17);
}

static inline int next_int(uint64_t *s, int bound) {
    int r = next31(s);
    int m = bound - 1;
    if ((bound & m) == 0) return (int) (((int64_t) bound * r) >> 31);
    for (int u = r; u - (r = u % bound) + m < 0; u = next31(s)) {}
    return r;
}

static inline int fits(int32_t seed, const jint *rd) {
    int shelves = rd[0];
    uint64_t s = ((uint64_t) (int64_t) seed ^ MULT) & MASK;
    int half = (shelves >> 1) + 1, n = shelves + 1;

    int a = next_int(&s, 8) + half + next_int(&s, n);
    int top = a / 3;
    if (top < 1) top = 1;
    if (top != rd[1]) return 0;

    int b = next_int(&s, 8) + half + next_int(&s, n);
    int middle = b * 2 / 3 + 1;
    if (middle < 2) middle = 0;
    if (middle != rd[2]) return 0;

    int c = next_int(&s, 8) + half + next_int(&s, n);
    int bottom = c > shelves * 2 ? c : shelves * 2;
    if (bottom < 3) bottom = 0;
    return bottom == rd[3];
}

// readings: [shelves, top, middle, bottom] * k. Returns matches written to out, or -1 if out filled up.
JNIEXPORT jint JNICALL
Java_com_emniko_blockpedia_enchant_EnchantNative_nativeCrack(JNIEnv *env, jobject thiz, jintArray readings,
                                                            jlong start, jlong count, jintArray out) {
    jsize len = (*env)->GetArrayLength(env, readings);
    jsize cap = (*env)->GetArrayLength(env, out);
    jint rd[64 * 4];
    int k = len / 4;
    if (k > 64) k = 64;
    (*env)->GetIntArrayRegion(env, readings, 0, k * 4, rd);

    jint *dst = (*env)->GetIntArrayElements(env, out, NULL);
    jint found = 0;
    for (jlong i = 0; i < count; i++) {
        int32_t seed = (int32_t) (start + i);
        int ok = 1;
        for (int j = 0; j < k && ok; j++) ok = fits(seed, rd + j * 4);
        if (!ok) continue;
        if (found == cap) {
            found = -1;
            break;
        }
        dst[found++] = seed;
    }
    (*env)->ReleaseIntArrayElements(env, out, dst, 0);
    return found;
}
