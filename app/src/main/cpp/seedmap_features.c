/*
 * Terrain-dependent decoration features for the 1.18+ Java Overworld: dungeons (monster rooms),
 * lava lakes and fossils.
 * Positions come from the game's decoration RNG; whether a room fits is checked against the
 * approximate terrain in seedmap_world.c.
 */
#include "seedmap_features.h"
#include "seedmap_world.h"

#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "cubiomes/features/ore.h"
#include "cubiomes/rng.h"

/* The block model is kept per seed so neighbouring map tiles share its caches. */
static World *world_for(SeedMap *sm)
{
    if (!sm->world) sm->world = world_new(sm);
    else world_trim(sm->world);
    return sm->world;
}

/* Feature salts: step * 10000 + index of the feature in that decoration step. */
typedef struct { int upper, deep; } DungeonSalts;

static int dungeon_salts(int mc, DungeonSalts *s)
{
    if (mc < MC_1_18) return 0;
    s->upper = 30002;
    s->deep = 30003;
    return 1;
}

/* Mirrors MonsterRoomFeature.place. Returns the spawner mob (0 skeleton, 1 zombie, 2 spider) or
 * -1 when the room doesn't fit; consumes the RNG the way the game does. */
static int monster_room(World *w, Xoroshiro *xr, int ox, int oy, int oz)
{
    static const int mobs[4] = { 0, 1, 1, 2 };
    int xrad = xNextIntJ(xr, 2) + 2;
    int zrad = xNextIntJ(xr, 2) + 2;
    int minX = -xrad - 1, maxX = xrad + 1, minZ = -zrad - 1, maxZ = zrad + 1;
    /* Same conditions as the game, checked cheapest-first: a few floor and ceiling blocks, then the
     * openings in the walls (most candidates sit in solid rock and have none), then the rest. */
    static const int probe[5][2] = { {0, 0}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1} };
    for (int i = 0; i < 5; i++) {
        int x = ox + (probe[i][0] < 0 ? minX : probe[i][0] > 0 ? maxX : 0);
        int z = oz + (probe[i][1] < 0 ? minZ : probe[i][1] > 0 ? maxZ : 0);
        if (!world_solid(w, x, oy - 1, z) || !world_solid(w, x, oy + 4, z)) return -1;
    }
    int holes = 0;
    for (int dx = minX; dx <= maxX; dx++) {
        for (int dz = minZ; dz <= maxZ; dz++) {
            if (dx != minX && dx != maxX && dz != minZ && dz != maxZ) continue;
            if (world_air(w, ox + dx, oy, oz + dz) && world_air(w, ox + dx, oy + 1, oz + dz) && ++holes > 5) return -1;
        }
    }
    if (holes < 1) return -1;
    for (int dx = minX; dx <= maxX; dx++)
        for (int dz = minZ; dz <= maxZ; dz++)
            if (!world_solid(w, ox + dx, oy - 1, oz + dz) || !world_solid(w, ox + dx, oy + 4, oz + dz)) return -1;

    /* Floor: mossy or plain cobblestone, decided per block that has solid ground below. */
    for (int dx = minX; dx <= maxX; dx++)
        for (int dz = minZ; dz <= maxZ; dz++)
            if (oy - 1 <= -64 || world_solid(w, ox + dx, oy - 2, oz + dz)) xNextIntJ(xr, 4);

    /* Up to two chests, each against exactly one solid wall. */
    int chestX = 0, chestZ = 0, haveChest = 0, nchests = 0, chests[4];
    for (int i = 0; i < 2; i++) {
        for (int j = 0; j < 3; j++) {
            int cx = ox + xNextIntJ(xr, xrad * 2 + 1) - xrad;
            int cz = oz + xNextIntJ(xr, zrad * 2 + 1) - zrad;
            if (haveChest && cx == chestX && cz == chestZ) continue;
            static const int dirs[4][2] = { {0, -1}, {1, 0}, {0, 1}, {-1, 0} };
            int solid = 0;
            for (int d = 0; d < 4; d++) {
                int nx = cx + dirs[d][0], nz = cz + dirs[d][1];
                int rx = nx - ox, rz = nz - oz;
                int wall = rx == minX || rx == maxX || rz == minZ || rz == maxZ;
                if (haveChest && nx == chestX && nz == chestZ) solid++;
                else if (wall && world_solid(w, nx, oy, nz)) solid++;
            }
            if (solid == 1) {
                xNextLongJ(xr); /* loot table seed */
                chests[2 * nchests] = cx; chests[2 * nchests + 1] = cz; nchests++;
                chestX = cx; chestZ = cz; haveChest = 1;
                break;
            }
        }
    }
    world_add_room(w, ox, oy, oz, xrad, zrad, nchests, chests);
    return mobs[xNextIntJ(xr, 4)];
}

