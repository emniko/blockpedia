/*
 * Extra map layers on top of cubiomes (Java Edition, 1.18+): point features with Y levels,
 * structure variants, ravine footprints and approximate terrain height.
 */
#include "seedmap_internal.h"

#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "cubiomes/carver.h"
#include "cubiomes/features/end_city.h"
#include "cubiomes/features/ore.h"
#include "cubiomes/terrainnoise.h"

/* ------------------------------------------------------------------ lazy state */

static OreVeinParameters *ore_veins(SeedMap *sm)
{
    if (!sm->oreVeins) {
        sm->oreVeins = (OreVeinParameters *) calloc(1, sizeof(OreVeinParameters));
        if (sm->oreVeins) initOreVeinNoise(sm->oreVeins, sm->seed, sm->mc);
    }
    return sm->oreVeins;
}

static SurfaceNoise *surface_noise(SeedMap *sm)
{
    if (!sm->surface) {
        sm->surface = (SurfaceNoise *) calloc(1, sizeof(SurfaceNoise));
        if (sm->surface) initSurfaceNoise(sm->surface, sm->dim, sm->seed);
    }
    return sm->surface;
}

static TerrainNoise *terrain(SeedMap *sm)
{
    if (!sm->terrain) {
        sm->terrain = (TerrainNoise *) calloc(1, sizeof(TerrainNoise));
        if (sm->terrain) {
            setupTerrainNoise(sm->terrain, sm->mc, 0);
            initTerrainNoise(sm->terrain, sm->seed, sm->dim);
        }
    }
    return sm->terrain;
}

void sm_free_layers(SeedMap *sm)
{
    free(sm->oreVeins);
    free(sm->surface);
    free(sm->terrain);
}

/* ------------------------------------------------------------------ point layers */

typedef struct { int *out; int max; int n; } Sink;

static int put(Sink *s, int x, int y, int z, int info)
{
    if (s->n >= s->max) return 0;
    s->out[4 * s->n] = x;
    s->out[4 * s->n + 1] = y;
    s->out[4 * s->n + 2] = z;
    s->out[4 * s->n + 3] = info;
    s->n++;
    return 1;
}

/* Per-chunk features from cubiomes' structure finder (geodes, desert wells, end gateways). */
static void chunk_features(SeedMap *sm, int stype, int cx0, int cz0, int cx1, int cz1, Sink *s)
{
    StructureConfig sc;
    if (!getStructureConfig(stype, sm->mc, &sc)) return;
    for (int cz = cz0; cz <= cz1; cz++) {
        for (int cx = cx0; cx <= cx1; cx++) {
            Pos p;
            if (!getStructurePos(stype, sm->mc, sm->seed, cx, cz, &p)) continue;
            if (!isViableStructurePos(stype, &sm->g, p.x, p.z, 0)) continue;
            int y = INT_MIN, info = 0;
            if (stype == Geode) {
                StructureVariant sv;
                memset(&sv, 0, sizeof sv);
                if (getVariant(&sv, Geode, sm->mc, sm->seed, p.x, p.z, -1)) {
                    y = sv.y;
                    info = sv.size | (sv.cracked ? 0x100 : 0);
                }
            }
            if (!put(s, p.x, y, p.z, info)) return;
        }
    }
}

/* Copper or iron ore veins: sample a few columns per chunk through the vein's height range and
 * report the first ore block found. info = 1 when it is a raw ore block. */
static void ore_vein_points(SeedMap *sm, int iron, int cx0, int cz0, int cx1, int cz1, Sink *s)
{
    OreVeinParameters *ov = ore_veins(sm);
    if (!ov) return;
    const int minY = iron ? -60 : 0, maxY = iron ? -8 : 50;
    const int ore = iron ? IRON_ORE : COPPER_ORE, raw = iron ? RAW_IRON_BLOCK : RAW_COPPER_BLOCK;
    static const int offs[4][2] = { {3, 3}, {11, 3}, {3, 11}, {11, 11} };
    for (int cz = cz0; cz <= cz1; cz++) {
        for (int cx = cx0; cx <= cx1; cx++) {
            int found = 0;
            for (int k = 0; k < 4 && !found; k++) {
                int x = cx * 16 + offs[k][0], z = cz * 16 + offs[k][1];
                for (int y = minY; y <= maxY && !found; y += 3) {
                    int b = getOreVeinBlockAt(x, y, z, ov);
                    if (b == ore || b == raw) {
                        found = 1;
                        if (!put(s, x, y, z, b == raw)) return;
                    }
                }
            }
        }
    }
}

/* Ore blocks from decoration (diamonds, ancient debris): every block position, info = ore type.
 * The game later removes some ore that touches air (caves), which isn't modelled here. */
