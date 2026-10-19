#include "seedmap_world.h"

#include <limits.h>
#include <math.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>

#include "cubiomes/carver.h"
#include "cubiomes/rng.h"
#include "cubiomes/terrainnoise.h"

/* ------------------------------------------------------------------ small hash map */

typedef struct {
    uint64_t *keys; /* 0 = empty; stored keys are key | 1 << 63 */
    void **vals;
    size_t cap, n;
} Map;

static uint64_t mix(uint64_t k)
{
    k ^= k >> 33; k *= 0xff51afd7ed558ccdULL;
    k ^= k >> 33; k *= 0xc4ceb9fe1a85ec53ULL;
    return k ^ (k >> 33);
}

static void **map_slot(Map *m, uint64_t key, int create);

static int map_grow(Map *m)
{
    Map old = *m;
    size_t cap = old.cap ? old.cap * 2 : 1024;
    m->keys = (uint64_t *) calloc(cap, sizeof(uint64_t));
    m->vals = (void **) calloc(cap, sizeof(void *));
    if (!m->keys || !m->vals) {
        free(m->keys); free(m->vals);
        *m = old;
        return 0;
    }
    m->cap = cap; m->n = 0;
    for (size_t i = 0; i < old.cap; i++)
        if (old.keys[i]) *map_slot(m, old.keys[i] & ~(1ULL << 63), 1) = old.vals[i];
    free(old.keys); free(old.vals);
    return 1;
}

static void **map_slot(Map *m, uint64_t key, int create)
{
    if (create && (m->n + 1) * 2 > m->cap && !map_grow(m)) return NULL;
    if (!m->cap) return NULL;
    uint64_t k = key | (1ULL << 63);
    size_t i = mix(key) & (m->cap - 1);
    while (m->keys[i]) {
        if (m->keys[i] == k) return &m->vals[i];
        i = (i + 1) & (m->cap - 1);
    }
    if (!create) return NULL;
    m->keys[i] = k; m->n++;
    return &m->vals[i];
}

static void map_free(Map *m, int freeVals)
{
    if (freeVals) for (size_t i = 0; i < m->cap; i++) if (m->keys[i]) free(m->vals[i]);
    free(m->keys); free(m->vals);
    memset(m, 0, sizeof *m);
}

static uint64_t key2(int a, int b) { return ((uint64_t)(uint32_t) a << 32) | (uint32_t) b; }
static uint64_t key3(int a, int b, int c)
{
    return ((uint64_t)(a & 0x3FFFFFF) << 38) | ((uint64_t)(b & 0xFFF) << 26) | (uint64_t)(c & 0x3FFFFFF);
}

/* ------------------------------------------------------------------ world */

typedef struct { double depth, factor, jagged, erosion; } Column;

/* A dungeon placed earlier in the same chunk: later rooms see its air and walls. */
typedef struct { int x, y, z, xr, zr, nchests, cx[2], cz[2]; } Room;

#define WORLD_H 384
#define CARVE_BYTES (16 * 16 * WORLD_H / 8)

struct World {
    SeedMap *sm;
    TerrainNoise *tn;
    Map columns;  /* (cellX, cellZ) -> Column* */
    Map corners;  /* (cellX, cellY, cellZ) -> double* */
    Map carves;   /* (chunkX, chunkZ) -> uint8_t[CARVE_BYTES] */
    Map surface;  /* (x >> 2, z >> 2) -> int* */
    Map centers;  /* aquifer grid cell -> Pos3* (randomised centre) */
    Map cheese;   /* 8-block grid cell -> float bits of the cheese cave noise */
    Map statuses; /* aquifer centre -> Fluid* */
    DoublePerlinNoise barrier, floodedness, spread, lava;
    PerlinNoise oct[8];
    uint64_t aquiferLo, aquiferHi;
    Room rooms[16];
    int nrooms;
    CaveCarverConfig cave, extra;
    CanyonCarverConfig canyon;
    int haveCave, haveExtra, haveCanyon;
};

