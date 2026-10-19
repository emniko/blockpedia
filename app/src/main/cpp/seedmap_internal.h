#ifndef SEEDMAP_INTERNAL_H
#define SEEDMAP_INTERNAL_H

#include "seedmap_core.h"

#include "cubiomes/finders.h"
#include "cubiomes/generator.h"
#include "cubiomes/terrainnoise.h"
#include "cubiomes/util.h"

struct SeedMap {
    Generator g;
    SurfaceNoise endSurface; /* Java End: end city terrain check */
    uint64_t seed;
    int mc;
    int edition;
    int dim;
    int *cache;
    size_t cacheLen;
    /* Created on first use by the extra layers. */
    OreVeinParameters *oreVeins;
    SurfaceNoise *surface;
    TerrainNoise *terrain;
};

void sm_free_layers(SeedMap *sm);

#endif
