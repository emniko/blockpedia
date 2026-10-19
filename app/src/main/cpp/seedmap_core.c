#include "seedmap_core.h"

#include <stdlib.h>
#include <string.h>

#include "cubiomes/finders.h"
#include "cubiomes/generator.h"
#include "cubiomes/util.h"

/* The Kotlin side uses these cubiomes enum values directly. */
_Static_assert(Desert_Pyramid == 1 && Village == 5 && Ancient_City == 13 && Treasure == 14,
               "cubiomes StructureType ids changed");
_Static_assert(Trail_Ruins == 24 && Trial_Chambers == 25 && Abandoned_Camp == 26 && Stronghold == 27,
               "cubiomes StructureType ids changed");

/* Biomes are sampled near the top of the world, which gives surface biomes and matches
 * how cubiomes checks structure biomes for 1.18+. In 1:4 vertical units. */
#define SURFACE_Y (319 >> 2)

struct SeedMap {
    Generator g;
    SurfaceNoise endSurface; /* Java End: end city terrain check */
    uint64_t seed;
    int mc;
    int edition;
    int dim;
    int *cache;
    size_t cacheLen;
};

SeedMap *sm_create(uint64_t seed, int mc, int edition, int dim)
{
    SeedMap *sm = (SeedMap *) calloc(1, sizeof(SeedMap));
    if (!sm) return NULL;
    sm->seed = seed;
    sm->mc = mc;
    sm->edition = edition;
    sm->dim = dim;
    setupGenerator(&sm->g, mc, 0);
    applySeed(&sm->g, dim, seed);
    if (dim == DIM_END) initSurfaceNoise(&sm->endSurface, DIM_END, seed);
    return sm;
}

/* Biome sampling height in 1:4 units. Nether biomes barely vary with height. */
static int sample_y(const SeedMap *sm)
{
    if (sm->dim == DIM_NETHER) return 64 >> 2;
    if (sm->dim == DIM_END) return 0;
    return SURFACE_Y;
}

void sm_free(SeedMap *sm)
{
    if (!sm) return;
    free(sm->cache);
    free(sm);
}

int sm_biomes(SeedMap *sm, int scale, int x, int z, int w, int h, int *out)
{
    Range r;
    memset(&r, 0, sizeof r);
    r.scale = scale;
    r.x = x;
    r.z = z;
    r.sx = w;
    r.sz = h;
    r.y = scale == 1 ? 319 : sample_y(sm);
    r.sy = 1;

    size_t need = getMinCacheSize(&sm->g, scale, w, 1, h);
    if (need > sm->cacheLen) {
        int *c = (int *) realloc(sm->cache, need * sizeof(int));
        if (!c) return 1;
        sm->cache = c;
        sm->cacheLen = need;
    }
    if (genBiomes(&sm->g, sm->cache, r)) return 1;
    memcpy(out, sm->cache, (size_t) w * h * sizeof(int));
    return 0;
}

int sm_biome_at(SeedMap *sm, int blockX, int blockZ)
{
    return getBiomeAt(&sm->g, 4, blockX >> 2, sample_y(sm), blockZ >> 2);
}

/* ------------------------------------------------------------------ Bedrock */

/* Standard MT19937. */
typedef struct { uint32_t s[624]; int i; } Mt;

static void mt_init(Mt *m, uint32_t seed)
{
    m->s[0] = seed;
    for (int k = 1; k < 624; k++)
        m->s[k] = 1812433253u * (m->s[k - 1] ^ (m->s[k - 1] >> 30)) + (uint32_t) k;
    m->i = 624;
}

static void mt_twist(Mt *m)
{
    for (int k = 0; k < 624; k++) {
        uint32_t y = (m->s[k] & 0x80000000u) | (m->s[(k + 1) % 624] & 0x7fffffffu);
        uint32_t v = m->s[(k + 397) % 624] ^ (y >> 1);
        if (y & 1u) v ^= 0x9908b0dfu;
        m->s[k] = v;
    }
    m->i = 0;
}

static uint32_t mt_next(Mt *m)
{
    if (m->i >= 624) mt_twist(m);
    uint32_t y = m->s[m->i++];
    y ^= y >> 11;
    y ^= (y << 7) & 0x9d2c5680u;
    y ^= (y << 15) & 0xefc60000u;
    y ^= y >> 18;
    return y;
}

