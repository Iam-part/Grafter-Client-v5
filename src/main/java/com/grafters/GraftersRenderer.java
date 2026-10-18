package com.grafters;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;
import java.util.Map;

/**
 * Through-wall outlines via vanilla Gizmos ("always on top").
 * Everything here is anchored to world coordinates, so nothing follows the player.
 */
public final class GraftersRenderer {
    private GraftersRenderer() {}

    private static final int GREEN = 0x00FF00;
    private static boolean warned;
    /** Where tracers start: just in front of the camera, so they appear to leave the crosshair. */
    private static Vec3 origin = Vec3.ZERO;

    public static void register() {
        LevelRenderEvents.BEFORE_GIZMOS.register(ctx -> render());
    }

    private static void render() {
        Minecraft mc = Minecraft.getInstance();
        GraftersConfig cfg = GraftersConfig.get();
        if (mc.level == null || mc.player == null) return;
        if (!(cfg.playerEsp || cfg.chestEsp || cfg.spawnerEsp || cfg.blockEsp || cfg.susChunks)) return;

        try {
            Entity ce = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            origin = ce.getEyePosition(pt).add(ce.getViewVector(pt).scale(0.5));

            if (cfg.playerEsp) drawPlayers(mc, cfg);
            if (cfg.chestEsp) drawChests(mc, cfg);
            if (cfg.spawnerEsp) drawSpawners(mc, cfg);
            if (cfg.blockEsp) drawBlocks(mc, cfg);
            if (cfg.susChunks) drawSus(cfg);
        } catch (IllegalStateException e) {
            if (!warned) { warned = true; e.printStackTrace(); }
        }
    }

    // ---------------- Player ESP / hitboxes ----------------
    private static void drawPlayers(Minecraft mc, GraftersConfig cfg) {
        double r2 = (double) cfg.espRange * cfg.espRange;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player || mc.player.distanceToSqr(p) > r2) continue;
            Vec3 at = p.getPosition(pt);
            AABB box = p.getDimensions(p.getPose()).makeBoundingBox(at);
            int col = p.isCrouching() ? 0x00FFFF : cfg.playerColor;
            box(box, col);
            if (cfg.playerTracers) tracer(box.getCenter(), col);

