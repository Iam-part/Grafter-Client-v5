package com.grafters.mods;

import com.grafters.Freecam;
import com.grafters.GraftersConfig;
import com.grafters.GraftersState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

/** While gliding with an elytra, uses a firework rocket whenever you get slow. */
public final class AutoFirework {
    private AutoFirework() {}

    private static int cooldown;

    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        LocalPlayer p = mc.player;
        if (p == null || !p.isFallFlying() || GraftersState.screen(mc) != null || Freecam.isActive()) return;
        if (cooldown > 0) { cooldown--; return; }
        if (p.getDeltaMovement().horizontalDistance() >= c.fireworkMinSpeed) return;

        if (p.getOffhandItem().is(Items.FIREWORK_ROCKET)) {
            mc.gameMode.useItem(p, InteractionHand.OFF_HAND);
            cooldown = c.fireworkDelay;
            return;
        }
        for (int i = 0; i < 9; i++) {
            if (!p.getInventory().getItem(i).is(Items.FIREWORK_ROCKET)) continue;
            int prev = p.getInventory().getSelectedSlot();
            p.getInventory().setSelectedSlot(i);
            mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
            p.getInventory().setSelectedSlot(prev);
            cooldown = c.fireworkDelay;
            return;
        }
    }
}