static void init_aquifer(World *w)
{
    static const uint64_t md5_barrier[2] = { 0xe170fda6c51d093fULL, 0x134f3e4bc3f40b0cULL };
    static const uint64_t md5_flood[2] = { 0x1b5d4f85fdc2d89eULL, 0x22c7e3e611bf67c6ULL };
    static const uint64_t md5_spread[2] = { 0x08f136f47cd9aaa2ULL, 0x3666182f5fa0ac6fULL };
    static const uint64_t md5_lava[2] = { 0x0e5121277ab51b72ULL, 0x2724d8c56aa005baULL };
    static const uint64_t md5_aquifer[2] = { 0x7bb15cc403c6ace6ULL, 0x0bdd56bc9d232691ULL };
    static const double amp[1] = { 1.0 };
    Xoroshiro ws;
    xSetSeed(&ws, w->sm->seed);
    uint64_t lo = xNextLong(&ws), hi = xNextLong(&ws);
    Xoroshiro x;
    x.lo = lo ^ md5_barrier[0]; x.hi = hi ^ md5_barrier[1];
    int n = xDoublePerlinInit(&w->barrier, &x, w->oct, amp, -3, 1, -1);
    x.lo = lo ^ md5_flood[0]; x.hi = hi ^ md5_flood[1];
    n += xDoublePerlinInit(&w->floodedness, &x, w->oct + n, amp, -7, 1, -1);
    x.lo = lo ^ md5_spread[0]; x.hi = hi ^ md5_spread[1];
    n += xDoublePerlinInit(&w->spread, &x, w->oct + n, amp, -5, 1, -1);
    x.lo = lo ^ md5_lava[0]; x.hi = hi ^ md5_lava[1];
    xDoublePerlinInit(&w->lava, &x, w->oct + n, amp, -1, 1, -1);
    x.lo = lo ^ md5_aquifer[0]; x.hi = hi ^ md5_aquifer[1];
    w->aquiferLo = xNextLong(&x);
    w->aquiferHi = xNextLong(&x);
}

World *world_new(SeedMap *sm)
{
    if (sm->edition != SM_JAVA || sm->dim != DIM_OVERWORLD || sm->mc < MC_1_18) return NULL;
    World *w = (World *) calloc(1, sizeof(World));
    if (!w) return NULL;
    w->sm = sm;
    w->tn = (TerrainNoise *) calloc(1, sizeof(TerrainNoise));
    if (!w->tn) { free(w); return NULL; }
    setupTerrainNoise(w->tn, sm->mc, 0);
    initTerrainNoise(w->tn, sm->seed, DIM_OVERWORLD);
    w->haveCave = getCaveCarverConfig(CAVE_CARVER, sm->mc, -1, &w->cave);
    w->haveExtra = getCaveCarverConfig(CAVE_EXTRA_UNDERGROUND_CARVER, sm->mc, -1, &w->extra);
    w->haveCanyon = getCanyonCarverConfig(CANYON_CARVER, sm->mc, &w->canyon);
    init_aquifer(w);
    return w;
}

void world_free(World *w)
{
    if (!w) return;
    map_free(&w->columns, 1);
    map_free(&w->corners, 1);
    map_free(&w->carves, 1);
    map_free(&w->surface, 1);
    map_free(&w->centers, 1);
    map_free(&w->cheese, 0);
    map_free(&w->statuses, 1);
    free(w->tn);
    free(w);
}

static Column *column(World *w, int cellX, int cellZ)
{
    void **slot = map_slot(&w->columns, key2(cellX, cellZ), 1);
    if (!slot) return NULL;
    if (*slot) return (Column *) *slot;
    Column *c = (Column *) malloc(sizeof(Column));
    if (!c) return NULL;
    TerrainNoise *tn = w->tn;
    float np[4];
    sampleNoiseParameters(&tn->g.bn, cellX, cellZ, np);
    c->depth = getSpline(tn->g.bn.sp, np) - 0.50375f;
    c->factor = getSpline(tn->factorSpline, np);
    c->erosion = np[1];
    double jagged = sampleDoublePerlin(&tn->noises[OTP_JAGGED], cellX * 4 * 1500.0, 0, cellZ * 4 * 1500.0);
    jagged = jagged >= 0.0 ? jagged : jagged / 2.0;
    c->jagged = jagged * getSpline(tn->jaggednessSpline, np);
    *slot = c;
    return c;
}

