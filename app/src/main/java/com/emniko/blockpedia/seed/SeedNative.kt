package com.emniko.blockpedia.seed

/** JNI bridge to the native seed engine (cubiomes + Bedrock placement), see src/main/cpp. */
object SeedNative {
    val loadError: Throwable? = runCatching { System.loadLibrary("seedmap") }.exceptionOrNull()

    external fun nativeCreate(seed: Long, mc: Int, edition: Int, dim: Int): Long
    external fun nativeFree(handle: Long)
    external fun nativeBiomes(handle: Long, scale: Int, x: Int, z: Int, w: Int, h: Int, y: Int, out: IntArray): Boolean
    external fun nativeBiomeAt(handle: Long, x: Int, z: Int, y: Int): Int
    external fun nativePoints(handle: Long, layer: Int, x0: Int, z0: Int, x1: Int, z1: Int, out: IntArray): Int
    external fun nativeVariant(handle: Long, type: Int, x: Int, z: Int, yOut: IntArray): Int
    external fun nativeRavines(handle: Long, cx0: Int, cz0: Int, cw: Int, ch: Int, out: ShortArray): Int
    external fun nativeHeights(handle: Long, scale: Int, x: Int, z: Int, w: Int, h: Int, out: IntArray): Boolean
    external fun nativeLoot(handle: Long, type: Int, x: Int, z: Int): String?
    external fun nativeLootCount(handle: Long, type: Int, x: Int, z: Int, item: String): Int
    external fun nativeStructures(handle: Long, type: Int, x0: Int, z0: Int, x1: Int, z1: Int, out: IntArray): Int
    external fun nativeSpawn(handle: Long, out: IntArray): Boolean
    external fun nativeStrongholds(handle: Long, out: IntArray): Int
    external fun nativeVersionId(name: String): Int
    external fun nativeBiomeName(mc: Int, id: Int): String?
    external fun nativeBiomeColors(out: IntArray)
}
