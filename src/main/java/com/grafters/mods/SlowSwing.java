package com.grafters.mods;

import com.grafters.GraftersConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Visual only: stretches your arm swing animation. Each tick the swing only moves forward a
 * fraction of the time (1 / slowness), by holding the swing counter back on the other ticks.
 * Attack timing and damage are not changed.
 */
public final class SlowSwing {
    private SlowSwing() {}

    private static float acc;

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        float factor = Math.max(1.0f, GraftersConfig.get().slowSwingFactor);
        if (!p.swinging || p.swingTime <= 0) { acc = 0; return; }

        acc += 1.0f / factor;
        if (acc >= 1.0f) acc -= 1.0f;     // let the swing advance this tick
        else p.swingTime--;               // hold it back
    }
}