static int dungeons_in_chunk(SeedMap *sm, World *w, const DungeonSalts *salts, int cx, int cz, int *out, int max)
{
    uint64_t pop = getPopulationSeed(sm->mc, sm->seed, cx * 16, cz * 16);
    int n = 0;
    world_clear_rooms(w);
    for (int pass = 0; pass < 2; pass++) {
        Xoroshiro xr;
        xSetSeed(&xr, pop + (pass == 0 ? salts->upper : salts->deep));
        int count = pass == 0 ? 10 : 4;
        for (int i = 0; i < count; i++) {
            int x = cx * 16 + xNextIntJ(&xr, 16);
            int z = cz * 16 + xNextIntJ(&xr, 16);
            int y = pass == 0 ? xNextIntJ(&xr, 320) : xNextIntJ(&xr, 58) - 58;
            int mob = monster_room(w, &xr, x, y, z);
            if (mob < 0 || n >= max) continue;
            out[4 * n] = x; out[4 * n + 1] = y; out[4 * n + 2] = z; out[4 * n + 3] = mob;
            n++;
        }
    }
    return n;
}

int sm_dungeons(SeedMap *sm, int cx0, int cz0, int cx1, int cz1, int *out, int maxItems)
{
    DungeonSalts salts;
    if (sm->edition != SM_JAVA || sm->dim != DIM_OVERWORLD || !dungeon_salts(sm->mc, &salts)) return -1;
    World *w = world_for(sm);
    if (!w) return -1;
    int n = 0;
    for (int cz = cz0; cz <= cz1 && n < maxItems; cz++)
        for (int cx = cx0; cx <= cx1 && n < maxItems; cx++)
            n += dungeons_in_chunk(sm, w, &salts, cx, cz, out + 4 * n, maxItems - n);
    return n;
}

/* ------------------------------------------------------------------ lava lakes */

/* LakeFeature with lava: carve a blob of ellipsoids into a 16x8x16 box and give up if the edge of
 * the blob would touch a fluid above its middle or open air below it. */
static int lava_lake(World *w, Xoroshiro *xr, int x, int y, int z, int centred)
{
    if (y <= -64 + 4) return 0;
    int ox = x - (centred ? 8 : 0), oy = y - 4, oz = z - (centred ? 8 : 0);
    static __thread unsigned char grid[16 * 16 * 8];
    memset(grid, 0, sizeof grid);
    int blobs = xNextIntJ(xr, 4) + 4;
    for (int i = 0; i < blobs; i++) {
        double d = xNextDoubleJ(xr) * 6.0 + 3.0;
        double e = xNextDoubleJ(xr) * 4.0 + 2.0;
        double f = xNextDoubleJ(xr) * 6.0 + 3.0;
        double g = xNextDoubleJ(xr) * (16.0 - d - 2.0) + 1.0 + d / 2.0;
        double h = xNextDoubleJ(xr) * (8.0 - e - 4.0) + 2.0 + e / 2.0;
        double k = xNextDoubleJ(xr) * (16.0 - f - 2.0) + 1.0 + f / 2.0;
        for (int l = 1; l < 15; l++)
            for (int m = 1; m < 15; m++)
                for (int n = 1; n < 7; n++) {
                    double a = (l - g) / (d / 2.0), b = (n - h) / (e / 2.0), c = (m - k) / (f / 2.0);
                    if (a * a + b * b + c * c < 1.0) grid[(l * 16 + m) * 8 + n] = 1;
                }
    }
#define G(i, j, t) grid[((i) * 16 + (j)) * 8 + (t)]
    for (int i = 0; i < 16; i++)
        for (int j = 0; j < 16; j++)
            for (int t = 0; t < 8; t++) {
                if (G(i, j, t)) continue;
                int edge = (i < 15 && G(i + 1, j, t)) || (i > 0 && G(i - 1, j, t)) ||
                           (j < 15 && G(i, j + 1, t)) || (j > 0 && G(i, j - 1, t)) ||
                           (t < 7 && G(i, j, t + 1)) || (t > 0 && G(i, j, t - 1));
                if (!edge) continue;
                int b = world_block(w, ox + i, oy + t, oz + j);
                if (t >= 4 && (b == WB_WATER || b == WB_LAVA)) return 0;
                if (t < 4 && b != WB_SOLID && b != WB_LAVA) return 0;
            }
#undef G
    return 1;
}

