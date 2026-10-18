package com.grafters.mods;

import com.grafters.Freecam;
import com.grafters.GraftersConfig;
import com.grafters.GraftersState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Random;

/**
 * Builds the schematic you have loaded in Litematica: finds blocks that are missing, turns
 * toward them smoothly (with a little randomness), places them from your hotbar, and walks
 * toward targets that are out of reach. Remembers the last block it placed, writes it to
 * grafters_last_block.txt and, if you set a Discord webhook, posts it when you disconnect.
 */
public final class SchematicBuilder {
    private SchematicBuilder() {}

    private record Plan(BlockPos target, BlockPos clicked, Direction side, Vec3 hit, Float forcedYaw, BlockState want) {}
    private record LastPlacement(BlockPos pos, String block, String dimension, String server) {}

    private static final Random RNG = new Random();
    private static final String[] NO_CLICK = {"door", "button", "lever", "gate", "chest", "barrel", "shulker",
            "furnace", "table", "anvil", "bed", "hopper", "dispenser", "dropper", "loom", "stonecutter",
            "grindstone", "smithing", "brewing", "enchanting", "beacon", "note_block", "jukebox", "lectern",
            "repeater", "comparator", "sign", "bell", "cake", "composter", "cauldron"};

    private static Plan plan;
    private static float jitterYaw, jitterPitch;
    private static int rescan, cooldown, aligned, missingMsg;
    private static boolean walking, warnedMissingMod, warnedNoSchematic;
    private static LastPlacement last;
    private static boolean reported = true;

    public static void reset(Minecraft mc) {
        plan = null; aligned = 0; cooldown = 0;
        warnedMissingMod = false; warnedNoSchematic = false;
        stopWalking(mc);
    }

    // ------------------------------------------------------------------
    //  Per-tick logic
    // ------------------------------------------------------------------
    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        if (GraftersState.screen(mc) != null || Freecam.isActive()) { stopWalking(mc); return; }

        if (!LitematicaBridge.installed()) {
            stopWalking(mc);
            if (!warnedMissingMod) { warnedMissingMod = true; Util.chat("Schematic Builder needs the Litematica mod installed."); }
            return;
        }
        BlockGetter sw = LitematicaBridge.world();
        if (sw == null || !LitematicaBridge.hasPlacements()) {
            stopWalking(mc);
            if (!warnedNoSchematic) { warnedNoSchematic = true; Util.chat("No schematic found: load and place one in Litematica first."); }
            return;
        }
        warnedNoSchematic = false;

        if (cooldown > 0) cooldown--;
        if (plan == null || --rescan <= 0) {
            rescan = 4;
            Plan fresh = findPlan(mc, p, sw, c);
            if (fresh != plan) { plan = fresh; aligned = 0; if (fresh != null) newJitter(); }
        }
        if (plan == null) { stopWalking(mc); return; }

        if (!stillNeeded(plan.want(), mc.level.getBlockState(plan.target()))) { plan = null; aligned = 0; return; }

        Item item = plan.want().getBlock().asItem();
        int slot = findHotbar(p, item);
        if (slot < 0) {
            stopWalking(mc);
            if (missingMsg-- <= 0) {
                Util.chat("Missing " + plan.want().getBlock().getName().getString() + " in hotbar. Put it there and the bot continues.");
                missingMsg = 200;
            }
            return;
        }

        Vec3 eye = p.getEyePosition();
        if (eye.distanceTo(plan.hit()) > c.sbReach) {
            if (c.sbWalk) walkToward(mc, p, plan.hit(), c); else stopWalking(mc);
            aligned = 0;
            return;
        }
        stopWalking(mc);

        // turn toward the target (stairs etc. need an exact horizontal facing)
        float[] ang = angles(eye, plan.hit());
        float ty = (plan.forcedYaw() != null ? plan.forcedYaw() : ang[0]) + jitterYaw;
        float tp = ang[1] + jitterPitch;
        if (!rotateToward(p, ty, tp, c)) { aligned = 0; return; }
        aligned++;
        // wait a tick so the server has our new rotation before the placement arrives
        if (aligned < 2 || cooldown > 0) return;

