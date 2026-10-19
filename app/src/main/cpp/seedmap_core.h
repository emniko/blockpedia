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
 * (x, z) is the north-west corner in scaled units. blockY picks the height to sample
 * (Overworld only); INT_MIN means the surface. Returns 0 on success. */
int sm_biomes(SeedMap *sm, int scale, int x, int z, int w, int h, int blockY, int *out);

/* Biome id at a block position; blockY as for sm_biomes. */
int sm_biome_at(SeedMap *sm, int blockX, int blockZ, int blockY);

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

/* ---- Extra layers (Java Edition, 1.18+) -------------------------------- */

#define SM_NONE_Y 32767

enum {
    SMP_GEODE = 1,      /* y = geode Y, info = size | 0x100 if cracked */
    SMP_DESERT_WELL,
    SMP_END_GATEWAY,
    SMP_COPPER_VEIN,    /* info = 1 for a raw copper block */
    SMP_IRON_VEIN,
    SMP_DIAMOND,        /* every diamond ore block (meant for one chunk at a time) */
    SMP_DEBRIS,         /* every ancient debris block (Nether) */
};

/* Point features in [x0, x1) x [z0, z1). Writes (x, y, z, info) groups; y is INT_MIN when
 * unknown. Returns the count, or -1 when the layer isn't available here. */
int sm_points(SeedMap *sm, int layer, int x0, int z0, int x1, int z1, int *out, int maxItems);

#define SMV_ZOMBIE_VILLAGE 1
#define SMV_GIANT_PORTAL   2
#define SMV_UNDERGROUND    4
#define SMV_BASEMENT       8
#define SMV_END_SHIP       16

/* Variant flags (SMV_*) for a structure at a position from sm_structures. */
int sm_variant(SeedMap *sm, int stype, int x, int z, int *y);

/* Ravine floor: lowest carved Y per block column for a chunk-aligned area, SM_NONE_Y where no
 * ravine passes. out has chunksW*16 x chunksH*16 entries, row-major. Returns 1 if any. */
int sm_ravines(SeedMap *sm, int chunkX0, int chunkZ0, int chunksW, int chunksH, short *out);

/* Chest loot of a structure (from sm_structures) as text lines "chest\titem\tcount\n", chest
 * numbered from 0. Returns the number of chests; 0 when the structure has no supported loot. */
int sm_loot(SeedMap *sm, int stype, int x, int z, char *out, int outLen);

/* Total count of one item (e.g. "enchanted_golden_apple") across the structure's chests. */
int sm_loot_count(SeedMap *sm, int stype, int x, int z, const char *item);

/* Approximate surface height on a grid of scale-sized cells. Returns 0 on success. */
int sm_heights(SeedMap *sm, int scale, int x, int z, int w, int h, int *out);

#endif