static int is_deep_dark(SeedMap *sm, int x, int y, int z)
{
    return getBiomeAt(&sm->g, 4, x >> 2, y >> 2, z >> 2) == deep_dark;
}

static int lakes_in_chunk(SeedMap *sm, World *w, int cx, int cz, int *out, int max)
{
    uint64_t pop = getPopulationSeed(sm->mc, sm->seed, cx * 16, cz * 16);
    int centred = sm->mc >= MC_26_1;
    int n = 0;
    for (int pass = 0; pass < 2 && n < max; pass++) {
        Xoroshiro xr;
        xSetSeed(&xr, pop + 10000 + pass);
        if (xNextFloat(&xr) >= (pass == 0 ? 1.0f / 9 : 1.0f / 200)) continue;
        int x = cx * 16 + xNextIntJ(&xr, 16);
        int z = cz * 16 + xNextIntJ(&xr, 16);
        int y;
        if (pass == 0) {
            y = xNextIntJ(&xr, 320);
            /* Environment scan: down to 32 blocks to the first non-air block. */
            int found = 0;
            for (int i = 0; i <= 32; i++) {
                if (world_block(w, x, y, z) != WB_AIR && y - 5 >= -64) { found = 1; break; }
                if (i == 32) break;
                y--;
                if (y < -64) break;
            }
            if (!found) continue;
            if (y > world_height(w, x, z, 0) - 5) continue;
        } else {
            y = world_height(w, x, z, 1);
        }
        if (is_deep_dark(sm, x, y, z)) continue;
        if (!lava_lake(w, &xr, x, y, z, centred)) continue;
        /* Report the middle of the lake: before 26.1 it extends 16 blocks east and south. */
        int shift = centred ? 0 : 8;
        out[4 * n] = x + shift; out[4 * n + 1] = y; out[4 * n + 2] = z + shift; out[4 * n + 3] = pass;
        n++;
    }
    return n;
}

/* ------------------------------------------------------------------ fossils */

static const int FOSSIL_SIZES[8][3] = {
    {3, 3, 13}, {5, 4, 13}, {7, 4, 13}, {9, 5, 13}, /* spine_1..4 */
    {6, 5, 7}, {7, 5, 5}, {5, 4, 5}, {4, 4, 4},     /* skull_1..4 */
};

static int fossil_biome(int b) { return b == desert || b == swamp || b == mangrove_swamp; }

/* FossilFeature: sink the fossil 15-24 blocks below the lowest ocean floor under it and give up if
 * more than four corners of its box are air or fluid. Returns the type (0-7) or -1. */
static int fossil(World *w, Xoroshiro *xr, int x, int y, int z, int *outX, int *outY, int *outZ)
{
    int rot = xNextIntJ(xr, 4); /* NONE, CW90, CW180, CCW90 */
    int type = xNextIntJ(xr, 8);
    int sx = FOSSIL_SIZES[type][0], sy = FOSSIL_SIZES[type][1], sz = FOSSIL_SIZES[type][2];
    if (rot == 1 || rot == 3) { int t = sx; sx = sz; sz = t; }
    int px = x - sx / 2, pz = z - sz / 2;
    int j = y;
    for (int k = 0; k < sx; k++)
        for (int l = 0; l < sz; l++) {
            int hgt = world_height(w, px + k, pz + l, 0);
            if (hgt < j) j = hgt;
        }
    int fy = j - 15 - xNextIntJ(xr, 10);
    if (fy < -64 + 10) fy = -64 + 10;
    int empty = 0;
    for (int c = 0; c < 8; c++) {
        int bx = px + ((c & 1) ? sx - 1 : 0);
        int by = fy + ((c & 2) ? sy - 1 : 0);
        int bz = pz + ((c & 4) ? sz - 1 : 0);
        if (world_block(w, bx, by, bz) != WB_SOLID) empty++;
    }
    if (empty > 4) return -1;
    *outX = px + sx / 2; *outY = fy; *outZ = pz + sz / 2;
    return type;
}

