package com.grafters.mods;

import com.grafters.GraftersConfig;
import com.grafters.GraftersState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Switches to the fastest hotbar tool for the block you are mining. */
public final class AutoTool {
    private AutoTool() {}

    private static int prev = -1;

    public static void restore(Minecraft mc) {
        if (prev >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(prev);
        prev = -1;
    }

    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        LocalPlayer p = mc.player;
        if (p == null) return;

        boolean mining = mc.options.keyAttack.isDown() && GraftersState.screen(mc) == null;
        HitResult hr = mc.hitResult;
        if (!mining || !(hr instanceof BlockHitResult bhr) || hr.getType() != HitResult.Type.BLOCK) {
            if (prev >= 0 && c.toolSwitchBack) restore(mc);
            else prev = -1;
            return;
        }

        BlockState st = mc.level.getBlockState(bhr.getBlockPos());
        int cur = p.getInventory().getSelectedSlot();
        int best = cur;
        float bestSpeed = p.getInventory().getItem(cur).getDestroySpeed(st);
        for (int i = 0; i < 9; i++) {
            float sp = p.getInventory().getItem(i).getDestroySpeed(st);
            if (sp > bestSpeed) { bestSpeed = sp; best = i; }
        }
        if (best != cur) {
            if (prev < 0) prev = cur;
            p.getInventory().setSelectedSlot(best);
        }
    }
}