/* Terrain values at a noise cell corner. The game interpolates each of these between corners and
 * only then combines the noodle cave parts, so they are kept separately. */
typedef struct { double main, noodle, thickness, ridgeA, ridgeB; } Corner;

static double cmap(double v, double from0, double from1, double to0, double to1)
{
    double t = (v - from0) / (from1 - from0);
    if (t < 0) t = 0;
    if (t > 1) t = 1;
    return to0 + t * (to1 - to0);
}

static double slide_overworld(double d, int y)
{
    double a = cmap(y, -64 + 384 - 80, -64 + 384 - 64, 1.0, 0.0);
    d = -0.078125 + a * (d + 0.078125);
    double c = cmap(y, -64 + 0, -64 + 24, 0.0, 1.0);
    return 0.1171875 + c * (d - 0.1171875);
}

static const Corner *corner(World *w, int cellX, int cellY, int cellZ)
{
    static const Corner below = { 1.0, -1.0, 0, 0, 0 }, above = { -1.0, -1.0, 0, 0, 0 };
    if (cellY < 0) return &below;
    if (cellY > 48) return &above;
    void **slot = map_slot(&w->corners, key3(cellX, cellY, cellZ), 1);
    if (slot && *slot) return (const Corner *) *slot;
    Column *col = column(w, cellX, cellZ);
    if (!col || !slot) return &below;
    Corner *c = (Corner *) malloc(sizeof(Corner));
    if (!c) return &below;
    TerrainNoise *tn = w->tn;
    int x = cellX * 4, y = -64 + cellY * 8, z = cellZ * 4;
    double sr = sampleSpaghettiRoughness(tn, x, y, z);
    double en = sampleEntrances(tn, x, y, z, sr);
    double sc = sampleSlopedCheese(tn, x, y, z, col->depth, col->factor, col->jagged);
    double b = (sc >= -1000000.0 && sc < 1.5625) ? fmin(sc, 5.0 * en) : sampleUnderground(tn, x, y, z, sr, en, sc);
    c->main = slide_overworld(b, y);
    int inRange = y >= -60 && y <= 320;
    const double k = 2.6666666666666665;
    c->noodle = inRange ? sampleDoublePerlin(&tn->noises[OTP_NOODLE], x, y, z) : -1.0;
    c->thickness = inRange ? -0.075 - 0.025 * sampleDoublePerlin(&tn->noises[OTP_NOODLE_THICKNESS], x, y, z) : 0.0;
    c->ridgeA = inRange ? sampleDoublePerlin(&tn->noises[OTP_NOODLE_RIDGE_A], x * k, y * k, z * k) : 0.0;
    c->ridgeB = inRange ? sampleDoublePerlin(&tn->noises[OTP_NOODLE_RIDGE_B], x * k, y * k, z * k) : 0.0;
    *slot = c;
    return c;
}

#define LERP_FIELD(f) lerp3(px, py, pz, c[0]->f, c[1]->f, c[2]->f, c[3]->f, c[4]->f, c[5]->f, c[6]->f, c[7]->f)

