package com.grafters;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;

/**
 * Heuristic "activity" visualizer. Each indicator looks for something that
 * does NOT occur from world generation alone (e.g. amethyst growing outside a
 * geode, a crafted beehive, bamboo outside jungles), which suggests a player
 * has been there. Results are kept per chunk and stay fixed in the world.
 */
public final class SusChunks {
    private SusChunks() {}

    public enum Indicator {
        KELP(0x00E676), CAVE_VINES(0xFFB300), VINES(0x7CB342), AMETHYST(0xC447FF),
        BAMBOO(0xC6FF00), COCOA(0xA1887F), BEE_NEST(0xFFEA00);
        public final int rgb;
        Indicator(int rgb) { this.rgb = rgb; }
    }

    public record Hit(BlockPos pos, Indicator type) {}
    /** Where and when something happened. y is fixed, so overlays never follow the player. */
    public record Activity(long time, int y) {}

    public static final Map<ChunkPos, List<Hit>> RESULTS = new HashMap<>();
    public static final Map<ChunkPos, Activity> BLOCK_ACTIVITY = new HashMap<>();
    public static final Map<ChunkPos, Activity> PLAYER_ACTIVITY = new HashMap<>();

    private static final ArrayDeque<ChunkPos> QUEUE = new ArrayDeque<>();
    private static final Set<ChunkPos> QUEUED = new HashSet<>();
    private static ClientLevel lastLevel;
    private static int timer;
    private static boolean forceRescan;
    private static final int MAX_HITS_PER_CHUNK = 96;

    public static void clear() {
        RESULTS.clear(); BLOCK_ACTIVITY.clear(); PLAYER_ACTIVITY.clear();
        QUEUE.clear(); QUEUED.clear();
    }

    /** Called from ClientPacketListenerMixin when the server changes a block. */
    public static void markBlockActivity(BlockPos pos) {
        GraftersConfig cfg = GraftersConfig.get();
        if (!cfg.susChunks || !cfg.susBlocks) return;
        BLOCK_ACTIVITY.put(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4),
                new Activity(System.currentTimeMillis(), pos.getY()));
    }

    public static void tick(Minecraft mc) {
        GraftersConfig cfg = GraftersConfig.get();
        if (!cfg.susChunks || mc.level == null || mc.player == null) return;

        if (mc.level != lastLevel) { clear(); lastLevel = mc.level; }
        timer++;
        int radius = cfg.susRadius;

        // other players -> activity (recorded at the height they were seen)
        if (cfg.susPlayers && timer % 10 == 0) {
            long now = System.currentTimeMillis();
            for (AbstractClientPlayer p : mc.level.players()) {
                if (p == mc.player) continue;
                PLAYER_ACTIVITY.put(new ChunkPos(p.getBlockX() >> 4, p.getBlockZ() >> 4),
                        new Activity(now, p.getBlockY()));
            }
        }

        // periodic refresh: results are replaced in place, never cleared (no flicker)
        if (timer % 1200 == 0) forceRescan = true;

        // forget chunks that unloaded
        if (timer % 100 == 0)
            RESULTS.keySet().removeIf(k -> mc.level.getChunkSource().getChunkNow(k.x(), k.z()) == null);

        if (timer % 20 == 0) {
            int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int cx = pcx + dx, cz = pcz + dz;
                    ChunkPos key = new ChunkPos(cx, cz);
                    if (QUEUED.contains(key)) continue;
                    if (!forceRescan && RESULTS.containsKey(key)) continue;
                    if (mc.level.getChunkSource().getChunkNow(cx, cz) != null) {
                        QUEUE.add(key);
                        QUEUED.add(key);
                    }
                }
            }
            forceRescan = false;
        }

        for (int i = 0; i < 2 && !QUEUE.isEmpty(); i++) {
            ChunkPos key = QUEUE.poll();
            QUEUED.remove(key);
            LevelChunk ch = mc.level.getChunkSource().getChunkNow(key.x(), key.z());
            if (ch != null) RESULTS.put(key, scan(mc.level, ch));
        }
    }

    private static boolean isAmethyst(Block b) {
        return b == Blocks.AMETHYST_CLUSTER || b == Blocks.LARGE_AMETHYST_BUD
                || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.SMALL_AMETHYST_BUD;
    }

    private static boolean interesting(BlockState st) {
        Block b = st.getBlock();
        return b == Blocks.KELP || b == Blocks.CAVE_VINES || b == Blocks.VINE
                || isAmethyst(b) || b == Blocks.BAMBOO || b == Blocks.COCOA
                || b == Blocks.BEE_NEST || b == Blocks.BEEHIVE;
    }

    private static List<Hit> scan(ClientLevel w, LevelChunk ch) {
        List<Hit> hits = new ArrayList<>();
        LevelChunkSection[] sections = ch.getSections();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int sx = ch.getPos().getMinBlockX(), sz = ch.getPos().getMinBlockZ();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir() || !s.maybeHas(SusChunks::interesting)) continue;
            int baseY = ch.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        BlockState st = s.getBlockState(x, y, z);
                        if (!interesting(st)) continue;
                        p.set(sx + x, baseY + y, sz + z);
                        Indicator ind = classify(w, p, st);
                        if (ind != null) {
                            hits.add(new Hit(p.immutable(), ind));
                            if (hits.size() >= MAX_HITS_PER_CHUNK) return hits;
                        }
                    }
        }
        return hits;
    }

    private static Indicator classify(ClientLevel w, BlockPos p, BlockState st) {
        Block b = st.getBlock();

        if (b == Blocks.KELP)        // worldgen never produces max-age (25) kelp heads
            return st.getValue(BlockStateProperties.AGE_25) == 25 ? Indicator.KELP : null;

        if (b == Blocks.CAVE_VINES)  // same idea for cave vine heads
            return st.getValue(BlockStateProperties.AGE_25) == 25 ? Indicator.CAVE_VINES : null;

        if (b == Blocks.VINE) {      // unusually long hanging columns (end of column only)
            if (w.getBlockState(p.below()).is(Blocks.VINE)) return null;
            int len = 0;
            BlockPos.MutableBlockPos q = p.mutable();
            while (len < 12 && w.getBlockState(q).is(Blocks.VINE)) { len++; q.move(0, 1, 0); }
            return len >= 9 ? Indicator.VINES : null;
        }

        if (isAmethyst(b)) {         // natural crystals sit inside a smooth-basalt geode shell
            for (BlockPos n : BlockPos.betweenClosed(p.offset(-6, -6, -6), p.offset(6, 6, 6)))
                if (w.getBlockState(n).is(Blocks.SMOOTH_BASALT)) return null;
            return Indicator.AMETHYST;
        }

        if (b == Blocks.BAMBOO) {    // naturally only in jungles; flag stalk base only
            if (w.getBlockState(p.below()).is(Blocks.BAMBOO)) return null;
            return w.getBiome(p).is(BiomeTags.IS_JUNGLE) ? null : Indicator.BAMBOO;
        }

        if (b == Blocks.COCOA)
            return w.getBiome(p).is(BiomeTags.IS_JUNGLE) ? null : Indicator.COCOA;

        if (b == Blocks.BEEHIVE)     // craftable only -> never generated
            return Indicator.BEE_NEST;
        if (b == Blocks.BEE_NEST)    // full honey = has been sitting loaded with bees
            return st.getValue(BeehiveBlock.HONEY_LEVEL) == 5 ? Indicator.BEE_NEST : null;

        return null;
    }
}
