package com.grafters.mods;

import com.grafters.Freecam;
import com.grafters.GraftersConfig;
import com.grafters.GraftersState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Glide assist: dives when you get slow and climbs when you get fast, so you
 * keep gliding without fireworks. In between, your own mouse input is untouched.
 */
public final class ElytraGlide {
    private ElytraGlide() {}

    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        LocalPlayer p = mc.player;
        if (p == null || !p.isFallFlying() || GraftersState.screen(mc) != null || Freecam.isActive()) return;

        double speed = p.getDeltaMovement().horizontalDistance();
        float pitch = p.getXRot();
        float target;
        if (speed < c.glideMin) target = c.glideDive;
        else if (speed > c.glideMax) target = -c.glideClimb;
        else return;

        pitch += (target - pitch) * 0.12f;
        p.setXRot(Mth.clamp(pitch, -80f, 80f));
    }
}