double world_density(World *w, int x, int y, int z)
{
    if (y < -64) return 1.0;
    if (y >= 320) return -1.0;
    int cx = x >> 2, cz = z >> 2, cy = (y + 64) >> 3;
    double px = (x & 3) / 4.0, pz = (z & 3) / 4.0, py = ((y + 64) & 7) / 8.0;
    const Corner *c[8] = {
        corner(w, cx, cy, cz), corner(w, cx + 1, cy, cz),
        corner(w, cx, cy + 1, cz), corner(w, cx + 1, cy + 1, cz),
        corner(w, cx, cy, cz + 1), corner(w, cx + 1, cy, cz + 1),
        corner(w, cx, cy + 1, cz + 1), corner(w, cx + 1, cy + 1, cz + 1),
    };
    double main = LERP_FIELD(main);
    double cl = main * 0.64;
    if (cl > 1) cl = 1;
    if (cl < -1) cl = -1;
    double density = cl / 2.0 - cl * cl * cl / 24.0;
    double toggle = LERP_FIELD(noodle);
    if (!(toggle >= -1000000.0 && toggle < 0.0)) {
        double ra = fabs(LERP_FIELD(ridgeA)), rb = fabs(LERP_FIELD(ridgeB));
        double noodle = LERP_FIELD(thickness) + 1.5 * (ra > rb ? ra : rb);
        if (noodle < density) density = noodle;
    }
    return density;
}

static void mark(uint8_t *mask, int cx, int cz, Pos3List *list)
{
    for (int i = 0; i < list->size; i++) {
        Pos3 p = list->pos3s[i];
        int lx = p.x - cx * 16, lz = p.z - cz * 16, ly = p.y + 64;
        if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || ly < 0 || ly >= WORLD_H) continue;
        int bit = (ly * 16 + lz) * 16 + lx;
        mask[bit >> 3] |= (uint8_t)(1 << (bit & 7));
    }
}

static uint8_t *carve_mask(World *w, int cx, int cz)
{
    void **slot = map_slot(&w->carves, key2(cx, cz), 1);
    if (!slot) return NULL;
    if (*slot) return (uint8_t *) *slot;
    uint8_t *mask = (uint8_t *) calloc(1, CARVE_BYTES);
    if (!mask) return NULL;
    static int biomes[17][17]; /* not used by the 1.18+ carvers */
    Pos3List list;
    if (w->haveCave) {
        createPos3List(&list, 1024);
        carveCave(w->sm->seed, w->sm->mc, cx, cz, w->cave, CAVE_CARVER, biomes, &list);
        mark(mask, cx, cz, &list);
        freePos3List(&list);
    }
    if (w->haveExtra) {
        createPos3List(&list, 1024);
        carveCave(w->sm->seed, w->sm->mc, cx, cz, w->extra, CAVE_EXTRA_UNDERGROUND_CARVER, biomes, &list);
        mark(mask, cx, cz, &list);
        freePos3List(&list);
    }
    if (w->haveCanyon) {
        createPos3List(&list, 1024);
        carveCanyon(w->sm->seed, w->sm->mc, cx, cz, w->canyon, CANYON_CARVER, biomes, &list);
        mark(mask, cx, cz, &list);
        freePos3List(&list);
    }
    *slot = mask;
    return mask;
}

int world_carved(World *w, int x, int y, int z)
{
    if (y < -64 || y >= 320) return 0;
#ifdef SM_EXPERIMENT
    if (getenv("NOCARVE")) return 0;
#endif
    uint8_t *mask = carve_mask(w, x >> 4, z >> 4);
    if (!mask) return 0;
    int bit = ((y + 64) * 16 + (z & 15)) * 16 + (x & 15);
    return (mask[bit >> 3] >> (bit & 7)) & 1;
}

/* ------------------------------------------------------------------ aquifers (NoiseBasedAquifer) */

typedef struct { int level, type; } Fluid;

#define WAY_BELOW (-32512)

static Fluid global_fluid(int y)
{
    Fluid f = { y < -54 ? -54 : 63, y < -54 ? WB_LAVA : WB_WATER };
    return f;
}

static int fluid_at(Fluid f, int y) { return y < f.level ? f.type : WB_AIR; }

static int prelim_surface(World *w, int x, int z)
{
    x &= ~3; z &= ~3;
    void **slot = map_slot(&w->surface, key2(x >> 2, z >> 2), 1);
    if (slot && *slot) return *(int *) *slot;
    int s = samplePreliminarySurfaceLevel(w->tn, x, z);
    if (slot) {
        int *v = (int *) malloc(sizeof(int));
        if (v) { *v = s; *slot = v; }
    }
    return s;
}

