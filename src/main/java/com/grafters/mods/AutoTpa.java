package com.grafters.mods;

import com.grafters.GraftersConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;

import java.util.Locale;

/** Accepts teleport requests that show up in chat (whitelist by default). */
public final class AutoTpa {
    private AutoTpa() {}

    private static int pending;
    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            GraftersConfig c = GraftersConfig.get();
            if (!c.autoTpa || overlay) return;
            String s = message.getString().toLowerCase(Locale.ROOT);
            if (!isRequest(s)) return;

            if (c.tpaWhitelist) {
                boolean ok = false;
                for (String n : c.tpaNames.toLowerCase(Locale.ROOT).split(",")) {
                    n = n.trim();
                    if (!n.isEmpty() && s.contains(n)) ok = true;
                }
                if (!ok) {
                    Util.chat("Auto TPA ignored a request (not on your list). Add names with .set tpa name1,name2");
                    return;
                }
            }
            pending = Math.max(1, c.tpaDelay);
        });
    }

    private static boolean isRequest(String s) {
        if (!s.contains("teleport") && !s.contains("tpa")) return false;
        return s.contains("has requested") || s.contains("wants to teleport") || s.contains("requests to teleport")
                || s.contains("sent you") || s.contains("to teleport to you") || s.contains("to teleport you");
    }

    public static void tick(Minecraft mc) {
        if (pending > 0 && --pending == 0) {
            Util.command("tpaccept");
            Util.chat("Accepted teleport request");
        }
    }
}
