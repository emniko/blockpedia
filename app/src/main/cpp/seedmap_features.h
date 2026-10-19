#ifndef SEEDMAP_FEATURES_H
#define SEEDMAP_FEATURES_H

#include "seedmap_internal.h"
#include "cubiomes/finders.h"

/* Dungeons in chunks [cx0, cx1] x [cz0, cz1]: x, y, z, mob (0 skeleton, 1 zombie, 2 spider).
 * Returns the count, or -1 if not supported for this world. */
int sm_dungeons(SeedMap *sm, int cx0, int cz0, int cx1, int cz1, int *out, int maxItems);

/* Lava lakes: x, y, z, kind (0 underground, 1 surface). */
int sm_lava_lakes(SeedMap *sm, int cx0, int cz0, int cx1, int cz1, int *out, int maxItems);

/* Fossils: x, y, z, info = (0 upper/coal, 1 lower/diamond) << 8 | shape (0-3 spine, 4-7 skull). */
int sm_fossils(SeedMap *sm, int cx0, int cz0, int cx1, int cz1, int *out, int maxItems);

/* Cheese caves whose deepest point lies in [x0, x1) x [z0, z1): 10 ints each — deepest point x, y, z;
 * min x, y, z; max x, y, z (on an 8-block grid); info = size in grid cells | biome << 16. */
int sm_caves(SeedMap *sm, int x0, int z0, int x1, int z1, int *out, int maxItems);

/* Copper (iron = 0) or iron ore veins, same layout as sm_caves; info = cells | ore cells << 16. */
int sm_veins(SeedMap *sm, OreVeinParameters *ov, int iron, int x0, int z0, int x1, int z1, int *out, int maxItems);

#endif