static int64_t mth_seed(int x, int y, int z)
{
    int64_t l = (int64_t)(int32_t)((uint32_t) x * 3129871u) ^ (int64_t) z * 116129781LL ^ (int64_t) y;
    l = l * l * 42317861LL + l * 11LL;
    return l >> 16;
}

static Pos3 aquifer_center(World *w, int gx, int gy, int gz)
{
    void **slot = map_slot(&w->centers, key3(gx, gy, gz), 1);
    if (slot && *slot) return *(Pos3 *) *slot;
    Xoroshiro r;
    r.lo = (uint64_t) mth_seed(gx, gy, gz) ^ w->aquiferLo;
    r.hi = w->aquiferHi;
    Pos3 p;
    p.x = gx * 16 + xNextInt(&r, 10);
    p.y = gy * 12 + xNextInt(&r, 9);
    p.z = gz * 16 + xNextInt(&r, 10);
    if (slot) {
        Pos3 *v = (Pos3 *) malloc(sizeof(Pos3));
        if (v) { *v = p; *slot = v; }
    }
    return p;
}

static double clamped_map(double v, double a, double b, double c, double d) { return cmap(v, a, b, c, d); }

static Fluid compute_fluid(World *w, int x, int y, int z)
{
    static const int offs[13][2] = {
        {0, 0}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {-3, 0}, {-2, 0}, {-1, 0}, {1, 0},
        {-2, 1}, {-1, 1}, {0, 1}, {1, 1},
    };
    Fluid global = global_fluid(y);
    int minSurface = INT_MAX, up = y + 12, down = y - 12, flooded = 0;
    for (int i = 0; i < 13; i++) {
        int sx = x + offs[i][0] * 16, sz = z + offs[i][1] * 16;
        int surf = prelim_surface(w, sx, sz);
        int o = surf + 8;
        int here = offs[i][0] == 0 && offs[i][1] == 0;
        if (here && down > o) return global;
        int above = up > o;
        if (above || here) {
            Fluid f = global_fluid(o);
            if (fluid_at(f, o) != WB_AIR) {
                if (here) flooded = 1;
                if (above) return f;
            }
        }
        if (surf < minSurface) minSurface = surf;
    }

    double d, e;
    Column *col = column(w, x >> 2, z >> 2);
    double depth = col ? col->depth + cmap(y, -64, 320, 1.5, -1.5) : 0;
    if (col && col->erosion < -0.225 && depth > 0.9) {
        d = -1.0; e = -1.0;
    } else {
        int i = minSurface + 8 - y;
        double f = flooded ? clamped_map(i, 0.0, 64.0, 1.0, 0.0) : 0.0;
        double g = sampleDoublePerlin(&w->floodedness, x, y * 0.67, z);
        if (g < -1) g = -1;
        if (g > 1) g = 1;
        double h = -0.3 + (1.0 - f) * 1.1;
        double k = -0.8 + (1.0 - f) * 1.2;
        d = g - k;
        e = g - h;
    }
    int level;
    if (e > 0.0) {
        level = global.level;
    } else if (d > 0.0) {
        int gx = floordiv(x, 16), gy = floordiv(y, 40), gz = floordiv(z, 16);
        double s = sampleDoublePerlin(&w->spread, gx, gy * 0.7142857142857143, gz) * 10.0;
        int q = gy * 40 + 20 + (int) floor(s / 3.0) * 3;
        level = q < minSurface ? q : minSurface;
    } else {
        level = WAY_BELOW;
    }
    int type = global.type;
    if (level <= -10 && level != WAY_BELOW && global.type != WB_LAVA) {
        double l = sampleDoublePerlin(&w->lava, floordiv(x, 64), floordiv(y, 40), floordiv(z, 64));
        if (fabs(l) > 0.3) type = WB_LAVA;
    }
    Fluid r = { level, type };
    return r;
}

