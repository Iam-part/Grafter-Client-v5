package com.grafters;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Finds specific valuable blocks in loaded chunks. Results are cached per chunk. */
public final class BlockEsp {
    private BlockEsp() {}

    public enum Target {
        RESPAWN_ANCHOR(0xB04CFF), ANCIENT_DEBRIS(0xC8782E), PIGLIN_HEAD(0xFFB6C1),
        NETHERITE_BLOCK(0xA89F91), DIAMOND_BLOCK(0x4AEDD9), DIAMOND_ORE(0x1E90FF),
        BEACON(0xFFFFFF), REDSTONE_BLOCK(0xFF2D2D);
        public final int rgb;
        Target(int rgb) { this.rgb = rgb; }
    }

    public record Found(BlockPos pos, Target type) {}

    public static final Map<ChunkPos, List<Found>> RESULTS = new HashMap<>();

    private static final ArrayDeque<ChunkPos> QUEUE = new ArrayDeque<>();
    private static final Set<ChunkPos> QUEUED = new HashSet<>();
    private static final Set<ChunkPos> DIRTY = ConcurrentHashMap.newKeySet();
    private static ClientLevel lastLevel;
    private static int timer;
    private static boolean forceRescan;
    private static final int MAX_PER_CHUNK = 400;

    public static void clear() {
        RESULTS.clear(); QUEUE.clear(); QUEUED.clear(); DIRTY.clear();
    }

    /** Called when the server changes a block, so that chunk is rescanned. */
    public static void onBlockChange(BlockPos pos) {
        if (!GraftersConfig.get().blockEsp) return;
        DIRTY.add(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
    }

    static Target classify(BlockState st) {
        Block b = st.getBlock();
        if (b == Blocks.RESPAWN_ANCHOR) return Target.RESPAWN_ANCHOR;
        if (b == Blocks.ANCIENT_DEBRIS) return Target.ANCIENT_DEBRIS;
        if (b == Blocks.PIGLIN_HEAD || b == Blocks.PIGLIN_WALL_HEAD) return Target.PIGLIN_HEAD;
        if (b == Blocks.NETHERITE_BLOCK) return Target.NETHERITE_BLOCK;
        if (b == Blocks.DIAMOND_BLOCK) return Target.DIAMOND_BLOCK;
        if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) return Target.DIAMOND_ORE;
        if (b == Blocks.BEACON) return Target.BEACON;
        if (b == Blocks.REDSTONE_BLOCK) return Target.REDSTONE_BLOCK;
        return null;
    }

    private static boolean interesting(BlockState st) { return classify(st) != null; }

    public static void tick(Minecraft mc) {
        GraftersConfig cfg = GraftersConfig.get();
        if (!cfg.blockEsp || mc.level == null || mc.player == null) return;

        if (mc.level != lastLevel) { clear(); lastLevel = mc.level; }
        timer++;
        int radius = Math.min(16, (cfg.blockRange >> 4) + 1);

        if (timer % 600 == 0) forceRescan = true;

        if (timer % 100 == 0)
            RESULTS.keySet().removeIf(k -> mc.level.getChunkSource().getChunkNow(k.x(), k.z()) == null);

        if (!DIRTY.isEmpty()) {
            for (ChunkPos k : DIRTY) {
                if (QUEUED.contains(k)) continue;
                if (mc.level.getChunkSource().getChunkNow(k.x(), k.z()) != null) {
                    QUEUE.addFirst(k);
                    QUEUED.add(k);
                }
            }
            DIRTY.clear();
        }

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

        for (int i = 0; i < 3 && !QUEUE.isEmpty(); i++) {
            ChunkPos key = QUEUE.poll();
            QUEUED.remove(key);
            LevelChunk ch = mc.level.getChunkSource().getChunkNow(key.x(), key.z());
            if (ch != null) RESULTS.put(key, scan(ch));
        }
    }

    private static List<Found> scan(LevelChunk ch) {
        List<Found> out = new ArrayList<>();
        LevelChunkSection[] sections = ch.getSections();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int sx = ch.getPos().getMinBlockX(), sz = ch.getPos().getMinBlockZ();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s == null || s.hasOnlyAir() || !s.maybeHas(BlockEsp::interesting)) continue;
            int baseY = ch.getSectionYFromSectionIndex(i) << 4;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        Target t = classify(s.getBlockState(x, y, z));
                        if (t == null) continue;
                        p.set(sx + x, baseY + y, sz + z);
                        out.add(new Found(p.immutable(), t));
                        if (out.size() >= MAX_PER_CHUNK) return out;
                    }
        }
        return out;
    }
}
