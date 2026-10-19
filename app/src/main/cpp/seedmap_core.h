#ifndef SEEDMAP_CORE_H
#define SEEDMAP_CORE_H

#include <stdint.h>

/*
 * Seed tools on top of cubiomes: biome maps, structure positions, spawn and
 * strongholds for one world. Java Edition uses cubiomes directly. Bedrock uses
 * the same overworld biomes (identical for the same seed since 1.18) with
 * Bedrock's own structure placement implemented here.
 *
 * A SeedMap is NOT thread-safe: use one per thread.
 */

#define SM_JAVA    0
#define SM_BEDROCK 1

typedef struct SeedMap SeedMap;

/* dim: 0 overworld, -1 nether, +1 end. */
SeedMap *sm_create(uint64_t seed, int mc, int edition, int dim);
void sm_free(SeedMap *sm);

/* Biome ids for a w*h grid at the given scale (4, 16, 64 or 256 blocks per cell).
 * (x, z) is the north-west corner in scaled units. Returns 0 on success. */
int sm_biomes(SeedMap *sm, int scale, int x, int z, int w, int h, int *out);

/* Biome id at a block position (surface). */
int sm_biome_at(SeedMap *sm, int blockX, int blockZ);

/* Structures of a cubiomes StructureType whose position lies in the block
 * rectangle [x0, x1) x [z0, z1). Writes (x, z) block pairs; returns the count,
 * or -1 if the type isn't supported for this edition/version. */
int sm_structures(SeedMap *sm, int stype, int x0, int z0, int x1, int z1, int *out, int maxPairs);

/* World spawn. Java only; returns 0 on success. */
int sm_spawn(SeedMap *sm, int *x, int *z);

/* Stronghold positions in generation order. Java only; returns the count. */
int sm_strongholds(SeedMap *sm, int *out, int maxPairs);

/* Bedrock placement attempt for a region (no biome check), block coordinates
 * of the chunk centre. Returns 1 if the type has Bedrock MT placement. */
int sm_bedrock_attempt(int stype, uint64_t seed, int regX, int regZ, int *x, int *z);

/* First output of MT19937 for a seed (exposed for tests). */
uint32_t sm_mt_first(uint32_t seed);

#endif
