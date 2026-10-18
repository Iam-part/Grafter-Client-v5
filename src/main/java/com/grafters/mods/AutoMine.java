package com.grafters.mods;

import com.grafters.Freecam;
import com.grafters.GraftersState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Holds the attack button so you keep mining whatever you look at. */
public final class AutoMine {
    private AutoMine() {}

    public static void tick(Minecraft mc) {
        if (mc.player == null || GraftersState.screen(mc) != null || Freecam.isActive()) { release(mc); return; }
        mc.options.keyAttack.setDown(true);
    }

    public static void release(Minecraft mc) {
        mc.options.keyAttack.setDown(false);
        KeyMapping.setAll();
    }
}