static void ore_points(SeedMap *sm, const int *types, int ntypes, int cx0, int cz0, int cx1, int cz1, Sink *s)
{
    SurfaceNoise *sn = surface_noise(sm);
    if (!sn) return;
    for (int cz = cz0; cz <= cz1; cz++) {
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int t = 0; t < ntypes; t++) {
                OreConfig oc;
                int biome = getBiomeForOreGen(&sm->g, cx, cz, 0);
                if (!getOreConfig(types[t], sm->mc, biome, &oc)) continue;
                if (oc.dim != sm->dim) continue;
                Pos3List list = generateOres(&sm->g, sn, oc, cx, cz);
                int full = 0;
                for (int i = 0; i < list.size && !full; i++)
                    full = !put(s, list.pos3s[i].x, list.pos3s[i].y, list.pos3s[i].z, types[t]);
                freePos3List(&list);
                if (full) return;
            }
        }
    }
}

int sm_points(SeedMap *sm, int layer, int x0, int z0, int x1, int z1, int *out, int maxItems)
{
    Sink s = { out, maxItems, 0 };
    if (sm->edition != SM_JAVA) return -1;
    int cx0 = floordiv(x0, 16), cz0 = floordiv(z0, 16);
    int cx1 = floordiv(x1 - 1, 16), cz1 = floordiv(z1 - 1, 16);
    switch (layer) {
    case SMP_GEODE:
        if (sm->dim != DIM_OVERWORLD) return -1;
        chunk_features(sm, Geode, cx0, cz0, cx1, cz1, &s);
        break;
    case SMP_DESERT_WELL:
        if (sm->dim != DIM_OVERWORLD) return -1;
        chunk_features(sm, Desert_Well, cx0, cz0, cx1, cz1, &s);
        break;
    case SMP_END_GATEWAY:
        if (sm->dim != DIM_END) return -1;
        chunk_features(sm, End_Gateway, cx0, cz0, cx1, cz1, &s);
        break;
    case SMP_COPPER_VEIN:
    case SMP_IRON_VEIN:
        if (sm->dim != DIM_OVERWORLD) return -1;
        ore_vein_points(sm, layer == SMP_IRON_VEIN, cx0, cz0, cx1, cz1, &s);
        break;
    case SMP_DIAMOND: {
        if (sm->dim != DIM_OVERWORLD) return -1;
        static const int diamonds[] = { DiamondOre, MediumDiamondOre, LargeDiamondOre, BuriedDiamondOre };
        ore_points(sm, diamonds, 4, cx0, cz0, cx1, cz1, &s);
        break;
    }
    case SMP_DEBRIS: {
        if (sm->dim != DIM_NETHER) return -1;
        static const int debris[] = { LargeDebrisOre, SmallDebrisOre };
        ore_points(sm, debris, 2, cx0, cz0, cx1, cz1, &s);
        break;
    }
    default:
        return -1;
    }
    return s.n;
}

/* ------------------------------------------------------------------ variants */

int sm_variant(SeedMap *sm, int stype, int x, int z, int *yOut)
{
    *yOut = INT_MIN;
    if (sm->edition != SM_JAVA) return 0;
    int flags = 0;
    if (stype == End_City) {
        Piece *pieces = (Piece *) calloc(END_CITY_PIECES_MAX, sizeof(Piece));
        if (!pieces) return 0;
        int n = getEndCityPieces(pieces, sm->seed, x >> 4, z >> 4);
        for (int i = 0; i < n; i++) if (pieces[i].type == END_SHIP) flags |= SMV_END_SHIP;
        free(pieces);
        return flags;
    }
    if (stype != Village && stype != Ruined_Portal && stype != Ruined_Portal_N && stype != Igloo) return 0;
    int biome = getBiomeAt(&sm->g, 4, x >> 2, 319 >> 2, z >> 2);
    StructureVariant sv;
    memset(&sv, 0, sizeof sv);
    if (!getVariant(&sv, stype, sm->mc, sm->seed, x, z, biome)) return 0;
    if (sv.abandoned) flags |= SMV_ZOMBIE_VILLAGE;
    if (sv.giant) flags |= SMV_GIANT_PORTAL;
    if (sv.underground) flags |= SMV_UNDERGROUND;
    if (sv.basement) flags |= SMV_BASEMENT;
    return flags;
}

/* ------------------------------------------------------------------ ravines */

int sm_ravines(SeedMap *sm, int chunkX0, int chunkZ0, int chunksW, int chunksH, short *out)
{
    const int W = chunksW * 16, H = chunksH * 16;
    for (int i = 0; i < W * H; i++) out[i] = SM_NONE_Y;
    if (sm->edition != SM_JAVA || sm->dim != DIM_OVERWORLD) return -1;
    CanyonCarverConfig ccc;
    if (!getCanyonCarverConfig(CANYON_CARVER, sm->mc, &ccc)) return -1;
    static int biomes[17][17]; /* unused by the canyon carver since 1.18 */
    int found = 0;
    for (int dz = 0; dz < chunksH; dz++) {
        for (int dx = 0; dx < chunksW; dx++) {
            int cx = chunkX0 + dx, cz = chunkZ0 + dz;
            Pos3List list;
            createPos3List(&list, 1024);
            carveCanyon(sm->seed, sm->mc, cx, cz, ccc, CANYON_CARVER, biomes, &list);
            for (int i = 0; i < list.size; i++) {
                int lx = list.pos3s[i].x - chunkX0 * 16;
                int lz = list.pos3s[i].z - chunkZ0 * 16;
                if (lx < 0 || lz < 0 || lx >= W || lz >= H) continue;
                short y = (short) list.pos3s[i].y;
                short *cell = &out[lz * W + lx];
                if (y < *cell) *cell = y;
                found = 1;
            }
            freePos3List(&list);
        }
    }
    return found;
}