        p.getInventory().setSelectedSlot(slot);
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(plan.hit(), plan.side(), plan.clicked(), false));
        p.swing(InteractionHand.MAIN_HAND);
        record(mc, plan);

        cooldown = c.sbDelay + (c.sbJitter ? RNG.nextInt(4) : 0);
        plan = null;
        aligned = 0;
    }

    private static void newJitter() {
        boolean j = GraftersConfig.get().sbJitter;
        jitterYaw = j ? (RNG.nextFloat() - 0.5f) * 1.6f : 0f;
        jitterPitch = j ? (RNG.nextFloat() - 0.5f) * 1.6f : 0f;
    }

    // ------------------------------------------------------------------
    //  Finding something to place
    // ------------------------------------------------------------------
    private static boolean stillNeeded(BlockState want, BlockState have) {
        if (want.isAir()) return false;
        if (want.getBlock() instanceof SlabBlock && have.getBlock() == want.getBlock()
                && want.hasProperty(BlockStateProperties.SLAB_TYPE) && have.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            return want.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE
                    && have.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE;
        }
        return have.isAir() || (have.canBeReplaced() && have.getBlock() != want.getBlock());
    }

    private static Plan findPlan(Minecraft mc, LocalPlayer p, BlockGetter sw, GraftersConfig c) {
        BlockPos base = p.blockPosition();
        int r = c.sbScan;
        Vec3 eye = p.getEyePosition();
        AABB me = p.getBoundingBox();
        Plan best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    BlockState want = sw.getBlockState(m);
                    if (want.isAir() || want.getBlock() instanceof LiquidBlock) continue;
                    BlockState have = mc.level.getBlockState(m);
                    if (!stillNeeded(want, have)) continue;
                    if (want.getBlock().asItem().getDefaultInstance().isEmpty()) continue;
                    if (me.intersects(new AABB(m))) continue;               // don't wall yourself in

                    Plan pl = planFor(mc, want, have, m.immutable(), eye, c);
                    if (pl == null) continue;
                    double d = eye.distanceToSqr(pl.hit());
                    if (!c.sbWalk && Math.sqrt(d) > c.sbReach + 0.5) continue;
                    double score = (c.sbBottomUp ? pl.target().getY() * 1000.0 : 0.0) + d;
                    if (score < bestScore) { bestScore = score; best = pl; }
                }
        return best;
    }

    private static Plan planFor(Minecraft mc, BlockState want, BlockState have, BlockPos pos, Vec3 eye, GraftersConfig c) {
        Block b = want.getBlock();
        boolean stair = b instanceof StairBlock, slab = b instanceof SlabBlock;

        // finishing a double slab: click the slab that is already there
        if (slab && have.getBlock() == b && have.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            Direction side = have.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.BOTTOM ? Direction.UP : Direction.DOWN;
            Vec3 hit = Vec3.atCenterOf(pos).add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);
            return new Plan(pos, pos, side, hit, null, want);
        }

        Boolean wantTop = null;
        if (stair && want.hasProperty(BlockStateProperties.HALF))
            wantTop = want.getValue(BlockStateProperties.HALF) == Half.TOP;
        if (slab && want.hasProperty(BlockStateProperties.SLAB_TYPE)) {
            SlabType t = want.getValue(BlockStateProperties.SLAB_TYPE);
            if (t == SlabType.TOP) wantTop = true; else if (t == SlabType.BOTTOM) wantTop = false;
        }
        Float forcedYaw = null;
        if (want.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction f = want.getValue(BlockStateProperties.HORIZONTAL_FACING);
            forcedYaw = stair ? f.toYRot() : (slab ? null : f.getOpposite().toYRot());
        }
        Direction.Axis axis = want.hasProperty(BlockStateProperties.AXIS) ? want.getValue(BlockStateProperties.AXIS) : null;

        Plan best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            BlockPos nb = pos.relative(d);
            BlockState ns = mc.level.getBlockState(nb);
            if (ns.isAir() || ns.canBeReplaced() || !ns.getFluidState().isEmpty()) continue;
            if (!clickable(mc, nb, ns)) continue;

            Direction side = d.getOpposite();                 // face of the neighbour we click
            if (wantTop != null) {
                if (side == Direction.UP && wantTop) continue;    // clicking a top face gives a bottom half
                if (side == Direction.DOWN && !wantTop) continue;
            }
            double hx = nb.getX() + 0.5 + side.getStepX() * 0.5;
            double hy = nb.getY() + 0.5 + side.getStepY() * 0.5;
            double hz = nb.getZ() + 0.5 + side.getStepZ() * 0.5;
            if (c.sbJitter) {                                  // land somewhere on the face, not dead centre
                if (side.getStepX() == 0) hx += (RNG.nextDouble() - 0.5) * 0.4;
                if (side.getStepZ() == 0) hz += (RNG.nextDouble() - 0.5) * 0.4;
                if (side.getStepY() == 0 && wantTop == null) hy += (RNG.nextDouble() - 0.5) * 0.4;
            }
            if (wantTop != null && side.getAxis().isHorizontal()) hy = nb.getY() + (wantTop ? 0.75 : 0.25);
            Vec3 hit = new Vec3(hx, hy, hz);

            double dist = eye.distanceTo(hit) + (axis != null && side.getAxis() != axis ? 100 : 0);
            if (dist < bestDist) { bestDist = dist; best = new Plan(pos, nb, side, hit, forcedYaw, want); }
        }
        return best;
    }

    /** Don't click things that would open a menu or toggle instead of placing. */
    private static boolean clickable(Minecraft mc, BlockPos pos, BlockState st) {
        if (mc.level.getBlockEntity(pos) != null) return false;
        String id = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
        for (String s : NO_CLICK) if (id.contains(s)) return false;
        return true;
    }

    private static int findHotbar(LocalPlayer p, Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    // ------------------------------------------------------------------
    //  Looking and walking
    // ------------------------------------------------------------------
    private static float[] angles(Vec3 eye, Vec3 target) {
        Vec3 d = target.subtract(eye);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, flat));
        return new float[]{yaw, pitch};
    }

    /** Turns a limited, slightly randomised amount per tick. Returns true once close enough. */
    private static boolean rotateToward(LocalPlayer p, float ty, float tp, GraftersConfig c) {
        float yaw = p.getYRot(), pitch = p.getXRot();
        float dy = Mth.wrapDegrees(ty - yaw), dp = tp - pitch;
        float max = c.sbRotSpeed * (c.sbJitter ? 0.7f + RNG.nextFloat() * 0.6f : 1f);
        float sy = Mth.clamp(dy, -max, max), sp = Mth.clamp(dp, -max * 0.7f, max * 0.7f);
        p.setYRot(yaw + sy);
        p.setXRot(Mth.clamp(pitch + sp, -90f, 90f));
        return Math.abs(dy - sy) < 2.5f && Math.abs(dp - sp) < 2.5f;
    }

    private static void walkToward(Minecraft mc, LocalPlayer p, Vec3 target, GraftersConfig c) {
        Vec3 eye = p.getEyePosition();
        float ty = (float) Math.toDegrees(Math.atan2(-(target.x - eye.x), target.z - eye.z));
        float yaw = p.getYRot();
        float dy = Mth.wrapDegrees(ty - yaw);
        p.setYRot(yaw + Mth.clamp(dy, -c.sbRotSpeed, c.sbRotSpeed));
        mc.options.keyUp.setDown(Math.abs(dy) < 40f);
        mc.options.keyJump.setDown(p.horizontalCollision);
        walking = true;
    }

    private static void stopWalking(Minecraft mc) {
        if (!walking) return;
        walking = false;
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        KeyMapping.setAll();      // give keys back to whatever you are physically holding
    }

    // ------------------------------------------------------------------
    //  Last placed block: file + optional Discord webhook
    // ------------------------------------------------------------------
    private static void record(Minecraft mc, Plan pl) {
        ServerData sd = mc.getCurrentServer();
        String server = sd != null ? sd.ip : "singleplayer";
        last = new LastPlacement(pl.target(), BuiltInRegistries.BLOCK.getKey(pl.want().getBlock()).getPath(),
                dimensionName(mc), server);
        reported = false;
    }

    private static String dimensionName(Minecraft mc) {
        String s = String.valueOf(mc.level.dimension());      // ResourceKey[minecraft:dimension / minecraft:overworld]
        int i = s.lastIndexOf('/');
        if (i >= 0) s = s.substring(i + 1).replace("]", "").trim();
        return s;
    }

    /** Called when you disconnect or the game closes. */
    public static void reportLast() {
        if (last == null || reported) return;
        reported = true;
        LastPlacement l = last;
        String text = "Last block placed at X " + l.pos().getX() + ", Y " + l.pos().getY() + ", Z " + l.pos().getZ()
                + " (" + l.block() + ") in " + l.dimension() + " on " + l.server();

        try {
            Files.writeString(FabricLoader.getInstance().getGameDir().resolve("grafters_last_block.txt"),
                    text + System.lineSeparator());
        } catch (Exception e) {
            System.err.println("[Grafters] Could not write last-block file: " + e.getMessage());
        }

        String url = GraftersConfig.get().webhookUrl;
        if (url == null || url.isBlank()) return;
        try {
            String json = "{\"content\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.err.println("[Grafters] Webhook failed: " + e.getMessage());
        }
    }
}