static Fluid aquifer_status(World *w, Pos3 c)
{
    void **slot = map_slot(&w->statuses, key3(c.x, c.y, c.z), 1);
    if (slot && *slot) return *(Fluid *) *slot;
    Fluid f = compute_fluid(w, c.x, c.y, c.z);
    if (slot) {
        Fluid *v = (Fluid *) malloc(sizeof(Fluid));
        if (v) { *v = f; *slot = v; }
    }
    return f;
}

static double similarity(int a, int b) { return 1.0 - abs(b - a) / 25.0; }

static double pressure(World *w, int x, int y, int z, double *barrier, Fluid a, Fluid b)
{
    int sa = fluid_at(a, y), sb = fluid_at(b, y);
    if ((sa == WB_LAVA && sb == WB_WATER) || (sa == WB_WATER && sb == WB_LAVA)) return 2.0;
    int j = abs(a.level - b.level);
    if (j == 0) return 0.0;
    double mid = 0.5 * (a.level + b.level);
    double e = y + 0.5 - mid;
    double o = j / 2.0 - fabs(e);
    double q;
    if (e > 0.0) {
        double p = 0.0 + o;
        q = p > 0.0 ? p / 1.5 : p / 2.5;
    } else {
        double p = 3.0 + o;
        q = p > 0.0 ? p / 3.0 : p / 10.0;
    }
    double s = 0.0;
    if (q >= -2.0 && q <= 2.0) {
        if (isnan(*barrier)) *barrier = sampleDoublePerlin(&w->barrier, x, y * 0.5, z);
        s = *barrier;
    }
    return 2.0 * (s + q);
}

/* What the aquifer puts at a non-solid position: WB_AIR, WB_WATER, WB_LAVA, or WB_SOLID for a
 * barrier between two aquifers. */
static int aquifer(World *w, int x, int y, int z, double substance)
{
    if (substance > 0.0) return WB_SOLID;
    Fluid global = global_fluid(y);
    if (fluid_at(global, y) == WB_LAVA) return WB_LAVA;
    int l = floordiv(x - 5, 16), m = floordiv(y + 1, 12), n = floordiv(z - 5, 16);
    int o = INT_MAX, p = INT_MAX, q = INT_MAX;
    Pos3 r = {0, 0, 0}, s = {0, 0, 0}, t = {0, 0, 0};
    for (int u = 0; u <= 1; u++) {
        for (int v = -1; v <= 1; v++) {
            for (int ww = 0; ww <= 1; ww++) {
                Pos3 c = aquifer_center(w, l + u, m + v, n + ww);
                int dx = c.x - x, dy = c.y - y, dz = c.z - z;
                int dist = dx * dx + dy * dy + dz * dz;
                if (o >= dist) {
                    t = s; s = r; r = c; q = p; p = o; o = dist;
                } else if (p >= dist) {
                    t = s; s = c; q = p; p = dist;
                } else if (q >= dist) {
                    t = c; q = dist;
                }
            }
        }
    }
    Fluid f1 = aquifer_status(w, r);
    double d = similarity(o, p);
    int state = fluid_at(f1, y);
    if (d <= 0.0) return state;
    if (state == WB_WATER && fluid_at(global_fluid(y - 1), y - 1) == WB_LAVA) return state;
    double barrier = NAN;
    Fluid f2 = aquifer_status(w, s);
    if (substance + d * pressure(w, x, y, z, &barrier, f1, f2) > 0.0) return WB_SOLID;
    Fluid f3 = aquifer_status(w, t);
    double f = similarity(o, q);
    if (f > 0.0 && substance + d * f * pressure(w, x, y, z, &barrier, f1, f3) > 0.0) return WB_SOLID;
    double g = similarity(p, q);
    if (g > 0.0 && substance + d * g * pressure(w, x, y, z, &barrier, f2, f3) > 0.0) return WB_SOLID;
    return state;
}

static int base_block(World *w, int x, int y, int z)
{
    if (y < -64) return WB_SOLID;
    if (y >= 320) return WB_AIR;
    double d = world_density(w, x, y, z);
    if (d > 0.0) {
        if (!world_carved(w, x, y, z)) return WB_SOLID;
        if (y <= -56) return WB_LAVA;
        return aquifer(w, x, y, z, 0.0);
    }
    return aquifer(w, x, y, z, d);
}

