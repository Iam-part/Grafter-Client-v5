package com.grafters.mods;

import com.grafters.GraftersConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Shift-clicks everything out of a container you opened. Only runs for real
 * containers you were looking at, never for server menus (auction house etc.).
 */
public final class AutoLoot {
    private AutoLoot() {}

    private static int menuId = -1;
    private static boolean allowed;
    private static int delay, stuck, lastSlot = -1;

    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        LocalPlayer p = mc.player;
        ChestMenu cm = Util.chestMenu(mc);
        if (p == null || cm == null) { menuId = -1; return; }

        if (cm.containerId != menuId) {            // a new container just opened
            menuId = cm.containerId;
            allowed = lookingAtContainer(mc);
            delay = 3; stuck = 0; lastSlot = -1;
        }
        if (!allowed) return;
        if (delay-- > 0) return;

        int rows = cm.getRowCount();
        for (int i = 0; i < rows * 9; i++) {
            if (!cm.getSlot(i).hasItem()) continue;
            if (i == lastSlot && ++stuck > 3) { finish(mc, c); return; }   // inventory full
            if (i != lastSlot) { lastSlot = i; stuck = 0; }
            Util.click(cm.containerId, i, 0, ContainerInput.QUICK_MOVE);
            delay = c.lootDelay;
            return;
        }
        finish(mc, c);
    }

    private static void finish(Minecraft mc, GraftersConfig c) {
        allowed = false;
        if (c.lootClose) mc.player.closeContainer();
    }

    private static boolean lookingAtContainer(Minecraft mc) {
        HitResult hr = mc.hitResult;
        if (!(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK) return false;
        if (mc.level.getBlockState(bhr.getBlockPos()).is(Blocks.ENDER_CHEST)) return true;
        BlockEntity be = mc.level.getBlockEntity(bhr.getBlockPos());
        return be instanceof Container;
    }
}