static int fossils_in_chunk(SeedMap *sm, World *w, int cx, int cz, int *out, int max)
{
    uint64_t pop = getPopulationSeed(sm->mc, sm->seed, cx * 16, cz * 16);
    int n = 0;
    for (int pass = 0; pass < 2 && n < max; pass++) {
        Xoroshiro xr;
        xSetSeed(&xr, pop + 30000 + pass);
        if (xNextFloat(&xr) >= 1.0f / 64) continue;
        int x = cx * 16 + xNextIntJ(&xr, 16);
        int z = cz * 16 + xNextIntJ(&xr, 16);
        int y = pass == 0 ? xNextIntJ(&xr, 320) : xNextIntJ(&xr, 57) - 64;
        if (!fossil_biome(getBiomeAt(&sm->g, 4, x >> 2, y >> 2, z >> 2))) continue;
        int fx, fy, fz;
        int type = fossil(w, &xr, x, y, z, &fx, &fy, &fz);
        if (type < 0) continue;
        out[4 * n] = fx; out[4 * n + 1] = fy; out[4 * n + 2] = fz; out[4 * n + 3] = pass << 8 | type;
        n++;
    }
    return n;
}

int sm_lava_lakes(SeedMap *sm, int cx0, int cz0, int cx1, int cz1, int *out, int maxItems)
{
    if (sm->edition != SM_JAVA || sm->dim != DIM_OVERWORLD || sm->mc < MC_1_18) return -1;
    World *w = world_for(sm);
    if (!w) return -1;
    int n = 0;
    for (int cz = cz0; cz <= cz1 && n < maxItems; cz++)
        for (int cx = cx0; cx <= cx1 && n < maxItems; cx++)
            n += lakes_in_chunk(sm, w, cx, cz, out + 4 * n, maxItems - n);
    return n;
}

int sm_fossils(SeedMap *sm, int cx0, int cz0, int cx1, int cz1, int *out, int maxItems)
{
    if (sm->edition != SM_JAVA || sm->dim != DIM_OVERWORLD || sm->mc < MC_1_18) return -1;
    World *w = world_for(sm);
    if (!w) return -1;
    int n = 0;
    for (int cz = cz0; cz <= cz1 && n < maxItems; cz++)
        for (int cx = cx0; cx <= cx1 && n < maxItems; cx++)
            n += fossils_in_chunk(sm, w, cx, cz, out + 4 * n, maxItems - n);
    return n;
}

/* ------------------------------------------------------------------ clusters on an 8-block grid */

/* Flood fill over grid cells (26-connected) shared by cheese caves and ore veins. */
typedef struct {
    int (*inside)(void *ctx, int x, int y, int z, float *value);
    void *ctx;
    int minY, maxY;  /* grid limits in blocks */
    int maxCells;
} Fill;

typedef struct { int x, y, z; } Cell;

typedef struct {
    int min[3], max[3], ref[3];
    float best;
    int count;
} Blob;

/* A set of visited grid cells, keyed like the world caches. */
typedef struct { uint64_t *keys; size_t cap, n; } Seen;

static uint64_t cell_key(int x, int y, int z)
{
    return ((uint64_t)((x >> 3) & 0x3FFFFFF) << 38) | ((uint64_t)((y >> 3) & 0xFFF) << 26) | (uint64_t)((z >> 3) & 0x3FFFFFF);
}

static int seen_add(Seen *s, uint64_t k)
{
    if ((s->n + 1) * 2 > s->cap) {
        size_t cap = s->cap ? s->cap * 2 : 4096;
        uint64_t *keys = (uint64_t *) calloc(cap, sizeof(uint64_t));
        if (!keys) return 0;
        for (size_t i = 0; i < s->cap; i++) {
            if (!s->keys[i]) continue;
            size_t j = (s->keys[i] * 0x9E3779B97F4A7C15ULL >> 20) & (cap - 1);
            while (keys[j]) j = (j + 1) & (cap - 1);
            keys[j] = s->keys[i];
        }
        free(s->keys);
        s->keys = keys; s->cap = cap;
    }
    k |= 1ULL << 63;
    size_t j = (k * 0x9E3779B97F4A7C15ULL >> 20) & (s->cap - 1);
    while (s->keys[j]) {
        if (s->keys[j] == k) return 0;
        j = (j + 1) & (s->cap - 1);
    }
    s->keys[j] = k; s->n++;
    return 1;
}

static int seen_has(const Seen *s, uint64_t k)
{
    if (!s->cap) return 0;
    k |= 1ULL << 63;
    size_t j = (k * 0x9E3779B97F4A7C15ULL >> 20) & (s->cap - 1);
    while (s->keys[j]) {
        if (s->keys[j] == k) return 1;
        j = (j + 1) & (s->cap - 1);
    }
    return 0;
}