void world_add_room(World *w, int ox, int oy, int oz, int xr, int zr, int nchests, const int *chests)
{
    if (w->nrooms >= (int)(sizeof w->rooms / sizeof w->rooms[0])) return;
    Room *r = &w->rooms[w->nrooms++];
    r->x = ox; r->y = oy; r->z = oz; r->xr = xr; r->zr = zr; r->nchests = nchests;
    for (int i = 0; i < nchests && i < 2; i++) { r->cx[i] = chests[2 * i]; r->cz[i] = chests[2 * i + 1]; }
}

void world_clear_rooms(World *w) { w->nrooms = 0; }

int world_block(World *w, int x, int y, int z)
{
    for (int i = w->nrooms - 1; i >= 0; i--) {
        const Room *r = &w->rooms[i];
        int dx = x - r->x, dy = y - r->y, dz = z - r->z;
        if (dx < -r->xr - 1 || dx > r->xr + 1 || dz < -r->zr - 1 || dz > r->zr + 1 || dy < -1 || dy > 4) continue;
        if (dy == 4) break; /* the ceiling is left as it was */
        int wall = dy == -1 || dx == -r->xr - 1 || dx == r->xr + 1 || dz == -r->zr - 1 || dz == r->zr + 1;
        if (!wall) {
            if (dx == 0 && dy == 0 && dz == 0) return WB_SOLID; /* spawner */
            for (int c = 0; c < r->nchests; c++)
                if (dy == 0 && x == r->cx[c] && z == r->cz[c]) return WB_SOLID;
            return WB_AIR;
        }
        if (base_block(w, x, y - 1, z) != WB_SOLID) return WB_AIR;
        break;
    }
    return base_block(w, x, y, z);
}

int world_solid(World *w, int x, int y, int z) { return world_block(w, x, y, z) == WB_SOLID; }

int world_air(World *w, int x, int y, int z) { return world_block(w, x, y, z) == WB_AIR; }

int world_height(World *w, int x, int z, int fluidsCount)
{
    /* Highest terrain is well below the top of the world, so start a little above the estimate. */
    int start = prelim_surface(w, x, z) + 96;
    if (start > 319) start = 319;
    for (int y = start; y >= -64; y--) {
        int b = world_block(w, x, y, z);
        if (b == WB_SOLID || (fluidsCount && b != WB_AIR)) return y + 1;
    }
    return -64;
}

float world_cheese(World *w, int x, int y, int z)
{
    uint64_t k = key3(x >> 3, y >> 3, z >> 3);
    void **slot = map_slot(&w->cheese, k, 0);
    if (slot) {
        union { float f; uint32_t u; } v;
        v.u = (uint32_t)(uintptr_t) *slot;
        return v.f;
    }
    Column *col = column(w, x >> 2, z >> 2);
    float r = 1.0f;
    if (col) {
        TerrainNoise *tn = w->tn;
        double sc = sampleSlopedCheese(tn, x, y, z, col->depth, col->factor, col->jagged);
        r = (float)(sampleCaveLayer(tn, x, y, z) + sampleCaveCheese(tn, x, y, z, sc));
    }
    slot = map_slot(&w->cheese, k, 1);
    if (slot) {
        union { float f; uint32_t u; } v;
        v.f = r;
        *slot = (void *)(uintptr_t) v.u;
    }
    return r;
}

void world_trim(World *w)
{
    /* Keep memory bounded: drop every cache once it gets large; it refills on demand. */
    size_t big = w->corners.n + w->columns.n + w->surface.n + w->centers.n + w->statuses.n + w->cheese.n;
    if (big < 200000 && w->carves.n < 400) return;
    map_free(&w->columns, 1);
    map_free(&w->corners, 1);
    map_free(&w->carves, 1);
    map_free(&w->surface, 1);
    map_free(&w->centers, 1);
    map_free(&w->statuses, 1);
    map_free(&w->cheese, 0);
}