uint32_t sm_mt_first(uint32_t seed)
{
    Mt m;
    mt_init(&m, seed);
    return mt_next(&m);
}

enum { BE_LINEAR, BE_TRIANGULAR, BE_JAVA };

typedef struct { uint32_t salt; int spacing, separation, spread; } BeConf;

/* Bedrock overworld structure placement. Values as documented by the open-source
 * Bedrock seed tools (MCBE-seedcracker, SeedFinder), which agree on all of them. */
static int be_conf(int stype, BeConf *c)
{
    switch (stype) {
    case Village:        *c = (BeConf){ 10387312u, 34, 8, BE_TRIANGULAR }; return 1;
    case Mansion:        *c = (BeConf){ 10387319u, 80, 20, BE_TRIANGULAR }; return 1;
    case Monument:       *c = (BeConf){ 10387313u, 32, 5, BE_TRIANGULAR }; return 1;
    case Ancient_City:   *c = (BeConf){ 20083232u, 24, 8, BE_TRIANGULAR }; return 1;
    case Treasure:       *c = (BeConf){ 16842397u, 4, 2, BE_TRIANGULAR }; return 1;
    case Outpost:        *c = (BeConf){ 165745296u, 80, 24, BE_TRIANGULAR }; return 1;
    case Ocean_Ruin:     *c = (BeConf){ 14357621u, 20, 8, BE_LINEAR }; return 1;
    case Shipwreck:      *c = (BeConf){ 165745295u, 24, 4, BE_LINEAR }; return 1;
    case Desert_Pyramid:
    case Jungle_Temple:
    case Swamp_Hut:
    case Igloo:          *c = (BeConf){ 14357617u, 32, 8, BE_LINEAR }; return 1;
    case Ruined_Portal:  *c = (BeConf){ 40552231u, 40, 15, BE_LINEAR }; return 1;
    /* Nether: each region holds a fortress or a bastion; which one isn't documented, so
     * "Fortress" stands for both on Bedrock. */
    case Fortress:       *c = (BeConf){ 30084232u, 30, 4, BE_LINEAR }; return 1;
    case Ruined_Portal_N:*c = (BeConf){ 40552231u, 25, 10, BE_LINEAR }; return 1;
    /* End cities aren't offered on Bedrock: without Bedrock's End terrain, every region
     * would look like a city. */
    /* These use Java's placement on Bedrock too, with the full seed. */
    case Trail_Ruins:
    case Trial_Chambers:
    case Abandoned_Camp: *c = (BeConf){ 0, 0, 0, BE_JAVA }; return 1;
    default: return 0;
    }
}

static void be_pos(const BeConf *c, uint64_t seed, int rx, int rz, int *x, int *z)
{
    uint32_t rs = (uint32_t) rx * 2570712328u + (uint32_t) rz * 4048968661u + c->salt + (uint32_t) seed;
    Mt m;
    mt_init(&m, rs);
    uint32_t range = (uint32_t) (c->spacing - c->separation);
    int ox, oz;
    if (c->spread == BE_TRIANGULAR) {
        uint32_t a = mt_next(&m) % range, b = mt_next(&m) % range;
        uint32_t d = mt_next(&m) % range, e = mt_next(&m) % range;
        ox = (int) ((a + b) / 2);
        oz = (int) ((d + e) / 2);
    } else {
        ox = (int) (mt_next(&m) % range);
        oz = (int) (mt_next(&m) % range);
    }
    *x = (rx * c->spacing + ox) * 16 + 8;
    *z = (rz * c->spacing + oz) * 16 + 8;
}

int sm_bedrock_attempt(int stype, uint64_t seed, int regX, int regZ, int *x, int *z)
{
    BeConf c;
    if (!be_conf(stype, &c) || c.spread == BE_JAVA) return 0;
    be_pos(&c, seed, regX, regZ, x, z);
    return 1;
}

/* Bedrock-only village biomes (Minecraft Wiki, "Village"). */
static int be_village_only_biome(int id)
{
    return id == snowy_taiga || id == sunflower_plains;
}

/* Bedrock decides by the biome at the structure's chunk centre. Java's position check is
 * applied as well where the editions agree; it's skipped for outposts (it includes a
 * Java-only random roll) and for Bedrock-only village biomes (Java would reject them). */