            if (cfg.espEyeLine) {
                double ey = at.y + p.getEyeHeight(p.getPose());
                Vec3 a = new Vec3(box.minX, ey, box.minZ), b = new Vec3(box.maxX, ey, box.minZ);
                Vec3 c = new Vec3(box.maxX, ey, box.maxZ), d = new Vec3(box.minX, ey, box.maxZ);
                line(a, b, 0xFF3030); line(b, c, 0xFF3030); line(c, d, 0xFF3030); line(d, a, 0xFF3030);
            }
        }
    }

    // ---------------- Chest / storage ESP ----------------
    private static void drawChests(Minecraft mc, GraftersConfig cfg) {
        boolean tr = cfg.chestTracers;
        eachBlockEntity(mc, cfg.chestRange, be -> {
            BlockPos pos = be.getBlockPos();
            if (be instanceof EnderChestBlockEntity) {
                esp(new AABB(pos), 0xAA00FF, tr);
            } else if (be instanceof ShulkerBoxBlockEntity) {
                esp(new AABB(pos), 0xFF55FF, tr);
            } else if (be instanceof HopperBlockEntity) {
                if (cfg.chestHoppers) esp(new AABB(pos), 0xFFA500, tr);
            } else if (be instanceof ChestBlockEntity) {
                BlockState st = be.getBlockState();
                if (!st.hasProperty(ChestBlock.TYPE)) return;
                ChestType t = st.getValue(ChestBlock.TYPE);
                if (t == ChestType.SINGLE) {
                    esp(new AABB(pos), GREEN, tr);
                } else if (t == ChestType.LEFT) { // draw double chest once, as one box
                    BlockPos other = pos.relative(ChestBlock.getConnectedDirection(st));
                    esp(new AABB(pos).minmax(new AABB(other)), 0xFFFF00, tr);
                }
            }
        });
    }

    // ---------------- Spawner ESP ----------------
    private static void drawSpawners(Minecraft mc, GraftersConfig cfg) {
        eachBlockEntity(mc, cfg.spawnerRange, be -> {
            BlockState st = be.getBlockState();
            BlockPos pos = be.getBlockPos();
            int rgb;
            if (cfg.spawnerMob && st.is(Blocks.SPAWNER)) rgb = 0xFF1744;
            else if (cfg.spawnerTrial && st.is(Blocks.TRIAL_SPAWNER)) rgb = 0xFFC400;
            else return;
            highlight(pos, rgb, 70);
            if (cfg.spawnerTracers) tracer(Vec3.atCenterOf(pos), rgb);
        });
    }

    // ---------------- Block ESP ----------------
    private static void drawBlocks(Minecraft mc, GraftersConfig cfg) {
        double r2 = (double) cfg.blockRange * cfg.blockRange;
        int alpha = Math.max(0, Math.min(255, cfg.blockAlpha));
        for (List<BlockEsp.Found> list : BlockEsp.RESULTS.values()) {
            for (BlockEsp.Found f : list) {
                if (!cfg.isOn(f.type())) continue;
                if (mc.player.distanceToSqr(Vec3.atCenterOf(f.pos())) > r2) continue;
                int rgb = f.type().rgb;
                Gizmos.cuboid(new AABB(f.pos()), GizmoStyle.strokeAndFill(argb(rgb), 2f, (alpha << 24) | rgb)).setAlwaysOnTop();
            }
        }
    }

    /** Runs action for every loaded block entity within range blocks of the player. */
    private static void eachBlockEntity(Minecraft mc, int range, Consumer<BlockEntity> action) {
        int rc = Math.min(mc.options.renderDistance().get(), (range >> 4) + 1);
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        double r2 = (double) range * range;
        for (int dx = -rc; dx <= rc; dx++) {
            for (int dz = -rc; dz <= rc; dz++) {
                LevelChunk ch = mc.level.getChunkSource().getChunkNow(pcx + dx, pcz + dz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (mc.player.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > r2) continue;
                    action.accept(be);
                }
            }
        }
    }

    // ---------------- SUS chunks ----------------
    private static void drawSus(GraftersConfig cfg) {
        long now = System.currentTimeMillis();
        SusChunks.Indicator[] types = SusChunks.Indicator.values();

        for (Map.Entry<ChunkPos, List<SusChunks.Hit>> e : SusChunks.RESULTS.entrySet()) {
            int[] counts = new int[types.length];
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            boolean any = false;

            for (SusChunks.Hit h : e.getValue()) {
                if (!cfg.isOn(h.type())) continue;
                any = true;
                counts[h.type().ordinal()]++;
                minY = Math.min(minY, h.pos().getY());
                maxY = Math.max(maxY, h.pos().getY());
                highlight(h.pos(), h.type().rgb, cfg.susAlpha);
            }
            if (!any) continue;

            int best = 0;
            for (int i = 1; i < counts.length; i++) if (counts[i] > counts[best]) best = i;
            column(e.getKey(), minY, maxY, types[best].rgb);
        }

        if (cfg.susBlocks)
            for (Map.Entry<ChunkPos, SusChunks.Activity> e : SusChunks.BLOCK_ACTIVITY.entrySet())
                if (now - e.getValue().time() < 120_000) slab(e.getKey(), e.getValue().y(), 0xFF3030);
        if (cfg.susPlayers)
            for (Map.Entry<ChunkPos, SusChunks.Activity> e : SusChunks.PLAYER_ACTIVITY.entrySet())
                if (now - e.getValue().time() < 120_000) slab(e.getKey(), e.getValue().y(), 0xFF8800);
    }

    /** Filled block with nested outlines, like a glowing marker. */
    private static void highlight(BlockPos pos, int rgb, int alpha) {
        AABB b = new AABB(pos);
        int fill = (Math.max(0, Math.min(255, alpha)) << 24) | rgb;
        Gizmos.cuboid(b, GizmoStyle.strokeAndFill(argb(rgb), 2f, fill)).setAlwaysOnTop();
        Gizmos.cuboid(b.inflate(0.03), GizmoStyle.stroke(argb(rgb))).setAlwaysOnTop();
        Gizmos.cuboid(b.inflate(0.06), GizmoStyle.stroke(argb(rgb))).setAlwaysOnTop();
    }

    /** A fixed 16x16 column around the flagged finds, with a ring every 16 blocks. */
    private static void column(ChunkPos cp, int minY, int maxY, int rgb) {
        int bottom = Math.floorDiv(minY - 32, 16) * 16;
        int top = (Math.floorDiv(maxY + 32, 16) + 1) * 16;
        double x1 = cp.getMinBlockX(), z1 = cp.getMinBlockZ(), x2 = x1 + 16, z2 = z1 + 16;
        Gizmos.cuboid(new AABB(x1, bottom, z1, x2, top, z2), GizmoStyle.stroke(argb(rgb))).setAlwaysOnTop();
        for (int y = bottom + 16; y < top; y += 16) ring(x1, y, z1, x2, z2, rgb);
    }

    /** A flat 16x16 square at a fixed height (recorded when the activity happened). */
    private static void slab(ChunkPos cp, int y, int rgb) {
        double x1 = cp.getMinBlockX(), z1 = cp.getMinBlockZ();
        ring(x1, y, z1, x1 + 16, z1 + 16, rgb);
    }

    private static void ring(double x1, double y, double z1, double x2, double z2, int rgb) {
        Vec3 a = new Vec3(x1, y, z1), b = new Vec3(x2, y, z1), c = new Vec3(x2, y, z2), d = new Vec3(x1, y, z2);
        line(a, b, rgb); line(b, c, rgb); line(c, d, rgb); line(d, a, rgb);
    }

    // ---------------- helpers ----------------
    private static int argb(int rgb) { return 0xFF000000 | rgb; }

    private static void box(AABB b, int rgb) {
        Gizmos.cuboid(b, GizmoStyle.stroke(argb(rgb))).setAlwaysOnTop();
    }

    /** Outline box, plus an optional tracer to its centre. */
    private static void esp(AABB b, int rgb, boolean tracer) {
        box(b, rgb);
        if (tracer) tracer(b.getCenter(), rgb);
    }

    private static void tracer(Vec3 to, int rgb) {
        line(origin, to, rgb);
    }

    private static void line(Vec3 a, Vec3 b, int rgb) {
        Gizmos.line(a, b, argb(rgb)).setAlwaysOnTop();
    }
}
