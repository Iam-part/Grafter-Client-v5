package com.grafters.mods;

import com.grafters.GraftersConfig;
import com.grafters.GraftersState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Runs your RTP command, waits for the teleport and chunk loading, then counts storage
 * blocks (and optionally spawners) in the loaded chunks. Enough of them = probably a base:
 * it stops, tells you the coordinates and copies them. Otherwise it RTPs again.
 */
public final class RtpBaseFinder {
    private RtpBaseFinder() {}

    private enum S { IDLE, WAIT_TP, SETTLE }

    private static S state = S.IDLE;
    private static int timer, wait;
    private static Vec3 start = Vec3.ZERO;

    public static void reset() { state = S.IDLE; timer = 0; wait = 0; }

    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        if (timer > 0) { timer--; return; }

        switch (state) {
            case IDLE -> {
                if (GraftersState.screen(mc) != null) return;
                start = p.position();
                Util.command(c.rtpCommand);
                state = S.WAIT_TP; wait = 0;
            }
            case WAIT_TP -> {
                if (GraftersState.screen(mc) != null) return;   // the server may show a menu you have to click
                if (p.position().distanceTo(start) > 64) { state = S.SETTLE; wait = 0; }
                else if (++wait > 400) { state = S.IDLE; timer = c.rtpDelay; }
            }
            case SETTLE -> {
                if (++wait < 100) return;                        // let chunks load
                int[] r = scan(mc, p, c);
                int storage = r[0], spawners = r[1];
                if (storage >= c.rtpMinStorage || (c.rtpSpawners && spawners > 0)) {
                    found(mc, p, storage, spawners);
                } else {
                    state = S.IDLE; timer = c.rtpDelay;
                }
            }
        }
    }

    private static int[] scan(Minecraft mc, LocalPlayer p, GraftersConfig c) {
        int storage = 0, spawners = 0;
        int rc = Math.min(mc.options.renderDistance().get(), 12);
        int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;
        for (int dx = -rc; dx <= rc; dx++) {
            for (int dz = -rc; dz <= rc; dz++) {
                LevelChunk ch = mc.level.getChunkSource().getChunkNow(pcx + dx, pcz + dz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (be.getBlockState().is(Blocks.SPAWNER)) spawners++;
                    else if (be instanceof Container) storage++;
                }
            }
        }
        return new int[]{storage, spawners};
    }

    private static void found(Minecraft mc, LocalPlayer p, int storage, int spawners) {
        GraftersConfig.get().rtpBaseFinder = false;
        reset();
        String xyz = p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ();
        mc.keyboardHandler.setClipboard(xyz);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f));
        Util.chat("Possible base: " + storage + " storage blocks, " + spawners + " spawners near " + xyz + " (copied)");
    }
}