static int be_viable(SeedMap *sm, int stype, int x, int z)
{
    /* Bedrock's Nether biomes aren't modelled, so every Nether placement is reported. */
    if (sm->dim != DIM_OVERWORLD) return 1;
    /* Ancient cities need the deep dark biome far underground; cubiomes samples it there. */
    if (stype == Ancient_City) return isViableStructurePos(stype, &sm->g, x, z, 0);
    int id = getBiomeAt(&sm->g, 4, x >> 2, SURFACE_Y, z >> 2);
    if (stype == Village && be_village_only_biome(id)) return 1;
    if (!isViableFeatureBiome(sm->mc, stype, id)) return 0;
    if (stype == Outpost) return 1;
    return isViableStructurePos(stype, &sm->g, x, z, 0);
}

/* ------------------------------------------------------------------ structures */

static int java_structures(SeedMap *sm, int stype, int x0, int z0, int x1, int z1, int *out, int maxPairs)
{
    StructureConfig sc;
    if (!getStructureConfig(stype, sm->mc, &sc)) return -1;
    int regBlocks = sc.regionSize * 16;
    int rx0 = floordiv(x0, regBlocks) - 1, rz0 = floordiv(z0, regBlocks) - 1;
    int rx1 = floordiv(x1, regBlocks) + 1, rz1 = floordiv(z1, regBlocks) + 1;
    int n = 0;
    for (int rz = rz0; rz <= rz1; rz++) {
        for (int rx = rx0; rx <= rx1; rx++) {
            Pos p;
            if (!getStructurePos(stype, sm->mc, sm->seed, rx, rz, &p)) continue;
            if (p.x < x0 || p.x >= x1 || p.z < z0 || p.z >= z1) continue;
            if (!isViableStructurePos(stype, &sm->g, p.x, p.z, 0)) continue;
            if (stype == End_City && !isViableEndCityTerrain(&sm->g, &sm->endSurface, p.x, p.z)) continue;
            if (n >= maxPairs) return n;
            out[2 * n] = p.x;
            out[2 * n + 1] = p.z;
            n++;
        }
    }
    return n;
}

static int bedrock_structures(SeedMap *sm, int stype, int x0, int z0, int x1, int z1, int *out, int maxPairs)
{
    BeConf c;
    if (!be_conf(stype, &c)) return -1;
    if (c.spread == BE_JAVA) return java_structures(sm, stype, x0, z0, x1, z1, out, maxPairs);
    int regBlocks = c.spacing * 16;
    int rx0 = floordiv(x0, regBlocks), rz0 = floordiv(z0, regBlocks);
    int rx1 = floordiv(x1 - 1, regBlocks), rz1 = floordiv(z1 - 1, regBlocks);
    int n = 0;
    for (int rz = rz0; rz <= rz1; rz++) {
        for (int rx = rx0; rx <= rx1; rx++) {
            int x, z;
            be_pos(&c, sm->seed, rx, rz, &x, &z);
            if (x < x0 || x >= x1 || z < z0 || z >= z1) continue;
            if (!be_viable(sm, stype, x, z)) continue;
            if (n >= maxPairs) return n;
            out[2 * n] = x;
            out[2 * n + 1] = z;
            n++;
        }
    }
    return n;
}

int sm_structures(SeedMap *sm, int stype, int x0, int z0, int x1, int z1, int *out, int maxPairs)
{
    if (sm->edition == SM_BEDROCK) return bedrock_structures(sm, stype, x0, z0, x1, z1, out, maxPairs);
    return java_structures(sm, stype, x0, z0, x1, z1, out, maxPairs);
}

int sm_spawn(SeedMap *sm, int *x, int *z)
{
    if (sm->edition != SM_JAVA) return 1;
    Pos p = getSpawn(&sm->g);
    *x = p.x;
    *z = p.z;
    return 0;
}

int sm_strongholds(SeedMap *sm, int *out, int maxPairs)
{
    if (sm->edition != SM_JAVA) return 0;
    StrongholdIter sh;
    initFirstStronghold(&sh, sm->mc, sm->seed);
    int n = 0;
    while (n < maxPairs) {
        int remaining = nextStronghold(&sh, &sm->g);
        out[2 * n] = sh.pos.x;
        out[2 * n + 1] = sh.pos.z;
        n++;
        if (remaining <= 0) break;
    }
    return n;
}