/* Fills the blob containing the start cell. The reference cell is the one with the lowest value
 * (ties: the first reached). Returns 0 if the blob was larger than maxCells. */
static int fill(const Fill *f, Seen *seen, int x, int y, int z, float v0, Blob *b)
{
    size_t cap = 1024, head = 0, tail = 0;
    Cell *q = (Cell *) malloc(cap * sizeof(Cell));
    if (!q) return 0;
    q[tail++] = (Cell) { x, y, z };
    seen_add(seen, cell_key(x, y, z));
    b->min[0] = b->max[0] = b->ref[0] = x;
    b->min[1] = b->max[1] = b->ref[1] = y;
    b->min[2] = b->max[2] = b->ref[2] = z;
    b->best = v0;
    b->count = 0;
    int ok = 1;
    while (head < tail) {
        Cell c = q[head++];
        b->count++;
        if (b->count > f->maxCells) { ok = 0; break; }
        for (int d = 0; d < 27; d++) {
            int dx = d % 3 - 1, dy = (d / 3) % 3 - 1, dz = d / 9 - 1;
            if (!dx && !dy && !dz) continue;
            int nx = c.x + dx * 8, ny = c.y + dy * 8, nz = c.z + dz * 8;
            if (ny < f->minY || ny > f->maxY) continue;
            uint64_t k = cell_key(nx, ny, nz);
            if (seen_has(seen, k)) continue;
            float v;
            if (!f->inside(f->ctx, nx, ny, nz, &v)) continue;
            seen_add(seen, k);
            if (tail == cap) {
                Cell *nq = (Cell *) realloc(q, cap * 2 * sizeof(Cell));
                if (!nq) { ok = 0; break; }
                q = nq; cap *= 2;
            }
            q[tail++] = (Cell) { nx, ny, nz };
            if (nx < b->min[0]) b->min[0] = nx;
            if (nx > b->max[0]) b->max[0] = nx;
            if (ny < b->min[1]) b->min[1] = ny;
            if (ny > b->max[1]) b->max[1] = ny;
            if (nz < b->min[2]) b->min[2] = nz;
            if (nz > b->max[2]) b->max[2] = nz;
            if (v < b->best) { b->best = v; b->ref[0] = nx; b->ref[1] = ny; b->ref[2] = nz; }
        }
        if (!ok) break;
    }
    free(q);
    return ok;
}

static int put_blob(int *out, int n, int max, const Blob *b, int info)
{
    if (n >= max) return n;
    int *o = out + 10 * n;
    o[0] = b->ref[0]; o[1] = b->ref[1]; o[2] = b->ref[2];
    o[3] = b->min[0]; o[4] = b->min[1]; o[5] = b->min[2];
    o[6] = b->max[0]; o[7] = b->max[1]; o[8] = b->max[2];
    o[9] = info;
    return n + 1;
}

/* Runs the fill from every inside cell of the area and keeps blobs whose reference cell lies in
 * it, so a blob that spans several map tiles is reported by exactly one of them. */
static int clusters(const Fill *f, int x0, int z0, int x1, int z1, int minCells,
                    int (*info)(void *ctx, const Blob *b), int *out, int max)
{
    Seen seen = { 0 };
    int n = 0;
    int gx0 = (x0 + 7) & ~7, gz0 = (z0 + 7) & ~7;
    for (int x = gx0; x < x1 && n < max; x += 8) {
        for (int z = gz0; z < z1 && n < max; z += 8) {
            for (int y = f->minY; y <= f->maxY; y += 8) {
                if (seen_has(&seen, cell_key(x, y, z))) continue;
                float v;
                if (!f->inside(f->ctx, x, y, z, &v)) continue;
                Blob b;
                if (!fill(f, &seen, x, y, z, v, &b)) continue;
                if (b.count < minCells) continue;
                if (b.ref[0] < x0 || b.ref[0] >= x1 || b.ref[2] < z0 || b.ref[2] >= z1) continue;
                n = put_blob(out, n, max, &b, info(f->ctx, &b));
            }
        }
    }
    free(seen.keys);
    return n;
}

/* Cheese caves: the big open caves of the 1.18+ noise caves, from Y -48 to 88. */
typedef struct { SeedMap *sm; World *w; } CaveCtx;

static int cave_inside(void *ctx, int x, int y, int z, float *v)
{
    CaveCtx *c = (CaveCtx *) ctx;
    *v = world_cheese(c->w, x, y, z);
    return *v < 0.0f;
}

