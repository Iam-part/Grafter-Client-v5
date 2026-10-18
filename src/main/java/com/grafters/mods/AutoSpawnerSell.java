package com.grafters.mods;

import com.grafters.GraftersConfig;
import com.grafters.GraftersState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Opens the nearest spawner, takes its loot (filtered), closes it, then sells the loot
 * with /sell. Repeats on an interval.
 */
public final class AutoSpawnerSell {
    private AutoSpawnerSell() {}

    private enum S { IDLE, WAIT_MENU, TAKE, SELL }

    private static final SellTask SELL = new SellTask();
    private static S state = S.IDLE;
    private static int timer, wait, delay;
    private static boolean warned;

    public static void reset() { state = S.IDLE; timer = 0; wait = 0; warned = false; }

    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;

        if (state == S.SELL) {
            if (SELL.tick(mc)) { state = S.IDLE; timer = c.spawnerInterval; }
            return;
        }
        ChestMenu cm = Util.chestMenu(mc);

        switch (state) {
            case IDLE -> {
                if (timer-- > 0 || GraftersState.screen(mc) != null) return;
                BlockPos sp = nearestSpawner(mc, p);
                if (sp == null) {
                    timer = 100;
                    if (!warned) { warned = true; Util.chat("Auto Spawner Sell: no spawner within reach"); }
                    return;
                }
                warned = false;
                lookAt(p, Vec3.atCenterOf(sp));
                mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(sp), Direction.UP, sp, false));
                state = S.WAIT_MENU; wait = 0;
            }
            case WAIT_MENU -> {
                if (cm != null) { state = S.TAKE; delay = 4; }
                else if (++wait > 40) { state = S.IDLE; timer = 60; }
            }
            case TAKE -> {
                if (cm == null) { state = S.IDLE; timer = c.spawnerInterval; return; }
                if (delay-- > 0) return;
                for (int i = 0; i < cm.getRowCount() * 9; i++) {
                    ItemStack st = cm.getSlot(i).getItem();
                    if (st.isEmpty() || !Util.matches(st, c.spawnerItems)) continue;
                    Util.click(cm.containerId, i, 0, ContainerInput.QUICK_MOVE);
                    delay = 1;
                    return;
                }
                p.closeContainer();
                if (c.spawnerItems.isBlank()) { state = S.IDLE; timer = c.spawnerInterval; return; }
                SELL.start(c.spawnerItems);
                state = S.SELL;
            }
            default -> { }
        }
    }

    private static BlockPos nearestSpawner(Minecraft mc, LocalPlayer p) {
        BlockPos base = p.blockPosition();
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-5, -5, -5), base.offset(5, 5, 5))) {
            if (!mc.level.getBlockState(pos).is(Blocks.SPAWNER)) continue;
            double d = p.distanceToSqr(Vec3.atCenterOf(pos));
            if (d < bestD) { bestD = d; best = pos.immutable(); }
        }
        return best;
    }

    private static void lookAt(LocalPlayer p, Vec3 target) {
        Vec3 d = target.subtract(p.getEyePosition());
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        p.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        p.setXRot((float) -Math.toDegrees(Math.atan2(d.y, flat)));
    }
}