/* ------------------------------------------------------------------ terrain */

int sm_heights(SeedMap *sm, int scale, int x, int z, int w, int h, int *out)
{
    if (sm->dim != DIM_OVERWORLD || sm->mc < MC_1_18) return 1;
    TerrainNoise *tn = terrain(sm);
    if (!tn) return 1;
    for (int j = 0; j < h; j++)
        for (int i = 0; i < w; i++)
            out[j * w + i] = samplePreliminarySurfaceLevel(tn, (x + i) * scale + scale / 2, (z + j) * scale + scale / 2);
    return 0;
}

/* ------------------------------------------------------------------ chest loot */

#include "cubiomes/loot/loot_table_context.h"
#include "cubiomes/loot/loot_tables.h"
#include "cubiomes/loot/mc_loot.h"

#include <pthread.h>

#define MAX_PIECES 1024

/* The library's loot tables are static singletons that hold their own random state and output,
 * so loot generation is serialized across threads. Contexts must not be freed. */
static pthread_mutex_t loot_lock = PTHREAD_MUTEX_INITIALIZER;

/* Visits every chest of a structure: callback gets (chest index, loot context after generation).
 * Returns the number of chests, 0 when the structure has no supported loot. */
static int each_chest(SeedMap *sm, int stype, int x, int z,
                      void (*fn)(int chest, LootTableContext *ctx, void *arg), void *arg)
{
    if (sm->edition != SM_JAVA) return 0;
    int biome = getBiomeAt(&sm->g, 4, x >> 2, 319 >> 2, z >> 2);
    StructureSaltConfig ssc;
    if (!getStructureSaltConfig(stype, sm->mc, biome, &ssc)) return 0;
    StructureVariant sv;
    memset(&sv, 0, sizeof sv);
    int hasVariant = getVariant(&sv, stype, sm->mc, sm->seed, x, z, biome);
    Piece *pieces = (Piece *) calloc(MAX_PIECES, sizeof(Piece));
    if (!pieces) return 0;
    int n = getStructurePieces(pieces, MAX_PIECES, stype, ssc, hasVariant ? &sv : NULL, sm->mc, sm->seed, x, z);
    int chests = 0;
    pthread_mutex_lock(&loot_lock);
    for (int i = 0; i < n && i < MAX_PIECES; i++) {
        for (int c = 0; c < pieces[i].chestCount && c < 4; c++) {
            const char *table = pieces[i].lootTables[c];
            if (!table) continue;
            LootTableContext *ctx = NULL;
            if (!init_loot_table_name(&ctx, table, sm->mc) || !ctx) continue;
            set_loot_seed(ctx, pieces[i].lootSeeds[c]);
            generate_loot(ctx);
            fn(chests, ctx, arg);
            chests++;
        }
    }
    pthread_mutex_unlock(&loot_lock);
    free(pieces);
    return chests;
}

typedef struct { char *out; int len; int used; } TextOut;

static void append_items(int chest, LootTableContext *ctx, void *arg)
{
    TextOut *t = (TextOut *) arg;
    for (int i = 0; i < ctx->generated_item_count; i++) {
        const char *name = get_item_name(ctx, ctx->generated_items[i].item);
        if (!name) continue;
        int w = snprintf(t->out + t->used, (size_t) (t->len - t->used), "%d\t%s\t%d\n",
                         chest, name, ctx->generated_items[i].count);
        if (w < 0 || w >= t->len - t->used) return;
        t->used += w;
    }
}

int sm_loot(SeedMap *sm, int stype, int x, int z, char *out, int outLen)
{
    TextOut t = { out, outLen, 0 };
    if (outLen > 0) out[0] = 0;
    return each_chest(sm, stype, x, z, append_items, &t);
}

typedef struct { const char *item; int count; } ItemCount;

static void count_item(int chest, LootTableContext *ctx, void *arg)
{
    (void) chest;
    ItemCount *ic = (ItemCount *) arg;
    for (int i = 0; i < ctx->generated_item_count; i++) {
        const char *name = get_item_name(ctx, ctx->generated_items[i].item);
        if (!name) continue;
        const char *bare = strchr(name, ':') ? strchr(name, ':') + 1 : name;
        if (strcmp(bare, ic->item) == 0) ic->count += ctx->generated_items[i].count;
    }
}

int sm_loot_count(SeedMap *sm, int stype, int x, int z, const char *item)
{
    ItemCount ic = { item, 0 };
    each_chest(sm, stype, x, z, count_item, &ic);
    return ic.count;
}
