package com.grafters.mods;

import com.grafters.GraftersConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Disconnects when your health is low or another player gets close. */
public final class AutoLog {
    private AutoLog() {}

    public static void tick(Minecraft mc) {
        GraftersConfig c = GraftersConfig.get();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;

        if (p.getHealth() <= c.logHealth) { logout(mc, "health " + (int) p.getHealth()); return; }

        if (c.logPlayers) {
            double r2 = (double) c.logRange * c.logRange;
            for (AbstractClientPlayer o : mc.level.players()) {
                if (o == p) continue;
                if (p.distanceToSqr(o) <= r2) { logout(mc, o.getName().getString() + " nearby"); return; }
            }
        }
    }

    private static void logout(Minecraft mc, String reason) {
        GraftersConfig.get().autoLog = false;     // so you can rejoin without instantly leaving again
        mc.player.connection.getConnection().disconnect(Component.literal("[Grafters] AutoLog: " + reason));
    }
}