static int cave_info(void *ctx, const Blob *b)
{
    CaveCtx *c = (CaveCtx *) ctx;
    int biome = getBiomeAt(&c->sm->g, 4, b->ref[0] >> 2, b->ref[1] >> 2, b->ref[2] >> 2);
    int count = b->count > 0xFFFF ? 0xFFFF : b->count;
    return count | (biome & 0xFF) << 16;
}

int sm_caves(SeedMap *sm, int x0, int z0, int x1, int z1, int *out, int maxItems)
{
    if (sm->edition != SM_JAVA || sm->dim != DIM_OVERWORLD || sm->mc < MC_1_18) return -1;
    World *w = world_for(sm);
    if (!w) return -1;
    CaveCtx ctx = { sm, w };
    Fill f = { cave_inside, &ctx, -48, 88, 60000 };
    return clusters(&f, x0, z0, x1, z1, 460, cave_info, out, maxItems);
}

/* Large ore veins: where the vein noise allows ore (ignoring the per-block chance). info = number of
 * grid cells | cells where ore (not just filler stone) can appear << 16. */
typedef struct { OreVeinParameters *ov; int iron; } VeinCtx;

static double clamped(double v, double a, double b, double c, double d)
{
    double t = (v - a) / (b - a);
    if (t < 0) t = 0;
    if (t > 1) t = 1;
    return c + t * (d - c);
}

/* 0 = no vein, 1 = vein filler only, 2 = vein with ore. */
static int vein_at(OreVeinParameters *p, int iron, int x, int y, int z)
{
    const int minY = iron ? -60 : 0, maxY = iron ? -8 : 50;
    if (y < minY || y > maxY) return 0;
    double a = fabs(sampleDoublePerlin(&p->oreVeinA, 4 * x, 4 * y, 4 * z));
    double b = fabs(sampleDoublePerlin(&p->oreVeinB, 4 * x, 4 * y, 4 * z));
    if (fmax(a, b) >= 0.08f) return 0;
    double toggle = sampleDoublePerlin(&p->oreVeininess, x * 1.5, y * 1.5, z * 1.5);
    if ((toggle > 0.0) == iron) return 0;
    int off = maxY - y < y - minY ? maxY - y : y - minY;
    if (fabs(toggle) + clamped(off, 0.0, 20.0, -0.2, 0.0) < 0.4f) return 0;
    return sampleDoublePerlin(&p->oreGap, x, y, z) > -0.3f ? 2 : 1;
}

static int vein_inside(void *ctx, int x, int y, int z, float *v)
{
    VeinCtx *c = (VeinCtx *) ctx;
    int r = vein_at(c->ov, c->iron, x, y, z);
    *v = r == 2 ? -1.0f : 0.0f;
    return r != 0;
}

typedef struct { VeinCtx vc; int ores; } VeinCount;

static int vein_info(void *ctx, const Blob *b)
{
    (void) ctx;
    int count = b->count > 0xFFFF ? 0xFFFF : b->count;
    return count;
}

int sm_veins(SeedMap *sm, OreVeinParameters *ov, int iron, int x0, int z0, int x1, int z1, int *out, int maxItems)
{
    if (!ov) return -1;
    VeinCtx ctx = { ov, iron };
    Fill f = { vein_inside, &ctx, iron ? -64 : 0, iron ? -8 : 48, 4000 };
    int n = clusters(&f, x0, z0, x1, z1, 6, vein_info, out, maxItems);
    /* Count the cells that can hold ore, for the details card. */
    for (int i = 0; i < n; i++) {
        int *o = out + 10 * i, ores = 0;
        for (int x = o[3]; x <= o[6]; x += 8)
            for (int y = o[4]; y <= o[7]; y += 8)
                for (int z = o[5]; z <= o[8]; z += 8)
                    if (vein_at(ov, iron, x, y, z) == 2) ores++;
        o[9] |= (ores > 0xFFFF ? 0xFFFF : ores) << 16;
    }
    return n;
}

int sm_surface(SeedMap *sm, int x, int z, int *ground, int *top)
{
    if (sm->edition != SM_JAVA || sm->dim != DIM_OVERWORLD || sm->mc < MC_1_18) return 0;
    World *w = world_for(sm);
    if (!w) return 0;
    *ground = world_height(w, x, z, 0);
    *top = world_height(w, x, z, 1);
    return 1;
}
