#ifndef SEEDMAP_WORLD_H
#define SEEDMAP_WORLD_H

/*
 * Approximate block-level view of the 1.18+ Overworld, built from cubiomes' terrain noise (with
 * noise caves), carvers and aquifers. Surface rules and earlier features are not modelled, so
 * features that depend on the terrain (dungeons, lava pools, fossils) are approximate.
 */
#include "seedmap_internal.h"

typedef struct World World;

World *world_new(SeedMap *sm);
void world_free(World *w);

enum { WB_SOLID, WB_AIR, WB_WATER, WB_LAVA };

/* WB_SOLID, WB_AIR, WB_WATER or WB_LAVA. */
int world_block(World *w, int x, int y, int z);

/* 1 if the block is solid (stone-like). */
int world_solid(World *w, int x, int y, int z);

/* 1 if the block is air (not solid and not a fluid). */
int world_air(World *w, int x, int y, int z);

/* Height map at a column: one above the highest solid block (OCEAN_FLOOR_WG), or above the highest
 * non-air block when fluidsCount is set (WORLD_SURFACE_WG). */
int world_height(World *w, int x, int z, int fluidsCount);

/* Cheese cave noise (cave layer + cave cheese, < 0 is open), cached on an 8-block grid. */
float world_cheese(World *w, int x, int y, int z);

/* Frees the caches when they have grown large. */
void world_trim(World *w);

/* Remember a placed dungeon room so later checks in the same chunk see it. */
void world_add_room(World *w, int ox, int oy, int oz, int xr, int zr, int nchests, const int *chests);
void world_clear_rooms(World *w);

/* Interpolated terrain density (> 0 is solid), ignoring carvers. */
double world_density(World *w, int x, int y, int z);

/* 1 if a carver removed this block. */
int world_carved(World *w, int x, int y, int z);

#endif
